package dev.hominin.evolution.world.feature;

import com.mojang.serialization.Codec;

import dev.hominin.evolution.ModBlocks;

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

    /** How rare glass is: about one chert outcrop in thirty-three - and no other kind of outcrop at all. */
    private static final float OBSIDIAN_CHANCE = 0.03F;

    /** No outcrop stands within this many blocks of another: scattered over the country, not heaped up together. */
    private static final int APART = 20;
    /** Round a chert outcrop, now and then: loose fine chert weathered out of it, or a block or two in its foot. */
    private static final float FINE_ROCKS_BY_CHERT = 0.3F;
    private static final float FINE_SEAM_IN_CHERT = 0.12F;
    /** Round any other workable outcrop: a loose piece of fine chert, rarely. */
    private static final float FINE_ROCKS_BY_OTHER = 0.05F;

    private static boolean isWorkable(BlockState stone) {
        return stone.is(ModBlocks.CHERT_DEPOSIT.get()) || stone.is(ModBlocks.QUARTZITE_DEPOSIT.get())
                || stone.is(ModBlocks.BASALT_DEPOSIT.get()) || stone.is(ModBlocks.LIMESTONE_DEPOSIT.get());
    }

    /**
     * Three or four blocks of obsidian side by side on the face of the outcrop - the tops of its columns, where the
     * weather has worn it bare: a vein of glass you can see from a way off.
     */
    private static void veinOfGlass(WorldGenLevel level, java.util.List<BlockPos> foot, RandomSource random) {
        BlockState glass = ModBlocks.OBSIDIAN_DEPOSIT.get().defaultBlockState();
        BlockPos start = foot.get(random.nextInt(foot.size()));
        int want = 3 + random.nextInt(2);
        java.util.List<BlockPos> vein = new java.util.ArrayList<>();
        vein.add(start);
        for (BlockPos pos : foot) {
            if (vein.size() >= want) {
                break;
            }
            if (!pos.equals(start) && Math.abs(pos.getX() - start.getX()) <= 1 && Math.abs(pos.getZ() - start.getZ()) <= 1) {
                vein.add(pos);
            }
        }
        for (BlockPos pos : foot) {
            if (vein.size() >= want) {
                break;
            }
            if (!vein.contains(pos) && Math.abs(pos.getX() - start.getX()) <= 2 && Math.abs(pos.getZ() - start.getZ()) <= 2) {
                vein.add(pos);
            }
        }
        for (BlockPos pos : vein) {
            level.setBlock(pos, glass, 2);
        }
    }

    public OutcropFeature(Codec<BlockStateConfiguration> codec) {
        super(codec);
    }

    /**
     * Whether another outcrop already stands near here. Only the chunks this feature may touch are looked at - its
     * own and the ring round it - which is as far as {@link #APART} reaches from anywhere in the middle chunk.
     */
    private static boolean crowded(WorldGenLevel level, BlockPos origin, BlockPos site) {
        int minX = (origin.getX() >> 4 << 4) - 16;
        int minZ = (origin.getZ() >> 4 << 4) - 16;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(minX, site.getX() - APART); x <= Math.min(minX + 47, site.getX() + APART); x += 2) {
            for (int z = Math.max(minZ, site.getZ() - APART); z <= Math.min(minZ + 47, site.getZ() + APART); z += 2) {
                if ((x - site.getX()) * (x - site.getX()) + (z - site.getZ()) * (z - site.getZ()) > APART * APART
                        || !level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
                for (int y = top - 1; y >= top - 4; y--) {
                    if (SurfaceSite.isLandmark(level.getBlockState(at.set(x, y, z)))
                            && !level.getBlockState(at).is(ModBlocks.TERMITE_MOUND.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** A few pieces of loose fine chert on the ground round the outcrop's edge. */
    private static void fineRocks(WorldGenLevel level, BlockPos site, int r, RandomSource random) {
        BlockState rock = ModBlocks.FINE_CHERT_ROCK.get().defaultBlockState();
        int want = 1 + random.nextInt(3);
        for (int attempt = 0; attempt < 16 && want > 0; attempt++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2.0D);
            int reach = r + 1 + random.nextInt(3);
            int x = site.getX() + Math.round((float) Math.cos(angle) * reach);
            int z = site.getZ() + Math.round((float) Math.sin(angle) * reach);
            int ground = SurfaceSite.groundY(level, x, z);
            if (ground == SurfaceSite.NO_GROUND) {
                continue;
            }
            BlockPos at = new BlockPos(x, ground + 1, z);
            if (level.isEmptyBlock(at) && rock.canSurvive(level, at)) {
                level.setBlock(at, rock, 2);
                want--;
            }
        }
    }

    /** One or two blocks of fine chert on the face of a chert outcrop, side by side, where they show. */
    private static void fineSeam(WorldGenLevel level, java.util.List<BlockPos> foot, RandomSource random) {
        BlockState fine = ModBlocks.FINE_CHERT_DEPOSIT.get().defaultBlockState();
        BlockPos start = foot.get(random.nextInt(foot.size()));
        level.setBlock(start, fine, 2);
        if (random.nextBoolean()) {
            for (BlockPos pos : foot) {
                if (!pos.equals(start) && Math.abs(pos.getX() - start.getX()) + Math.abs(pos.getZ() - start.getZ()) == 1) {
                    level.setBlock(pos, fine, 2);
                    break;
                }
            }
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockState stone = context.config().state;
        float roll = random.nextFloat();
        // Fine chert only ever shows as a small seam.
        boolean fineSeam = stone.is(ModBlocks.FINE_CHERT_DEPOSIT.get());
        Size size = fineSeam || roll < 0.4F ? SMALL : roll < 0.85F ? MEDIUM : LARGE;
        BlockPos site = SurfaceSite.find(level, context.origin(), random, size.radius(),
                size.radius() >= 3 ? 2 : 1, ATTEMPTS, SPREAD);
        if (site == null && size != SMALL) {
            // Rough country: settle for a smaller one.
            size = SMALL;
            site = SurfaceSite.find(level, context.origin(), random, size.radius(), 1, ATTEMPTS, SPREAD);
        }
        if (site == null || crowded(level, context.origin(), site)) {
            return false;
        }
        int r = size.radius();
        int span = r * 2 + 1;
        int peak = fineSeam ? 1 : size.minPeak() + random.nextInt(size.maxPeak() - size.minPeak() + 1);
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
        java.util.List<BlockPos> foot = new java.util.ArrayList<>();
        // The top of each column: what shows. Glass and fine chert go where they can be seen, not buried in the foot.
        java.util.List<BlockPos> surface = new java.util.ArrayList<>();
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
                foot.add(new BlockPos(x, ground, z));
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
                surface.add(new BlockPos(x, Math.max(ground, top), z));
            }
        }
        // Glass only ever shows in chert - which keeps it rare.
        if (stone.is(ModBlocks.CHERT_DEPOSIT.get()) && random.nextFloat() < OBSIDIAN_CHANCE && foot.size() >= 4) {
            veinOfGlass(level, surface, random);
        }
        boolean chert = stone.is(ModBlocks.CHERT_DEPOSIT.get());
        if (chert && random.nextFloat() < FINE_SEAM_IN_CHERT && !foot.isEmpty()) {
            fineSeam(level, surface, random);
        }
        if (random.nextFloat() < (chert || fineSeam ? FINE_ROCKS_BY_CHERT : isWorkable(stone) ? FINE_ROCKS_BY_OTHER : 0.0F)) {
            fineRocks(level, site, r, random);
        }
        return true;
    }
}
