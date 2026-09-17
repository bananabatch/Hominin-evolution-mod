package dev.hominin.evolution.block;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ground nest of bent-over leaves and twigs - what chimpanzees build every night,
 * and what early hominins that had come down out of the trees would have made on the
 * ground instead.
 *
 * <p>It works as a bed: sleep the night away, and it becomes where you wake. It is flat
 * on the ground rather than raised, and a nest someone else left behind is a sign that
 * a band slept here recently.
 */
public class NestBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<NestBlock> CODEC = simpleCodec(NestBlock::new);

    /** How high a sleeper lies in it: on the woven floor, not up on a mattress. */
    public static final double SLEEP_HEIGHT = 3.0D / 16.0D;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);

    public NestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        if (!BedBlock.canSetSpawn(level)) {
            return InteractionResult.PASS;
        }
        int filled = Nests.largestAround(level, pos);
        if (filled < Nests.SIZE) {
            player.displayClientMessage(Component.literal("This is only the start of a nest (" + filled + "/"
                    + Nests.SIZE + "). It needs to be two wide and three long to sleep in."), true);
            return InteractionResult.SUCCESS;
        }
        player.startSleepInBed(pos).ifLeft(problem -> {
            if (problem.getMessage() != null) {
                player.displayClientMessage(problem.getMessage(), true);
            }
        });
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isBed(BlockState state, BlockGetter level, BlockPos pos, LivingEntity sleeper) {
        return true;
    }

    /** A nest has no occupied flag; anyone can lie down in it. */
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
