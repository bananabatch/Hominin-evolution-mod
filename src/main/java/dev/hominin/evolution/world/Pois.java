package dev.hominin.evolution.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.BandNames;
import dev.hominin.evolution.band.Bands;
import dev.hominin.evolution.band.Relations;
import dev.hominin.evolution.band.ToolPiles;
import dev.hominin.evolution.mind.MentalMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Places worth knowing: the points of interest on the mental map.
 *
 * <ul>
 * <li><b>Fertile ground</b> and <b>megafauna breeding grounds</b> - hidden: you have to work out what they are.</li>
 * <li>A <b>termite super colony</b>, an <b>obsidian pool</b> (lava, and black glass at its edge).</li>
 * <li>A <b>spring</b> that runs in the driest days, where the animals come at dusk.</li>
 * <li>A <b>salt lick</b>, where the grazers come at first light.</li>
 * <li>An <b>old tool deposit</b> (erectus on) - a dead band's tools, or somebody's from long before.</li>
 * </ul>
 *
 * <p>Places are the band's knowledge, not one mind's: they do not take up room in your head, they pass down
 * when you evolve, and they die with the band. Every band out there knows one or two of its own. Stand
 * with one - allies, and close by - and it tells you what it knows, and learns what you do; a band you
 * used to stand with remembers what your old band showed it, if you can win it round again. A new band
 * always knows one place. And walking into one you do not know: <i>this place is worth keeping in mind</i> -
 * think on it (the think key) and it is yours.
 *
 * <p>Places are worth having, so they are worth fighting over: every one adds to the pressure on the
 * ground it lies in (see {@link Land}).
 */
public final class Pois extends SavedData {
    private static final String NAME = "hominin_evolution_pois";
    private static final int REGION = 384;
    /** How far round a point a band's knowledge of places reaches. */
    private static final int KNOWLEDGE_REACH = 320;
    private static final long HINT_GAP_TICKS = 6000L;

    public enum Kind {
        FERTILE("Fertile ground", 2, true, 0xFF7CB342, 24),
        TERMITES("Termite super colony", 4, false, 0xFFC06A30, 26),
        OBSIDIAN("Obsidian pool", 3, false, 0xFFFF7A10, 16),
        BREEDING("Megafauna breeding ground", 4, true, 0xFFE0C060, 40),
        TOOLS("Old tool deposit", 1, false, 0xFFA8A8B8, 10),
        SPRING("Spring", 2, false, 0xFF40A8FF, 14),
        LICK("Salt lick", 2, false, 0xFFF4F0DC, 12);

        public final String label;
        /** What having it in your ground adds to its pressure. */
        public final int pressure;
        /** Worked out rather than seen. */
        public final boolean hidden;
        public final int colour;
        /** How close you have to come to notice it. */
        public final int notice;

        Kind(String label, int pressure, boolean hidden, int colour, int notice) {
            this.label = label;
            this.pressure = pressure;
            this.hidden = hidden;
            this.colour = colour;
            this.notice = notice;
        }
    }

    /** One place. {@code placed}: whether the spring, lick or deposit has been laid out in the world yet. */
    public record Poi(String id, Kind kind, BlockPos pos, String label, boolean placed) {
        Poi withPlaced(BlockPos at) {
            return new Poi(id, kind, at, label, true);
        }
    }

    private final Map<String, Poi> pois = new LinkedHashMap<>();
    /** What each player's band knows. */
    private final Map<UUID, LinkedHashSet<String>> playerKnows = new HashMap<>();
    /** How many times each player's band knowledge has been handed down to a new kind. */
    private final Map<UUID, Integer> passedDown = new HashMap<>();
    /** What each wild band knows. */
    private final Map<UUID, Set<String>> bandKnows = new HashMap<>();
    /** "band|player": the places a band learned from that player's band. */
    private final Map<String, Set<String>> heldFor = new HashMap<>();
    /** "band|player": bands that stood with a band of yours that is gone. They know you. */
    private final Set<String> familiar = new HashSet<>();

    private static final Map<String, Long> hinted = new HashMap<>();
    private static final Map<String, Integer> toldToday = new HashMap<>();

