package dev.hominin.evolution.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.BandNames;
import dev.hominin.evolution.band.BandSizes;
import dev.hominin.evolution.band.Bands;
import dev.hominin.evolution.band.Presence;
import dev.hominin.evolution.band.Relations;
import dev.hominin.evolution.band.WildBands;
import dev.hominin.evolution.item.StoneMaterial;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Havens: the best ground there is. A spring, a great seam of chert (glass in it, more often than not), and old tools
 * or a salt lick, all within a stone's throw - pressure 8 to 10, and every band for miles knows the place.
 *
 * <p>Every stretch of country has one - never in a swamp - and one lies within a few hundred blocks of where the
 * world begins. Each has a spring and something to live on: tide pools by the sea, a termite super colony inland; and
 * at first light the animals come to it, as they come to an oasis (see {@link Oases}).
 *
 * <p>Most lie empty - three in five, when you first come near one. The rest are held, and the band that holds a haven
 * is the best armed of its kind: the best stone, the best weapons. Two in five of those have little presence (the
 * haven is theirs, for now, and anybody could take it off them); the rest are strong. Walk onto a held haven and they
 * want a high price for it - and pay, and it is open to you for a few days. Stay without paying and they come at you.
 *
 * <p>Kill them all - they fight to the last, nobody asks to join you - and the haven is yours to decide: <b>take the
 * land</b> (your band moves there) or <b>set an example</b> (you leave it, and every band for miles learns what
 * happened: your own ground is left alone, even in desperate times, and the haven is open to you whoever lives there
 * after). An empty haven does not stay empty: bands on the move go to it.
 */
public final class Havens extends SavedData {
    private static final String NAME = "hominin_evolution_havens";
    /** At most one haven in this much country: a square four regions on a side. */
    public static final int SUPER = 1536;
    /** Every stretch of country has one. */
    private static final float CHANCE = 1.0F;
    /** How far out a haven's own places lie from its heart. */
    public static final int SPREAD = 16;
    /** Every band this near a haven knows it. */
    public static final int EVERYONE_KNOWS = 1500;
    /** And so does your band, from anyone it meets, this near. */
    private static final int TALK_REACH = 900;
    /** A haven is rolled for - held or empty - once somebody comes this near. */
    private static final int ROLL_REACH = 400;
    public static final int ACTION_CONQUERED = 63;
    private static final int TAKE = 0;
    private static final int EXAMPLE = 1;
    /** What a held haven wants for letting you on it: food, or half as much good stone. */
    public static final int TOLL = 12;
    public static final long TOLL_TICKS = 3 * 24000L;
    /** The counter that marks a player who set an example. */
    public static final String EXAMPLE_KEY = "haven_example";

    public record Site(String id, BlockPos centre) {
    }

    private static final class State {
        final BlockPos centre;
        boolean rolled;
        @Nullable
        UUID holder;
        /** The day it was found empty, or fell empty. */
        long emptySince = -1L;
        /** Players who set an example here: it is open to them for good. */
        final Set<UUID> examples = new HashSet<>();

        State(BlockPos centre) {
            this.centre = centre;
        }
    }

    private final Map<String, State> states = new HashMap<>();
    private long settledDay = -1L;
    /** Players who have just wiped out a haven's band, and which haven: waiting on their choice. */
    private static final Map<UUID, String> conquered = new HashMap<>();

