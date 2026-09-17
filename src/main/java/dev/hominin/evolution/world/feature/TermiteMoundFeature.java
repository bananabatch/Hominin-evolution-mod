package dev.hominin.evolution.world.feature;

import com.mojang.serialization.Codec;

import dev.hominin.evolution.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A savanna termite mound: a stepped spire of packed earth, three or four blocks
 * tall, with the vented blocks that hold the colony worked through it.
 *
 * <p>Built as a height map over a 5x5 footprint rather than a blob, so it always
 * rises clear of the grass - the old boulder-shaped mounds were a block tall half
 * the time and vanished into it. Each column is filled from its own ground level,
 * so on a one-block step nothing floats and nothing is buried; and the ground under
 * the mound is turned to packed earth, so the base reads as growing out of the soil
 * instead of sitting on top of it.
 */
public class TermiteMoundFeature extends Feature<NoneFeatureConfiguration> {
    private static final int RADIUS = 2;
    private static final int SIZE = RADIUS * 2 + 1;
    private static final int ATTEMPTS = 8;
    private static final int SPREAD = 6;

    private static final float TALL_PEAK_CHANCE = 0.35F;
    private static final float INNER_ORTHOGONAL_CHANCE = 0.75F;
    private static final float INNER_DIAGONAL_CHANCE = 0.45F;
    private static final float OUTER_CHANCE = 0.3F;
    private static final int MIN_INNER_CELLS = 3;
    private static final float APRON_CHANCE = 0.35F;

    public TermiteMoundFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos site = SurfaceSite.find(level, context.origin(), random, RADIUS, 1, ATTEMPTS, SPREAD);
        if (site == null) {
            return false;
        }
        int[][] heights = shape(random);
        int baseY = site.getY();

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int height = heights[dx + RADIUS][dz + RADIUS];
                int x = site.getX() + dx;
                int z = site.getZ() + dz;
                int ground = SurfaceSite.groundY(level, x, z);
                if (ground == SurfaceSite.NO_GROUND) {
                    continue;
                }
                if (height <= 0) {
                    // A little packed earth spreading out beside the base, so the edge
                    // of the mound fades into the ground rather than stopping dead.
                    if (touchesMound(heights, dx, dz) && random.nextFloat() < APRON_CHANCE) {
                        setBlock(level, new BlockPos(x, ground, z), Blocks.PACKED_MUD.defaultBlockState());
                    }
                    continue;
                }
                setBlock(level, new BlockPos(x, ground, z), pick(random, 0));
                int top = baseY + height;
                for (int y = ground + 1; y <= top; y++) {
                    setBlock(level, new BlockPos(x, y, z), pick(random, y - baseY));
                }
            }
        }
        ventTheSummit(level, site, heights, baseY);
        return true;
    }

    /**
     * Heights above the centre's ground for each cell of the footprint: a peak in
     * the middle, a broken ring a step or two lower around it, and the odd low
     * shoulder on the outside. The randomness is all in which cells are included,
     * which is what gives each mound its own lopsided outline.
     */
    private static int[][] shape(RandomSource random) {
        int[][] heights = new int[SIZE][SIZE];
        int peak = random.nextFloat() < TALL_PEAK_CHANCE ? 4 : 3;
        heights[RADIUS][RADIUS] = peak;

        int inner = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                boolean orthogonal = dx == 0 || dz == 0;
                if (random.nextFloat() < (orthogonal ? INNER_ORTHOGONAL_CHANCE : INNER_DIAGONAL_CHANCE)) {
                    heights[dx + RADIUS][dz + RADIUS] = innerHeight(random, peak);
                    inner++;
                }
            }
        }
        // A spire with nothing round its foot reads as a post, not a mound.
        while (inner < MIN_INNER_CELLS) {
            int dx = random.nextInt(3) - 1;
            int dz = random.nextInt(3) - 1;
            if ((dx != 0 || dz != 0) && heights[dx + RADIUS][dz + RADIUS] == 0) {
                heights[dx + RADIUS][dz + RADIUS] = innerHeight(random, peak);
                inner++;
            }
        }
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != RADIUS) {
                    continue;
                }
                int innerHeight = heights[Math.max(-1, Math.min(1, dx)) + RADIUS][Math.max(-1, Math.min(1, dz)) + RADIUS];
                if (innerHeight >= 2 && random.nextFloat() < OUTER_CHANCE) {
                    heights[dx + RADIUS][dz + RADIUS] = 1;
                }
            }
        }
        return heights;
    }

    private static int innerHeight(RandomSource random, int peak) {
        return Math.max(1, peak - 1 - random.nextInt(2));
    }

    private static boolean touchesMound(int[][] heights, int dx, int dz) {
        int[][] neighbours = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] n : neighbours) {
            int nx = dx + n[0] + RADIUS;
            int nz = dz + n[1] + RADIUS;
            if (nx >= 0 && nx < SIZE && nz >= 0 && nz < SIZE && heights[nx][nz] > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vents get likelier the higher up the mound they are, the way a real mound's
     * chimneys open near the top; the base is mostly solid packed earth.
     */
    private static BlockState pick(RandomSource random, int heightAboveGround) {
        float ventChance = switch (heightAboveGround) {
            case 0 -> 0.1F;
            case 1 -> 0.35F;
            case 2 -> 0.6F;
            default -> 0.85F;
        };
        return random.nextFloat() < ventChance
                ? ModBlocks.TERMITE_MOUND.get().defaultBlockState()
                : Blocks.PACKED_MUD.defaultBlockState();
    }

    /** The peak's top block, and the top of the tallest shoulder, are always vented. */
    private void ventTheSummit(WorldGenLevel level, BlockPos site, int[][] heights, int baseY) {
        BlockState vent = ModBlocks.TERMITE_MOUND.get().defaultBlockState();
        setBlock(level, new BlockPos(site.getX(), baseY + heights[RADIUS][RADIUS], site.getZ()), vent);

        int bestDx = 0;
        int bestDz = 0;
        int best = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int h = heights[dx + RADIUS][dz + RADIUS];
                if ((dx != 0 || dz != 0) && h > best) {
                    best = h;
                    bestDx = dx;
                    bestDz = dz;
                }
            }
        }
        if (best > 0) {
            setBlock(level, new BlockPos(site.getX() + bestDx, baseY + best, site.getZ() + bestDz), vent);
        }
    }
}
