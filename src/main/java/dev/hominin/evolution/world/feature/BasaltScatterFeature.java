package dev.hominin.evolution.world.feature;

import com.mojang.serialization.Codec;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.block.LooseRockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Basalt: lava that cooled. Wherever there is lava - a pool out in the open, a lake down in a cave -
 * loose basalt lies scattered around it, thick near the lava and thinning out to forty blocks away,
 * and a few pieces of obsidian lie right at the lava's edge. So a scatter of dark cobbles is a sign:
 * follow it in, and there is volcanic glass at the end of it.
 *
 * <p>Runs once per chunk and looks only at lava inside that chunk (a feature may read no further than
 * its neighbours), then throws stone into the chunk and the eight round it, which is as far as a
 * feature may write. Anything that would land further out is skipped rather than written.
 */
public class BasaltScatterFeature extends Feature<NoneFeatureConfiguration> {
    /** How far from the lava the scatter reaches, at the most. */
    private static final int REACH = 40;
    private static final int SURFACE_TRIES = 30;
    /** Lava this close under the top of the ground counts as out in the open. */
    private static final int SURFACE_DEPTH = 4;
    private static final int CAVE_TRIES = 10;
    private static final int CAVE_REACH = 14;
    /** Deep down, lava is everywhere: stop counting once there is plenty to pick from. */
    private static final int ENOUGH_CAVE_LAVA = 512;

    public BasaltScatterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        ChunkPos chunk = new ChunkPos(context.origin());
        ChunkAccess access = level.getChunk(chunk.x, chunk.z);

