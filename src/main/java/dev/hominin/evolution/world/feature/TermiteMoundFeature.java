package dev.hominin.evolution.world.feature;

import com.mojang.serialization.Codec;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.block.TermiteMoundBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Savanna termite mounds: stepped spires of packed earth, four to six blocks tall, with the vented
 * blocks that hold the colony worked through them - and, rarely, a super colony: three great mounds
 * eight to ten blocks high standing together, raised by one vast colony (see
 * {@link dev.hominin.evolution.survival.Termites}).
 *
 * <p>Mounds are laid out on a jittered grid rather than rolled chunk by chunk. The country is cut into
 * cells; each cell holds at most one mound, somewhere away from its edges, so two mounds are never
 * closer than the margins allow - a colony keeps its neighbours at a distance, and so do these. Which
 * cells have one, and where in the cell it stands, comes from the world seed, so it is the same
 * whichever chunk is generated first.
 */
public class TermiteMoundFeature extends Feature<NoneFeatureConfiguration> {
    private final int cell;
    private final int margin;
    private final float chance;
    private final boolean homeland;
    private final float colonyChance;
    private final long salt;

    /**
     * @param cell         grid cell size in blocks
     * @param chance       the chance a cell has a mound at all
     * @param homeland     whether this is the homeland's mound field (else the thin scatter elsewhere)
     * @param colonyChance the chance a mound cell is a super colony instead
     */
    public TermiteMoundFeature(Codec<NoneFeatureConfiguration> codec, int cell, float chance, boolean homeland,
            float colonyChance) {
        super(codec);
        this.cell = cell;
        this.margin = cell / 4;
        this.chance = chance;
        this.homeland = homeland;
        this.colonyChance = colonyChance;
        this.salt = homeland ? 0x5EED7E4417EL : 0x7E4417E5EEDL;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        long seed = level.getSeed();
        boolean placed = false;
        for (int cx = Math.floorDiv(chunk.getMinBlockX(), cell); cx <= Math.floorDiv(chunk.getMaxBlockX(), cell); cx++) {
            for (int cz = Math.floorDiv(chunk.getMinBlockZ(), cell); cz <= Math.floorDiv(chunk.getMaxBlockZ(), cell); cz++) {
                RandomSource random = RandomSource.create(seed ^ salt ^ (cx * 341873128712L) ^ (cz * 132897987541L));
                if (random.nextFloat() >= chance) {
                    continue;
                }
                int x = cx * cell + margin + random.nextInt(cell - margin * 2);
                int z = cz * cell + margin + random.nextInt(cell - margin * 2);
                if (x < chunk.getMinBlockX() || x > chunk.getMaxBlockX() || z < chunk.getMinBlockZ() || z > chunk.getMaxBlockZ()) {
                    continue;
                }
                BlockPos origin = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
                if (level.getBiome(origin).is(ModTags.Biomes.HOMININ_HOMELAND) != homeland) {
                    continue;
                }
                boolean colony = random.nextFloat() < colonyChance;
                placed |= colony ? placeColony(level, random, origin) : placeMound(level, random, origin, false);
            }
        }
        return placed;
    }

    // ------------------------------------------------------------ one mound

    /** An ordinary mound (radius 2-3, four to six high), or one of a colony's great ones. */
    private static boolean placeMound(WorldGenLevel level, RandomSource random, BlockPos origin, boolean great) {
        int radius = great ? 4 : random.nextFloat() < 0.4F ? 3 : 2;
        int peak = great ? 8 + random.nextInt(3) : radius == 3 ? 5 + random.nextInt(2) : 4 + random.nextInt(2);
        BlockPos site = SurfaceSite.find(level, origin, random, radius, great ? 2 : 1, great ? 4 : 6, great ? 1 : 4);
        if (site == null) {
            return false;
        }
        build(level, random, site, shape(random, radius, peak, great), radius, peak, great);
        return true;
    }

