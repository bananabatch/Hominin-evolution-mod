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

    /**
     * Lava that cooled: dense, dark and fine-grained - a tier behind chert under the hammer. It lies
     * scattered for tens of blocks around lava, so a trail of it is a sign there is obsidian ahead.
     */
    public static final DeferredBlock<LooseRockBlock> BASALT_ROCK = BLOCKS.registerBlock("basalt_rock",
            LooseRockBlock::new, looseRock(MapColor.TERRACOTTA_GRAY));

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
     * Ribs and hide, lying where something stopped. Everything that dies leaves one; old
     * ones out in the country are picked cleaner but hold far more bone.
     */
    /** A flat anvil stone on a hide mat: where the Acheulean is made. */
    public static final DeferredBlock<dev.hominin.evolution.block.KnappingStationBlock> KNAPPING_STATION =
            BLOCKS.registerBlock("knapping_station", dev.hominin.evolution.block.KnappingStationBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(1.5F)
                            .noOcclusion());

    /** The primitive work station: branches, thatch, bedding and a proper digging stick are made here. */
    public static final DeferredBlock<dev.hominin.evolution.block.WorkStationBlock> WORK_STATION =
            BLOCKS.registerBlock("work_station", dev.hominin.evolution.block.WorkStationBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(2.0F)
                            .noOcclusion());

    /** An upright post in a ring of rocks: what a thatch shelter is framed with. */
    public static final DeferredBlock<dev.hominin.evolution.block.BuildingBranchBlock> BUILDING_BRANCH =
            BLOCKS.registerBlock("building_branch", dev.hominin.evolution.block.BuildingBranchBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.0F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            .ignitedByLava());

    /** Bound thatch: a wall or a roof, unless the weather gets to it first. */
    public static final DeferredBlock<dev.hominin.evolution.block.ThatchBlock> THATCH_BLOCK =
            BLOCKS.registerBlock("thatch_block", dev.hominin.evolution.block.ThatchBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_YELLOW)
                            .strength(0.5F)
                            .sound(SoundType.GRASS)
                            .randomTicks()
                            .ignitedByLava());

    /** Hide over thatch: a real bed, laid in pairs. */
    public static final DeferredBlock<dev.hominin.evolution.block.ThatchBeddingBlock> THATCH_BEDDING =
            BLOCKS.registerBlock("thatch_bedding", dev.hominin.evolution.block.ThatchBeddingBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_BROWN)
                            .strength(0.4F)
                            .sound(SoundType.WOOL)
                            .noOcclusion()
                            .ignitedByLava());

    /** A hominin, chimpanzee or bonobo, dead. Butchers to hominin meat, ribs, a brain, maybe the skull. */
    public static final DeferredBlock<dev.hominin.evolution.block.HomininCarcassBlock> HOMININ_CARCASS =
            BLOCKS.registerBlock("hominin_carcass", dev.hominin.evolution.block.HomininCarcassBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_BROWN)
                            .strength(0.6F)
                            .noOcclusion()
                            .randomTicks());

    /** A megafauna kill: a ribcage you could stand inside. */
    public static final DeferredBlock<dev.hominin.evolution.block.GiantCarcassBlock> GIANT_CARCASS =
            BLOCKS.registerBlock("giant_carcass", dev.hominin.evolution.block.GiantCarcassBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.TERRACOTTA_WHITE)
                            .strength(1.0F)
                            .noOcclusion()
                            .randomTicks()
                            .sound(SoundType.BONE_BLOCK));

    public static final DeferredBlock<dev.hominin.evolution.block.CarcassBlock> CARCASS = BLOCKS.registerBlock(
            "carcass", dev.hominin.evolution.block.CarcassBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .strength(0.6F)
                    .noOcclusion()
                    .randomTicks()
                    .sound(SoundType.BONE_BLOCK));

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
