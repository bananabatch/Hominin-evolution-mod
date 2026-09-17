package dev.hominin.evolution.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A scatter of pebbles lying on the ground. Ground clutter in the vanilla
 * short-grass mould: no collision, breaks instantly by hand, and pops off if
 * the block under it goes. This is the mod's stone source - there is no
 * mining - so it deliberately sits outside every tool gate.
 *
 * <p>Holds between one and four rocks, and gives up exactly that many when
 * broken. Dropping more rocks onto an existing scatter adds to the pile rather
 * than placing a second block, the same way sea pickles and candles stack in
 * place - so a patch of ground accumulates instead of tiling.
 *
 * <p>Extends {@link BushBlock} for the support check and shape-update plumbing,
 * but unlike a plant it will lie on any block with a sturdy top face (sand,
 * gravel, bare stone), not just soil.
 */
public class LooseRockBlock extends BushBlock {
    public static final MapCodec<LooseRockBlock> CODEC = simpleCodec(LooseRockBlock::new);

    public static final int MAX_ROCKS = 4;
    public static final IntegerProperty ROCKS = IntegerProperty.create("rocks", 1, MAX_ROCKS);

    /** The scatter spreads as it grows, so the outline follows the count. */
    private static final VoxelShape[] SHAPE_BY_COUNT = {
            Block.box(6.0, 0.0, 6.0, 10.0, 2.0, 10.0),
            Block.box(4.0, 0.0, 4.0, 12.0, 2.0, 12.0),
            Block.box(2.0, 0.0, 2.0, 14.0, 2.5, 14.0),
            Block.box(1.0, 0.0, 1.0, 15.0, 3.0, 15.0)};

    public LooseRockBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROCKS, 1));
    }

    @Override
    protected MapCodec<LooseRockBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROCKS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) {
            return existing.setValue(ROCKS, Math.min(MAX_ROCKS, existing.getValue(ROCKS) + 1));
        }
        return super.getStateForPlacement(context);
    }

    /**
     * Lets another rock of the same kind be placed "into" this one. Sneaking opts
     * out, so a player who actually wants a separate block alongside still can.
     */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        if (!useContext.isSecondaryUseActive()
                && useContext.getItemInHand().is(this.asItem())
                && state.getValue(ROCKS) < MAX_ROCKS) {
            return true;
        }
        return super.canBeReplaced(state, useContext);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_COUNT[state.getValue(ROCKS) - 1];
    }
}
