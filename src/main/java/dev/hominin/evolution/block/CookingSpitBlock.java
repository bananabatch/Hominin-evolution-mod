package dev.hominin.evolution.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.ModBlockEntities;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The branch laid across two cooking racks. Meat hangs from it on three hooks a block - over a fire, it cooks
 * evenly and slowly, the way meat laid in the flames never does; with no fire, it hangs there out of the dirt,
 * which is as good as storing food gets. It rests on the racks at either end: take one away and it comes down,
 * and everything on it.
 */
public class CookingSpitBlock extends BaseEntityBlock {
    public static final MapCodec<CookingSpitBlock> CODEC = simpleCodec(CookingSpitBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Whether a rack's fork holds this end - toward negative x or z, and toward positive. */
    public static final BooleanProperty NEG = BooleanProperty.create("neg");
    public static final BooleanProperty POS = BooleanProperty.create("pos");
    private static final VoxelShape BAR_Z = Block.box(7.25D, 6.75D, 0.0D, 8.75D, 8.25D, 16.0D);
    private static final VoxelShape BAR_X = Block.box(0.0D, 6.75D, 7.25D, 16.0D, 8.25D, 8.75D);
    /** The bar and what hangs from it: easier to aim at what you want to take down. */
    private static final VoxelShape HANGING_Z = Shapes.or(BAR_Z, Block.box(6.0D, 0.0D, 0.0D, 10.0D, 7.0D, 16.0D));
    private static final VoxelShape HANGING_X = Shapes.or(BAR_X, Block.box(0.0D, 0.0D, 6.0D, 16.0D, 7.0D, 10.0D));

    public CookingSpitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(NEG, false)
                .setValue(POS, false));
    }

    /** One block of spit, laid going {@code dir} from a rack: which of its ends sit in a fork. */
    public static BlockState across(Direction.Axis axis, boolean first, boolean last, Direction dir) {
        boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return ModBlocks.COOKING_SPIT.get().defaultBlockState().setValue(AXIS, axis)
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? HANGING_Z : HANGING_X;
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
        return new CookingSpitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.COOKING_SPIT.get(), CookingSpitBlockEntity::serverTick);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.WORKABLE_BRANCH.get());
    }

    // ------------------------------------------------------------ resting on the racks

    /** A rack's top half, or more spit running the same way. */
    private static boolean holds(BlockState neighbour, Direction.Axis axis, boolean rack) {
        if (neighbour.is(ModBlocks.COOKING_RACK.get())) {
            return neighbour.getValue(CookingRackBlock.HALF) == DoubleBlockHalf.UPPER;
        }
        return !rack && neighbour.is(ModBlocks.COOKING_SPIT.get()) && neighbour.getValue(AXIS) == axis;
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
        boolean rack = holds(neighbour, axis, true);
        return state.setValue(direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? POS : NEG, rack);
    }

    // ------------------------------------------------------------ hanging and taking down

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CookingSpitBlockEntity spit)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return CookingSpitBlockEntity.hangable(stack) ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return spit.hang((ServerPlayer) player, stack, hit.getLocation()) ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            // Something in hand that does not hang: it is not a reach for what is on the spit.
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CookingSpitBlockEntity spit) {
            spit.takeDown((ServerPlayer) player, hit.getLocation());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CookingSpitBlockEntity spit) {
            spit.spill();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
