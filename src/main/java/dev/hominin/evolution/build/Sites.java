package dev.hominin.evolution.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Everything marked out to be built, and everything built from a blueprint, in one level: where it stands, whose
 * it is, how far along it is, and - once it stands - what it is for.
 */
public final class Sites extends SavedData {
    private static final String NAME = "hominin_evolution_sites";

    /** What a finished build is for. */
    public enum Use {
        NONE("not decided"),
        STORE("the band's store"),
        MINE("yours"),
        THEIRS("given");

        private final String label;

        Use(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final class Site {
        private final int id;
        private final ResourceLocation blueprint;
        private final BlockPos origin;
        private final Direction forward;
        private final UUID owner;
        boolean built;
        Use use = Use.NONE;
        @Nullable
        UUID givenTo;
        String givenName = "";
        /** Whether finishing it has counted for presence already. */
        boolean credited;
        /** One of the band thinks this should be built here, and has not been answered yet. */
        boolean proposed;
        String proposer = "";
        @Nullable
        UUID proposerId;
        long proposedAt;
        /** How much of it already stood when it was suggested. */
        int proposalPlaced;
        int placed;
        int total;
        @Nullable
        private Footprint footprint;

        Site(int id, ResourceLocation blueprint, BlockPos origin, Direction forward, UUID owner) {
            this.id = id;
            this.blueprint = blueprint;
            this.origin = origin.immutable();
            this.forward = forward;
            this.owner = owner;
        }

        public int id() {
            return id;
        }

        public ResourceLocation blueprint() {
            return blueprint;
        }

        public BlockPos origin() {
            return origin;
        }

        public Direction forward() {
            return forward;
        }

        public UUID owner() {
            return owner;
        }

        public boolean built() {
            return built;
        }

        public Use use() {
            return use;
        }

        @Nullable
        public UUID givenTo() {
            return givenTo;
        }

        public String givenName() {
            return givenName;
        }

        public boolean proposed() {
            return proposed;
        }

        public String proposer() {
            return proposer;
        }

        public int placed() {
            return placed;
        }

        /** Where it all goes in the world - worked out again if the blueprint has changed under it. */
        @Nullable
        public Footprint footprint() {
            Blueprint current = Blueprints.get(blueprint);
            if (current == null) {
                return null;
            }
            if (footprint == null || footprint.blueprint() != current) {
                footprint = new Footprint(current, origin, forward);
            }
            return footprint;
        }

        /** "small hut". */
        public String name() {
            Blueprint current = Blueprints.get(blueprint);
            return (current != null ? current.name() : blueprint.getPath().replace('_', ' ')).toLowerCase();
        }

        /** How it reads on a map or in a list. */
        public String label() {
            String name = Character.toUpperCase(name().charAt(0)) + name().substring(1);
            if (proposed) {
                return name + " - " + proposer + " wants it built here";
            }
            if (!built) {
                return name + " - marked out, " + placed + "/" + total;
            }
            return switch (use) {
                case NONE -> name + " - not decided what it is for";
                case STORE -> name + " - the band's store";
                case MINE -> name + " - yours";
                case THEIRS -> name + " - " + givenName + "'s";
            };
        }
    }

    private final List<Site> sites = new ArrayList<>();
    private int nextId = 1;
    /** Which blueprints each player has finished at least once, anywhere. Kept in the overworld's copy. */
    private final Map<UUID, Set<ResourceLocation>> finished = new HashMap<>();

    public static Sites of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Sites::new, Sites::load), NAME);
    }

    public static List<Site> all(ServerLevel level) {
        return List.copyOf(of(level).sites);
    }

    @Nullable
    public static Site byId(ServerLevel level, int id) {
        for (Site site : of(level).sites) {
            if (site.id == id) {
                return site;
            }
        }
        return null;
    }

    /** The build that wants a block in exactly this spot. */
    @Nullable
    public static Site cellAt(ServerLevel level, BlockPos pos) {
        for (Site site : of(level).sites) {
            Footprint footprint = site.footprint();
            if (footprint != null && footprint.box().isInside(pos) && footprint.cells().containsKey(pos)) {
                return site;
            }
        }
        return null;
    }

    /** The build this spot is any part of. */
    @Nullable
    public static Site containing(ServerLevel level, BlockPos pos) {
        for (Site site : of(level).sites) {
            Footprint footprint = site.footprint();
            if (footprint != null && footprint.contains(pos)) {
                return site;
            }
        }
        return null;
    }

    /** The finished build whose room this spot is in. */
    @Nullable
    public static Site roomAt(ServerLevel level, BlockPos pos) {
        for (Site site : of(level).sites) {
            Footprint footprint = site.footprint();
            if (site.built && footprint != null && footprint.isInside(pos)) {
                return site;
            }
        }
        return null;
    }

    /** Whether this spot is inside a band's store. */
    public static boolean isStore(ServerLevel level, BlockPos pos) {
        Site site = roomAt(level, pos);
        return site != null && site.use == Use.STORE;
    }

    /** The build someone was given, if they were given one here. */
    @Nullable
    public static Site givenTo(ServerLevel level, UUID member) {
        for (Site site : of(level).sites) {
            if (site.built && site.use == Use.THEIRS && member.equals(site.givenTo)) {
                return site;
            }
        }
        return null;
    }

    /** Whether another build already has this spot. */
    public static boolean taken(ServerLevel level, BlockPos pos) {
        return containing(level, pos) != null;
    }

    public static List<Site> ownedBy(ServerLevel level, UUID owner) {
        List<Site> list = new ArrayList<>();
        for (Site site : of(level).sites) {
            if (site.owner.equals(owner)) {
                list.add(site);
            }
        }
        return list;
    }

    Site add(ResourceLocation blueprint, BlockPos origin, Direction forward, UUID owner) {
        Site site = new Site(nextId++, blueprint, origin, forward, owner);
        Footprint footprint = site.footprint();
        site.total = footprint != null ? footprint.total() : 0;
        sites.add(site);
        setDirty();
        return site;
    }

    void remove(Site site) {
        if (sites.remove(site)) {
            setDirty();
        }
    }

    void changed() {
        setDirty();
    }

    // ------------------------------------------------------------ what has been built before

    private static Sites overworld(MinecraftServer server) {
        return of(server.overworld());
    }

    public static boolean hasFinished(MinecraftServer server, UUID player, ResourceLocation blueprint) {
        return overworld(server).finished.getOrDefault(player, Set.of()).contains(blueprint);
    }

    static void markFinished(MinecraftServer server, UUID player, ResourceLocation blueprint) {
        Sites data = overworld(server);
        if (data.finished.computeIfAbsent(player, k -> new HashSet<>()).add(blueprint)) {
            data.setDirty();
        }
    }

    // ------------------------------------------------------------ saving

    private static Sites load(CompoundTag tag, HolderLookup.Provider registries) {
        Sites data = new Sites();
        data.nextId = Math.max(1, tag.getInt("NextId"));
        for (Tag entry : tag.getList("Sites", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) entry;
            ResourceLocation blueprint = ResourceLocation.tryParse(s.getString("Blueprint"));
            if (blueprint == null || !s.hasUUID("Owner")) {
                continue;
            }
            Site site = new Site(s.getInt("Id"), blueprint, BlockPos.of(s.getLong("Origin")),
                    Direction.from2DDataValue(s.getInt("Forward")), s.getUUID("Owner"));
            site.built = s.getBoolean("Built");
            site.use = Use.values()[Math.max(0, Math.min(Use.values().length - 1, s.getInt("Use")))];
            site.givenTo = s.hasUUID("GivenTo") ? s.getUUID("GivenTo") : null;
            site.givenName = s.getString("GivenName");
            site.credited = s.getBoolean("Credited");
            site.placed = s.getInt("Placed");
            site.total = s.getInt("Total");
            site.proposed = s.getBoolean("Proposed");
            site.proposer = s.getString("Proposer");
            site.proposerId = s.hasUUID("ProposerId") ? s.getUUID("ProposerId") : null;
            site.proposedAt = s.getLong("ProposedAt");
            site.proposalPlaced = s.getInt("ProposalPlaced");
            data.sites.add(site);
            data.nextId = Math.max(data.nextId, site.id + 1);
        }
        for (Tag entry : tag.getList("Finished", Tag.TAG_COMPOUND)) {
            CompoundTag f = (CompoundTag) entry;
            Set<ResourceLocation> set = new HashSet<>();
            for (Tag id : f.getList("Blueprints", Tag.TAG_STRING)) {
                ResourceLocation blueprint = ResourceLocation.tryParse(id.getAsString());
                if (blueprint != null) {
                    set.add(blueprint);
                }
            }
            data.finished.put(f.getUUID("Player"), set);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextId", nextId);
        ListTag list = new ListTag();
        for (Site site : sites) {
            CompoundTag s = new CompoundTag();
            s.putInt("Id", site.id);
            s.putString("Blueprint", site.blueprint.toString());
            s.putLong("Origin", site.origin.asLong());
            s.putInt("Forward", site.forward.get2DDataValue());
            s.putUUID("Owner", site.owner);
            s.putBoolean("Built", site.built);
            s.putInt("Use", site.use.ordinal());
            if (site.givenTo != null) {
                s.putUUID("GivenTo", site.givenTo);
            }
            s.putString("GivenName", site.givenName);
            s.putBoolean("Credited", site.credited);
            s.putInt("Placed", site.placed);
            s.putInt("Total", site.total);
            s.putBoolean("Proposed", site.proposed);
            s.putString("Proposer", site.proposer);
            if (site.proposerId != null) {
                s.putUUID("ProposerId", site.proposerId);
            }
            s.putLong("ProposedAt", site.proposedAt);
            s.putInt("ProposalPlaced", site.proposalPlaced);
            list.add(s);
        }
        tag.put("Sites", list);
        ListTag finishedList = new ListTag();
        for (var entry : finished.entrySet()) {
            CompoundTag f = new CompoundTag();
            f.putUUID("Player", entry.getKey());
            ListTag ids = new ListTag();
            for (ResourceLocation id : entry.getValue()) {
                ids.add(StringTag.valueOf(id.toString()));
            }
            f.put("Blueprints", ids);
            finishedList.add(f);
        }
        tag.put("Finished", finishedList);
        return tag;
    }

    private Sites() {
    }
}
