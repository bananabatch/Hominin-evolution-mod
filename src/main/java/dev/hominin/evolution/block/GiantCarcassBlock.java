package dev.hominin.evolution.block;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a megafauna kill leaves: a ribcage you could stand inside, with more meat on it than a
 * band can carry off in one go.
 *
 * <p>It is also the loudest smell on the plain. A giant hyena will come a long way for one, and
 * a clan will strip it over four sittings if nobody is standing over it - each sitting taking
 * a quarter of what is on it.
 */
public class GiantCarcassBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<GiantCarcassBlock> CODEC = simpleCodec(GiantCarcassBlock::new);
    /** How much the clan has already had: 0 untouched, 3 nearly stripped. */
    public static final IntegerProperty BITES = IntegerProperty.create("bites", 0, 3);
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0);

    public GiantCarcassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BITES, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BITES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** What the clan left, thinned or fattened by the season. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        int bites = state.getValue(BITES);
        if (bites > 0) {
            for (ItemStack stack : drops) {
                stack.shrink(stack.getCount() * bites / 4);
            }
            drops.removeIf(ItemStack::isEmpty);
        }
        ServerLevel level = params.getLevel();
        dev.hominin.evolution.survival.Seasons.adjust(level, drops, level.random);
        net.minecraft.world.phys.Vec3 origin = params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN);
        if (origin != null) {
            dev.hominin.evolution.hunt.CarcassAge.spoilIfLeft(level, BlockPos.containing(origin), drops,
                    dev.hominin.evolution.hunt.CarcassAge.GIANT_DAYS);
        }
        return drops;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        dev.hominin.evolution.hunt.Carcasses.clanFeeds(level, pos);
        if (level.getBlockState(pos).is(this)) {
            dev.hominin.evolution.hunt.CarcassAge.age(level, pos, dev.hominin.evolution.hunt.CarcassAge.GIANT_DAYS);
        }
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
