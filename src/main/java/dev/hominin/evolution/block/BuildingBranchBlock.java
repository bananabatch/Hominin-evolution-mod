package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An upright post: a workable branch wedged in a ring of rocks. Stacked, the posts above the
 * first stand on the one below and need no rocks of their own - so a frame reads as poles
 * set in the ground, not a pile of cairns.
 */
public class BuildingBranchBlock extends Block {
    public static final MapCodec<BuildingBranchBlock> CODEC = simpleCodec(BuildingBranchBlock::new);
    /** Whether this post stands on the ground, in its ring of rocks. */
    public static final BooleanProperty BASE = BooleanProperty.create("base");
    private static final VoxelShape POST = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);
    private static final VoxelShape POST_AND_ROCKS = net.minecraft.world.phys.shapes.Shapes.or(POST,
            Block.box(3.0, 0.0, 3.0, 13.0, 3.0, 13.0));

    public BuildingBranchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BASE, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BASE);
    }

    private BlockState standingOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(BASE, !level.getBlockState(pos.below()).is(this));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return standingOn(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.DOWN ? standingOn(state, level, pos) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(BASE) ? POST_AND_ROCKS : POST;
    }
}
