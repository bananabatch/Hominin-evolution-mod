package dev.hominin.evolution.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The branch laid across two tool rack posts. Spears, clubs and branches lean against it, three to a block, on
 * either side - butts on the ground, tops on the bar. It rests on the posts at either end: take one away and it
 * comes down, and everything on it.
 */
public class ToolRackBarBlock extends BaseEntityBlock {
    public static final MapCodec<ToolRackBarBlock> CODEC = simpleCodec(ToolRackBarBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty NEG = CookingSpitBlock.NEG;
    public static final BooleanProperty POS = CookingSpitBlock.POS;
    private static final VoxelShape BAR_Z = Block.box(7.25D, 6.75D, 0.0D, 8.75D, 8.25D, 16.0D);
    private static final VoxelShape BAR_X = Block.box(0.0D, 6.75D, 7.25D, 16.0D, 8.25D, 8.75D);
    /** The bar and what leans on it, to aim at. */
    private static final VoxelShape AIM_Z = Block.box(2.0D, 0.0D, 0.0D, 14.0D, 10.0D, 16.0D);
    private static final VoxelShape AIM_X = Block.box(0.0D, 0.0D, 2.0D, 16.0D, 10.0D, 14.0D);

    public ToolRackBarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(NEG, false)
                .setValue(POS, false));
    }

    /** One block of bar, laid going {@code dir} from a post: which of its ends sit in a fork. */
    public static BlockState across(Direction.Axis axis, boolean first, boolean last, Direction dir) {
        boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return ModBlocks.TOOL_RACK_BAR.get().defaultBlockState().setValue(AXIS, axis)
                .setValue(NEG, positive ? first : last).setValue(POS, positive ? last : first);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, NEG, POS);
    }

    @Override
    public net.minecraft.world.level.pathfinder.PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos,
            @Nullable net.minecraft.world.entity.Mob mob) {
        return net.minecraft.world.level.pathfinder.PathType.BLOCKED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? AIM_Z : AIM_X;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? BAR_Z : BAR_X;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ToolRackBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.WORKABLE_BRANCH.get());
    }

    // ------------------------------------------------------------ resting on the posts

    private static boolean holds(BlockState neighbour, Direction.Axis axis, boolean post) {
        if (neighbour.is(ModBlocks.TOOL_RACK.get())) {
            return neighbour.getValue(CookingRackBlock.HALF) == DoubleBlockHalf.UPPER;
        }
        return !post && neighbour.is(ModBlocks.TOOL_RACK_BAR.get()) && neighbour.getValue(AXIS) == axis;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        Direction.Axis axis = state.getValue(AXIS);
        if (direction.getAxis() != axis) {
            return state;
        }
        if (!holds(neighbour, axis, false)) {
            return Blocks.AIR.defaultBlockState();
        }
        boolean post = holds(neighbour, axis, true);
        return state.setValue(direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? POS : NEG, post);
    }

    // ------------------------------------------------------------ leaning and taking

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!ToolRackBlockEntity.rackable(stack) || !(level.getBlockEntity(pos) instanceof ToolRackBlockEntity rack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        rack.put((ServerPlayer) player, stack, hit.getLocation());
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ToolRackBlockEntity rack) {
            rack.take((ServerPlayer) player, hit.getLocation());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ToolRackBlockEntity rack) {
            rack.spill();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
