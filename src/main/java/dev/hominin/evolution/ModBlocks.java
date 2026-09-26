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
 * <p>Each loose rock drops itself: its stone is what it knaps into.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HomininEvolutionMod.MODID);

    /** Dark, glassy, conchoidal - the knapper's stone. Found where water has worked it loose. */
    public static final DeferredBlock<LooseRockBlock> CHERT_ROCK = BLOCKS.registerBlock("chert_rock",
            LooseRockBlock::new, looseRock(MapColor.DEEPSLATE));

    /** Glassy amber chert, sifted out of river gravel - or, now and then, weathered out beside a chert outcrop. */
    public static final DeferredBlock<LooseRockBlock> FINE_CHERT_ROCK = BLOCKS.registerBlock("fine_chert_rock",
            LooseRockBlock::new, looseRock(MapColor.COLOR_ORANGE));

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

    /** Columns of cooled lava by a lava pool: a face of basalt, a tier behind chert. */
    public static final DeferredBlock<Block> BASALT_DEPOSIT = BLOCKS.registerSimpleBlock("basalt_deposit",
            deposit(MapColor.COLOR_BLACK));

    /** The knapper's bedrock. Scarce on purpose - chert is what you go looking for. */
    public static final DeferredBlock<Block> CHERT_DEPOSIT = BLOCKS.registerSimpleBlock("chert_deposit",
            deposit(MapColor.DEEPSLATE));

    /**
     * Fine chert in the living rock: a block or two in the foot of a chert outcrop, now and then, and very rarely a
     * small seam of its own. A face of it gives up the best stone there is short of glass.
     */
    public static final DeferredBlock<Block> FINE_CHERT_DEPOSIT = BLOCKS.registerSimpleBlock("fine_chert_deposit",
            deposit(MapColor.COLOR_ORANGE));

    /**
     * Volcanic glass in the foot of an outcrop: a few blocks of it at most, and in very few outcrops. Where it shows,
     * every band for miles knows the place.
     */
    public static final DeferredBlock<Block> OBSIDIAN_DEPOSIT = BLOCKS.registerSimpleBlock("obsidian_deposit",
            deposit(MapColor.COLOR_BLACK));

    /** Salt, crusted in the ground at a lick: licked, or struck for a chunk to carry (two to a block). */
    public static final DeferredBlock<dev.hominin.evolution.block.SaltBlock> SALT_BLOCK = BLOCKS.registerBlock("salt_block",
            dev.hominin.evolution.block.SaltBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(1.2F, 3.0F).sound(SoundType.CALCITE));

    /** Sand the sea keeps wet: round the tide pools, holding the water in. */
    public static final DeferredBlock<Block> WET_SAND = BLOCKS.registerSimpleBlock("wet_sand",
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.6F).sound(SoundType.SAND));

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

    /** Three logs round a bed of sticks: where fire is kept. Lit, it lights the country round it. */
    public static final DeferredBlock<dev.hominin.evolution.block.FirePitBlock> FIRE_PIT =
            BLOCKS.registerBlock("fire_pit", dev.hominin.evolution.block.FirePitBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PODZOL)
                            .strength(1.5F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            .lightLevel(state -> state.getValue(dev.hominin.evolution.block.FirePitBlock.LIT) ? 15 : 0));

    /** A burning torch stood in the ground. */
    /** Tools laid on the ground: a band's shared store, or an old deposit nobody owns. */
    public static final DeferredBlock<dev.hominin.evolution.block.ToolPileBlock> TOOL_PILE =
            BLOCKS.registerBlock("tool_pile", dev.hominin.evolution.block.ToolPileBlock::new,
                    BlockBehaviour.Properties.of()
                            .noOcclusion()
                            .instabreak()
                            .sound(SoundType.STONE)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    public static final DeferredBlock<dev.hominin.evolution.block.PlacedTorchBlock> PLACED_TORCH =
            BLOCKS.registerBlock("placed_torch", dev.hominin.evolution.block.PlacedTorchBlock::new,
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .instabreak()
                            .lightLevel(state -> 14)
                            .sound(SoundType.WOOD)
                            .randomTicks()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** A burning torch wedged into a wall. */
    public static final DeferredBlock<dev.hominin.evolution.block.WallPlacedTorchBlock> WALL_PLACED_TORCH =
            BLOCKS.registerBlock("wall_placed_torch", dev.hominin.evolution.block.WallPlacedTorchBlock::new,
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .instabreak()
                            .lightLevel(state -> 14)
                            .sound(SoundType.WOOD)
                            .randomTicks()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** An upright post in a ring of rocks: what a thatch shelter is framed with. */
    public static final DeferredBlock<dev.hominin.evolution.block.BuildingBranchBlock> BUILDING_BRANCH =
            BLOCKS.registerBlock("building_branch", dev.hominin.evolution.block.BuildingBranchBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.0F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            .ignitedByLava());

    /** A forked post two blocks tall: one end of a tool rack. */
    public static final DeferredBlock<dev.hominin.evolution.block.ToolRackBlock> TOOL_RACK =
            BLOCKS.registerBlock("tool_rack", dev.hominin.evolution.block.ToolRackBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.0F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** The branch across two tool rack posts, that spears and clubs lean on. */
    public static final DeferredBlock<dev.hominin.evolution.block.ToolRackBarBlock> TOOL_RACK_BAR =
            BLOCKS.registerBlock("tool_rack_bar", dev.hominin.evolution.block.ToolRackBarBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(0.5F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** A forked stick two blocks tall: one end of a cooking rack. */
    public static final DeferredBlock<dev.hominin.evolution.block.CookingRackBlock> COOKING_RACK =
            BLOCKS.registerBlock("cooking_rack", dev.hominin.evolution.block.CookingRackBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(1.0F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /** The branch laid across two racks, that meat hangs from. */
    public static final DeferredBlock<dev.hominin.evolution.block.CookingSpitBlock> COOKING_SPIT =
            BLOCKS.registerBlock("cooking_spit", dev.hominin.evolution.block.CookingSpitBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.WOOD)
                            .strength(0.5F)
                            .sound(SoundType.WOOD)
                            .noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

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
    public static final DeferredBlock<dev.hominin.evolution.block.TermiteMoundBlock> TERMITE_MOUND = BLOCKS.registerBlock(
            "termite_mound", dev.hominin.evolution.block.TermiteMoundBlock::new, deposit(MapColor.COLOR_BROWN));

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
