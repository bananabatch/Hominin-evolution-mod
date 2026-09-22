package dev.hominin.evolution.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.knapping.KnappingStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A flat anvil stone on a hide mat: where stone is worked properly. Sit at it and you see
 * what is laid out - the hammerstone, the bone for the fine flaking, the stone waiting - and
 * choose what to make, Oldowan or, from erectus, Acheulean.
 *
 * <p>It shows what is on it: an empty mat, or a hammerstone and a bone laid by the anvil and
 * a pile of cobbles that grows as you stock it.
 */
public class KnappingStationBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<KnappingStationBlock> CODEC = simpleCodec(KnappingStationBlock::new);
    public static final BooleanProperty HAMMER = BooleanProperty.create("hammer");
    public static final BooleanProperty BOPPER = BooleanProperty.create("bopper");
    /** 0 (none) to 3 (a heap). */
    public static final IntegerProperty STONES = IntegerProperty.create("stones", 0, 3);
    private static final Component TITLE = Component.literal("Knapping Station");
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);

    public KnappingStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HAMMER, false).setValue(BOPPER, false).setValue(STONES, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAMMER, BOPPER, STONES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KnappingStationBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        MenuProvider menu = getMenuProvider(state, level, pos);
        if (menu != null) {
            player.openMenu(menu);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof KnappingStationBlockEntity station)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, who) -> new KnappingStationMenu(id, inventory,
                station.items(), pos), TITLE);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KnappingStationBlockEntity station) {
            Containers.dropContents(level, pos, station.items());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
