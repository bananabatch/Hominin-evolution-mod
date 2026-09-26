package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What is left of an animal: a ribcage with meat still on it, lying where it fell.
 *
 * <p>Everything that dies out here leaves one, and old ones are scattered across the
 * country where something died unseen - those are picked much cleaner, but there is
 * far more of them. This is where scavenging comes from: not killing, but getting
 * there first.
 *
 * <p>Getting there first is the whole problem. A carcass carries on the wind, and a
 * giant hyena will take it if it beats you to it.
 */
public class CarcassBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CarcassBlock> CODEC = simpleCodec(CarcassBlock::new);

    /** An old bone bed out in the country, rather than a fresh kill: more bones, less meat. */
    public static final BooleanProperty LARGE = BooleanProperty.create("large");

    private static final VoxelShape SHAPE_X = Block.box(1.0, 0.0, 3.0, 15.0, 7.0, 13.0);
    private static final VoxelShape SHAPE_Z = Block.box(3.0, 0.0, 1.0, 13.0, 7.0, 15.0);

    /** How close a scavenger has to be before it starts on the carcass. */
    private static final double SCAVENGE_RANGE = 3.0D;
    /** How close a hominin has to be to claim a kill off something else. */
    private static final double CLAIM_RANGE = 8.0D;

    public CarcassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LARGE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LARGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    /** A carcass in the dry has been picked thin; one in the rains is fat. */
    @Override
    protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<net.minecraft.world.item.ItemStack> drops = new java.util.ArrayList<>(super.getDrops(state, params));
        net.minecraft.server.level.ServerLevel level = params.getLevel();
        dev.hominin.evolution.survival.Seasons.adjust(level, drops, level.random);
        net.minecraft.world.phys.Vec3 origin = params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        if (origin != null && rots(state)) {
            dev.hominin.evolution.hunt.CarcassAge.spoilIfLeft(level, BlockPos.containing(origin), drops,
                    dev.hominin.evolution.hunt.CarcassAge.CARCASS_DAYS);
        }
        return drops;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** A clan that reaches it eats it, and there is nothing left for anyone. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Whoever is at it: a clan strips it if nobody is standing over it. The giant hyena
        // does its own eating, and fights for the privilege.
        dev.hominin.evolution.hunt.Carcasses.clanFeeds(level, pos);
        if (rots(state) && level.getBlockState(pos).is(this)) {
            dev.hominin.evolution.hunt.CarcassAge.age(level, pos, dev.hominin.evolution.hunt.CarcassAge.CARCASS_DAYS);
        }
    }

    /** A fresh kill rots. An old bone bed is past rotting, and a hominin's body is for the band to see to. */
    protected boolean rots(BlockState state) {
        return !state.getValue(LARGE) && !(this instanceof HomininCarcassBlock);
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState,
            boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            dev.hominin.evolution.hunt.CarcassAge.forget(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
