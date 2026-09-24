package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every other band in the world, remembered whether or not anyone is near it: its name, its kind, where
 * its ground is, how many it is, and what each player has done to earn its regard.
 *
 * <p>The people themselves only exist near a player - a band nobody is near is not kept walking about -
 * but the band does not stop existing. Come back to its ground and it is there again, as many as it
 * was, as it felt about you when you left.
 *
 * <p>Hominin bands hold ground: 80 blocks around their camp from erectus on, 48 before. Paranthropus
 * holds none - a troop moves on every day, to wherever there is food or water.
 */
public final class Bands extends SavedData {
    private static final String NAME = "hominin_evolution_bands";
    public static final int ERECTUS_RADIUS = 80;
    public static final int EARLY_RADIUS = 48;

    public static final class Record {
        public final UUID id;
        public String name;
        public final ResourceLocation species;
        public BlockPos home;
        public int size;
        /** Standing with each player who has met them, 0-50. */
        public final Map<UUID, Integer> standing = new HashMap<>();
        /** Players who know this band exists - heard it, met it, or been told. */
        public final Set<UUID> known = new HashSet<>();
        /** Uses of this band's ground by players it has not met yet: counted against them when it does. */
        public final Map<UUID, Integer> trespass = new HashMap<>();
        /** What they have heard about a player they have not met - from someone who left that player's band. */
        public final Map<UUID, Integer> rumours = new HashMap<>();
        /** The day a nomadic troop last moved on. */
        public long movedDay;
        /**
         * How much the country round their camp knows to leave them alone, 0-50 - as a player's presence is.
         * A strong band has fewer predators about it and is not worth trying; a weak one is.
         */
        public int presence;
        /** How well they hold together, 0-50. It follows their presence, with a temper of its own. */
        public int cohesion;
        /** The day presence and cohesion last moved. */
        public long driftDay;
        /** How badly they need things, 1 to 5: it climbs through hard times and falls in good ones. */
        public int desperation = 1;
        /** What each player's ground has them doing: offering for it, demanding for it, or after it. */
        public final Map<UUID, Integer> stance = new HashMap<>();
        /** Players who have let them use their ground, until when. */
        public final Map<UUID, Long> accessUntil = new HashMap<>();
        /** Other bands this one stands with: raid one and the others remember, and may come with them. */
        public final Set<UUID> allies = new HashSet<>();

        Record(UUID id, String name, ResourceLocation species, BlockPos home, int size) {
            this.id = id;
            this.name = name;
            this.species = species;
            this.home = home;
            this.size = size;
        }

        public boolean nomadic() {
            return Paranthropus.STAGE.equals(species);
        }

        /** How far its ground runs from its camp; none for a troop that holds no ground. */
        public int radius() {
            return nomadic() ? 0 : radiusFor(species);
        }

        public boolean holds(BlockPos pos) {
            if (nomadic()) {
                return false;
            }
            double dx = pos.getX() - home.getX();
            double dz = pos.getZ() - home.getZ();
            return dx * dx + dz * dz <= (double) radius() * radius();
        }

        public boolean knownTo(UUID player) {
            return known.contains(player);
        }

        /** What they would think of trying you - and what you might think of trying them. */
        public String strength() {
            return presence >= 35 ? "strong - predators keep well clear of them"
                    : presence >= 20 ? "holding their own" : "weak - easy pickings for anything";
        }

        public String temper() {
            return cohesion >= 35 ? "close-knit" : cohesion >= 20 ? "steady" : "coming apart - quarrelling, hungry";
        }

        /** Where this band's presence and cohesion settle when nothing much happens to it. */
        private int presenceMean() {
            return 15 + randomFor(id).nextInt(21) + Math.min(6, size / 2);
        }

        private int cohesionTrait() {
            RandomSource random = randomFor(id);
            random.nextInt(21);
            return 8 + random.nextInt(15);
        }

        /** A day passes for them: presence drifts round its mean, cohesion after it with a temper of its own. */
        void drift(RandomSource random, boolean hard) {
            int mean = presenceMean();
            presence += random.nextInt(5) - 2 + Integer.signum(mean - presence) - (hard ? 1 : 0);
            presence = Math.max(0, Math.min(50, presence));
            int target = presence * 3 / 5 + cohesionTrait() - (hard ? 4 : 0);
            cohesion += (target - cohesion) / 3 + random.nextInt(7) - 3;
            cohesion = Math.max(0, Math.min(50, cohesion));
        }