    /**
     * Three great mounds round a patch of bare, packed ground, a dozen blocks apart: one colony. Every
     * block stays within sixteen blocks of the centre, inside what this chunk's decoration may touch.
     */
    public static boolean placeColony(WorldGenLevel level, RandomSource random, BlockPos centre) {
        float start = random.nextFloat() * Mth.TWO_PI;
        int built = 0;
        for (int i = 0; i < 3; i++) {
            float angle = start + i * Mth.TWO_PI / 3.0F + (random.nextFloat() - 0.5F) * 0.5F;
            // Close enough to read as one colony; near enough the centre that the search round each
            // one (its footprint, and a few blocks past it) stays inside what this chunk may read.
            int distance = 7 + random.nextInt(2);
            BlockPos at = centre.offset(Math.round(Mth.cos(angle) * distance), 0, Math.round(Mth.sin(angle) * distance));
            if (placeMound(level, random, at, true)) {
                built++;
            }
        }
        if (built < 2) {
            return built > 0;
        }
        // The worked ground between them: bare, trampled earth.
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = centre.getX() + random.nextInt(15) - 7;
            int z = centre.getZ() + random.nextInt(15) - 7;
            int ground = SurfaceSite.groundY(level, x, z);
            if (ground != SurfaceSite.NO_GROUND) {
                level.setBlock(new BlockPos(x, ground, z), (random.nextBoolean() ? Blocks.PACKED_MUD : Blocks.COARSE_DIRT)
                        .defaultBlockState(), 2);
            }
        }
        dev.hominin.evolution.survival.Termites.generated(level.getLevel(), centre);
        return true;
    }

    /**
     * A haven's colony: it is there, whatever the ground. Worldgen may skip a mound it cannot find flat, open ground
     * for - and with fewer than two, there is no colony at all. A haven always feeds its people, so here every mound
     * that will not sit is set down anyway: the ground under it cleared of brush and trees, and built on where it is.
     */
    public static void placeHavenColony(net.minecraft.server.level.ServerLevel level, RandomSource random, BlockPos centre) {
        float start = random.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < 3; i++) {
            float angle = start + i * Mth.TWO_PI / 3.0F + (random.nextFloat() - 0.5F) * 0.5F;
            int distance = 7 + random.nextInt(2);
            BlockPos at = centre.offset(Math.round(Mth.cos(angle) * distance), 0, Math.round(Mth.sin(angle) * distance));
            if (placeMound(level, random, at, true)) {
                continue;
            }
            int radius = 4;
            int peak = 8 + random.nextInt(3);
            int ground = SurfaceSite.groundY(level, at.getX(), at.getZ());
            if (ground == SurfaceSite.NO_GROUND) {
                ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        at.getX(), at.getZ()) - 1;
            }
            // Clear what stands in the way: brush, saplings, trees.
            for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                    for (int dy = 1; dy <= peak + 10; dy++) {
                        BlockPos pos = new BlockPos(at.getX() + dx, ground + dy, at.getZ() + dz);
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                        if (!state.isAir() && state.getFluidState().isEmpty() && (state.canBeReplaced()
                                || state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS))) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
            BlockPos site = new BlockPos(at.getX(), ground, at.getZ());
            build(level, random, site, shape(random, radius, peak, true), radius, peak, true);
        }
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = centre.getX() + random.nextInt(15) - 7;
            int z = centre.getZ() + random.nextInt(15) - 7;
            int ground = SurfaceSite.groundY(level, x, z);
            if (ground != SurfaceSite.NO_GROUND) {
                level.setBlock(new BlockPos(x, ground, z), (random.nextBoolean() ? Blocks.PACKED_MUD : Blocks.COARSE_DIRT)
                        .defaultBlockState(), 2);
            }
        }
        dev.hominin.evolution.survival.Termites.generated(level, centre);
    }

    /**
     * Heights above the centre's ground for each cell of the footprint: a cone that falls away from a
     * spire set a little off centre, with a ragged edge, plus chimneys - smaller spires standing up
     * beside the main one, two to four on a great mound.
     */
    private static int[][] shape(RandomSource random, int radius, int peak, boolean great) {
        int size = radius * 2 + 1;
        int[][] heights = new int[size][size];
        double leanX = (random.nextDouble() - 0.5D) * 0.8D;
        double leanZ = (random.nextDouble() - 0.5D) * 0.8D;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.sqrt((dx - leanX) * (dx - leanX) + (dz - leanZ) * (dz - leanZ)) / (radius + 0.6D);
                if (d >= 1.0D) {
                    continue;
                }
                int h = (int) Math.round(peak * Math.pow(1.0D - d, 1.3D) + (random.nextDouble() - 0.5D) * 1.2D);
                if (d > 0.7D && random.nextFloat() < 0.35F) {
                    h = 0;
                }
                heights[dx + radius][dz + radius] = Mth.clamp(h, d < 0.45D ? 1 : 0, peak);
            }
        }
        heights[radius][radius] = peak;
        int chimneys = great ? 2 + random.nextInt(3) : random.nextInt(2);
        for (int i = 0; i < chimneys; i++) {
            int dx = random.nextInt(3) - 1;
            int dz = random.nextInt(3) - 1;
            if (dx == 0 && dz == 0) {
                continue;
            }
            int cx = dx * (1 + random.nextInt(Math.max(1, radius - 1))) + radius;
            int cz = dz * (1 + random.nextInt(Math.max(1, radius - 1))) + radius;
            heights[cx][cz] = Math.max(heights[cx][cz], peak - 1 - random.nextInt(great ? 3 : 2));
        }
        return heights;
    }

    private static void build(WorldGenLevel level, RandomSource random, BlockPos site, int[][] heights, int radius,
            int peak, boolean great) {
        int baseY = site.getY();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int height = heights[dx + radius][dz + radius];
                int x = site.getX() + dx;
                int z = site.getZ() + dz;
                int ground = SurfaceSite.groundY(level, x, z);
                if (ground == SurfaceSite.NO_GROUND) {
                    continue;
                }
                if (height <= 0) {
                    // A little packed earth spreading out beside the base, so the edge fades into the ground.
                    if (touches(heights, dx + radius, dz + radius) && random.nextFloat() < 0.4F) {
                        level.setBlock(new BlockPos(x, ground, z), Blocks.PACKED_MUD.defaultBlockState(), 2);
                    }
                    continue;
                }
                level.setBlock(new BlockPos(x, ground, z), earth(random, great), 2);
                int top = baseY + height;
                for (int y = ground + 1; y <= top; y++) {
                    float up = (float) (y - baseY) / peak;
                    level.setBlock(new BlockPos(x, y, z), random.nextFloat() < 0.1F + up * 0.75F
                            ? vent(great) : earth(random, great), 2);
                }
                // Every column is capped with a vent: the colony breathes out of the tops.
                if (top > ground && (dx == 0 && dz == 0 || height >= peak - 2)) {
                    level.setBlock(new BlockPos(x, top, z), vent(great), 2);
                }
            }
        }
    }

    /** A great mound is built of redder earth, brought up from deep down. */
    private static BlockState earth(RandomSource random, boolean great) {
        return great && random.nextFloat() < 0.55F ? Blocks.TERRACOTTA.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
    }

    private static BlockState vent(boolean great) {
        return ModBlocks.TERMITE_MOUND.get().defaultBlockState().setValue(TermiteMoundBlock.COLONY, great);
    }

    private static boolean touches(int[][] heights, int cx, int cz) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] n : around) {
            int x = cx + n[0];
            int z = cz + n[1];
            if (x >= 0 && x < heights.length && z >= 0 && z < heights.length && heights[x][z] > 0) {
                return true;
            }
        }
        return false;
    }
}
