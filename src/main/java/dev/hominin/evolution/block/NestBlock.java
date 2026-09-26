package dev.hominin.evolution.block;

import java.util.Optional;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
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
 * <p>It works as a bed: sleep the night away, and it becomes where you wake. A nest someone else left behind is a
 * sign that a band slept here recently.
 *
 * <p>The climbers - Australopithecus, habilis and those before them - can weave one up a tree as well: on the
 * leaves, against a trunk or bough, or out from a piece already lying on them, the way chimpanzees bend branches
 * over into a platform. From erectus on it goes on the ground.
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

    /**
     * A nest is a night's bed. Through the day it comes apart - the branches spring back, the leaves wilt and blow
     * off - and by evening there is nothing to sleep in: make a new one. Nobody reuses last night's.
     */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
            net.minecraft.util.RandomSource random) {
        long time = level.getDayTime() % 24000L;
        if (time < 1000L || time > 11500L || random.nextFloat() >= 0.35F) {
            return;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(0.5D),
                LivingEntity::isSleeping).isEmpty()) {
            return;
        }
        level.levelEvent(2001, pos, Block.getId(state));
        level.removeBlock(pos, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // Up a tree is for the climbers. The check is the server's: that is where the stage is known for certain.
        if (!level.isClientSide() && context.getPlayer() instanceof ServerPlayer player && !player.isCreative()
                && upATree(level, pos)
                && !dev.hominin.evolution.climb.Climbing.climbsTrees(
                        player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            player.displayClientMessage(Component.literal(
                    "Your kind sleeps on the ground now: a nest up a tree is for climbers."), true);
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return restsOn(level, pos.below()) || inTree(level, pos);
    }

    /** Solid ground under it - or a block of leaves, or a bough, which hold a nest as well as ground does. */
    public static boolean restsOn(BlockGetter level, BlockPos below) {
        BlockState state = level.getBlockState(below);
        return state.isFaceSturdy(level, below, Direction.UP) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }

    /**
     * Woven into a tree rather than laid on anything: lashed to a trunk or bough beside it, bent into the leaves
     * beside it, or carried out from a piece of the nest that does rest on them. One piece out, no further.
     */
    public static boolean inTree(BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            BlockState state = level.getBlockState(next);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                    || state.getBlock() instanceof NestBlock && restsOn(level, next.below())) {
                return true;
            }
        }
        return false;
    }

    /** Up a tree, not on the ground: nothing but air, leaves, boughs or nest for the first two blocks down. */
    public static boolean upATree(BlockGetter level, BlockPos pos) {
        for (int down = 1; down <= 2; down++) {
            BlockState state = level.getBlockState(pos.below(down));
            if (!state.isAir() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                    && !(state.getBlock() instanceof NestBlock)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Anything holding it up may go - the ground, the leaves under it, the bough beside it.
        return direction != Direction.UP && !canSurvive(state, level, pos)
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
        // Under a roof, a nest of any size will do.
        if (filled < Nests.SIZE && !dev.hominin.evolution.build.Building.inRoom(level, pos)) {
            player.displayClientMessage(Component.literal("This is only the start of a nest (" + filled + "/"
                    + Nests.SIZE + "). It needs to be two wide and three long to sleep in."), true);
            return InteractionResult.SUCCESS;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            var refusal = NestOwners.refusal(server, player, pos);
            if (refusal.refused()) {
                if (refusal.maker() != null) {
                    player.sendSystemMessage(Component.literal("<" + refusal.maker().getName().getString() + "> ")
                            .withStyle(net.minecraft.ChatFormatting.GOLD).append(Component.literal(refusal.line())
                                    .withStyle(net.minecraft.ChatFormatting.WHITE)));
                    refusal.maker().getLookControl().setLookAt(player);
                } else {
                    player.displayClientMessage(Component.literal(refusal.line()), true);
                }
                return InteractionResult.SUCCESS;
            }
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