        /** A day passes: hard times make a band desperate, good ones ease it. */
        void strain(ServerLevel level, RandomSource random) {
            int d = desperation;
            d += dev.hominin.evolution.survival.Seasons.isDry(level) ? 1 : -1;
            if (dev.hominin.evolution.survival.Drought.isActive(level)) {
                d++;
            }
            if (dev.hominin.evolution.survival.Drought.isProsperousDay(level)) {
                d--;
            }
            if (cohesion < 15) {
                d++;
            }
            if (desperateTimes(level)) {
                // Everybody around them is starving too.
                d = Math.max(d + 1, 2);
            }
            if (presence >= 35 && random.nextBoolean()) {
                d--;
            }
            desperation = Math.max(1, Math.min(5, d));
        }
    }

    /** Before erectus a band holds less ground, and nobody in it is past caring what the rest think. */
    public static boolean erectusOn(ResourceLocation species) {
        String path = species.getPath();
        return !(path.equals("ardipithecus") || path.startsWith("australopithecus") || path.equals("homo_habilis")
                || path.equals("homo_rudolfensis") || path.startsWith("paranthropus"));
    }

    /** Ground held by a band of this kind. */
    public static int radiusFor(ResourceLocation species) {
        return erectusOn(species) ? ERECTUS_RADIUS : EARLY_RADIUS;
    }

    private final Map<UUID, Record> bands = new LinkedHashMap<>();
    /** The day desperate times began, or -1 when times are ordinary. */
    private long desperateSince = -1L;

    /** Most bands out there are desperate: everyone is, a little more, and everything is more dangerous. */
    public static boolean desperateTimes(ServerLevel level) {
        return of(level).desperateSince >= 0L;
    }

    /** Developer: desperate times, on or off. */
    public static void setDesperateTimes(ServerLevel level, boolean on) {
        Bands data = of(level);
        data.desperateSince = on ? level.getDayTime() / 24000L : -1L;
        data.setDirty();
    }

    /** Two bands take each other as allies. */
    private static void ally(Record a, Record b) {
        if (a != b && !a.nomadic() && !b.nomadic()) {
            a.allies.add(b.id);
            b.allies.add(a.id);
        }
    }