    public static Havens of(ServerLevel level) {
        ServerLevel home = level.getServer().overworld();
        return home.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Havens::new, Havens::load), NAME);
    }

    // ------------------------------------------------------------ where they are

    private static long mix(long seed, long a, long b) {
        long h = seed * 31L + a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    /** The haven in this stretch of country, if it has one - worked out from the seed. */
    @Nullable
    public static Site siteIn(ServerLevel level, int sx, int sz) {
        String id = "haven_" + sx + "_" + sz;
        java.util.Optional<Site> known = sites.get(id);
        if (known != null) {
            return known.orElse(null);
        }
        long h = mix(level.getSeed() ^ 0x4A7E11L, sx, sz);
        Site found = null;
        if (Math.abs(h) % 1000L < (long) (CHANCE * 1000.0F)) {
            BlockPos spawn = level.getSharedSpawnPos();
            boolean home = Math.floorDiv(spawn.getX(), SUPER) == sx && Math.floorDiv(spawn.getZ(), SUPER) == sz;
            // Somewhere it can be: looked for over the stretch until it fits - and in the stretch the world starts in,
            // close enough to home to be found, but not on top of it.
            for (int attempt = 0; attempt < 16 && found == null; attempt++) {
                long ha = mix(h, attempt, 3L);
                int x;
                int z;
                if (home) {
                    double angle = (Math.abs(ha >> 4) % 6283L) / 1000.0D;
                    int distance = 380 + (int) (Math.abs(ha >> 20) % 260L);
                    x = net.minecraft.util.Mth.clamp(spawn.getX() + (int) (Math.cos(angle) * distance), sx * SUPER + 120, (sx + 1) * SUPER - 120);
                    z = net.minecraft.util.Mth.clamp(spawn.getZ() + (int) (Math.sin(angle) * distance), sz * SUPER + 120, (sz + 1) * SUPER - 120);
                } else {
                    x = sx * SUPER + 200 + (int) (Math.abs(ha >> 8) % (SUPER - 400));
                    z = sz * SUPER + 200 + (int) (Math.abs(ha >> 24) % (SUPER - 400));
                }
                BlockPos pos = new BlockPos(x, 64, z);
                var biome = level.getBiome(pos);
                if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER)
                        || biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(net.minecraft.world.level.biome.Biomes.DESERT)
                        || biome.is(net.minecraft.world.level.biome.Biomes.SWAMP)
                        || biome.is(net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP)) {
                    continue;
                }
                found = new Site(id, pos);
            }
        }
        if (sites.size() > 1024) {
            sites.clear();
        }
        sites.put(id, java.util.Optional.ofNullable(found));
        return found;
    }

    private static final Map<String, java.util.Optional<Site>> sites = new HashMap<>();
    private static final Map<String, Boolean> coastal = new HashMap<>();

    /** Whether a haven lies by the sea: then what feeds it is the tide pools; inland, a termite super colony. */
    private static boolean byTheSea(ServerLevel level, Site site) {
        return coastal.computeIfAbsent(site.id(), k -> {
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI / 6.0D;
                BlockPos at = site.centre().offset((int) (Math.cos(angle) * 56), 0, (int) (Math.sin(angle) * 56));
                var biome = level.getBiome(at);
                if (biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_OCEAN)) {
                    return true;
                }
            }
            return false;
        });
    }

    public static List<Site> near(ServerLevel level, BlockPos centre, int radius) {
        List<Site> list = new ArrayList<>();
        int s0x = Math.floorDiv(centre.getX() - radius, SUPER);
        int s1x = Math.floorDiv(centre.getX() + radius, SUPER);
        int s0z = Math.floorDiv(centre.getZ() - radius, SUPER);
        int s1z = Math.floorDiv(centre.getZ() + radius, SUPER);
        for (int sx = s0x; sx <= s1x; sx++) {
            for (int sz = s0z; sz <= s1z; sz++) {
                Site site = siteIn(level, sx, sz);
                if (site != null && Bands.horizontal(site.centre(), centre) <= (double) radius * radius) {
                    list.add(site);
                }
            }
        }
        return list;
    }

    @Nullable
    public static Site byId(ServerLevel level, @Nullable String id) {
        if (id == null || !id.startsWith("haven_")) {
            return null;
        }
        String[] parts = id.split("_");
        try {
            return siteIn(level, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Whether a place is one of a haven's: its spring, its seam, its old tools. */
    public static boolean partOfHaven(String poiId) {
        return poiId.startsWith("haven");
    }

    /** The haven and its places, as places: the heart, a spring, a seam, a salt lick, and (from erectus) old tools. */
    static void addPlaces(ServerLevel level, Pois data, BlockPos centre, int radius, boolean erectusOn,
            Map<String, Pois.Poi> found) {
        for (Site site : near(level, centre, radius + SPREAD * 2)) {
            String tail = site.id().substring("haven".length());
            BlockPos c = site.centre();
            found.put(site.id(), data.intern(site.id(), Pois.Kind.HAVEN, c, Pois.Kind.HAVEN.label));
            String spring = "havenspring" + tail;
            found.put(spring, data.intern(spring, Pois.Kind.SPRING, c.offset(10, 0, 6), "Spring (haven)"));
            String chert = "havenchert" + tail;
            found.put(chert, data.intern(chert, Pois.Kind.CHERT, c.offset(-12, 0, -6), "Chert super deposit (haven)"));
            String lick = "havenlick" + tail;
            found.put(lick, data.intern(lick, Pois.Kind.LICK, c.offset(-6, 0, 14), "Salt lick (haven)"));
            // Every haven feeds whoever holds it: tide pools by the sea, a termite super colony inland.
            String food = "havenfood" + tail;
            boolean sea = byTheSea(level, site);
            found.put(food, data.intern(food, sea ? Pois.Kind.TIDE_POOL : Pois.Kind.TERMITES, c.offset(14, 0, -10),
                    sea ? "Tide pools (haven)" : "Termite super colony (haven)"));
            if (erectusOn) {
                // Old tools, for those who know what they are looking at.
                String tools = "haventools" + tail;
                found.put(tools, data.intern(tools, Pois.Kind.TOOLS, c.offset(4, 0, -14), "Old tool deposit (haven)"));
            }
        }
    }

    /** A haven's seam has glass in its foot, more often than not. */
    static boolean glassySeam(String poiId, ServerLevel level) {
        return poiId.startsWith("havenchert") && (mix(level.getSeed() ^ 0x0B51D1L, poiId.hashCode(), 7L) & 1023L) < 614L;
    }

    // ------------------------------------------------------------ who holds them

    private State state(Site site) {
        return states.computeIfAbsent(site.id(), k -> new State(site.centre()));
    }

    @Nullable
    public static UUID holder(ServerLevel level, String havenId) {
        State state = of(level).states.get(havenId);
        return state == null ? null : state.holder;
    }

    /** Whether this band holds a haven - and so is the best armed of its kind. */
    public static boolean holds(Bands.Record band) {
        return band.haven != null;
    }

    /** The first time anyone comes near: held, or empty - and if held, by whom. */
    private static void roll(ServerLevel level, Havens data, Site site, ServerPlayer near) {
        State state = data.state(site);
        if (state.rolled) {
            return;
        }
        state.rolled = true;
        data.setDirty();
        long day = level.getDayTime() / 24000L;
        if (playerCampNear(level, site.centre())) {
            // A player's band lives on it already: nobody else does.
            state.emptySince = day;
            return;
        }
        // Somebody's ground already runs over it: it is theirs.
        Bands.Record there = Bands.groundAt(level, site.centre());
        if (there != null) {
            occupy(level, site, there);
            return;
        }
        RandomSource random = RandomSource.create(mix(level.getSeed() ^ 0x0CC0L, site.centre().getX(), site.centre().getZ()));
        if (random.nextFloat() >= 0.4F) {
            state.emptySince = day;
            return;
        }
        ResourceLocation era = near.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        ResourceLocation species = WildBands.speciesFor(era, random);
        if (dev.hominin.evolution.band.Paranthropus.STAGE.equals(species)) {
            species = era;
        }
        int size = BandSizes.of(species).start() + 2 + random.nextInt(3);
        UUID id = UUID.randomUUID();
        Bands.Record band = Bands.register(level, id, species, site.centre(), size);
        dev.hominin.evolution.band.Territory.settle(id, site.centre());
        band.haven = site.id();
        band.havenPresence = random.nextFloat() < 0.4F ? 4 : 16;
        band.presence = band.havenPresence;
        band.desperation = 1;
        state.holder = id;
        Bands.changed(level);
    }

    /** A band moves onto a haven: it holds it now - and goes armed as a haven's people are. */
    public static void occupy(ServerLevel level, Site site, Bands.Record band) {
        Havens data = of(level);
        State state = data.state(site);
        state.rolled = true;
        state.holder = band.id;
        state.emptySince = -1L;
        band.haven = site.id();
        band.havenPresence = level.random.nextFloat() < 0.4F ? 4 : 16;
        band.presence = Math.max(band.presence, band.havenPresence);
        data.setDirty();
        Bands.changed(level);
        for (BandMember member : level.getEntities(dev.hominin.evolution.ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && band.id.equals(m.getBandId()))) {
            arm(member, level.random);
        }
    }

    /** A band leaves the haven it held: it is empty now, for the next band to find. */
    public static void vacate(ServerLevel level, Bands.Record band) {
        Havens data = of(level);
        State state = band.haven == null ? null : data.states.get(band.haven);
        if (state != null && band.id.equals(state.holder)) {
            state.holder = null;
            state.emptySince = level.getDayTime() / 24000L;
            data.setDirty();
        }
        band.haven = null;
        band.havenPresence = -1;
        Bands.changed(level);
    }

    /** The empty havens within reach of somewhere, for a band thinking of moving. */
    public static List<Site> emptyNear(ServerLevel level, BlockPos around, int radius) {
        Havens data = of(level);
        List<Site> list = new ArrayList<>();
        for (Site site : near(level, around, radius)) {
            State state = data.states.get(site.id());
            // Nobody has been near enough to know: nobody holds it either. Empty, as far as a band can tell.
            if (state == null || state.holder == null || Bands.get(level, state.holder) == null) {
                list.add(site);
            }
        }
        return list;
    }

    // ------------------------------------------------------------ armed as the haven's people are

    /**
     * The best of their kind: Australopithecus with Lomekwian cores of chert - some of obsidian - habilis with the
     * multi tool and a spear, erectus with a good hand axe and a fire-hardened spear, and the later kinds with the
     * Levallois hand axe and the best spears there are. And they are strong: they eat well, on ground like that.
     */
    public static void arm(BandMember member, RandomSource random) {
        if (member.isBaby()) {
            return;
        }
        String path = member.getStage() == null ? "" : member.getStage().getPath();
        StoneMaterial best = random.nextFloat() < 0.3F ? StoneMaterial.OBSIDIAN : StoneMaterial.CHERT;
        ItemStack main;
        ItemStack spare;
        if (path.startsWith("australopithecus") || path.equals("ardipithecus")) {
            main = StoneMaterial.stamp(new ItemStack(ModItems.LOMEKWIAN_TOOL.get()), best);
            spare = StoneMaterial.stamp(new ItemStack(ModItems.LOMEKWIAN_TOOL.get()), StoneMaterial.CHERT);
        } else if (path.equals("homo_habilis") || path.equals("homo_rudolfensis")) {
            main = StoneMaterial.stamp(new ItemStack(ModItems.OLDOWAN_MULTITOOL.get()), best);
            spare = new ItemStack(ModItems.SHARPENED_STICK.get());
        } else if (path.equals("homo_erectus") || path.equals("homo_ergaster")) {
            main = random.nextBoolean() ? new ItemStack(ModItems.FIRE_HARDENED_SPEAR.get())
                    : StoneMaterial.stamp(new ItemStack(ModItems.HAND_AXE.get()), best);
            spare = StoneMaterial.stamp(new ItemStack(ModItems.HAND_AXE.get()), StoneMaterial.CHERT);
        } else {
            main = random.nextBoolean() ? new ItemStack(ModItems.SCHONINGEN_SPEAR.get())
                    : StoneMaterial.mark(new ItemStack(ModItems.STONE_TIPPED_SPEAR.get()), best);
            spare = StoneMaterial.stamp(new ItemStack(ModItems.LEVALLOIS_HAND_AXE.get()), best);
        }
        member.setItemSlot(EquipmentSlot.MAINHAND, main);
        member.getInventory().addItem(spare);
        member.getInventory().addItem(new ItemStack(ModItems.MEAT_CHUNK.get(), 2));
        member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false,
                false));
    }

    // ------------------------------------------------------------ every so often, round each player

    public static void tick(ServerPlayer player) {
        if (player.isSpectator() || player.tickCount % 200 != 91) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return;
        }
        Havens data = of(level);
        for (Site site : near(level, player.blockPosition(), ROLL_REACH)) {
            roll(level, data, site, player);
        }
        if (player.tickCount % 6000 == 91) {
            talkOf(player, data);
        }
        if (player.tickCount % 1200 == 91) {
            settleEmpty(level, data);
            everyBandKnows(level);
        }
    }

    /** Everyone talks of the havens: your band hears of any within a long walk. */
    private static void talkOf(ServerPlayer player, Havens data) {
        ServerLevel level = player.serverLevel();
        for (Site site : near(level, player.blockPosition(), TALK_REACH)) {
            Pois.Poi poi = Pois.get(level, site.id());
            if (poi == null) {
                Pois.near(level, site.centre(), 1, false);
                poi = Pois.get(level, site.id());
            }
            if (poi == null || Pois.knows(player, site.id())) {
                continue;
            }
            Pois.learn(player, poi);
            LandReveal.telling().around(level, site.centre(), LandReveal.AROUND_PLACE + SPREAD).send(player);
            State state = data.states.get(site.id());
            Bands.Record holder = state == null || state.holder == null ? null : Bands.get(level, state.holder);
            player.sendSystemMessage(Component.literal("Every band for miles talks of it: a haven - a spring, a great "
                    + "seam of stone, everything a band could want, all in one place. " + Pois.where(player, poi) + ". "
                    + (holder != null ? BandNames.capital(holder.name) + " hold it, and want paying by anyone who sets "
                            + "foot on it." : "Nobody is sure whether anyone holds it now."))
                    .withStyle(ChatFormatting.GOLD).append(Pois.leadLink(poi)));
            if (holder != null && !holder.knownTo(player.getUUID())) {
                Relations.meet(player, holder, "");
            }
            return;
        }
    }

    /** Every band knows every haven near it. */
    private static void everyBandKnows(ServerLevel level) {
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic()) {
                continue;
            }
            for (Site site : near(level, band.home, EVERYONE_KNOWS)) {
                Pois.bandLearns(level, band.id, site.id());
            }
        }
    }

    /** Once a day: an empty haven has been noticed by some band on the move. */
    private static void settleEmpty(ServerLevel level, Havens data) {
        long day = level.getDayTime() / 24000L;
        if (data.settledDay >= day) {
            return;
        }
        data.settledDay = day;
        data.setDirty();
        for (Map.Entry<String, State> entry : new ArrayList<>(data.states.entrySet())) {
            State state = entry.getValue();
            if (!state.rolled || state.holder != null && Bands.get(level, state.holder) != null) {
                continue;
            }
            if (state.holder != null) {
                // Its holders are gone.
                state.holder = null;
                state.emptySince = day;
            }
            if (day - state.emptySince < 2 || level.random.nextFloat() >= 0.15F) {
                continue;
            }
            Site site = byId(level, entry.getKey());
            if (site == null || playerCampNear(level, site.centre())) {
                continue;
            }
            Bands.Record mover = null;
            double best = 700.0D * 700.0D;
            for (Bands.Record band : Bands.all(level)) {
                double d = Bands.horizontal(band.home, site.centre());
                if (!band.nomadic() && band.haven == null && d < best && band.size > 0) {
                    best = d;
                    mover = band;
                }
            }
            if (mover != null) {
                dev.hominin.evolution.band.Moves.relocate(level, mover, site.centre(), "into the haven");
                occupy(level, site, mover);
            }
        }
    }

    /** Whether a player's band has its ground on or right by this spot. */
    private static boolean playerCampNear(ServerLevel level, BlockPos centre) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (dev.hominin.evolution.hunt.Predation.settled(player)
                    && Bands.horizontal(dev.hominin.evolution.hunt.Predation.campOf(player), centre) < 170.0D * 170.0D) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ the toll, and the end of them

    /** Whether a haven's ground is open to this player for good: they set an example there. */
    public static boolean openForGood(ServerPlayer player, Bands.Record band) {
        if (band.haven == null) {
            return false;
        }
        State state = of(player.serverLevel()).states.get(band.haven);
        return state != null && state.examples.contains(player.getUUID());
    }

    /** A player who set an example: bands do not come for their ground, even in desperate times. */
    public static boolean madeAnExample(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().getOrDefault(EXAMPLE_KEY, 0) > 0;
    }

    /**
     * The last of a haven's people is dead. Whoever did it - you, or your band - decides what the haven is now.
     */
    public static void wipedOut(ServerLevel level, Bands.Record band, @Nullable Entity killer) {
        Havens data = of(level);
        State state = band.haven == null ? null : data.states.get(band.haven);
        if (state != null) {
            state.holder = null;
            state.emptySince = level.getDayTime() / 24000L;
            data.setDirty();
        }
        ServerPlayer player = killer instanceof ServerPlayer p ? p
                : killer instanceof BandMember own && own.leaderPlayer() instanceof ServerPlayer p ? p : null;
        if (player == null || band.haven == null) {
            return;
        }
        conquered.put(player.getUUID(), band.haven);
        player.sendSystemMessage(Component.literal("The last of " + band.name + " is dead. The haven is nobody's - "
                + "and yours to decide.").withStyle(ChatFormatting.GOLD));
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION_CONQUERED,
                "The haven is empty. What now?",
                List.of("Take the land - the band moves here", "Set an example - leave it, and let every band hear of it"),
                List.of(TAKE, EXAMPLE)));
    }

    public static void choose(ServerPlayer player, int choice) {
        String havenId = conquered.remove(player.getUUID());
        ServerLevel level = player.serverLevel();
        Site site = byId(level, havenId);
        if (site == null) {
            return;
        }
        if (choice == TAKE) {
            dev.hominin.evolution.hunt.Predation.settle(player, site.centre());
            Presence.add(player, 3, "you took the haven");
            player.sendSystemMessage(Component.literal("The haven is your band's ground now. Everything a band could want "
                    + "- and every band for miles wants it. They will come.").withStyle(ChatFormatting.GREEN));
            dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/haven_taken");
            return;
        }
        Havens data = of(level);
        data.state(site).examples.add(player.getUUID());
        data.setDirty();
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(EXAMPLE_KEY, 1);
        Presence.add(player, Presence.MAX, "every band heard what you did at the haven");
        for (Bands.Record band : Bands.all(level)) {
            if (!band.nomadic() && Bands.horizontal(band.home, site.centre()) < (double) EVERYONE_KNOWS * EVERYONE_KNOWS) {
                band.known.add(player.getUUID());
            }
        }
        Bands.changed(level);
        player.sendSystemMessage(Component.literal("You leave the haven as it is - with its dead. Every band for miles hears "
                + "of it. Nobody will come for your ground now, not even in desperate times; and whoever lives at the "
                + "haven after, it is open to you.").withStyle(ChatFormatting.DARK_RED));
        dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/haven_example");
    }

    public static void forget(UUID player) {
        conquered.remove(player);
    }

    // ------------------------------------------------------------ saving

    private static Havens load(CompoundTag tag, HolderLookup.Provider registries) {
        Havens data = new Havens();
        data.settledDay = tag.getLong("SettledDay");
        for (Tag entry : tag.getList("Havens", Tag.TAG_COMPOUND)) {
            CompoundTag h = (CompoundTag) entry;
            State state = new State(BlockPos.of(h.getLong("Centre")));
            state.rolled = h.getBoolean("Rolled");
            if (h.hasUUID("Holder")) {
                state.holder = h.getUUID("Holder");
            }
            state.emptySince = h.getLong("EmptySince");
            for (Tag e : h.getList("Examples", Tag.TAG_COMPOUND)) {
                state.examples.add(((CompoundTag) e).getUUID("Id"));
            }
            data.states.put(h.getString("Id"), state);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("SettledDay", settledDay);
        ListTag list = new ListTag();
        for (Map.Entry<String, State> entry : states.entrySet()) {
            State state = entry.getValue();
            CompoundTag h = new CompoundTag();
            h.putString("Id", entry.getKey());
            h.putLong("Centre", state.centre.asLong());
            h.putBoolean("Rolled", state.rolled);
            if (state.holder != null) {
                h.putUUID("Holder", state.holder);
            }
            h.putLong("EmptySince", state.emptySince);
            ListTag examples = new ListTag();
            for (UUID id : state.examples) {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", id);
                examples.add(e);
            }
            h.put("Examples", examples);
            list.add(h);
        }
        tag.put("Havens", list);
        return tag;
    }
}
