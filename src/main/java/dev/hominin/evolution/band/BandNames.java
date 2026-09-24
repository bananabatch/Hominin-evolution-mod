package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What a band is called: after its ground, the way people name the people over the hill - the ones by
 * the river, the ones at the black stone, the thorn-tree people. Whatever is most striking about where
 * they live gives the name; a band on plain grass gets a plain name.
 */
public final class BandNames {
    private static final String[] WATER = {"River", "Reed Bed", "Spring", "Two Pools", "Still Water", "Lake Shore",
            "Wide Water", "Mud Bank"};
    private static final String[] LAVA = {"Black Stone", "Burning Ground", "Smoke Hill", "Glass Stone"};
    private static final String[] TERMITES = {"Termite Hill", "Red Mound", "Tall Mounds", "Termite Field"};
    private static final String[] STONE = {"Flint", "Sharp Stone", "Grey Stone", "Rock Face", "Stone Hill", "White Rock"};
    private static final String[] TREES = {"Acacia", "Thorn Tree", "Fig Tree", "Dead Tree", "Shade Tree", "Tall Tree"};
    private static final String[] HIGH = {"High Ground", "Ridge", "Windy Hill", "Far Hill"};
    private static final String[] PLAIN = {"Long Grass", "Dry Grass", "Open Ground", "Dust", "Red Earth", "Sun",
            "Wind", "Short Grass"};
    private static final String[] HOMININ = {"band", "people", "band", "people", "ones"};
    private static final String[] DISTINGUISH = {"Upper", "Lower", "Far", "Near", "Little", "Old", "East", "West"};

    public static String name(ServerLevel level, BlockPos home, ResourceLocation species, UUID id, Set<String> taken) {
        RandomSource random = Bands.randomFor(id);
        List<String[]> kinds = new ArrayList<>();
        boolean loaded = level.hasChunk(home.getX() >> 4, home.getZ() >> 4);
        if (loaded) {
            boolean water = false;
            boolean lava = false;
            boolean termites = false;
            boolean stone = false;
            boolean trees = false;
            for (int i = 0; i < 48; i++) {
                int x = home.getX() + random.nextInt(49) - 24;
                int z = home.getZ() + random.nextInt(49) - 24;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos at = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(at);
                water |= level.getFluidState(at).is(FluidTags.WATER);
                lava |= level.getFluidState(at).is(FluidTags.LAVA) || state.is(dev.hominin.evolution.ModBlocks.BASALT_ROCK.get());
                termites |= state.is(dev.hominin.evolution.ModBlocks.TERMITE_MOUND.get());
                stone |= dev.hominin.evolution.mind.MentalMap.depositName(state) != null
                        || state.getBlock() instanceof dev.hominin.evolution.block.LooseRockBlock;
                trees |= state.is(BlockTags.LEAVES);
            }
            if (lava) {
                kinds.add(LAVA);
            }
            if (termites) {
                kinds.add(TERMITES);
            }
            if (water) {
                kinds.add(WATER);
            }
            if (stone) {
                kinds.add(STONE);
            }
            if (trees) {
                kinds.add(TREES);
            }
            if (home.getY() > 100) {
                kinds.add(HIGH);
            }
        }
        if (kinds.isEmpty()) {
            kinds.add(PLAIN);
        }
        // The most striking thing about the ground, usually; now and then the second.
        String[] words = kinds.get(kinds.size() > 1 && random.nextInt(3) == 0 ? 1 : 0);
        String place = words[random.nextInt(words.length)];
        String kind = Paranthropus.STAGE.equals(species) ? "troop" : HOMININ[random.nextInt(HOMININ.length)];
        String name = "the " + place + " " + kind;
        for (int i = 0; taken.contains(name) && i < DISTINGUISH.length * 2; i++) {
            name = "the " + DISTINGUISH[random.nextInt(DISTINGUISH.length)] + " " + place + " " + kind;
        }
        return name;
    }

    /** A name for the player's own band, suggested when it forms - they can call it whatever they like. */
    public static String suggestion(ServerLevel level, BlockPos camp, UUID seed) {
        return name(level, camp, ResourceLocation.fromNamespaceAndPath("hominin_evolution", "homo_erectus"), seed,
                java.util.Set.of());
    }

    /** "the River band" at the start of a sentence. */
    public static String capital(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private BandNames() {
    }
}