        int[] top = new int[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                top[z * 16 + x] = access.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            }
        }
        // One lava block of each kind, picked fairly from all of them.
        BlockPos surface = null;
        BlockPos cave = null;
        int surfaceSeen = 0;
        int caveSeen = 0;
        LevelChunkSection[] sections = access.getSections();
        for (int i = sections.length - 1; i >= 0 && caveSeen < ENOUGH_CAVE_LAVA; i--) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(BasaltScatterFeature::isLava)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(access.getSectionYFromSectionIndex(i));
            for (int y = 15; y >= 0; y--) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!isLava(section.getBlockState(x, y, z))) {
                            continue;
                        }
                        BlockPos at = new BlockPos(chunk.getMinBlockX() + x, baseY + y, chunk.getMinBlockZ() + z);
                        if (baseY + y >= top[z * 16 + x] - SURFACE_DEPTH) {
                            if (random.nextInt(++surfaceSeen) == 0) {
                                surface = at;
                            }
                        } else if (random.nextInt(++caveSeen) == 0) {
                            cave = at;
                        }
                    }
                }
            }
        }
        if (surface != null) {
            scatterAround(level, random, chunk, surface);
            glassAtTheEdge(level, random, chunk, surface);
            if (random.nextFloat() < 0.2F) {
                columns(level, random, chunk, surface);
            }
        }
        if (cave != null) {
            scatterInCave(level, random, chunk, cave);
        }
        return surface != null || cave != null;
    }

    /** Out in the open: cobbles for forty blocks round, thickest near the lava. */
    private static void scatterAround(WorldGenLevel level, RandomSource random, ChunkPos chunk, BlockPos lava) {
        int wanted = SURFACE_TRIES + random.nextInt(14);
        int placed = 0;
        for (int attempt = 0; attempt < wanted * 2 && placed < wanted; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            // Weighted in towards the lava, but with a long tail out to the full reach.
            double distance = 3.0D + (REACH - 3.0D) * Math.pow(random.nextDouble(), 0.85D);
            int x = lava.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = lava.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!writable(chunk, x, z)) {
                continue;
            }
            BlockPos ground = groundAt(level, x, z);
            float near = 1.0F - (float) (distance / REACH);
            if (ground != null && put(level, ground, ModBlocks.BASALT_ROCK.get().defaultBlockState()
                    .setValue(LooseRockBlock.ROCKS, pile(random, near)))) {
                placed++;
            }
        }
    }

    /** Where lava has run and cooled fast, it went to glass: a few pieces lie right at its edge. */
    private static void glassAtTheEdge(WorldGenLevel level, RandomSource random, ChunkPos chunk, BlockPos lava) {
        // Two to four pieces: a pool spans several chunks, and each leaves its own.
        int wanted = 2 + random.nextInt(3);
        for (int attempt = 0; attempt < 48 && wanted > 0; attempt++) {
            int x = lava.getX() + random.nextInt(11) - 5;
            int z = lava.getZ() + random.nextInt(11) - 5;
            if (!writable(chunk, x, z)) {
                continue;
            }
            BlockPos ground = groundAt(level, x, z);
            if (ground != null && besideLava(level, ground) && put(level, ground,
                    ModBlocks.OBSIDIAN_ROCK.get().defaultBlockState().setValue(LooseRockBlock.ROCKS, 1 + random.nextInt(3)))) {
                wanted--;
            }
        }
    }

    /**
     * A low outcrop of basalt columns a few blocks back from the lava: where the flow cooled slowly enough
     * to crack into pillars. Worked with a hammerstone like any deposit.
     */
    private static void columns(WorldGenLevel level, RandomSource random, ChunkPos chunk, BlockPos lava) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int cx = lava.getX() + (int) Math.round(Math.cos(angle) * (6 + random.nextInt(5)));
        int cz = lava.getZ() + (int) Math.round(Math.sin(angle) * (6 + random.nextInt(5)));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5 || random.nextFloat() < 0.25F) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                if (!writable(chunk, x, z)) {
                    continue;
                }
                BlockPos ground = groundAt(level, x, z);
                if (ground == null) {
                    continue;
                }
                int height = 1 + random.nextInt(dx == 0 && dz == 0 ? 3 : 2);
                for (int y = -1; y < height; y++) {
                    level.setBlock(ground.above(y), ModBlocks.BASALT_DEPOSIT.get().defaultBlockState(), 2);
                }
            }
        }
    }

    /** Down in a cave: a few cobbles on the floors round a lava lake. */
    private static void scatterInCave(WorldGenLevel level, RandomSource random, ChunkPos chunk, BlockPos lava) {
        int placed = 0;
        for (int attempt = 0; attempt < CAVE_TRIES * 3 && placed < CAVE_TRIES; attempt++) {
            int x = lava.getX() + random.nextInt(CAVE_REACH * 2 + 1) - CAVE_REACH;
            int z = lava.getZ() + random.nextInt(CAVE_REACH * 2 + 1) - CAVE_REACH;
            if (!writable(chunk, x, z)) {
                continue;
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, lava.getY() + 4, z);
            for (int step = 0; step < 10 && pos.getY() > level.getMinBuildHeight() + 1; step++) {
                BlockState here = level.getBlockState(pos);
                BlockPos below = pos.below();
                BlockState floor = level.getBlockState(below);
                if (here.isAir() && floor.getFluidState().isEmpty() && floor.isFaceSturdy(level, below, Direction.UP)) {
                    if (put(level, pos.immutable(), ModBlocks.BASALT_ROCK.get().defaultBlockState()
                            .setValue(LooseRockBlock.ROCKS, pile(random, 0.5F)))) {
                        placed++;
                    }
                    break;
                }
                pos.move(Direction.DOWN);
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private static boolean isLava(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA);
    }

    /** Only the chunk being decorated and the eight round it may be written to. */
    private static boolean writable(ChunkPos chunk, int x, int z) {
        return Math.abs((x >> 4) - chunk.x) <= 1 && Math.abs((z >> 4) - chunk.z) <= 1;
    }

    /** More stones to a pile nearer the lava. */
    private static int pile(RandomSource random, float near) {
        int rocks = 1;
        if (random.nextFloat() < 0.3F + near * 0.35F) {
            rocks++;
            if (random.nextFloat() < 0.2F + near * 0.3F) {
                rocks++;
                if (random.nextFloat() < near * 0.4F) {
                    rocks++;
                }
            }
        }
        return rocks;
    }

    /**
     * The open ground at this column: under any canopy, on something solid, and not in water or lava.
     * Null if there is nowhere to lie.
     */
    private static BlockPos groundAt(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
        for (int step = 0; step < 32; step++) {
            BlockState below = level.getBlockState(pos.below());
            if (!below.getFluidState().isEmpty()) {
                return null;
            }
            if (below.isAir() || below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS) || below.canBeReplaced()) {
                pos.move(Direction.DOWN);
                continue;
            }
            break;
        }
        BlockState here = level.getBlockState(pos);
        boolean clear = here.isAir() || (here.canBeReplaced() && here.getFluidState().isEmpty()
                && !here.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF));
        return clear ? pos.immutable() : null;
    }

    private static boolean besideLava(WorldGenLevel level, BlockPos pos) {
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((dx != 0 || dz != 0) && isLava(level.getBlockState(pos.offset(dx, dy, dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean put(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (!state.canSurvive(level, pos)) {
            return false;
        }
        level.setBlock(pos, state, 2);
        return true;
    }
}
