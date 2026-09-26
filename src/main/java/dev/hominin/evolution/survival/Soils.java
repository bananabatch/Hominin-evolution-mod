package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Ground gives only so much. Every chunk of it can be foraged successfully so many times - a dozen or so,
 * fewer in a dry season or on a dry day, more in the rains, far more on a prosperous day - and then it is
 * picked clean, and gives nothing until it has come back: five days, or two in the rains, or overnight
 * after a prosperous day.
 *
 * <p>Some ground is <b>fertile</b>: dark, soft soil full of roots and grubs, in patches a few chunks across.
 * It gives four times as long and comes back in three days. Nothing tells you where it is; you have to
 * notice it (see {@link dev.hominin.evolution.mind.Insights}).
 */
public final class Soils extends SavedData {
    private static final String NAME = "hominin_soils";
    /** Forage counts on ground left alone this long are forgotten. */
    private static final long FORGET_TICKS = 48000L;
    /** Fertile patches: one chance in this many cells of 6 x 6 chunks. */
    public static final int CELL = 6;
    private static final float FERTILE_CHANCE = 0.22F;

    private record Soil(int count, long last, long recoversAt) {
    }

    private final Map<Long, Soil> chunks = new HashMap<>();

    private static Soils of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Soils::new, Soils::load), NAME);
    }

    // ------------------------------------------------------------ fertile ground

    private static long mix(long seed, long a, long b) {
        long h = seed * 31L + a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return h;
    }

    /** Whether this chunk is part of a fertile patch. Worked out from the seed: nothing to save. */
    /** A patch is five chunks by five: 25 chunks of good ground round its middle. */
    private static final int PATCH_RADIUS = 2;

    public static boolean fertile(ServerLevel level, int cx, int cz) {
        int cellX = Math.floorDiv(cx, CELL);
        int cellZ = Math.floorDiv(cz, CELL);
        boolean inside = false;
        // A patch near the edge of its cell runs over into the next: look at the cells round this one as well.
        for (int dx = -1; dx <= 1 && !inside; dx++) {
            for (int dz = -1; dz <= 1 && !inside; dz++) {
                long h = mix(level.getSeed() ^ 0x5F3E11L, cellX + dx, cellZ + dz);
                if ((Math.abs(h) % 1000L) >= (long) (FERTILE_CHANCE * 1000.0F)) {
                    continue;
                }
                int centreX = (cellX + dx) * CELL + (int) (Math.abs(h >> 8) % CELL);
                int centreZ = (cellZ + dz) * CELL + (int) (Math.abs(h >> 16) % CELL);
                inside = Math.abs(cx - centreX) <= PATCH_RADIUS && Math.abs(cz - centreZ) <= PATCH_RADIUS;
            }
        }
        if (!inside) {
            return false;
        }
        var biome = level.getBiome(new BlockPos((cx << 4) + 8, 64, (cz << 4) + 8));
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_BEACH) && !biome.is(BiomeTags.IS_BADLANDS)
                && !biome.is(Biomes.DESERT) && !biome.is(BiomeTags.IS_MOUNTAIN);
    }

    /** The middle of the fertile patch in this cell of chunks, if the cell has one. */
    @javax.annotation.Nullable
    public static BlockPos patchCentre(ServerLevel level, int cellX, int cellZ) {
        long h = mix(level.getSeed() ^ 0x5F3E11L, cellX, cellZ);
        if ((Math.abs(h) % 1000L) >= (long) (FERTILE_CHANCE * 1000.0F)) {
            return null;
        }
        int centreX = cellX * CELL + (int) (Math.abs(h >> 8) % CELL);
        int centreZ = cellZ * CELL + (int) (Math.abs(h >> 16) % CELL);
        return fertile(level, centreX, centreZ) ? new BlockPos((centreX << 4) + 8, 64, (centreZ << 4) + 8) : null;
    }

    public static boolean fertile(ServerLevel level, BlockPos pos) {
        return fertile(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** A key naming the fertile patch this chunk belongs to - one per patch, for remembering having noticed it. */
    public static String patchKey(int cx, int cz) {
        return Math.floorDiv(cx, CELL) + "_" + Math.floorDiv(cz, CELL);
    }

    // ------------------------------------------------------------ how much it gives

    /** Successful forages a chunk gives before it is picked clean, today. */
    public static int capacity(Level level, boolean fertile) {
        if (Drought.isProsperousDay(level)) {
            return fertile ? 65 : 25;
        }
        if (fertile && !Seasons.superDry(level)) {
            // Good ground holds its water: an ordinary dry season or a dry day does not touch it. A super-dry one does.
            return 55;
        }
        if (Drought.isActive(level)) {
            return fertile ? 35 : 8;
        }
        if (Seasons.isDry(level)) {
            return fertile ? 45 : 10;
        }
        return fertile ? 55 : 15;
    }

    /** When ground picked clean now will give again. */
    private static long recovery(Level level, boolean fertile, long now) {
        if (Drought.isProsperousDay(level)) {
            // Overnight: by the next morning.
            return (now / 24000L + 1L) * 24000L;
        }
        int days = Seasons.isProsperous(level) ? 2 : fertile ? 3 : 5;
        return now + days * 24000L;
    }

    /** Days until this ground gives again; zero when it gives now. */
    public static float driedFor(ServerLevel level, BlockPos pos) {
        Soil soil = of(level).chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        long now = level.getGameTime();
        if (soil == null || soil.recoversAt() <= now) {
            return 0.0F;
        }
        return (soil.recoversAt() - now) / 24000.0F;
    }

    /** Successful forages this ground has left in it today. */
    public static int left(ServerLevel level, BlockPos pos) {
        Soil soil = of(level).chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        int used = soil == null || level.getGameTime() - soil.last() > FORGET_TICKS ? 0 : soil.count();
        return Math.max(0, capacity(level, fertile(level, pos)) - used);
    }

    /**
     * A successful forage here. Returns what to tell the forager when it has just given its last, or null
     * while there is still more in it.
     */
    public static String foraged(ServerLevel level, BlockPos pos) {
        Soils soils = of(level);
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        long now = level.getGameTime();
        Soil soil = soils.chunks.get(key);
        int count = soil == null || now - soil.last() > FORGET_TICKS || soil.recoversAt() > 0L && soil.recoversAt() <= now
                ? 0 : soil.count();
        count++;
        boolean fertile = fertile(level, pos);
        String dried = null;
        long recovers = 0L;
        if (count >= capacity(level, fertile)) {
            recovers = recovery(level, fertile, now);
            float days = (recovers - now) / 24000.0F;
            dried = "That was the last this ground had in it. It is picked clean - "
                    + (days < 1.0F ? "it will come back overnight." : "it needs " + Math.round(days) + " days to come back.")
                    + " Forage further off.";
        }
        soils.chunks.put(key, new Soil(dried == null ? count : 0, now, recovers));
        soils.setDirty();
        return dried;
    }

    /** Now and then, ground long left alone is forgotten, so the record does not grow forever. */
    public static void tidy(ServerLevel level) {
        if (level.getGameTime() % 24000L != 777L) {
            return;
        }
        Soils soils = of(level);
        long now = level.getGameTime();
        boolean changed = false;
        for (Iterator<Map.Entry<Long, Soil>> it = soils.chunks.entrySet().iterator(); it.hasNext();) {
            Soil soil = it.next().getValue();
            if (soil.recoversAt() <= now && now - soil.last() > FORGET_TICKS) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            soils.setDirty();
        }
    }

    // ------------------------------------------------------------ saving

    private static Soils load(CompoundTag tag, HolderLookup.Provider registries) {
        Soils soils = new Soils();
        for (Tag entry : tag.getList("Chunks", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            soils.chunks.put(c.getLong("Pos"), new Soil(c.getInt("Count"), c.getLong("Last"), c.getLong("Recovers")));
        }
        return soils;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : chunks.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Pos", entry.getKey());
            c.putInt("Count", entry.getValue().count());
            c.putLong("Last", entry.getValue().last());
            c.putLong("Recovers", entry.getValue().recoversAt());
            list.add(c);
        }
        tag.put("Chunks", list);
        return tag;
    }

    private Soils() {
    }
}
