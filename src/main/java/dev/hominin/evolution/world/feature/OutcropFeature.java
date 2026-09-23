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
 * Bedrock breaking the surface: a broad knuckle of stone, sunk into the ground and standing
 * proud of it.
 *
 * <p>The stone block comes from the configured feature, so one feature serves every deposit type.
 * Outcrops come in three sizes - a low knuckle you could walk past, a proper outcrop, and now and
 * then a big one with a second crest you can see from a long way off - so some are worth looking
 * for and some are landmarks. All are wider in the ground than above it: a patch of bare stone at
 * soil level with the raised part inside that, which is what makes it read as the top of something
 * much bigger underneath rather than a boulder somebody left on the grass.
 */
public class OutcropFeature extends Feature<BlockStateConfiguration> {
    private static final int ATTEMPTS = 8;
    private static final int SPREAD = 6;

    /** radius, lowest peak, highest peak, chance of a second crest. */
    private record Size(int radius, int minPeak, int maxPeak, float crestChance) {
    }

    private static final Size SMALL = new Size(2, 1, 2, 0.0F);
    private static final Size MEDIUM = new Size(3, 2, 3, 0.35F);
    private static final Size LARGE = new Size(4, 3, 5, 0.8F);

    public OutcropFeature(Codec<BlockStateConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockState stone = context.config().state;
        float roll = random.nextFloat();
        Size size = roll < 0.4F ? SMALL : roll < 0.85F ? MEDIUM : LARGE;
        BlockPos site = SurfaceSite.find(level, context.origin(), random, size.radius(),
                size.radius() >= 3 ? 2 : 1, ATTEMPTS, SPREAD);
        if (site == null && size != SMALL) {
            // Rough country: settle for a smaller one.
            size = SMALL;
            site = SurfaceSite.find(level, context.origin(), random, size.radius(), 1, ATTEMPTS, SPREAD);
        }
        if (site == null) {
            return false;
        }
        int r = size.radius();
        int span = r * 2 + 1;
        int peak = size.minPeak() + random.nextInt(size.maxPeak() - size.minPeak() + 1);
        // A second, lower crest off to one side makes the big ones read as a ridge, not a pile.
        int crestX = 0;
        int crestZ = 0;
        int crestPeak = 0;
        if (random.nextFloat() < size.crestChance()) {
            float angle = random.nextFloat() * (float) (Math.PI * 2.0D);
            crestX = Math.round((float) Math.cos(angle) * (r - 1));
            crestZ = Math.round((float) Math.sin(angle) * (r - 1));
            crestPeak = Math.max(1, peak - 1 - random.nextInt(2));
        }

        int[][] raised = new int[span][span];
        boolean[][] exposed = new boolean[span][span];
        exposed[r][r] = true;
        raised[r][r] = peak;
        // Out from the heart ring by ring, so every exposed cell touches one nearer the middle.
        for (int ring = 1; ring <= r; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    // The heart is solid rock; only the fringe breaks up into scattered stone.
                    boolean heart = distance <= Math.max(1.5D, r * 0.6D);
                    if (!heart) {
                        int ix = dx - Integer.signum(dx) + r;
                        int iz = dz - Integer.signum(dz) + r;
                        if (!exposed[ix][iz] && !exposed[dx - Integer.signum(dx) + r][dz + r]
                                && !exposed[dx + r][dz - Integer.signum(dz) + r]) {
                            continue;
                        }
                        double chance = 0.85D - 0.6D * (distance - r * 0.6D) / Math.max(1.0D, r * 0.4D + 0.5D);
                        if (random.nextFloat() >= Math.max(0.15D, chance)) {
                            continue;
                        }
                    }
                    exposed[dx + r][dz + r] = true;
                    double fall = distance / (r + 0.75D);
                    int height = (int) Math.round(peak * (1.0D - fall)) - random.nextInt(2);
                    if (crestPeak > 0) {
                        double toCrest = Math.sqrt((dx - crestX) * (dx - crestX) + (dz - crestZ) * (dz - crestZ));
                        height = Math.max(height, (int) Math.round(crestPeak * (1.0D - toCrest / 2.25D)) - random.nextInt(2));
                    }
                    raised[dx + r][dz + r] = Math.max(0, height);
                }
            }
        }

        int baseY = site.getY();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!exposed[dx + r][dz + r]) {
                    continue;
                }
                int x = site.getX() + dx;
                int z = site.getZ() + dz;
                int ground = SurfaceSite.groundY(level, x, z);
                if (ground == SurfaceSite.NO_GROUND) {
                    continue;
                }
                setBlock(level, new BlockPos(x, ground, z), stone);
                // Roots under the heart of the outcrop, so breaking the top layer turns up more of
                // the same stone instead of plain dirt - deeper under a big one.
                double distance = Math.sqrt(dx * dx + dz * dz);
                int roots = distance <= 1.0D ? Math.max(1, r - 1) : distance <= 2.0D && r >= 3 ? 1 : 0;
                for (int down = 1; down <= roots; down++) {
                    BlockPos below = new BlockPos(x, ground - down, z);
                    if (!SurfaceSite.isNaturalGround(level.getBlockState(below))) {
                        break;
                    }
                    setBlock(level, below, stone);
                }
                int top = baseY + raised[dx + r][dz + r];
                for (int y = ground + 1; y <= top; y++) {
                    setBlock(level, new BlockPos(x, y, z), stone);
                }
            }
        }
        return true;
    }
}
