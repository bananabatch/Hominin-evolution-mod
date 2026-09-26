package dev.hominin.evolution.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where each chimpanzee community holds its ground - far smaller than a hominin band's, and held far more
 * fiercely - and who has come across it. Known ranges are on the mental map.
 */
public final class ChimpRanges extends SavedData {
    private static final String NAME = "hominin_chimp_ranges";
    /** How far a community's ground runs from its heart. */
    public static final int RADIUS = 50;

    public record Range(UUID community, BlockPos home, Set<UUID> known) {
    }

    private final Map<UUID, Range> ranges = new HashMap<>();

    private static ChimpRanges of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ChimpRanges::new, ChimpRanges::load), NAME);
    }

    /** A community's heart: set the first time it is reported, and kept. */
    public static BlockPos note(ServerLevel level, UUID community, BlockPos home) {
        ChimpRanges data = of(level);
        Range range = data.ranges.get(community);
        if (range == null) {
            range = new Range(community, home.immutable(), new HashSet<>());
            data.ranges.put(community, range);
            data.setDirty();
        }
        return range.home();
    }

    /** Somebody has come across this community's ground: it is on their map now. */
    public static void know(ServerLevel level, UUID community, UUID player) {
        ChimpRanges data = of(level);
        Range range = data.ranges.get(community);
        if (range != null && range.known().add(player)) {
            data.setDirty();
        }
    }

    public static List<Range> knownTo(ServerLevel level, UUID player) {
        List<Range> known = new ArrayList<>();
        for (Range range : of(level).ranges.values()) {
            if (range.known().contains(player)) {
                known.add(range);
            }
        }
        return known;
    }

    /** A community that is gone - every one of them dead - holds nothing. */
    public static void forget(ServerLevel level, UUID community) {
        if (of(level).ranges.remove(community) != null) {
            of(level).setDirty();
        }
    }

    private static ChimpRanges load(CompoundTag tag, HolderLookup.Provider registries) {
        ChimpRanges data = new ChimpRanges();
        for (Tag entry : tag.getList("Ranges", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) entry;
            Set<UUID> known = new HashSet<>();
            for (Tag k : r.getList("Known", Tag.TAG_COMPOUND)) {
                known.add(((CompoundTag) k).getUUID("Player"));
            }
            UUID id = r.getUUID("Community");
            data.ranges.put(id, new Range(id, BlockPos.of(r.getLong("Home")), known));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Range range : ranges.values()) {
            CompoundTag r = new CompoundTag();
            r.putUUID("Community", range.community());
            r.putLong("Home", range.home().asLong());
            ListTag known = new ListTag();
            for (UUID player : range.known()) {
                CompoundTag k = new CompoundTag();
                k.putUUID("Player", player);
                known.add(k);
            }
            r.put("Known", known);
            list.add(r);
        }
        tag.put("Ranges", list);
        return tag;
    }

    private ChimpRanges() {
    }
}