    @Nullable
    private Record nearestHolder(Record to, double within) {
        Record best = null;
        double bestDistance = within * within;
        for (Record other : bands.values()) {
            if (other == to || other.nomadic()) {
                continue;
            }
            double distance = horizontal(other.home, to.home);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }
    /** Bands of an earlier age: any of their people still lying about in the world are gone on sight. */
    private final Set<UUID> retired = new HashSet<>();

    /**
     * A player has evolved: a long time has passed. Every band there was is gone - its people with it - and
     * new ones of the new age will be met as they are come across.
     */
    public static void newEra(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Bands data = of(level);
        data.retired.addAll(data.bands.keySet());
        data.bands.clear();
        data.setDirty();
        for (ServerLevel any : level.getServer().getAllLevels()) {
            for (BandMember member : any.getEntities(dev.hominin.evolution.ModEntities.BAND_MEMBER.get(),
                    m -> m.isWild() && m.getBandId() != null && data.retired.contains(m.getBandId()))) {
                member.discard();
            }
        }
    }

    public static boolean retired(ServerLevel level, @Nullable UUID id) {
        return id != null && of(level).retired.contains(id);
    }

    public static Bands of(ServerLevel level) {
        ServerLevel home = level.getServer().overworld();
        return home.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Bands::new, Bands::load), NAME);
    }

    @Nullable
    public static Record get(ServerLevel level, @Nullable UUID id) {
        return id == null ? null : of(level).bands.get(id);
    }

    public static List<Record> all(ServerLevel level) {
        return new ArrayList<>(of(level).bands.values());
    }

    /** A band has come into being here. It is named for its ground; nobody knows it yet. */
    public static Record register(ServerLevel level, UUID id, ResourceLocation species, BlockPos home, int size) {
        Bands data = of(level);
        Record existing = data.bands.get(id);
        if (existing != null) {
            return existing;
        }
        Record record = new Record(id, BandNames.name(level, home, species, id, data.names()), species,
                home.immutable(), size);
        record.movedDay = level.getDayTime() / 24000L;
        record.driftDay = record.movedDay;
        record.presence = record.presenceMean() + level.random.nextInt(9) - 4;
        record.cohesion = Math.max(5, Math.min(45, record.presence * 3 / 5 + record.cohesionTrait()));
        data.bands.put(id, record);
        // A new band may already stand with the nearest one.
        Record neighbour = data.nearestHolder(record, 300.0D);
        if (neighbour != null && level.random.nextFloat() < 0.35F) {
            ally(record, neighbour);
        }
        data.setDirty();
        // Every band knows a place or two of its own.
        dev.hominin.evolution.world.Pois.assign(level, record);
        return record;
    }

    private Set<String> names() {
        Set<String> names = new HashSet<>();
        for (Record record : bands.values()) {
            names.add(record.name);
        }
        return names;
    }

    /** One of a band's people has died. A band with nobody left is gone. */
    public static void memberDied(ServerLevel level, UUID id) {
        Bands data = of(level);
        Record record = data.bands.get(id);
        if (record == null) {
            return;
        }
        record.size--;
        if (record.size <= 0) {
            Fates.lastOneDied(level, record);
            data.bands.remove(id);
        }
        data.setDirty();
    }

    public static void remove(ServerLevel level, UUID id) {
        if (of(level).bands.remove(id) != null) {
            of(level).setDirty();
        }
    }

    public static void changed(ServerLevel level) {
        of(level).setDirty();
    }

    /** Once a day, for every band: presence and cohesion move. */
    public static void drift(ServerLevel level) {
        Bands data = of(level);
        long day = level.getDayTime() / 24000L;
        boolean hard = dev.hominin.evolution.survival.Seasons.strained(level);
        List<Record> drifted = new ArrayList<>();
        for (Record record : data.bands.values()) {
            if (record.driftDay >= day) {
                continue;
            }
            record.driftDay = day;
            record.drift(level.random, hard);
            record.strain(level, level.random);
            drifted.add(record);
        }
        if (drifted.isEmpty()) {
            return;
        }
        // Some days a band does not see the end of.
        for (Record record : drifted) {
            if (data.bands.containsKey(record.id)) {
                Fates.roll(level, record, level.random);
            }
        }
        // Now and then a band without friends finds one.
        for (Record record : data.bands.values()) {
            if (record.allies.isEmpty() && !record.nomadic() && level.random.nextFloat() < 0.05F) {
                Record neighbour = data.nearestHolder(record, 300.0D);
                if (neighbour != null) {
                    ally(record, neighbour);
                }
            }
            record.allies.removeIf(id -> !data.bands.containsKey(id));
        }
        // Desperate times: most bands out there desperate at once.
        List<Record> holders = data.bands.values().stream().filter(r -> !r.nomadic()).toList();
        long desperate = holders.stream().filter(r -> r.desperation >= 4).count();
        boolean was = data.desperateSince >= 0L;
        if (!was && holders.size() >= 3 && desperate >= holders.size() * 0.6D) {
            data.desperateSince = day;
            announce(level, "Desperate times. Nearly every band out there is going hungry, and hunger makes people "
                    + "dangerous: more patrols, more demands, more raids. Only a strong presence - or a name for "
                    + "breaking the bands that try you - will keep them off.", net.minecraft.ChatFormatting.DARK_RED);
        } else if (was && (holders.size() < 3 || desperate < holders.size() * 0.35D)) {
            data.desperateSince = -1L;
            announce(level, "The desperate times are passing. The bands out there are eating again.",
                    net.minecraft.ChatFormatting.GREEN);
        }
        data.setDirty();
    }

    private static void announce(ServerLevel level, String line, net.minecraft.ChatFormatting colour) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line).withStyle(colour));
        }
    }

    /** The band whose ground this is, if it is strong enough there to keep predators off. */
    public static float predatorsKeptOff(ServerLevel level, BlockPos pos) {
        Record record = groundAt(level, pos);
        return record == null || record.presence < 28 ? 0.0F : Math.min(0.6F, (record.presence - 25) / 40.0F);
    }

    /**
     * Whether a new band's ground here would run into anyone else's: another band's, or the ground a
     * player's band is living on. Grounds never overlap when a band first settles.
     */
    public static boolean groundIsFree(ServerLevel level, BlockPos site, int radius, List<BlockPos> playerCamps,
            int playerRadius) {
        for (Record record : of(level).bands.values()) {
            if (record.nomadic()) {
                continue;
            }
            double reach = radius + record.radius() + 8.0D;
            if (horizontal(site, record.home) < reach * reach) {
                return false;
            }
        }
        for (BlockPos camp : playerCamps) {
            double reach = radius + playerRadius + 8.0D;
            if (horizontal(site, camp) < reach * reach) {
                return false;
            }
        }
        return true;
    }

    public static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** The band whose ground this is, if any. */
    @Nullable
    public static Record groundAt(ServerLevel level, BlockPos pos) {
        for (Record record : of(level).bands.values()) {
            if (record.holds(pos)) {
                return record;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ saving

    private static Bands load(CompoundTag tag, HolderLookup.Provider registries) {
        Bands data = new Bands();
        for (Tag entry : tag.getList("Retired", Tag.TAG_COMPOUND)) {
            data.retired.add(((CompoundTag) entry).getUUID("Id"));
        }
        data.desperateSince = tag.contains("DesperateSince") ? tag.getLong("DesperateSince") : -1L;
        for (Tag entry : tag.getList("Bands", Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) entry;
            ResourceLocation species = ResourceLocation.tryParse(b.getString("Species"));
            if (species == null || !b.hasUUID("Id")) {
                continue;
            }
            Record record = new Record(b.getUUID("Id"), b.getString("Name"), species, BlockPos.of(b.getLong("Home")),
                    b.getInt("Size"));
            record.movedDay = b.getLong("MovedDay");
            record.desperation = Math.max(1, b.getInt("Desperation"));
            for (Tag s : b.getList("Stance", Tag.TAG_COMPOUND)) {
                CompoundTag st = (CompoundTag) s;
                record.stance.put(st.getUUID("Player"), st.getInt("Value"));
            }
            for (Tag s : b.getList("Allies", Tag.TAG_COMPOUND)) {
                record.allies.add(((CompoundTag) s).getUUID("Id"));
            }
            for (Tag s : b.getList("Access", Tag.TAG_COMPOUND)) {
                CompoundTag st = (CompoundTag) s;
                record.accessUntil.put(st.getUUID("Player"), st.getLong("Until"));
            }
            if (b.contains("Presence")) {
                record.presence = b.getInt("Presence");
                record.cohesion = b.getInt("Cohesion");
                record.driftDay = b.getLong("DriftDay");
            } else {
                // From before bands had their own: where they would have settled.
                record.presence = record.presenceMean();
                record.cohesion = record.presence * 3 / 5 + record.cohesionTrait();
                record.driftDay = record.movedDay;
            }
            for (Tag s : b.getList("Standing", Tag.TAG_COMPOUND)) {
                CompoundTag st = (CompoundTag) s;
                record.standing.put(st.getUUID("Player"), st.getInt("Value"));
            }
            for (Tag s : b.getList("Rumours", Tag.TAG_COMPOUND)) {
                CompoundTag st = (CompoundTag) s;
                record.rumours.put(st.getUUID("Player"), st.getInt("Value"));
            }
            for (Tag s : b.getList("Trespass", Tag.TAG_COMPOUND)) {
                CompoundTag st = (CompoundTag) s;
                record.trespass.put(st.getUUID("Player"), st.getInt("Value"));
            }
            for (Tag s : b.getList("Known", Tag.TAG_COMPOUND)) {
                record.known.add(((CompoundTag) s).getUUID("Player"));
            }
            data.bands.put(record.id, record);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Record record : bands.values()) {
            CompoundTag b = new CompoundTag();
            b.putUUID("Id", record.id);
            b.putString("Name", record.name);
            b.putString("Species", record.species.toString());
            b.putLong("Home", record.home.asLong());
            b.putInt("Size", record.size);
            b.putLong("MovedDay", record.movedDay);
            b.putInt("Presence", record.presence);
            b.putInt("Cohesion", record.cohesion);
            b.putLong("DriftDay", record.driftDay);
            b.putInt("Desperation", record.desperation);
            b.put("Stance", pairs(record.stance));
            ListTag access = new ListTag();
            for (var entry : record.accessUntil.entrySet()) {
                CompoundTag st = new CompoundTag();
                st.putUUID("Player", entry.getKey());
                st.putLong("Until", entry.getValue());
                access.add(st);
            }
            b.put("Access", access);
            ListTag allies = new ListTag();
            for (UUID ally : record.allies) {
                CompoundTag a = new CompoundTag();
                a.putUUID("Id", ally);
                allies.add(a);
            }
            b.put("Allies", allies);
            b.put("Standing", pairs(record.standing));
            b.put("Trespass", pairs(record.trespass));
            b.put("Rumours", pairs(record.rumours));
            ListTag known = new ListTag();
            for (UUID player : record.known) {
                CompoundTag k = new CompoundTag();
                k.putUUID("Player", player);
                known.add(k);
            }
            b.put("Known", known);
            list.add(b);
        }
        tag.put("Bands", list);
        ListTag retiredList = new ListTag();
        for (UUID id : retired) {
            CompoundTag r = new CompoundTag();
            r.putUUID("Id", id);
            retiredList.add(r);
        }
        tag.put("Retired", retiredList);
        tag.putLong("DesperateSince", desperateSince);
        return tag;
    }

    private static ListTag pairs(Map<UUID, Integer> values) {
        ListTag list = new ListTag();
        for (var entry : values.entrySet()) {
            CompoundTag st = new CompoundTag();
            st.putUUID("Player", entry.getKey());
            st.putInt("Value", entry.getValue());
            list.add(st);
        }
        return list;
    }

    /** A random source that is the same for the same band, for anything drawn from its id. */
    static RandomSource randomFor(UUID id) {
        return RandomSource.create(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
    }

    private Bands() {
    }
}