    public static Pois of(ServerLevel level) {
        ServerLevel home = level.getServer().overworld();
        return home.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Pois::new, Pois::load), NAME);
    }

    private static String key(UUID band, UUID player) {
        return band + "|" + player;
    }

    private static long mix(long seed, long a, long b) {
        long h = seed * 31L + a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    private static boolean erectus(ServerPlayer player) {
        return Bands.erectusOn(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
    }

    // ------------------------------------------------------------ where places are

    /** Where one kind of region place lies in a region, if the region has one. */
    @Nullable
    private static BlockPos regionSite(ServerLevel level, Kind kind, int rx, int rz) {
        long salt = switch (kind) {
            case SPRING -> 0x5921CL;
            case LICK -> 0x11C4L;
            default -> 0x7001L;
        };
        float chance = switch (kind) {
            case SPRING -> 0.4F;
            case LICK -> 0.3F;
            default -> 0.3F;
        };
        long h = mix(level.getSeed() ^ salt, rx, rz);
        if ((Math.abs(h) % 1000L) >= (long) (chance * 1000.0F)) {
            return null;
        }
        int x = rx * REGION + 40 + (int) (Math.abs(h >> 8) % (REGION - 80));
        int z = rz * REGION + 40 + (int) (Math.abs(h >> 20) % (REGION - 80));
        BlockPos pos = new BlockPos(x, 64, z);
        var biome = level.getBiome(pos);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER)
                || biome.is(BiomeTags.IS_MOUNTAIN) || (kind == Kind.SPRING && biome.is(Biomes.DESERT))) {
            return null;
        }
        return pos;
    }

    private static String regionId(Kind kind, int rx, int rz) {
        return kind.name().toLowerCase() + "_" + rx + "_" + rz;
    }

    /** Every place within this reach of a point - worked out from the seed, or already known to the world. */
    public static List<Poi> near(ServerLevel level, BlockPos centre, int radius, boolean erectusOn) {
        Pois data = of(level);
        Map<String, Poi> found = new LinkedHashMap<>();
        double reach = (double) radius * radius;
        int r0x = Math.floorDiv(centre.getX() - radius, REGION);
        int r1x = Math.floorDiv(centre.getX() + radius, REGION);
        int r0z = Math.floorDiv(centre.getZ() - radius, REGION);
        int r1z = Math.floorDiv(centre.getZ() + radius, REGION);
        for (int rx = r0x; rx <= r1x; rx++) {
            for (int rz = r0z; rz <= r1z; rz++) {
                BlockPos breeding = Land.breedingGroundIn(level, rx, rz);
                if (breeding != null) {
                    found.put(Land.breedingKey(breeding), data.intern(Land.breedingKey(breeding), Kind.BREEDING, breeding,
                            Kind.BREEDING.label));
                }
                for (Kind kind : new Kind[] {Kind.SPRING, Kind.LICK, Kind.TOOLS}) {
                    if (kind == Kind.TOOLS && !erectusOn) {
                        continue;
                    }
                    BlockPos site = regionSite(level, kind, rx, rz);
                    if (site != null) {
                        String id = regionId(kind, rx, rz);
                        found.put(id, data.intern(id, kind, site, kind.label));
                    }
                }
            }
        }
        int c0x = Math.floorDiv((centre.getX() - radius) >> 4, dev.hominin.evolution.survival.Soils.CELL);
        int c1x = Math.floorDiv((centre.getX() + radius) >> 4, dev.hominin.evolution.survival.Soils.CELL);
        int c0z = Math.floorDiv((centre.getZ() - radius) >> 4, dev.hominin.evolution.survival.Soils.CELL);
        int c1z = Math.floorDiv((centre.getZ() + radius) >> 4, dev.hominin.evolution.survival.Soils.CELL);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                BlockPos patch = dev.hominin.evolution.survival.Soils.patchCentre(level, cx, cz);
                if (patch != null) {
                    String id = "fertile_" + dev.hominin.evolution.survival.Soils.patchKey(patch.getX() >> 4, patch.getZ() >> 4);
                    found.put(id, data.intern(id, Kind.FERTILE, patch, Kind.FERTILE.label));
                }
            }
        }
        for (dev.hominin.evolution.survival.Termites.Colony colony : dev.hominin.evolution.survival.Termites.colonies(level)) {
            if (!colony.thriving()) {
                continue;
            }
            String id = "termites_" + (colony.centre().getX() >> 4) + "_" + (colony.centre().getZ() >> 4);
            found.put(id, data.intern(id, Kind.TERMITES, colony.centre(), Kind.TERMITES.label));
        }
        for (Poi poi : data.pois.values()) {
            if (poi.kind() == Kind.OBSIDIAN || (poi.kind() == Kind.TOOLS && erectusOn)) {
                found.putIfAbsent(poi.id(), poi);
            }
        }
        List<Poi> list = new ArrayList<>();
        for (Poi poi : found.values()) {
            if (Bands.horizontal(poi.pos(), centre) <= reach) {
                list.add(poi);
            }
        }
        return list;
    }

    /** The world's record of a place, made the first time anyone thinks of it. */
    private Poi intern(String id, Kind kind, BlockPos pos, String label) {
        Poi existing = pois.get(id);
        if (existing != null) {
            return existing;
        }
        Poi poi = new Poi(id, kind, pos.immutable(), label, false);
        pois.put(id, poi);
        setDirty();
        return poi;
    }

    @Nullable
    public static Poi get(ServerLevel level, String id) {
        return of(level).pois.get(id);
    }

    /** The places within this reach, known or not, that add to the pressure of the ground. */
    public static List<Poi> inGround(ServerLevel level, BlockPos centre, int radius) {
        List<Poi> list = new ArrayList<>();
        for (Poi poi : of(level).pois.values()) {
            if ((poi.kind() == Kind.OBSIDIAN || poi.kind() == Kind.SPRING || poi.kind() == Kind.LICK
                    || poi.kind() == Kind.TOOLS) && Bands.horizontal(poi.pos(), centre) <= (double) radius * radius) {
                list.add(poi);
            }
        }
        return list;
    }

    // ------------------------------------------------------------ what your band knows

    public static boolean knows(ServerPlayer player, String id) {
        Set<String> known = of(player.serverLevel()).playerKnows.get(player.getUUID());
        return known != null && known.contains(id);
    }

    /** Everything your band knows, oldest first. */
    public static List<Poi> known(ServerPlayer player) {
        Pois data = of(player.serverLevel());
        List<Poi> list = new ArrayList<>();
        for (String id : data.playerKnows.getOrDefault(player.getUUID(), new LinkedHashSet<>())) {
            Poi poi = data.pois.get(id);
            if (poi != null) {
                list.add(poi);
            }
        }
        return list;
    }

    public static int passedDown(ServerPlayer player) {
        return of(player.serverLevel()).passedDown.getOrDefault(player.getUUID(), 0);
    }

    /** The band knows this place now. Returns false if it already did. */
    public static boolean learn(ServerPlayer player, Poi poi) {
        Pois data = of(player.serverLevel());
        boolean fresh = data.playerKnows.computeIfAbsent(player.getUUID(), k -> new LinkedHashSet<>()).add(poi.id());
        if (fresh) {
            data.setDirty();
            if (poi.kind().hidden) {
                Land.learn(player, poi.id());
            }
        }
        return fresh;
    }

    /** A place worked out from afar - thinking back over your ground: the band knows it now. */
    public static void learnAt(ServerPlayer player, BlockPos where, String id) {
        for (Poi poi : near(player.serverLevel(), where, 64, erectus(player))) {
            if (poi.id().equals(id)) {
                learn(player, poi);
                return;
            }
        }
    }

    private static String where(ServerPlayer player, Poi poi) {
        int distance = (int) Math.sqrt(Bands.horizontal(poi.pos(), player.blockPosition()));
        return poi.label() + ", " + distance + " blocks " + MentalMap.bearing(player, poi.pos());
    }

    private static MutableComponent leadLink(Poi poi) {
        return Component.literal(" [Lead me there]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/hominin lead place " + poi.pos().getX() + " " + poi.pos().getZ()))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Follow the pointer at the top of your screen."))));
    }

    /** A new band of yours: it knows one place of its own, somewhere near. */
    public static void newBandKnows(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<Poi> candidates = new ArrayList<>(near(level, player.blockPosition(), KNOWLEDGE_REACH, erectus(player)));
        candidates.removeIf(p -> knows(player, p.id()));
        if (candidates.isEmpty()) {
            return;
        }
        // Somewhere they would actually have been: the nearer places first.
        candidates.sort(Comparator.comparingDouble(p -> Bands.horizontal(p.pos(), player.blockPosition())));
        Poi poi = candidates.get(player.getRandom().nextInt(Math.min(3, candidates.size())));
        learn(player, poi);
        player.sendSystemMessage(Component.literal("Your new band knows a place: " + where(player, poi) + ". (On your map.)")
                .withStyle(ChatFormatting.AQUA).append(leadLink(poi)));
    }

    /** A band has come into being: it knows one or two places near its ground. */
    public static void assign(ServerLevel level, Bands.Record band) {
        if (band.nomadic()) {
            return;
        }
        Pois data = of(level);
        if (data.bandKnows.containsKey(band.id)) {
            return;
        }
        List<Poi> candidates = new ArrayList<>(near(level, band.home, KNOWLEDGE_REACH, Bands.erectusOn(band.species)));
        Set<String> knows = new LinkedHashSet<>();
        RandomSource random = level.random;
        int wanted = 1 + random.nextInt(2);
        while (knows.size() < wanted && !candidates.isEmpty()) {
            knows.add(candidates.remove(random.nextInt(candidates.size())).id());
        }
        data.bandKnows.put(band.id, knows);
        data.setDirty();
    }

    public static int bandKnowsCount(ServerLevel level, UUID band) {
        return of(level).bandKnows.getOrDefault(band, Set.of()).size();
    }

    // ------------------------------------------------------------ noticing, and working it out

    /** The nearest place close enough to notice that your band does not know yet. */
    @Nullable
    private static Poi unknownHere(ServerPlayer player) {
        Poi best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Poi poi : near(player.serverLevel(), player.blockPosition(), 48, erectus(player))) {
            double distance = Bands.horizontal(poi.pos(), player.blockPosition());
            if (distance > (double) poi.kind().notice * poi.kind().notice || knows(player, poi.id())) {
                continue;
            }
            if (poi.kind() == Kind.OBSIDIAN && Math.abs(poi.pos().getY() - player.getBlockY()) > 24) {
                continue;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = poi;
            }
        }
        return best;
    }

    /** A hold of the think key where a place is waiting to be noticed: it is the band's now. */
    public static boolean revealHere(ServerPlayer player) {
        Poi poi = unknownHere(player);
        if (poi == null) {
            return false;
        }
        learn(player, poi);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 0.9F);
        player.sendSystemMessage(Component.literal(reveal(poi.kind())).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Your band will remember this place: " + poi.label() + ". It is on your "
                + "map, and it passes down with the band.").withStyle(ChatFormatting.AQUA));
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.PLACES);
        return true;
    }

    private static String reveal(Kind kind) {
        return switch (kind) {
            case FERTILE -> "The soil here is dark and soft, full of roots and grubs. Fertile ground: it will feed you far "
                    + "longer than ordinary ground before it is picked clean, and it comes back sooner.";
            case BREEDING -> "The grass here is grazed short and trampled flat, the ground is full of dung, and there are "
                    + "young everywhere you look. The herds come back here, year on year, to calve - a breeding ground.";
            case TERMITES -> "Three great mounds, and the ground between them alive: a super colony. It never runs dry - "
                    + "as long as nobody builds too near it.";
            case OBSIDIAN -> "Lava, and at its edges black glass - obsidian, the sharpest stone there is. When the rain "
                    + "meets the lava it leaves more of it lying at the edge.";
            case TOOLS -> "Stone that was worked, and left: flakes, a chopper, a hand axe gone dull. Somebody kept their "
                    + "tools here, and never came back for them. What is still good is anybody's.";
            case SPRING -> "Water coming up out of the ground, clear and cold. A spring: it runs in the driest days, and "
                    + "at dusk the animals come to drink.";
            case LICK -> "Pale crust on the ground, licked hollow by a thousand tongues: salt. The grazers come to it at "
                    + "first light - and so can you.";
        };
    }

    /** Every five seconds: places made real as you come near, noticed as you walk into them, shared with allies. */
    public static void tick(ServerPlayer player) {
        if (player.isSpectator() || player.tickCount % 100 != 37) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return;
        }
        Pois data = of(level);
        if (player.tickCount % 600 == 37) {
            scanForObsidian(player, data);
        }
        List<Poi> close = near(level, player.blockPosition(), 96, erectus(player));
        for (Poi poi : close) {
            if (!poi.placed() && (poi.kind() == Kind.SPRING || poi.kind() == Kind.LICK || poi.kind() == Kind.TOOLS)) {
                materialise(level, data, poi);
            }
        }
        Poi unknown = unknownHere(player);
        if (unknown != null) {
            hint(player, unknown);
        }
        if (player.tickCount % 200 == 137) {
            share(player, data);
        }
        gatherings(player, data);
    }

    private static void hint(ServerPlayer player, Poi poi) {
        String id = player.getUUID() + "|" + poi.id();
        long now = player.level().getGameTime();
        if (now - hinted.getOrDefault(id, -HINT_GAP_TICKS) < HINT_GAP_TICKS) {
            return;
        }
        if (hinted.size() > 4096) {
            hinted.clear();
        }
        hinted.put(id, now);
        player.sendSystemMessage(Component.literal("This place is worth keeping in mind. (Hold ")
                .append(Component.keybind("key.hominin_evolution.think"))
                .append(Component.literal(" to think on it.)")).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.PLACES);
    }

    /** Surface lava near you: a pool, and wherever there is lava there will be glass. */
    private static void scanForObsidian(ServerPlayer player, Pois data) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        for (int i = 0; i < 24; i++) {
            int x = player.getBlockX() + random.nextInt(97) - 48;
            int z = player.getBlockZ() + random.nextInt(97) - 48;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            if (!level.getFluidState(top).is(FluidTags.LAVA)) {
                continue;
            }
            for (Poi poi : data.pois.values()) {
                if (poi.kind() == Kind.OBSIDIAN && Bands.horizontal(poi.pos(), top) < 40.0D * 40.0D) {
                    return;
                }
            }
            data.intern("obsidian_" + (x >> 5) + "_" + (z >> 5), Kind.OBSIDIAN, top, Kind.OBSIDIAN.label);
            return;
        }
    }

    // ------------------------------------------------------------ allies, and the bands that remember you

    private static boolean memberNear(ServerLevel level, Bands.Record band, ServerPlayer player, double radius) {
        return !level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(radius),
                m -> m.isAlive() && band.id.equals(m.getBandId())).isEmpty();
    }

    /**
     * Allies close by tell you what they know of the country, and learn what you do. A band that stood with a
     * band of yours that is gone remembers the places your old band showed it, once you are friends again.
     */
    private static void share(ServerPlayer player, Pois data) {
        ServerLevel level = player.serverLevel();
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || !band.knownTo(player.getUUID())) {
                continue;
            }
            int standing = Relations.standing(player, band);
            String key = key(band.id, player.getUUID());
            boolean ally = standing >= Relations.ALLIED;
            boolean remembers = data.familiar.contains(key) && standing >= Relations.FRIENDLY;
            if ((!ally && !remembers) || !memberNear(level, band, player, 48.0D)) {
                continue;
            }
            Set<String> theirs = ally ? data.bandKnows.getOrDefault(band.id, Set.of()) : data.heldFor.getOrDefault(key, Set.of());
            List<Poi> gained = new ArrayList<>();
            for (String id : theirs) {
                Poi poi = data.pois.get(id);
                if (poi != null && !knows(player, id) && (poi.kind() != Kind.TOOLS || erectus(player))) {
                    learn(player, poi);
                    gained.add(poi);
                }
            }
            if (ally) {
                Set<String> ours = data.playerKnows.getOrDefault(player.getUUID(), new LinkedHashSet<>());
                data.bandKnows.computeIfAbsent(band.id, k -> new LinkedHashSet<>()).addAll(ours);
                data.heldFor.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(ours);
                data.setDirty();
            }
            if (gained.isEmpty()) {
                continue;
            }
            player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + (ally
                    ? " tell you what they know of the country:" : " remember the places your old band showed them:"))
                    .withStyle(ChatFormatting.AQUA));
            for (Poi poi : gained) {
                player.sendSystemMessage(Component.literal("  " + where(player, poi)).withStyle(ChatFormatting.GRAY)
                        .append(leadLink(poi)));
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.5F, 1.1F);
        }
    }

    /**
     * "Let me tell you where things are." Knowledge is worth more than food: a band you are on speaking terms
     * with thinks better of you for every place you tell it about - up to a point, in a day.
     */
    public static void tellBand(ServerPlayer player, Bands.Record band) {
        ServerLevel level = player.serverLevel();
        if (!memberNear(level, band, player, 32.0D)) {
            player.displayClientMessage(Component.literal("None of " + band.name + " are near enough to tell."), true);
            return;
        }
        if (Relations.standing(player, band) < Relations.UNFRIENDLY) {
            player.displayClientMessage(Component.literal(BandNames.capital(band.name) + " will not listen to you."), true);
            return;
        }
        Pois data = of(level);
        Set<String> theirs = data.bandKnows.computeIfAbsent(band.id, k -> new LinkedHashSet<>());
        List<String> told = new ArrayList<>();
        for (String id : data.playerKnows.getOrDefault(player.getUUID(), new LinkedHashSet<>())) {
            if (theirs.add(id)) {
                data.heldFor.computeIfAbsent(key(band.id, player.getUUID()), k -> new LinkedHashSet<>()).add(id);
                Poi poi = data.pois.get(id);
                told.add(poi == null ? id : poi.label().toLowerCase());
            }
        }
        data.setDirty();
        if (told.isEmpty()) {
            player.displayClientMessage(Component.literal(BandNames.capital(band.name) + " already know everywhere you do."),
                    true);
            return;
        }
        String dayKey = player.getUUID() + "|" + band.id + "|" + level.getDayTime() / 24000L;
        int already = toldToday.getOrDefault(dayKey, 0);
        int gain = Math.min(told.size() * 2, Math.max(0, 6 - already));
        toldToday.put(dayKey, already + gain);
        if (toldToday.size() > 1024) {
            toldToday.clear();
        }
        player.sendSystemMessage(Component.literal("You tell " + band.name + " where things are: " + String.join(", ", told)
                + ". They listen closely.").withStyle(ChatFormatting.GREEN));
        if (gain > 0) {
            Relations.change(player, band, gain, "you told them where things are");
        }
    }

    /** Whether this band would tell you your old places back: it stood with a band of yours that is gone. */
    public static boolean familiar(ServerLevel level, UUID band, UUID player) {
        return of(level).familiar.contains(key(band, player));
    }

    // ------------------------------------------------------------ bands living and dying

    /**
     * Your band is gone, and what it knew went with it. Bands that stood with it will still know you - familiar,
     * if not friends - and they remember the places it showed them.
     */
    public static void bandLost(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Pois data = of(level);
        LinkedHashSet<String> lost = data.playerKnows.remove(player.getUUID());
        data.setDirty();
        if (lost != null && !lost.isEmpty()) {
            player.sendSystemMessage(Component.literal("What your band knew of the country died with it: " + lost.size()
                    + (lost.size() == 1 ? " place is" : " places are") + " gone from your map. A band you stood with might "
                    + "remember them.").withStyle(ChatFormatting.DARK_RED));
        }
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || Relations.standing(player, band) < Relations.ALLIED) {
                continue;
            }
            data.familiar.add(key(band.id, player.getUUID()));
            int after = Math.max(0, Relations.standing(player, band) - 15);
            band.standing.put(player.getUUID(), after);
            Bands.changed(level);
            player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " will still know you - a familiar "
                    + "face, if not a friend now. (Standing " + after + "/50.) Win them round and they will remember what "
                    + "your band showed them.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Evolving: a long time passes, but the places are still there, and the band still knows them. */
    public static void passDown(ServerPlayer player) {
        Pois data = of(player.serverLevel());
        int times = data.passedDown.merge(player.getUUID(), 1, Integer::sum);
        data.bandKnows.clear();
        data.heldFor.clear();
        data.familiar.clear();
        data.setDirty();
        int count = data.playerKnows.getOrDefault(player.getUUID(), new LinkedHashSet<>()).size();
        if (count > 0) {
            player.sendSystemMessage(Component.literal("What your band knew of the country is handed down: " + count
                    + (count == 1 ? " place" : " places") + (times > 1 ? ", " + times + " kinds of you on." : ".") + " (On "
                    + "your map.)").withStyle(ChatFormatting.AQUA));
        }
    }

    /**
     * A band is gone. What it knew of the country goes with it, and from erectus on its tools lie where its camp
     * was - an old tool deposit, for anybody.
     */
    @Nullable
    public static Poi bandDied(ServerLevel level, Bands.Record band) {
        Pois data = of(level);
        data.bandKnows.remove(band.id);
        data.heldFor.keySet().removeIf(k -> k.startsWith(band.id + "|"));
        data.familiar.removeIf(k -> k.startsWith(band.id + "|"));
        data.setDirty();
        BlockPos store = ToolPiles.orphan(level, band.id);
        if (band.nomadic() || !Bands.erectusOn(band.species)) {
            return null;
        }
        String id = "tools_band_" + band.id.toString().substring(0, 8);
        Poi poi = data.intern(id, Kind.TOOLS, store != null ? store : band.home,
                "Old tool deposit (" + BandNames.capital(band.name) + ")");
        if (store != null) {
            data.pois.put(id, poi.withPlaced(store));
        }
        return poi;
    }

    // ------------------------------------------------------------ laying places out in the world

    /** A spring, a lick or an old deposit, laid out the first time anyone is near enough for it to be there. */
    private static void materialise(ServerLevel level, Pois data, Poi poi) {
        BlockPos at = poi.pos();
        for (int dx : new int[] {-4, 4}) {
            for (int dz : new int[] {-4, 4}) {
                if (!level.hasChunk((at.getX() + dx) >> 4, (at.getZ() + dz) >> 4)) {
                    return;
                }
            }
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        BlockPos ground = new BlockPos(at.getX(), y - 1, at.getZ());
        RandomSource random = RandomSource.create(mix(level.getSeed(), at.getX(), at.getZ()));
        BlockPos placed = switch (poi.kind()) {
            case SPRING -> spring(level, ground, random);
            case LICK -> lick(level, ground, random);
            default -> ToolPiles.deposit(level, ground.above(), random, true);
        };
        data.pois.put(poi.id(), poi.withPlaced(placed != null ? placed : ground));
        data.setDirty();
    }

    /** A pool of clear water in a ring of mud and reeds. */
    @Nullable
    private static BlockPos spring(ServerLevel level, BlockPos ground, RandomSource random) {
        if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.above()).isEmpty()) {
            return ground;
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d = dx * dx + dz * dz;
                if (d > 10) {
                    continue;
                }
                int x = ground.getX() + dx;
                int z = ground.getZ() + dz;
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (Math.abs(top.getY() - ground.getY()) > 2) {
                    continue;
                }
                BlockPos surface = new BlockPos(x, ground.getY(), z);
                clearAbove(level, surface);
                if (d <= 2 || (d <= 4 && random.nextFloat() < 0.6F)) {
                    level.setBlock(surface.below(), Blocks.MUD.defaultBlockState(), 2);
                    level.setBlock(surface, Blocks.WATER.defaultBlockState(), 3);
                } else {
                    level.setBlock(surface, Blocks.MUD.defaultBlockState(), 2);
                    if (random.nextFloat() < 0.35F && level.getBlockState(surface.above()).isAir()) {
                        BlockState reed = random.nextBoolean() ? Blocks.SUGAR_CANE.defaultBlockState()
                                : Blocks.TALL_GRASS.defaultBlockState();
                        if (reed.canSurvive(level, surface.above())) {
                            level.setBlock(surface.above(), reed, 2);
                        }
                    }
                }
            }
        }
        return ground;
    }

    /** A pale crust of salt in the ground, trampled round by hooves. */
    @Nullable
    private static BlockPos lick(ServerLevel level, BlockPos ground, RandomSource random) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d = dx * dx + dz * dz;
                int x = ground.getX() + dx;
                int z = ground.getZ() + dz;
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (Math.abs(top.getY() - ground.getY()) > 2 || !level.getFluidState(top).isEmpty()
                        || !level.getBlockState(top).isSolid()) {
                    continue;
                }
                if (d <= 2 || (d <= 5 && random.nextFloat() < 0.55F)) {
                    clearAbove(level, top);
                    level.setBlock(top, Blocks.CALCITE.defaultBlockState(), 2);
                } else if (d <= 10 && random.nextFloat() < 0.5F) {
                    clearAbove(level, top);
                    level.setBlock(top, Blocks.COARSE_DIRT.defaultBlockState(), 2);
                }
            }
        }
        return ground;
    }

    private static void clearAbove(ServerLevel level, BlockPos surface) {
        BlockPos above = surface.above();
        BlockState state = level.getBlockState(above);
        if (!state.isAir() && state.canBeReplaced()) {
            level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // ------------------------------------------------------------ what places give

    /** Whether this water is a spring's: it never runs dry, and it is clean. */
    public static boolean isSpring(ServerLevel level, BlockPos water) {
        for (Poi poi : of(level).pois.values()) {
            if (poi.kind() == Kind.SPRING && poi.placed() && poi.pos().distSqr(water) < 5.0D * 5.0D) {
                return true;
            }
        }
        return false;
    }

    /** Licking the salt: once a day, something the body did not know it was missing. */
    public static boolean lickSalt(ServerPlayer player, BlockPos at) {
        if (!player.level().getBlockState(at).is(Blocks.CALCITE)) {
            return false;
        }
        Poi lick = null;
        for (Poi poi : of(player.serverLevel()).pois.values()) {
            if (poi.kind() == Kind.LICK && poi.placed() && poi.pos().distSqr(at) < 5.0D * 5.0D) {
                lick = poi;
            }
        }
        if (lick == null) {
            return false;
        }
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int day = (int) (player.level().getDayTime() / 24000L);
        if (counters.getOrDefault("salt_day", -1) == day) {
            player.displayClientMessage(Component.literal("You have had your fill of salt today."), true);
            return true;
        }
        counters.put("salt_day", day);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, at, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.3F);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 0));
        player.getFoodData().eat(1, 0.6F);
        player.displayClientMessage(Component.literal("You lick the salt crust. Something the body did not know it was "
                + "missing."), true);
        if (!knows(player, lick.id())) {
            learn(player, lick);
        }
        return true;
    }

    private static final Map<String, Integer> gathered = new HashMap<>();

    /**
     * The animals keep their own hours at these places: at a spring the grazers come to drink at dusk, at a
     * lick they come at first light. Once a day each, if you are there to see it.
     */
    private static void gatherings(ServerPlayer player, Pois data) {
        ServerLevel level = player.serverLevel();
        long time = level.getDayTime() % 24000L;
        boolean dusk = time >= 11000L && time < 13000L;
        boolean dawn = time >= 23000L || time < 1000L;
        if (!dusk && !dawn) {
            return;
        }
        int day = (int) ((level.getDayTime() + 1000L) / 24000L);
        for (Poi poi : data.pois.values()) {
            boolean spring = poi.kind() == Kind.SPRING && dusk;
            boolean lick = poi.kind() == Kind.LICK && dawn;
            if ((!spring && !lick) || !poi.placed()) {
                continue;
            }
            double distance = Bands.horizontal(poi.pos(), player.blockPosition());
            if (distance > 96.0D * 96.0D || distance < 20.0D * 20.0D) {
                continue;
            }
            if (gathered.getOrDefault(poi.id(), -1) == day) {
                continue;
            }
            gathered.put(poi.id(), day);
            dev.hominin.evolution.entity.WildAnimals.gatherAt(player, poi.pos(), spring);
        }
    }

    // ------------------------------------------------------------ saving

    private static Pois load(CompoundTag tag, HolderLookup.Provider registries) {
        Pois data = new Pois();
        for (Tag entry : tag.getList("Pois", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) entry;
            Kind kind;
            try {
                kind = Kind.valueOf(p.getString("Kind"));
            } catch (IllegalArgumentException e) {
                continue;
            }
            data.pois.put(p.getString("Id"), new Poi(p.getString("Id"), kind, BlockPos.of(p.getLong("Pos")),
                    p.getString("Label"), p.getBoolean("Placed")));
        }
        for (Tag entry : tag.getList("PlayerKnows", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) entry;
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (Tag id : p.getList("Ids", Tag.TAG_STRING)) {
                ids.add(id.getAsString());
            }
            data.playerKnows.put(p.getUUID("Player"), ids);
            data.passedDown.put(p.getUUID("Player"), p.getInt("PassedDown"));
        }
        for (Tag entry : tag.getList("BandKnows", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) entry;
            Set<String> ids = new LinkedHashSet<>();
            for (Tag id : p.getList("Ids", Tag.TAG_STRING)) {
                ids.add(id.getAsString());
            }
            data.bandKnows.put(p.getUUID("Band"), ids);
        }
        for (Tag entry : tag.getList("HeldFor", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) entry;
            Set<String> ids = new LinkedHashSet<>();
            for (Tag id : p.getList("Ids", Tag.TAG_STRING)) {
                ids.add(id.getAsString());
            }
            data.heldFor.put(p.getString("Key"), ids);
        }
        for (Tag entry : tag.getList("Familiar", Tag.TAG_STRING)) {
            data.familiar.add(entry.getAsString());
        }
        return data;
    }

    private static ListTag strings(Set<String> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id)));
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Poi poi : pois.values()) {
            CompoundTag p = new CompoundTag();
            p.putString("Id", poi.id());
            p.putString("Kind", poi.kind().name());
            p.putLong("Pos", poi.pos().asLong());
            p.putString("Label", poi.label());
            p.putBoolean("Placed", poi.placed());
            list.add(p);
        }
        tag.put("Pois", list);
        ListTag players = new ListTag();
        Set<UUID> everyone = new HashSet<>(playerKnows.keySet());
        everyone.addAll(passedDown.keySet());
        for (UUID player : everyone) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Player", player);
            p.put("Ids", strings(playerKnows.getOrDefault(player, new LinkedHashSet<>())));
            p.putInt("PassedDown", passedDown.getOrDefault(player, 0));
            players.add(p);
        }
        tag.put("PlayerKnows", players);
        ListTag bands = new ListTag();
        for (var entry : bandKnows.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Band", entry.getKey());
            p.put("Ids", strings(entry.getValue()));
            bands.add(p);
        }
        tag.put("BandKnows", bands);
        ListTag held = new ListTag();
        for (var entry : heldFor.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putString("Key", entry.getKey());
            p.put("Ids", strings(entry.getValue()));
            held.add(p);
        }
        tag.put("HeldFor", held);
        tag.put("Familiar", strings(familiar));
        return tag;
    }

    // ------------------------------------------------------------ for testing

    /** Every place within reach, known at once. Returns how many were new. */
    public static int revealAll(ServerPlayer player, int radius) {
        int fresh = 0;
        for (Poi poi : near(player.serverLevel(), player.blockPosition(), radius, erectus(player))) {
            if (learn(player, poi)) {
                fresh++;
            }
        }
        return fresh;
    }

    /** Forgets everything the band knows. */
    public static void forgetAll(ServerPlayer player) {
        Pois data = of(player.serverLevel());
        data.playerKnows.remove(player.getUUID());
        data.setDirty();
    }

    /** A place of this kind, here, now: for trying things out. */
    public static Poi makeHere(ServerPlayer player, Kind kind) {
        Pois data = of(player.serverLevel());
        String id = "dev_" + kind.name().toLowerCase() + "_" + player.getBlockX() + "_" + player.getBlockZ();
        BlockPos at = player.blockPosition();
        Poi poi = new Poi(id, kind, at, kind.label, false);
        data.pois.put(id, poi);
        data.setDirty();
        if (kind == Kind.SPRING || kind == Kind.LICK || kind == Kind.TOOLS) {
            BlockPos offset = at.relative(player.getDirection(), 6);
            poi = new Poi(id, kind, offset, kind.label, false);
            data.pois.put(id, poi);
            materialise(player.serverLevel(), data, poi);
            poi = data.pois.get(id);
        }
        return poi;
    }

    /** Whether a species this far along lived by the kinds of places old tools are found in. */
    public static boolean toolsInEra(ResourceLocation species) {
        return Bands.erectusOn(species);
    }

    private Pois() {
    }
}
