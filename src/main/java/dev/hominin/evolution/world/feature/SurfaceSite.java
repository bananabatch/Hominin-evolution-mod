package dev.hominin.evolution.world.feature;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds somewhere a landmark can stand: open, level, natural ground.
 *
 * <p>Vanilla's placement filters can only test a handful of fixed offsets, which is
 * how the earlier outcrops ended up on hillsides - a slope only a block steeper
 * than the sample points passed every check. This reads the real ground height of
 * every column under the footprint and rejects the site if they spread by more
 * than a step, which is the thing that actually makes something look set down
 * rather than wedged in.
 *
 * <p>It also looks around a little when the first spot fails, so density is set by
 * the rarity filter and not by how rough the terrain happens to be.
 */
final class SurfaceSite {
    static final int NO_GROUND = Integer.MIN_VALUE;

    /** How far past the footprint to look for an existing landmark before allowing another. */
    private static final int SPACING = 3;

    /**
     * Y of the top block in this column, if that block is natural ground. Features in
     * {@code local_modifications} run before trees and plants, so the top block here
     * is bare terrain - grass, dirt, stone - or water, which is rejected.
     */
    static int groundY(WorldGenLevel level, int x, int z) {
        // getHeight is the first free block above the surface; the surface is one below.
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        if (y <= level.getMinBuildHeight()) {
            return NO_GROUND;
        }
        return isNaturalGround(level.getBlockState(new BlockPos(x, y, z))) ? y : NO_GROUND;
    }

    static boolean isNaturalGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL);
    }

    /** Anything this mod stands up out of the ground - these must never merge into each other. */
    static boolean isLandmark(BlockState state) {
        return state.is(ModBlocks.TERMITE_MOUND.get())
                || state.is(ModBlocks.QUARTZITE_DEPOSIT.get())
                || state.is(ModBlocks.LIMESTONE_DEPOSIT.get())
                || state.is(ModBlocks.BASALT_DEPOSIT.get())
                || state.is(ModBlocks.CHERT_DEPOSIT.get())
                || state.is(ModBlocks.FINE_CHERT_DEPOSIT.get())
                || state.is(ModBlocks.OBSIDIAN_DEPOSIT.get());
    }

    /**
     * Returns the ground block at the centre of a suitable footprint, or null.
     *
     * @param radius     footprint half-width; the footprint is (2r+1) square
     * @param maxRelief  most the ground height may vary across the footprint
     * @param attempts   candidate columns to try, the origin first
     * @param spread     how far from the origin later candidates may wander
     */
    @Nullable
    static BlockPos find(WorldGenLevel level, BlockPos origin, RandomSource random,
            int radius, int maxRelief, int attempts, int spread) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            int cx = origin.getX();
            int cz = origin.getZ();
            if (attempt > 0) {
                cx += random.nextInt(spread * 2 + 1) - spread;
                cz += random.nextInt(spread * 2 + 1) - spread;
            }
            int centre = groundY(level, cx, cz);
            if (centre == NO_GROUND || !isLevel(level, cx, cz, radius, maxRelief)) {
                continue;
            }
            if (landmarkNearby(level, cx, centre, cz, radius + SPACING)) {
                continue;
            }
            return new BlockPos(cx, centre, cz);
        }
        return null;
    }

    private static boolean isLevel(WorldGenLevel level, int cx, int cz, int radius, int maxRelief) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int y = groundY(level, cx + dx, cz + dz);
                if (y == NO_GROUND) {
                    return false;
                }
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
                if (highest - lowest > maxRelief) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean landmarkNearby(WorldGenLevel level, int cx, int cy, int cz, int reach) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -2; dy <= 5; dy++) {
                    if (isLandmark(level.getBlockState(pos.set(cx + dx, cy + dy, cz + dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private SurfaceSite() {
    }
}
