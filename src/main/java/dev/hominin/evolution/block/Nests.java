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
        // A good place to sleep: under a tree, or down by the water - and not on top of anyone else.
        BlockPos[] best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos corner = origin.offset(random.nextInt(radius * 2 + 1) - radius, 0,
                    random.nextInt(radius * 2 + 1) - radius);
            boolean alongX = random.nextBoolean();
            for (int dy : new int[] {0, -1, 1}) {
                BlockPos[] cells = cells(corner.above(dy), alongX);
                if (!fits(level, cells)) {
                    continue;
                }
                float score = score(level, cells) + random.nextFloat();
                if (score > bestScore) {
                    bestScore = score;
                    best = cells;
                }
                break;
            }
        }
        return best != null && bestScore > -50.0F ? best : null;
    }

    /** Under leaves +3, by the water +2, crowding another bed -5 - and right up against one, not at all. */
    private static float score(LevelReader level, BlockPos[] cells) {
        BlockPos corner = cells[0];
        float score = 0.0F;
        boolean canopy = false;
        for (int up = 2; up <= 9 && !canopy; up++) {
            canopy = level.getBlockState(corner.above(up)).is(net.minecraft.tags.BlockTags.LEAVES);
        }
        if (canopy) {
            score += 3.0F;
        }
        boolean shore = false;
        for (BlockPos near : BlockPos.betweenClosed(corner.offset(-3, -1, -3), corner.offset(3, 0, 3))) {
            if (level.getFluidState(near).is(net.minecraft.tags.FluidTags.WATER)) {
                shore = true;
                break;
            }
        }
        if (shore) {
            score += 2.0F;
        }
        for (BlockPos near : BlockPos.betweenClosed(corner.offset(-4, -1, -4), corner.offset(4 + WIDTH, 1, 4 + WIDTH))) {
            var state = level.getBlockState(near);
            if (state.is(ModBlocks.NEST.get()) || state.is(ModBlocks.THATCH_BEDDING.get())) {
                double d = Math.sqrt(near.distSqr(corner));
                score -= d <= 2.0D ? 100.0F : 5.0F;
                break;
            }
        }
        return score;
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

    /**
     * A tree to nest in, for the kinds that still climb: the top of a trunk near here, with the nest woven across
     * the crown of it - in place of the topmost leaves, over the last of the trunk and the leaves around it. The six
     * positions in build order, or null when there is no tree near enough, or none with room.
     */
    @Nullable
    public static BlockPos[] treeSiteNear(LevelReader level, BlockPos origin, int radius,
            net.minecraft.util.RandomSource random) {
        BlockPos[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -3, -radius), origin.offset(radius, 3, radius))) {
            if (!isTrunkBase(level, pos)) {
                continue;
            }
            BlockPos top = pos;
            while (level.getBlockState(top.above()).is(net.minecraft.tags.BlockTags.LOGS)
                    && top.getY() - pos.getY() < 20) {
                top = top.above();
            }
            // A sapling or a stump is no tree to sleep in.
            if (top.getY() - pos.getY() < 3) {
                continue;
            }
            BlockPos crown = top.above();
            for (boolean alongX : new boolean[] {random.nextBoolean(), false, true}) {
                BlockPos[] cells = crownCells(level, crown, alongX);
                if (cells == null) {
                    continue;
                }
                double score = pos.distSqr(origin) + random.nextDouble() * 4.0D;
                if (score < bestScore) {
                    bestScore = score;
                    best = cells;
                }
                break;
            }
        }
        return best;
    }

    /** A two-by-three over the trunk's top, every piece of it room to weave in and held up by the tree. */
    @Nullable
    private static BlockPos[] crownCells(LevelReader level, BlockPos crown, boolean alongX) {
        for (int l = 0; l < LENGTH; l++) {
            for (int w = 0; w < WIDTH; w++) {
                BlockPos corner = alongX ? crown.offset(-l, 0, -w) : crown.offset(-w, 0, -l);
                BlockPos[] cells = cells(corner, alongX);
                if (crownFits(level, cells)) {
                    // Woven from what holds it outwards: the pieces resting on the tree go in first.
                    java.util.Arrays.sort(cells, java.util.Comparator.comparingInt(
                            cell -> NestBlock.restsOn(level, cell.below()) ? 0 : 1));
                    return cells;
                }
            }
        }
        return null;
    }

    private static boolean crownFits(LevelReader level, BlockPos[] cells) {
        for (BlockPos cell : cells) {
            var state = level.getBlockState(cell);
            if (!(state.isAir() || state.is(net.minecraft.tags.BlockTags.LEAVES)) || !level.getFluidState(cell).isEmpty()
                    || !level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
                    && !level.getBlockState(cell.above()).is(net.minecraft.tags.BlockTags.LEAVES)) {
                return false;
            }
        }
        // Held up: each piece on leaves or a bough, or beside one of the pieces that is.
        for (BlockPos cell : cells) {
            if (NestBlock.restsOn(level, cell.below())) {
                continue;
            }
            boolean held = false;
            for (BlockPos other : cells) {
                if (other.distManhattan(cell) == 1 && NestBlock.restsOn(level, other.below())) {
                    held = true;
                    break;
                }
            }
            if (!held) {
                return false;
            }
        }
        return true;
    }

    /** The foot of a trunk: a log on something that is not a log, with more log over it. */
    private static boolean isTrunkBase(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)
                && !level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.LOGS)
                && level.getBlockState(pos.above()).is(net.minecraft.tags.BlockTags.LOGS)
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** The foot of the trunk a nest up a tree is woven onto - where to start climbing to it - or null. */
    @Nullable
    public static BlockPos trunkUnder(BlockGetter level, BlockPos nest) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(nest.offset(-3, -2, -3), nest.offset(3, -1, 3))) {
            if (!level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) {
                continue;
            }
            double distance = pos.distSqr(nest);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        if (best == null) {
            return null;
        }
        BlockPos base = best;
        while (level.getBlockState(base.below()).is(net.minecraft.tags.BlockTags.LOGS) && best.getY() - base.getY() < 24) {
            base = base.below();
        }
        return base;
    }

    /** Whether a nest block already lies within this many blocks - on the ground, or up a tree overhead. */
    public static boolean nestNearby(BlockGetter level, BlockPos center, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 9, radius))) {
            if (level.getBlockState(pos).is(ModBlocks.NEST.get())) {
                return true;
            }
        }
        return false;
    }

    private Nests() {
    }
}
