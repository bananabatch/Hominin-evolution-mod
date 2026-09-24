package dev.hominin.evolution.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The fire pit: three logs round a bed of sticks, built at the work station and set down where the band
 * sleeps. It is not fire - it is where fire is kept. Fill it (sticks, branches, thatch), work a fire drill
 * on it, and if there is enough in it and something to take the spark, it catches. Fed, it burns as long
 * as you like; left, it burns down to embers, and warm embers catch far more easily than a cold pit.
 *
 * <p>Everything about it is on the block entity; the state only shows it - lit, something in it, ash.
 */
public class FirePitBlock extends BaseEntityBlock {
    public static final MapCodec<FirePitBlock> CODEC = simpleCodec(FirePitBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Sticks, branches or thatch lying ready in it. */
    public static final BooleanProperty FUELLED = BooleanProperty.create("fuelled");
    /** What is left of the last fire. */
    public static final BooleanProperty ASHES = BooleanProperty.create("ashes");
    /** Thatch lying in the bottom of it, unlit: it shows, and it smokes a little. */
    public static final BooleanProperty THATCH = BooleanProperty.create("thatch");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 5.0D, 16.0D);

    public FirePitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false)
                .setValue(FUELLED, false).setValue(ASHES, false).setValue(THATCH, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, FUELLED, ASHES, THATCH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FirePitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.FIRE_PIT.get(), FirePitBlockEntity::serverTick);
    }

    // ------------------------------------------------------------ using it

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FirePitBlockEntity pit)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return FirePitBlockEntity.handles(stack, state) ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return pit.use((ServerPlayer) player, hand, stack) ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            // Something in hand the pit has no use for: let it be placed, or used, as it would be anywhere.
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FirePitBlockEntity pit) {
            pit.emptyHanded((ServerPlayer) player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FirePitBlockEntity pit) {
            pit.spill();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // ------------------------------------------------------------ burning

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(LIT) && entity instanceof LivingEntity living && !living.fireImmune()) {
            living.hurt(level.damageSources().inFire(), 1.0F);
        }
        super.entityInside(state, level, pos, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            if (state.getValue(THATCH)) {
                // Dry grass in a pit smoulders: a thread of smoke, thicker on the ashes of the last fire.
                int every = state.getValue(ASHES) ? 2 : 4;
                if (random.nextInt(every) == 0) {
                    level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3D + random.nextDouble() * 0.4D,
                            pos.getY() + 0.15D, pos.getZ() + 0.3D + random.nextDouble() * 0.4D, 0.0D, 0.03D, 0.0D);
                }
                if (state.getValue(ASHES) && random.nextInt(12) == 0) {
                    level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5D,
                            pos.getY() + 0.3D, pos.getZ() + 0.5D, 0.0D, 0.04D, 0.0D);
                }
            }
            return;
        }
        if (random.nextInt(10) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        if (random.nextInt(3) == 0) {
            CampfireBlock.makeParticles(level, pos, false, false);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5D, pos.getY() + 0.4D, pos.getZ() + 0.5D,
                    random.nextFloat() / 2.0F, 5.0E-5D, random.nextFloat() / 2.0F);
        }
        level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3D + random.nextDouble() * 0.4D, pos.getY() + 0.3D,
                pos.getZ() + 0.3D + random.nextDouble() * 0.4D, 0.0D, 0.02D, 0.0D);
    }
}
