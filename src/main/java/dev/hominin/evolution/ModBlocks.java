package dev.hominin.evolution;

import dev.hominin.evolution.block.LooseRockBlock;
import dev.hominin.evolution.block.NestBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's blocks. So far that is only the loose surface rocks: the mod has no
 * mining, so stone enters the game as pebble scatters picked up off the ground.
 *
 * <p>All three rock types currently drop the same {@link ModItems#ROCK}. They
 * differ only in look and in where they generate; tier-specific drops (chert
 * flaking finer than limestone, and so on) are a planned follow-up.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HomininEvolutionMod.MODID);

    /** Dark, glassy, conchoidal - the knapper's stone. Found where water has worked it loose. */
    public static final DeferredBlock<LooseRockBlock> CHERT_ROCK = BLOCKS.registerBlock("chert_rock",
            LooseRockBlock::new, looseRock(MapColor.DEEPSLATE));

    /** Coarse, speckled and heavy. Weathers out of mountain and hill country. */
    public static final DeferredBlock<LooseRockBlock> GRANITE_ROCK = BLOCKS.registerBlock("granite_rock",
            LooseRockBlock::new, looseRock(MapColor.TERRACOTTA_LIGHT_GRAY));

    /** Pale and soft-edged. The common stone, lying about almost everywhere. */
    public static final DeferredBlock<LooseRockBlock> LIMESTONE_ROCK = BLOCKS.registerBlock("limestone_rock",
            LooseRockBlock::new, looseRock(MapColor.TERRACOTTA_WHITE));

    /**
     * Volcanic glass - the sharpest edge a hominin can hold, and the hardest to
     * come by. Only weathers out on high peaks, so reaching it is the cost.
     */
    public static final DeferredBlock<LooseRockBlock> OBSIDIAN_ROCK = BLOCKS.registerBlock("obsidian_rock",
            LooseRockBlock::new, looseRock(MapColor.COLOR_BLACK));

    // Bedrock outcrops. Unlike the loose scatters these are solid stone you work
    // a face off, so each one is a source of its own material rather than a pickup,
    // and which type you found decides what the deposit gives up.

    /** The knapper's bedrock. Scarce on purpose - chert is what you go looking for. */
    public static final DeferredBlock<Block> CHERT_DEPOSIT = BLOCKS.registerSimpleBlock("chert_deposit",
            deposit(MapColor.DEEPSLATE));

    /** Coarse and plentiful. The workhorse stone, and the common outcrop. */
    public static final DeferredBlock<Block> QUARTZITE_DEPOSIT = BLOCKS.registerSimpleBlock("quartzite_deposit",
            deposit(MapColor.TERRACOTTA_WHITE));

    /** Soft and chalky. It will not hold an edge, which is exactly what makes it worth finding. */
    public static final DeferredBlock<Block> LIMESTONE_DEPOSIT = BLOCKS.registerSimpleBlock("limestone_deposit",
            deposit(MapColor.SAND));

    /**
     * Baked earth, raised by insects and hard as fired clay. A termite mound is a
     * larder that never moves and never runs out, which is exactly the kind of food
     * a primate can build a routine around - chimps at Gombe fish them with stripped
     * twigs, and it is one of the clearest tool traditions outside our own line.
     */
    public static final DeferredBlock<Block> TERMITE_MOUND = BLOCKS.registerSimpleBlock("termite_mound",
            deposit(MapColor.COLOR_BROWN));

    /**
     * A standing dead trunk, bleached and dried out but not yet eaten through. Most
     * of a dead tree is this: it is what makes the tree read as dead from across a
     * plain, while the few {@link #DECAYED_LOG}s in it are what make it worth
     * walking to.
     *
     * <p>Neither log is timber. Nothing here is sound enough to carry a blow or hold
     * a point, so both stay out of the tool tags a live log belongs to.
     */
    public static final DeferredBlock<RotatedPillarBlock> DECAYING_LOG = BLOCKS.registerBlock("decaying_log",
            RotatedPillarBlock::new, deadWood(MapColor.WOOD, 1.2F));

    /**
     * The part of a dead trunk the termites have reached: bored through and hollow
     * at the heart. This, not the whole tree, is where you fish.
     */
    public static final DeferredBlock<RotatedPillarBlock> DECAYED_LOG = BLOCKS.registerBlock("decayed_log",
            RotatedPillarBlock::new, deadWood(MapColor.TERRACOTTA_BROWN, 0.8F));

    /** Softer than a live log, and softer again once the core has been eaten out. */
    /** A night's bedding of leaves and twigs, flat on the ground. Pulled apart in a moment. */
    public static final DeferredBlock<NestBlock> NEST = BLOCKS.registerBlock("nest", NestBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(0.3F)
                    .sound(SoundType.GRASS)
                    .noOcclusion()
                    .ignitedByLava()
                    .pushReaction(PushReaction.DESTROY));

    private static BlockBehaviour.Properties deadWood(MapColor mapColor, float strength) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(strength)
                .sound(SoundType.WOOD);
    }

    /**
     * An outcrop: ordinary stone, but soft enough that a hammerstone can work a
     * face off it. Deliberately not {@code requiresCorrectToolForDrops} - the mod
     * has no pickaxe, so that flag would mean an outcrop you can break and never
     * collect. It is slow by hand, which is gate enough.
     */
    private static BlockBehaviour.Properties deposit(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE);
    }

    /**
     * The short-grass property set: walk-through, hand-instabreak, replaceable
     * by world gen, and destroyed rather than shoved by pistons. Stone sounds
     * instead of grass ones.
     */
    private static BlockBehaviour.Properties looseRock(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .replaceable()
                .noCollission()
                .instabreak()
                .sound(SoundType.STONE)
                .pushReaction(PushReaction.DESTROY);
    }

    private ModBlocks() {
    }
}
