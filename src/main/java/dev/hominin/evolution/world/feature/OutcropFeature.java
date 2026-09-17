package dev.hominin.evolution.world.feature;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

/**
 * Bedrock breaking the surface: a low, broad knuckle of stone, sunk into the ground
 * and standing a block or two proud of it.
 *
 * <p>The stone block comes from the configured feature, so one feature serves every
 * deposit type. It is deliberately wider in the ground than above it - a patch of
 * bare stone at soil level with the raised part inside that - because that is what
 * makes it read as the top of something much bigger underneath, rather than a
 * boulder somebody left on the grass.
 */
public class OutcropFeature extends Feature<BlockStateConfiguration> {
    private static final int RADIUS = 2;
    private static final int SIZE = RADIUS * 2 + 1;
    private static final int ATTEMPTS = 8;
    private static final int SPREAD = 6;

    private static final float LOW_CENTRE_CHANCE = 0.25F;
    private static final float HIGH_CENTRE_CHANCE = 0.15F;
    private static final float INNER_ORTHOGONAL_CHANCE = 0.8F;
    private static final float INNER_DIAGONAL_CHANCE = 0.5F;
    private static final float OUTER_EXPOSURE_CHANCE = 0.3F;

    public OutcropFeature(Codec<BlockStateConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockState stone = context.config().state;
        BlockPos site = SurfaceSite.find(level, context.origin(), random, RADIUS, 1, ATTEMPTS, SPREAD);
        if (site == null) {
            return false;
        }

        // raised[x][z]: blocks above the centre's ground level. exposed[x][z]: stone
        // replaces the ground block here. Every raised cell is also exposed.
        int[][] raised = new int[SIZE][SIZE];
        boolean[][] exposed = new boolean[SIZE][SIZE];
        float roll = random.nextFloat();
        int centre = roll < LOW_CENTRE_CHANCE ? 1 : roll < LOW_CENTRE_CHANCE + HIGH_CENTRE_CHANCE ? 3 : 2;
        raised[RADIUS][RADIUS] = centre;
        exposed[RADIUS][RADIUS] = true;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                boolean orthogonal = dx == 0 || dz == 0;
                if (random.nextFloat() < (orthogonal ? INNER_ORTHOGONAL_CHANCE : INNER_DIAGONAL_CHANCE)) {
                    exposed[dx + RADIUS][dz + RADIUS] = true;
                    // A shoulder is a step below the centre, or flush with the ground.
                    raised[dx + RADIUS][dz + RADIUS] = Math.max(0, centre - 1 - random.nextInt(2));
                }
            }
        }
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != RADIUS) {
                    continue;
                }
                int ix = Math.max(-1, Math.min(1, dx)) + RADIUS;
                int iz = Math.max(-1, Math.min(1, dz)) + RADIUS;
                if (exposed[ix][iz] && random.nextFloat() < OUTER_EXPOSURE_CHANCE) {
                    exposed[dx + RADIUS][dz + RADIUS] = true;
                }
            }
        }

        int baseY = site.getY();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (!exposed[dx + RADIUS][dz + RADIUS]) {
                    continue;
                }
                int x = site.getX() + dx;
                int z = site.getZ() + dz;
                int ground = SurfaceSite.groundY(level, x, z);
                if (ground == SurfaceSite.NO_GROUND) {
                    continue;
                }
                setBlock(level, new BlockPos(x, ground, z), stone);
                // Roots under the heart of the outcrop, so breaking the top layer
                // turns up more of the same stone instead of plain dirt.
                boolean heart = Math.abs(dx) + Math.abs(dz) <= 1;
                BlockPos below = new BlockPos(x, ground - 1, z);
                if (heart && SurfaceSite.isNaturalGround(level.getBlockState(below))) {
                    setBlock(level, below, stone);
                }
                int top = baseY + raised[dx + RADIUS][dz + RADIUS];
                for (int y = ground + 1; y <= top; y++) {
                    setBlock(level, new BlockPos(x, y, z), stone);
                }
            }
        }
        return true;
    }
}
