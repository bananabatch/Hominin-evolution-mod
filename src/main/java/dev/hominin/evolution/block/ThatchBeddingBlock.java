package dev.hominin.evolution.block;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A proper bed: a layer of thatch under stretched hide, softer and warmer than a nest, though
 * a nest still keeps ticks off you better. Two of these laid side by side are what it takes
 * to lie down in - one on its own is only a mat.
 *
 * <p>Beds laid against each other become one bed: the hide runs straight across the join with
 * no hem, the pillows meet, and a bed joined at its head end gives up its pillow to the one it
 * joins. A second bed laid beside the first turns to face the same way.
 */
public class ThatchBeddingBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<ThatchBeddingBlock> CODEC = simpleCodec(ThatchBeddingBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 9.0, 16.0);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public ThatchBeddingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(NORTH, false)
                .setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    private static BooleanProperty side(Direction direction) {
        return switch (direction) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    /** Which sides another bed is laid against. */
    private BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            state = state.setValue(side(direction), level.getBlockState(pos.relative(direction)).is(this));
        }
        return state;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        // Laid beside another bed, it lines up with that one, so the two make a single bed.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState beside = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            if (beside.is(this)) {
                facing = beside.getValue(FACING);
                break;
            }
        }
        return joined(defaultBlockState().setValue(FACING, facing), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return direction.getAxis().isHorizontal() ? state.setValue(side(direction), neighbour.is(this)) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Any of the four blocks beside this one, of the same kind, makes a pair worth sleeping in. */
    private boolean hasPartner(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && (state.getValue(NORTH) || state.getValue(EAST) || state.getValue(SOUTH)
                || state.getValue(WEST));
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (!BedBlock.canSetSpawn(level)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!hasPartner(level, pos)) {
            player.displayClientMessage(Component.literal(
                    "Thatch bedding sleeps two side by side. This one is alone."), true);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        player.startSleepInBed(pos).ifLeft(problem -> {
            if (problem.getMessage() != null) {
                player.displayClientMessage(problem.getMessage(), true);
            }
        });
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
        return hasPartner(level, pos);
    }

    @Override
    public void setBedOccupied(BlockState state, Level level, BlockPos pos, LivingEntity sleeper, boolean occupied) {
    }

    @Override
    public Optional<ServerPlayer.RespawnPosAngle> getRespawnPosition(BlockState state, EntityType<?> type,
            LevelReader level, BlockPos pos, float orientation) {
        return BedBlock.findStandUpPosition(type, level, pos, state.getValue(FACING), orientation)
                .map(position -> ServerPlayer.RespawnPosAngle.of(position, pos));
    }
}
