package dev.hominin.evolution.block;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;

/**
 * The shape of a finished nest. One armful of leaves is a cushion, not a bed: a nest
 * big enough to sleep in is two blocks wide and three long, the size of the animal
 * lying in it, and making one takes six armfuls and some time.
 */
public final class Nests {
    public static final int WIDTH = 2;
    public static final int LENGTH = 3;
    public static final int SIZE = WIDTH * LENGTH;

    /** Whether this nest block is part of a complete two-by-three nest, in either direction. */
    public static boolean isComplete(BlockGetter level, BlockPos pos) {
        return largestAround(level, pos) >= SIZE;
    }

    /**
     * How many of the six blocks the best-filled nest-sized rectangle through this block
     * has. Used to tell a player how far along an unfinished nest is.
     */
    public static int largestAround(BlockGetter level, BlockPos pos) {
        int best = 0;
        for (boolean alongX : new boolean[] {true, false}) {
            int sizeX = alongX ? LENGTH : WIDTH;
            int sizeZ = alongX ? WIDTH : LENGTH;
            for (int ox = 0; ox < sizeX; ox++) {
                for (int oz = 0; oz < sizeZ; oz++) {
                    BlockPos corner = pos.offset(-ox, 0, -oz);
                    best = Math.max(best, count(level, corner, sizeX, sizeZ));
                }
            }
        }
        return best;
    }

    private static int count(BlockGetter level, BlockPos corner, int sizeX, int sizeZ) {
        int filled = 0;
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                if (level.getBlockState(corner.offset(x, 0, z)).is(ModBlocks.NEST.get())) {
                    filled++;
                }
            }
        }
        return filled;
    }

    /** A place where a whole nest fits on flat ground, as its six positions in build order, or null. */
    @Nullable
    public static BlockPos[] siteNear(LevelReader level, BlockPos origin, int radius,
            net.minecraft.util.RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos corner = origin.offset(random.nextInt(radius * 2 + 1) - radius, 0,
                    random.nextInt(radius * 2 + 1) - radius);
            boolean alongX = random.nextBoolean();
            for (int dy : new int[] {0, -1, 1}) {
                BlockPos[] cells = cells(corner.above(dy), alongX);
                if (fits(level, cells)) {
                    return cells;
                }
            }
        }
        return null;
    }

    private static BlockPos[] cells(BlockPos corner, boolean alongX) {
        BlockPos[] cells = new BlockPos[SIZE];
        int i = 0;
        for (int l = 0; l < LENGTH; l++) {
            for (int w = 0; w < WIDTH; w++) {
                cells[i++] = alongX ? corner.offset(l, 0, w) : corner.offset(w, 0, l);
            }
        }
        return cells;
    }

    private static boolean fits(LevelReader level, BlockPos[] cells) {
        for (BlockPos cell : cells) {
            var state = level.getBlockState(cell);
            BlockPos below = cell.below();
            if (!state.canBeReplaced() || !level.getFluidState(cell).isEmpty()
                    || state.is(ModBlocks.NEST.get())
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                    || !level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Whether a nest block already lies within this many blocks. */
    public static boolean nestNearby(BlockGetter level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
            if (level.getBlockState(pos).is(ModBlocks.NEST.get())) {
                return true;
            }
        }
        return false;
    }

    private Nests() {
    }
}
