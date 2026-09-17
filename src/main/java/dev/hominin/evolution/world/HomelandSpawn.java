package dev.hominin.evolution.world;

import com.mojang.datafixers.util.Pair;

import dev.hominin.evolution.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Puts the world spawn in open savanna, inland, away from the coast.
 *
 * <p>Every stage of this mod assumes that country. Australopithecus is a woodland
 * and grassland animal - trees close enough to climb when something comes, open
 * ground between them worth crossing - and the whole progression is built around
 * having both. Dropping the player in taiga or on an island is not a harder start,
 * it is a start where half the mechanics have nothing to act on.
 *
 * <p>The search reads the biome noise directly rather than generating chunks, so
 * it costs nothing at world creation: sampling climate at a point is cheap, and
 * nothing here forces terrain to exist.
 */
public final class HomelandSpawn {
    /** How far out to look, in blocks. Beyond this, some seeds simply have nothing. */
    private static final int SEARCH_RADIUS = 6000;

    /** Distance between candidates. Savanna regions are far larger than this. */
    private static final int SEARCH_STEP = 192;

    /** Ring radius used to judge how much savanna surrounds a candidate. */
    private static final int OPENNESS_RADIUS = 256;

    /** Ring radius used to judge how far the sea is. */
    private static final int COAST_RADIUS = 512;

    /** Of the 8 ring samples, how many must also be homeland for "large" to hold. */
    private static final int MIN_OPEN_NEIGHBOURS = 5;

    /** Sixteen compass points, as unit offsets scaled by whatever radius is in play. */
    private static final double[][] RING = buildRing(8);

    private static double[][] buildRing(int points) {
        double[][] ring = new double[points][2];
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            ring[i][0] = Math.cos(angle);
            ring[i][1] = Math.sin(angle);
        }
        return ring;
    }

    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        ServerChunkCache chunkSource = level.getChunkSource();
        RandomState randomState = chunkSource.randomState();
        Climate.Sampler sampler = randomState.sampler();
        BiomeSource biomeSource = chunkSource.getGenerator().getBiomeSource();
        int seaLevel = level.getSeaLevel();

        BlockPos best = findHomeland(biomeSource, sampler, seaLevel);
        if (best == null) {
            // No savanna within reach on this seed. Vanilla's own choice is better
            // than forcing the player somewhere arbitrary, so leave it alone.
            return;
        }
        int y = chunkSource.getGenerator()
                .getFirstFreeHeight(best.getX(), best.getZ(), Heightmap.Types.WORLD_SURFACE, level, randomState);
        event.getSettings().setSpawn(new BlockPos(best.getX(), y, best.getZ()), 0.0F);
        event.setCanceled(true);
    }

    /**
     * Walks outward from the origin and takes the first candidate that is savanna,
     * is surrounded by more savanna, and has no ocean within {@link #COAST_RADIUS}.
     * Because the scan goes out in rings, the first full match is also the nearest
     * one - there is no reason to push a new world further from origin than needed.
     *
     * <p>If nothing clears the full bar, the best partial match is used instead, so
     * a seed with only small or coastal savanna still beats no savanna at all.
     */
    private static BlockPos findHomeland(BiomeSource biomeSource, Climate.Sampler sampler, int seaLevel) {
        int quartY = QuartPos.fromBlock(seaLevel);
        BlockPos fallback = null;
        int fallbackScore = -1;

        for (int radius = 0; radius <= SEARCH_RADIUS; radius += SEARCH_STEP) {
            for (int x = -radius; x <= radius; x += SEARCH_STEP) {
                for (int z = -radius; z <= radius; z += SEARCH_STEP) {
                    // Only the shell of each square, or every ring re-tests its middle.
                    if (radius > 0 && Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    if (!isHomeland(biomeSource, sampler, x, quartY, z)) {
                        continue;
                    }
                    int open = countOpenNeighbours(biomeSource, sampler, x, quartY, z);
                    boolean inland = !seaWithin(biomeSource, sampler, x, quartY, z);
                    if (open >= MIN_OPEN_NEIGHBOURS && inland) {
                        return new BlockPos(x, seaLevel, z);
                    }
                    // Openness matters more than distance from water, but both count.
                    int score = open * 2 + (inland ? 3 : 0);
                    if (score > fallbackScore) {
                        fallbackScore = score;
                        fallback = new BlockPos(x, seaLevel, z);
                    }
                }
            }
        }
        return fallback;
    }

    private static boolean isHomeland(BiomeSource biomeSource, Climate.Sampler sampler, int x, int quartY, int z) {
        Holder<Biome> biome = biomeSource.getNoiseBiome(
                QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z), sampler);
        return biome.is(ModTags.Biomes.HOMININ_HOMELAND);
    }

    private static int countOpenNeighbours(BiomeSource biomeSource, Climate.Sampler sampler,
            int x, int quartY, int z) {
        int open = 0;
        for (double[] direction : RING) {
            int sampleX = x + (int) (direction[0] * OPENNESS_RADIUS);
            int sampleZ = z + (int) (direction[1] * OPENNESS_RADIUS);
            if (isHomeland(biomeSource, sampler, sampleX, quartY, sampleZ)) {
                open++;
            }
        }
        return open;
    }

    private static boolean seaWithin(BiomeSource biomeSource, Climate.Sampler sampler, int x, int quartY, int z) {
        for (double[] direction : RING) {
            int sampleX = x + (int) (direction[0] * COAST_RADIUS);
            int sampleZ = z + (int) (direction[1] * COAST_RADIUS);
            Holder<Biome> biome = biomeSource.getNoiseBiome(
                    QuartPos.fromBlock(sampleX), quartY, QuartPos.fromBlock(sampleZ), sampler);
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) {
                return true;
            }
        }
        return false;
    }

    private HomelandSpawn() {
    }
}
