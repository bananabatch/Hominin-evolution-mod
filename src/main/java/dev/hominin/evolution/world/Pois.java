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
        LICK("Salt lick", 2, false, 0xFFF4F0DC, 12),
        /** A cold hearth and a place to sleep, where somebody alone keeps going. */
        CAMP("Lone camp", 1, false, 0xFFB08A60, 14),
        /** A great deal of chert in one place: a seam in a river bank, or a heap of it. */
        CHERT("Chert super deposit", 3, false, 0xFFD6A050, 20),
        /** Where a peaceful bonobo troop lives: nothing hunts there - which every band wants. */
        BONOBO("Bonobo country", 3, false, 0xFFD08AC8, 32),
        /** A spring, a great seam of stone and more, all together: the best ground there is. Every band knows it. */
        HAVEN("Haven", 2, false, 0xFFFFD84A, 48),
        /** Gravel along a river bank, in patches: worth sifting for stone. */
        GRAVEL("Gravel deposit", 1, false, 0xFF9C948A, 12),
        /** A river's whole lining gone to gravel, five blocks up the bank: a great deal of stone to sift. */
        SUPER_GRAVEL("Gravel super deposit", 3, false, 0xFFC4B8A6, 20),
        /** Pockets of sea water left in the rocks along a shore, with food in them. */
        TIDE_POOL("Tide pools", 3, false, 0xFF4FC3C0, 14),
        /** A pool in dry country in a horseshoe of hill: everything comes to drink at dawn. */
        OASIS("Oasis", 5, false, 0xFF3CB4A0, 40);

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

    /** Found once per region and kind: sampling biomes is not free. */
    private static final Map<String, java.util.Optional<BlockPos>> waterSites = new HashMap<>();

    /**
     * Places that belong to a kind of country: gravel on a river, tide pools on a shore, an oasis in dry country.
     * The region looks over itself for somewhere that fits, if it has one of these at all.
     */
    @Nullable
    private static BlockPos waterSite(ServerLevel level, Kind kind, int rx, int rz) {
        String id = regionId(kind, rx, rz);
        java.util.Optional<BlockPos> known = waterSites.get(id);
        if (known != null) {
            return known.orElse(null);
        }
        long salt = kind == Kind.GRAVEL ? 0x6EA7L : kind == Kind.SUPER_GRAVEL ? 0x5E6EA7L : kind == Kind.TIDE_POOL
                ? 0x71DEL : 0x0A515L;
        float chance = kind == Kind.GRAVEL ? 0.6F : kind == Kind.SUPER_GRAVEL ? 0.22F : kind == Kind.TIDE_POOL ? 0.55F
                : 0.16F;
        long h = mix(level.getSeed() ^ salt, rx, rz);
        BlockPos found = null;
        if ((Math.abs(h) % 1000L) < (long) (chance * 1000.0F)) {
            for (int i = 0; i < 20 && found == null; i++) {
                long hi = mix(h, i, 7L);
                int x = rx * REGION + 32 + (int) (Math.abs(hi >> 8) % (REGION - 64));
                int z = rz * REGION + 32 + (int) (Math.abs(hi >> 24) % (REGION - 64));
                BlockPos pos = new BlockPos(x, 64, z);
                var biome = level.getBiome(pos);
                boolean fits = switch (kind) {
                    case GRAVEL, SUPER_GRAVEL -> biome.is(BiomeTags.IS_RIVER);
                    case TIDE_POOL -> biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.STONY_SHORE);
                    default -> biome.is(BiomeTags.IS_SAVANNA) || biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS)
                            || biome.is(Biomes.PLAINS);
                };
                if (fits) {
                    found = pos;
                }
            }
        }
        if (waterSites.size() > 4096) {
            waterSites.clear();
        }
        waterSites.put(id, java.util.Optional.ofNullable(found));
        return found;
    }

    /** Where one kind of region place lies in a region, if the region has one. */
    @Nullable
    private static BlockPos regionSite(ServerLevel level, Kind kind, int rx, int rz) {
        long salt = switch (kind) {
            case SPRING -> 0x5921CL;
            case LICK -> 0x11C4L;
            case CAMP -> 0xCA4FL;
            case CHERT -> 0xC4E27L;
            default -> 0x7001L;
        };
        float chance = switch (kind) {
            case SPRING -> 0.4F;
            case LICK -> 0.3F;
            case CAMP -> 0.7F;
            case CHERT -> 0.3F;
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
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH)
                || biome.is(BiomeTags.IS_RIVER) && kind != Kind.CHERT
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
                for (Kind kind : new Kind[] {Kind.GRAVEL, Kind.SUPER_GRAVEL, Kind.TIDE_POOL, Kind.OASIS}) {
                    BlockPos site = waterSite(level, kind, rx, rz);
                    if (site != null) {
                        String id = regionId(kind, rx, rz);
                        found.put(id, data.intern(id, kind, site, kind.label));
                    }
                }
                for (Kind kind : new Kind[] {Kind.SPRING, Kind.LICK, Kind.TOOLS, Kind.CAMP, Kind.CHERT}) {
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
        Havens.addPlaces(level, data, centre, radius, erectusOn, found);
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
    Poi intern(String id, Kind kind, BlockPos pos, String label) {
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
                    || poi.kind() == Kind.TOOLS || poi.kind() == Kind.CHERT || poi.kind() == Kind.HAVEN)
                    && Bands.horizontal(poi.pos(), centre) <= (double) radius * radius) {
                list.add(poi);
            }
        }
        return list;
    }

    // ------------------------------------------------------------ what your band knows

    /** How many kinds of you on this player is: 0 for the first, one more at every evolution. */
    public static int generationOf(ServerLevel level, UUID player) {
        return of(level).passedDown.getOrDefault(player, 0);
    }

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

    static String where(ServerPlayer player, Poi poi) {
        int distance = (int) Math.sqrt(Bands.horizontal(poi.pos(), player.blockPosition()));
        return poi.label() + ", " + distance + " blocks " + MentalMap.bearing(player, poi.pos());
    }

    static MutableComponent leadLink(Poi poi) {
        return Component.literal(" [Lead me there]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/hominin lead place " + poi.pos().getX() + " " + poi.pos().getZ()))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Follow the pointer at the top of your screen."))));
    }

    /** How many places a new band of this kind already knows: one more with every step of the line. */
    public static int placesKnownBy(@Nullable net.minecraft.resources.ResourceLocation stage) {
        return switch (dev.hominin.evolution.stage.Kinds.line(stage)) {
            case "ardipithecus", "australopithecus" -> 1;
            case "homo_habilis" -> 2;
            case "homo_erectus" -> 3;
            default -> stage == null ? 1 : 4;
        };
    }

    /** A new band of yours: it knows places of its own, somewhere near - more, the further on your line is. */
    public static void newBandKnows(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<Poi> candidates = new ArrayList<>(near(level, player.blockPosition(), KNOWLEDGE_REACH, erectus(player)));
        candidates.removeIf(p -> knows(player, p.id()));
        if (candidates.isEmpty()) {
            return;
        }
        // Somewhere they would actually have been: the nearer places first.
        candidates.sort(Comparator.comparingDouble(p -> Bands.horizontal(p.pos(), player.blockPosition())));
        int wanted = placesKnownBy(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        for (int i = 0; i < wanted && !candidates.isEmpty(); i++) {
            Poi poi = candidates.remove(player.getRandom().nextInt(Math.min(3, candidates.size())));
            learn(player, poi);
            player.sendSystemMessage(Component.literal("Your new band knows a place: " + where(player, poi)
                    + ". (On your map.)").withStyle(ChatFormatting.AQUA).append(leadLink(poi)));
        }
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
        // And every haven within a long walk: everybody knows those.
        for (Havens.Site site : Havens.near(level, band.home, Havens.EVERYONE_KNOWS)) {
            knows.add(site.id());
        }
        data.bandKnows.put(band.id, knows);
        data.setDirty();
    }

    /** A band has taken you in: everything it knew, your band knows now. */
    public static void bandJoined(ServerPlayer player, Bands.Record band) {
        Pois data = of(player.serverLevel());
        for (String id : data.bandKnows.getOrDefault(band.id, Set.of())) {
            Poi poi = data.pois.get(id);
            if (poi != null) {
                learn(player, poi);
            }
        }
        data.bandKnows.remove(band.id);
        data.setDirty();
    }

    /** A band has heard of a place. */
    public static void bandLearns(ServerLevel level, UUID band, String id) {
        Pois data = of(level);
        if (data.bandKnows.computeIfAbsent(band, k -> new LinkedHashSet<>()).add(id)) {
            data.setDirty();
        }
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
            case CAMP -> "A cold hearth, a log to sit on, a few stones knocked about. Somebody lives here alone - whoever "
                    + "is left when a band is gone. Come back after a long time, and someone else will be.";
            case CHERT -> "Chert, and more of it than you have ever seen in one place: a whole seam of it, waxy and "
                    + "sharp-edged. Enough stone here for a band's lifetime - and every band for miles knows it.";
            case LICK -> "Pale crust on the ground, licked hollow by a thousand tongues: salt. The grazers come to it at "
                    + "first light - and so can you.";
            case HAVEN -> "A spring, a great seam of stone, and everything else a band could want, all within a stone's "
                    + "throw: a haven. Every band for miles knows it - and would like to live here.";
            case BONOBO -> "Bonobos, and nothing else: no cats, no hyenas, nothing that hunts comes near them. The "
                    + "safest ground there is - for as long as nobody hurts them. Every band for miles would like to "
                    + "live here.";
            case GRAVEL -> "Gravel along the water, washed down out of the hills: there is stone in it for whoever sifts "
                    + "(sneak-use it with your hands free).";
            case SUPER_GRAVEL -> "The whole bank gone to gravel, and the river bed under it: more stone to sift than a "
                    + "band could use. Every band for miles knows it.";
            case TIDE_POOL -> "Pockets of sea water left in the rocks: work a stick through one, crouching, and see what "
                    + "comes up - oysters, clams, small fish, now and then an octopus.";
            case OASIS -> "A great pool in dry country, in a horseshoe of hill - gravel at its edge, good stone lying "
                    + "about. At first light everything comes down to drink, even the hunters, who leave you be. Every "
                    + "band would live here if it could.";
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
            scanForBonobos(player, data);
        }
        List<Poi> close = near(level, player.blockPosition(), 96, erectus(player));
        for (Poi poi : close) {
            if (!poi.placed() && (poi.kind() == Kind.SPRING || poi.kind() == Kind.LICK || poi.kind() == Kind.TOOLS
                    || poi.kind() == Kind.CAMP || poi.kind() == Kind.CHERT || poi.kind() == Kind.GRAVEL
                    || poi.kind() == Kind.SUPER_GRAVEL || poi.kind() == Kind.TIDE_POOL || poi.kind() == Kind.OASIS
                    || poi.kind() == Kind.TERMITES && poi.id().startsWith("haven"))) {
                materialise(level, data, poi, player);
            }
        }
        for (Poi poi : close) {
            if (poi.placed() && poi.kind() == Kind.CAMP
                    && Bands.horizontal(poi.pos(), player.blockPosition()) < 48.0D * 48.0D) {
                lonerAt(player, data, poi);
            }
        }
        if (player.tickCount % 600 == 37) {
            lonelyCalls(player, data);
            // A lone camp's fire is kept going by whoever sits at it.
            for (Poi poi : near(level, player.blockPosition(), 200, erectus(player))) {
                if (poi.kind() == Kind.CAMP && poi.placed() && level.isLoaded(poi.pos())
                        && level.getBlockState(poi.pos()).is(ModBlocks.FIRE_PIT.get())) {
                    dev.hominin.evolution.band.WildCamps.tend(level, poi.pos());
                }
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

    /** A peaceful bonobo troop near you: bonobo country, a place in its own right. */
    private static void scanForBonobos(ServerPlayer player, Pois data) {
        ServerLevel level = player.serverLevel();
        for (dev.hominin.evolution.entity.Bonobo bonobo : level.getEntitiesOfClass(dev.hominin.evolution.entity.Bonobo.class,
                player.getBoundingBox().inflate(48.0D), b -> b.isAlive() && !b.isBetrayed())) {
            BlockPos at = bonobo.blockPosition();
            for (Poi poi : data.pois.values()) {
                if (poi.kind() == Kind.BONOBO && Bands.horizontal(poi.pos(), at) < 64.0D * 64.0D) {
                    return;
                }
            }
            data.intern("bonobo_" + (at.getX() >> 6) + "_" + (at.getZ() >> 6), Kind.BONOBO, at, Kind.BONOBO.label);
            return;
        }
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
            LandReveal.Telling telling = LandReveal.telling();
            for (Poi poi : gained) {
                telling.around(level, poi.pos(), LandReveal.AROUND_PLACE);
            }
            telling.send(player);
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
     * "What do you know of the country?" A band you are on terms with tells you: everything, if you are friends;
     * a place or two, if you are only neutral - once a day. Either way they tell you the lie of the land round
     * their own ground and round every place they name, and it goes on your map.
     */
    public static void askBand(ServerPlayer player, Bands.Record band) {
        ServerLevel level = player.serverLevel();
        if (!memberNear(level, band, player, 32.0D)) {
            player.displayClientMessage(Component.literal("None of " + band.name + " are near enough to ask."), true);
            return;
        }
        int standing = Relations.standing(player, band);
        if (standing < Relations.NEUTRAL) {
            player.displayClientMessage(Component.literal(BandNames.capital(band.name) + " will tell you nothing."), true);
            return;
        }
        var counters = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        String key = "asked_" + band.id.toString().substring(0, 8);
        int day = (int) (level.getDayTime() / 24000L);
        if (counters.getOrDefault(key, -1) == day) {
            player.displayClientMessage(Component.literal("You have asked " + band.name + " already today."), true);
            return;
        }
        counters.put(key, day);
        Pois data = of(level);
        List<Poi> gained = new ArrayList<>();
        int most = standing >= Relations.FRIENDLY ? Integer.MAX_VALUE : 2;
        for (String id : data.bandKnows.getOrDefault(band.id, Set.of())) {
            Poi poi = data.pois.get(id);
            if (poi != null && !knows(player, id) && (poi.kind() != Kind.TOOLS || erectus(player)) && gained.size() < most) {
                learn(player, poi);
                gained.add(poi);
            }
        }
        // Bands know things about each other, too.
        dev.hominin.evolution.band.Postures.gossip(player, band);
        LandReveal.Telling telling = LandReveal.telling().around(level, band.home, band.radius());
        for (Poi poi : gained) {
            telling.around(level, poi.pos(), LandReveal.AROUND_PLACE);
        }
        telling.send(player);
        player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " tell you the lie of their land"
                + (gained.isEmpty() ? " - and nowhere you do not already know. (On your map.)"
                        : ", and of places beyond it (on your map):")).withStyle(ChatFormatting.AQUA));
        for (Poi poi : gained) {
            player.sendSystemMessage(Component.literal("  " + where(player, poi)).withStyle(ChatFormatting.GRAY)
                    .append(leadLink(poi)));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.5F, 1.1F);
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
        data.pois.replaceAll((id, poi) -> poi.kind() == Kind.LICK || poi.kind() == Kind.TOOLS
                || poi.kind() == Kind.CHERT || poi.kind() == Kind.CAMP
                ? new Poi(poi.id(), poi.kind(), poi.pos(), poi.label(), false) : poi);
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

    /** Whether a player's band has its camp within a stone's throw of here. */
    private static boolean ownCampNear(ServerLevel level, BlockPos at) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (dev.hominin.evolution.hunt.Predation.settled(player)
                    && Bands.horizontal(dev.hominin.evolution.hunt.Predation.campOf(player), at) < 48.0D * 48.0D) {
                return true;
            }
        }
        return false;
    }

    /** How far along a lone camp is laid out: 0 before habilis, 1 habilis, 2 erectus and later. */
    private static int campTier(@Nullable ServerPlayer near) {
        ResourceLocation stage = near == null ? null : near.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        if (stage == null) {
            return 0;
        }
        return Bands.erectusOn(stage) ? 2 : dev.hominin.evolution.stage.Kinds.line(stage).equals("homo_habilis") ? 1 : 0;
    }

    /**
     * A spring, a lick or an old deposit, laid out the first time anyone is near enough for it to be there. A lone
     * camp is laid out as the kind of whoever came near lives - and again, differently, every time you evolve.
     */
    private static void materialise(ServerLevel level, Pois data, Poi poi, @Nullable ServerPlayer near) {
        BlockPos at = poi.pos();
        // The big places are laid out whole or not at all: every chunk they touch has to be there.
        int span = poi.kind() == Kind.OASIS ? 26 : poi.kind() == Kind.SUPER_GRAVEL ? 24 : poi.kind() == Kind.TERMITES ? 16 : 4;
        for (int dx : new int[] {-span, span}) {
            for (int dz : new int[] {-span, span}) {
                if (!level.hasChunk((at.getX() + dx) >> 4, (at.getZ() + dz) >> 4)) {
                    return;
                }
            }
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        BlockPos ground = new BlockPos(at.getX(), y - 1, at.getZ());
        if (poi.kind() != Kind.SPRING && !level.getFluidState(ground).isEmpty()) {
            // It fell on water: the nearest dry ground instead - and if there is none near, not here at all.
            BlockPos dry = dryGround(level, ground, 24);
            if (dry == null) {
                data.pois.put(poi.id(), poi.withPlaced(ground));
                data.setDirty();
                return;
            }
            ground = dry;
        }
        if (poi.kind() == Kind.CAMP && ownCampNear(level, ground)) {
            // Somebody's band lives right here now: nobody else's camp is laid out over theirs.
            data.pois.put(poi.id(), poi.withPlaced(ground));
            data.setDirty();
            return;
        }
        RandomSource random = RandomSource.create(mix(level.getSeed(), at.getX(), at.getZ()));
        BlockPos placed = switch (poi.kind()) {
            case SPRING -> spring(level, ground, random);
            case LICK -> lick(level, ground, random);
            case CAMP -> camp(level, ground, RandomSource.create(mix(level.getSeed() ^ level.getGameTime(), at.getX(),
                    at.getZ())), campTier(near));
            case CHERT -> chertSeam(level, data, poi, ground, random);
            case GRAVEL -> gravelBeds(level, ground, random, false);
            case SUPER_GRAVEL -> gravelBeds(level, ground, random, true);
            case TIDE_POOL -> dev.hominin.evolution.survival.TidePools.layOut(level, ground, random);
            case OASIS -> {
                BlockPos laid = Oases.layOut(level, ground, random);
                Oases.maybeInhabit(level, laid, near);
                yield laid;
            }
            case TERMITES -> {
                // A haven's own super colony: always there.
                dev.hominin.evolution.world.feature.TermiteMoundFeature.placeHavenColony(level, random, ground);
                yield ground;
            }
            default -> ToolPiles.deposit(level, ground.above(), random, true);
        };
        data.pois.put(poi.id(), poi.withPlaced(placed != null ? placed : ground));
        data.setDirty();
    }

    // ------------------------------------------------------------ lone camps, and whoever sits in them

    /** "poi|player": the generation - how many kinds of you on - a loner last sat at this place for this player. */
    private final Map<String, Integer> lonerFor = new HashMap<>();

    /**
     * Whoever is left at a lone camp - fresh every time you evolve. The only place a lone hominin is ever found: sitting
     * by the dead of the band they lost, one of them before habilis, one or two after. Of your own kind, grieving,
     * and willing to come with whoever comes for them.
     */
    private static void lonerAt(ServerPlayer player, Pois data, Poi poi) {
        String key = poi.id() + "|" + player.getUUID();
        int generation = data.passedDown.getOrDefault(player.getUUID(), 0);
        if (data.lonerFor.getOrDefault(key, -1) == generation) {
            return;
        }
        if (!dev.hominin.evolution.band.Band.hasRoomFor(player)) {
            // A loner nobody could take in is not worth sitting there: they will be there once there is room.
            return;
        }
        ServerLevel level = player.serverLevel();
        data.lonerFor.put(key, generation);
        data.setDirty();
        RandomSource random = RandomSource.create(mix(level.getSeed() ^ generation, poi.pos().getX(), poi.pos().getZ()));
        int count = campTier(player) >= 1 ? 1 + random.nextInt(2) : 1;
        List<BlockPos> dead = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(poi.pos().offset(-8, -3, -8), poi.pos().offset(8, 3, 8))) {
            if (level.getBlockState(at).is(ModBlocks.HOMININ_CARCASS.get())) {
                dead.add(at.immutable());
            }
        }
        if (dead.isEmpty()) {
            // The dead were taken by something: whoever is left sits where they lay.
            BlockPos laid = dev.hominin.evolution.hunt.Carcasses.placeCarcass(level,
                    poi.pos().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2),
                    ModBlocks.HOMININ_CARCASS.get().defaultBlockState());
            dead.add(laid != null ? laid : poi.pos());
        }
        for (int i = 0; i < count; i++) {
            BandMember loner = dev.hominin.evolution.ModEntities.BAND_MEMBER.get().create(level);
            if (loner == null) {
                return;
            }
            BlockPos by = dead.get(i % dead.size());
            BlockPos spot = dev.hominin.evolution.band.Band.standingSpotNear(level, by, 1, random.nextFloat() * 6.2831855F);
            loner.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            loner.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), net.minecraft.world.entity.MobSpawnType.EVENT,
                    null);
            loner.setStage(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
            loner.ensureName();
            loner.setGrieving(true);
            loner.setHunger(BandMember.MAX_HUNGER / 2);
            loner.setPersistenceRequired();
            level.addFreshEntity(loner);
        }
    }

    /**
     * The calls of the lonely. Somewhere out there, somebody alone is calling for anyone at all - a lone camp within a
     * long walk that still has someone at it. Heard now and then, from the way it lies; it goes on your map.
     */
    private static void lonelyCalls(ServerPlayer player, Pois data) {
        if (!dev.hominin.evolution.band.Band.hasRoomFor(player) || player.getRandom().nextBoolean()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int generation = data.passedDown.getOrDefault(player.getUUID(), 0);
        Poi best = null;
        double bestDistance = 260.0D * 260.0D;
        for (Poi poi : near(level, player.blockPosition(), 260, erectus(player))) {
            double d = Bands.horizontal(poi.pos(), player.blockPosition());
            if (poi.kind() != Kind.CAMP || d < 48.0D * 48.0D || d >= bestDistance
                    || data.lonerFor.getOrDefault(poi.id() + "|" + player.getUUID(), -1) == generation) {
                continue;
            }
            best = poi;
            bestDistance = d;
        }
        if (best == null) {
            return;
        }
        String key = "lonely|" + player.getUUID() + "|" + best.id();
        long now = level.getGameTime();
        if (now - hinted.getOrDefault(key, -99999L) < 4800L) {
            return;
        }
        hinted.put(key, now);
        learn(player, best);
        double dx = best.pos().getX() - player.getX();
        double dz = best.pos().getZ() - player.getZ();
        double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
        level.playSound(null, player.getX() + dx / length * 20.0D, player.getY() + 2.0D, player.getZ() + dz / length * 20.0D,
                dev.hominin.evolution.ModSounds.BAND_PANT_HOOT.get(), SoundSource.NEUTRAL, 1.6F, 1.25F);
        player.sendSystemMessage(Component.literal("The call of the lonely: somebody alone, calling for anyone at all - "
                + (int) length + " blocks " + MentalMap.bearing(player, best.pos()) + ". A lone camp. (On your map.)")
                .withStyle(ChatFormatting.AQUA).append(leadLink(best)));
    }

    /**
     * A lone camp, as the kind of the time would leave one - and the band that is gone, lying about it.
     * <ul>
     * <li>Before habilis: a trampled patch, two of the dead, stones knocked about.</li>
     * <li>Habilis: a pile of worn tools, two or three of the dead, a log to sit on.</li>
     * <li>Erectus and later: a fire, still burning; a knapping station or a work station (now and then both), and three
     * times in ten a hut; old tools, one or two of the dead.</li>
     * </ul>
     * Laid out again every time you evolve: what the last one left is cleared away first.
     */
    @Nullable
    private static BlockPos camp(ServerLevel level, BlockPos ground, RandomSource random, int tier) {
        BlockPos hearth = ground.above();
        clearAbove(level, ground);
        dev.hominin.evolution.band.WildCamps.clearOld(level, hearth, 9);
        if (tier >= 2 && level.getFluidState(hearth).isEmpty()) {
            dev.hominin.evolution.band.WildCamps.fire(level, hearth);
            float roll = random.nextFloat();
            if (roll < 0.15F) {
                dev.hominin.evolution.band.WildCamps.station(level, hearth, random, true);
                dev.hominin.evolution.band.WildCamps.station(level, hearth, random, false);
            } else {
                dev.hominin.evolution.band.WildCamps.station(level, hearth, random, roll < 0.6F);
            }
            if (random.nextFloat() < 0.3F) {
                dev.hominin.evolution.band.WildCamps.hut(level, hearth, random);
            }
        }
        if (tier >= 1) {
            ToolPiles.deposit(level, hearth.offset(random.nextInt(5) - 2, 0, random.nextBoolean() ? 3 : -3), random,
                    tier >= 2);
        }
        // The dead of the band that is gone.
        int dead = tier == 1 ? 2 + random.nextInt(2) : tier == 2 ? 1 + random.nextInt(2) : 2;
        for (int i = 0; i < dead; i++) {
            BlockPos at = surfaceAt(level, hearth.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
            if (at != null && !at.equals(hearth)) {
                dev.hominin.evolution.hunt.Carcasses.placeCarcass(level, at,
                        ModBlocks.HOMININ_CARCASS.get().defaultBlockState());
            }
        }
        if (tier == 0) {
            // No hearth before fire, and nothing to sit on but the ground.
            scatterRocks(level, hearth, random);
            return hearth;
        }
        net.minecraft.core.Direction side = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random);
        for (int i = 2; i <= 3; i++) {
            BlockPos seat = surfaceAt(level, hearth.relative(side, 2).relative(side.getClockWise(), i - 2));
            if (seat != null && level.getBlockState(seat).canBeReplaced()) {
                level.setBlock(seat, Blocks.OAK_LOG.defaultBlockState().setValue(
                        net.minecraft.world.level.block.RotatedPillarBlock.AXIS, side.getClockWise().getAxis()), 3);
            }
        }
        scatterRocks(level, hearth, random);
        return hearth;
    }

    private static void scatterRocks(ServerLevel level, BlockPos hearth, RandomSource random) {
        BlockState[] rocks = {ModBlocks.CHERT_ROCK.get().defaultBlockState(), ModBlocks.GRANITE_ROCK.get().defaultBlockState(),
                ModBlocks.BASALT_ROCK.get().defaultBlockState()};
        for (int i = 0; i < 3; i++) {
            BlockPos at = surfaceAt(level, hearth.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
            BlockState rock = rocks[random.nextInt(rocks.length)];
            if (at != null && !at.equals(hearth) && level.getBlockState(at).canBeReplaced() && rock.canSurvive(level, at)) {
                level.setBlock(at, rock, 3);
            }
        }
    }

    /** The nearest column top within reach that is solid ground with no water on it - or null. */
    @Nullable
    private static BlockPos dryGround(ServerLevel level, BlockPos from, int reach) {
        for (int r = 1; r <= reach; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !level.hasChunk((from.getX() + dx) >> 4,
                            (from.getZ() + dz) >> 4)) {
                        continue;
                    }
                    int x = from.getX() + dx;
                    int z = from.getZ() + dz;
                    BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                    if (level.getFluidState(top).isEmpty() && level.getFluidState(top.above()).isEmpty()
                            && level.getBlockState(top).isSolid()) {
                        return top;
                    }
                }
            }
        }
        return null;
    }

    /** Whether water lies on top of this column: a river bed or a lake floor, not a bank. */
    private static boolean underWater(ServerLevel level, int x, int z) {
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        return !level.getFluidState(top).isEmpty();
    }

    /** The open space on top of the ground at this column, near this height. */
    @Nullable
    private static BlockPos surfaceAt(ServerLevel level, BlockPos near) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ());
        return Math.abs(y - near.getY()) > 3 ? null : new BlockPos(near.getX(), y, near.getZ());
    }

    // ------------------------------------------------------------ a chert super deposit

    /** Ground a seam can run through: soil and stone, not what somebody built. */
    private static boolean natural(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || state.is(net.minecraft.tags.BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(net.minecraft.tags.BlockTags.TERRACOTTA) || state.is(Blocks.COARSE_DIRT);
    }

    /**
     * Where a river has cut down through it, a whole seam of chert shows in the bank - a great deal of it, set in the
     * ground. Away from water, it lies in a heap where the ground has weathered off it. Either way: chert, and more
     * of it than anywhere else.
     */
    @Nullable
    private static BlockPos chertSeam(ServerLevel level, Pois data, Poi poi, BlockPos ground, RandomSource random) {
        BlockState chert = ModBlocks.CHERT_DEPOSIT.get().defaultBlockState();
        BlockPos water = null;
        for (int i = 0; i < 80 && water == null; i++) {
            int x = ground.getX() + random.nextInt(81) - 40;
            int z = ground.getZ() + random.nextInt(81) - 40;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            if (level.getFluidState(top).is(FluidTags.WATER) && level.getBiome(top).is(BiomeTags.IS_RIVER)) {
                water = top;
            }
        }
        BlockPos centre = ground;
        if (water != null) {
            // The bank: step back from the water onto dry ground, and run the seam into it.
            BlockPos bank = null;
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int step = 2; step <= 8 && bank == null; step++) {
                    BlockPos at = water.relative(dir, step);
                    if (!level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
                        break;
                    }
                    BlockPos top = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            at.getX(), at.getZ()) - 1, at.getZ());
                    if (level.getFluidState(top).isEmpty() && natural(level.getBlockState(top))) {
                        bank = top;
                    }
                }
            }
            centre = bank != null ? bank : water.below();
            if (bank == null) {
                // No dry bank to cut into: a heap on the nearest dry ground instead.
                water = null;
            }
        }
        if (water != null) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dy = -4; dy <= 1; dy++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        double d = dx * dx / 36.0D + dy * dy / 12.0D + dz * dz / 36.0D;
                        BlockPos at = centre.offset(dx, dy, dz);
                        if (d > 1.0D || random.nextFloat() < d * 0.35F || !level.hasChunk(at.getX() >> 4, at.getZ() >> 4)
                                || underWater(level, at.getX(), at.getZ())) {
                            continue;
                        }
                        if (natural(level.getBlockState(at))) {
                            level.setBlock(at, chert, 2);
                        }
                    }
                }
            }
            data.pois.put(poi.id(), new Poi(poi.id(), poi.kind(), centre, "Chert seam in a river bank", true));
        } else {
            // A heap: a low mound of it, weathered out of the ground - on dry ground.
            centre = level.getFluidState(ground).isEmpty() && level.getFluidState(ground.above()).isEmpty() ? ground
                    : dryGround(level, ground, 24);
            if (centre == null) {
                return null;
            }
            ground = centre;
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    int height = (int) Math.round(3.2D - r * 0.7D + random.nextFloat() * 0.8D);
                    BlockPos column = surfaceAt(level, ground.offset(dx, 0, dz));
                    if (column == null || height <= 0 || underWater(level, column.getX(), column.getZ())) {
                        continue;
                    }
                    // Sunk into the ground a block, and heaped above it.
                    for (int dy = -1; dy < height; dy++) {
                        BlockPos at = column.above(dy);
                        BlockState here = level.getBlockState(at);
                        if (dy < 0 ? natural(here) : here.canBeReplaced() && level.getFluidState(at).isEmpty()) {
                            level.setBlock(at, chert, 2);
                        }
                    }
                }
            }
            data.pois.put(poi.id(), new Poi(poi.id(), poi.kind(), centre, "Chert heap", true));
        }
        if (Havens.glassySeam(poi.id(), level)) {
            glassFoot(level, centre, random);
        }
        fineFace(level, centre, random);
        // Loose pieces all round it - and a good deal of it fine chert, weathered out of the seam.
        for (int i = 0; i < 20; i++) {
            BlockPos at = surfaceAt(level, centre.offset(random.nextInt(17) - 8, 0, random.nextInt(17) - 8));
            BlockState rock = (i % 5 < 2 ? ModBlocks.FINE_CHERT_ROCK : ModBlocks.CHERT_ROCK).get().defaultBlockState();
            if (at != null && level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty()
                    && rock.canSurvive(level, at)) {
                level.setBlock(at, rock, 2);
            }
        }
        return centre;
    }

    /** Four to six blocks of fine chert on the face of a super deposit - on top, where they show. */
    private static void fineFace(ServerLevel level, BlockPos centre, RandomSource random) {
        List<BlockPos> face = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(centre.offset(-7, -5, -7), centre.offset(7, 4, 7))) {
            if (level.getBlockState(at).is(ModBlocks.CHERT_DEPOSIT.get()) && level.getBlockState(at.above()).isAir()) {
                face.add(at.immutable());
            }
        }
        java.util.Collections.shuffle(face, new java.util.Random(random.nextLong()));
        int want = 4 + random.nextInt(3);
        for (int i = 0; i < Math.min(want, face.size()); i++) {
            level.setBlock(face.get(i), ModBlocks.FINE_CHERT_DEPOSIT.get().defaultBlockState(), 2);
        }
    }

    /**
     * A haven's seam, more often than not, has glass in its foot: the lowest of its blocks, nearest its heart, are
     * obsidian - a vein of four to six.
     */
    private static void glassFoot(ServerLevel level, BlockPos centre, RandomSource random) {
        List<BlockPos> seam = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(centre.offset(-6, -4, -6), centre.offset(6, 3, 6))) {
            if (level.getBlockState(at).is(ModBlocks.CHERT_DEPOSIT.get())) {
                seam.add(at.immutable());
            }
        }
        // The face that shows - open to the sky, the highest first - not the foot nobody sees.
        seam.sort(Comparator.comparingInt((BlockPos p) -> level.getBlockState(p.above()).isAir() ? 0 : 1)
                .thenComparingInt(p -> -p.getY()).thenComparingDouble(p -> Bands.horizontal(p, centre)));
        int want = Math.min(seam.size() / 3, 4 + random.nextInt(3));
        for (int i = 0; i < want; i++) {
            level.setBlock(seam.get(i), ModBlocks.OBSIDIAN_DEPOSIT.get().defaultBlockState(), 2);
        }
    }

    /**
     * Gravel along the water. A deposit is patches of it, two blocks up the bank; a super deposit is the whole lining
     * of the river gone to gravel, five blocks up the bank on either side, and the bed under the water too.
     */
    private static BlockPos gravelBeds(ServerLevel level, BlockPos ground, RandomSource random, boolean great) {
        int reach = great ? 22 : 14;
        int out = great ? 5 : 2;
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        long patchSalt = level.getSeed() ^ ground.asLong();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz > reach * reach) {
                    continue;
                }
                int x = ground.getX() + dx;
                int z = ground.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
                if (!level.getFluidState(top).isEmpty()) {
                    // Under the water: the bed, for a super deposit.
                    BlockPos bed = top.below();
                    while (!level.getFluidState(bed).isEmpty() && bed.getY() > top.getY() - 6) {
                        bed = bed.below();
                    }
                    if (great && natural(level.getBlockState(bed)) && random.nextFloat() < 0.7F) {
                        level.setBlock(bed, gravel, 2);
                    }
                    continue;
                }
                if (!natural(level.getBlockState(top)) || !waterWithin(level, top, out)) {
                    continue;
                }
                if (!great && Math.floorMod(mix(patchSalt, x >> 2, z >> 2), 3L) != 0L) {
                    // Patches, not a lining.
                    continue;
                }
                clearAbove(level, top);
                level.setBlock(top, gravel, 2);
            }
        }
        return ground;
    }

    private static boolean waterWithin(ServerLevel level, BlockPos at, int out) {
        for (int dx = -out; dx <= out; dx++) {
            for (int dz = -out; dz <= out; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (level.getFluidState(at.offset(dx, dy, dz)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
                    level.setBlock(top, ModBlocks.SALT_BLOCK.get().defaultBlockState(), 2);
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
        var state = player.level().getBlockState(at);
        boolean salt = state.is(ModBlocks.SALT_BLOCK.get());
        if (!salt && !state.is(Blocks.CALCITE)) {
            return false;
        }
        Poi lick = null;
        for (Poi poi : of(player.serverLevel()).pois.values()) {
            if (poi.kind() == Kind.LICK && poi.placed() && poi.pos().distSqr(at) < 5.0D * 5.0D) {
                lick = poi;
            }
        }
        if (lick == null && !salt) {
            // Plain calcite is only salt at a lick.
            return false;
        }
        if (!tasteSalt(player, at, "You lick the salt crust. Something the body did not know it was missing.")) {
            return true;
        }
        if (lick != null && !knows(player, lick.id())) {
            learn(player, lick);
        }
        return true;
    }

    /** Salt, licked: once a day, a little food and a while of mending. Returns false if you have had it today. */
    public static boolean tasteSalt(ServerPlayer player, BlockPos at, String how) {
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int day = (int) (player.level().getDayTime() / 24000L);
        if (counters.getOrDefault("salt_day", -1) == day) {
            player.displayClientMessage(Component.literal("You have had your fill of salt today."), true);
            return false;
        }
        counters.put("salt_day", day);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, at, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.6F, 1.3F);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 20, 0));
        player.getFoodData().eat(1, 0.6F);
        player.displayClientMessage(Component.literal(how), true);
        return true;
    }

    /** A hammerstone against a salt block: a chunk comes away - two to a block, and then the crust is too thin. */
    public static boolean strikeSalt(ServerPlayer player, BlockPos at) {
        var state = player.level().getBlockState(at);
        if (!state.is(ModBlocks.SALT_BLOCK.get())) {
            return false;
        }
        int left = dev.hominin.evolution.block.SaltBlock.left(state);
        if (left <= 0) {
            player.displayClientMessage(Component.literal("The crust here is worked thin - nothing left worth striking off."),
                    true);
            return true;
        }
        player.level().setBlock(at, state.setValue(dev.hominin.evolution.block.SaltBlock.STRUCK,
                dev.hominin.evolution.block.SaltBlock.CHUNKS - left + 1), 3);
        player.level().playSound(null, at, SoundEvents.CALCITE_BREAK, SoundSource.PLAYERS, 0.9F, 1.2F);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        net.minecraft.world.level.block.Block.popResourceFromFace(player.level(), at, net.minecraft.core.Direction.UP,
                new net.minecraft.world.item.ItemStack(dev.hominin.evolution.ModItems.SALT_CHUNK.get()));
        player.displayClientMessage(Component.literal(left - 1 > 0 ? "A chunk of salt comes away. There is one more in it."
                : "A chunk of salt comes away - the last worth taking from this block."), true);
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
            if (dawn && (poi.kind() == Kind.OASIS && poi.placed() || poi.kind() == Kind.HAVEN)) {
                double distance = Bands.horizontal(poi.pos(), player.blockPosition());
                if (distance <= 128.0D * 128.0D && gathered.getOrDefault(poi.id(), -1) != day) {
                    gathered.put(poi.id(), day);
                    Oases.dawn(player, poi);
                }
                continue;
            }
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
        for (Tag entry : tag.getList("Loners", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) entry;
            data.lonerFor.put(p.getString("Key"), p.getInt("Generation"));
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
        ListTag loners = new ListTag();
        for (var entry : lonerFor.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putString("Key", entry.getKey());
            p.putInt("Generation", entry.getValue());
            loners.add(p);
        }
        tag.put("Loners", loners);
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
        if (kind == Kind.SPRING || kind == Kind.LICK || kind == Kind.TOOLS || kind == Kind.CAMP || kind == Kind.CHERT
                || kind == Kind.GRAVEL || kind == Kind.SUPER_GRAVEL || kind == Kind.TIDE_POOL || kind == Kind.OASIS) {
            // The big ones are laid out further ahead, so you are not standing in the middle of them.
            BlockPos offset = at.relative(player.getDirection(), kind == Kind.OASIS ? 30 : 6);
            poi = new Poi(id, kind, offset, kind.label, false);
            data.pois.put(id, poi);
            materialise(player.serverLevel(), data, poi, player);
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
