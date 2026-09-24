package dev.hominin.evolution.block;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One end of a cooking rack: a forked stick driven into the ground, taller than it is wide. Two of them a block or
 * two apart, and a workable branch laid across the forks - used on one of them - and there is a spit to hang meat
 * from, over a fire set between them. The spit is {@link CookingSpitBlock}.
 */
public class CookingRackBlock extends Block {
    public static final MapCodec<CookingRackBlock> CODEC = simpleCodec(CookingRackBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Which way the spit runs from this end: the fork opens across it. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape LOWER = Block.box(6.5D, 0.0D, 6.5D, 9.5D, 16.0D, 9.5D);
    private static final VoxelShape UPPER = Block.box(5.5D, 0.0D, 5.5D, 10.5D, 10.0D, 10.5D);
    /** The widest gap a branch will span: two blocks between the racks. */
    public static final int MOST_BETWEEN = 2;

    public CookingRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    // ------------------------------------------------------------ two blocks tall

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighbour.is(this) && neighbour.getValue(HALF) != half
                    ? state.setValue(FACING, neighbour.getValue(FACING)) : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken from the top in creative: the bottom goes too, without dropping a rack nobody paid for. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ------------------------------------------------------------ laying a branch across

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.WORKABLE_BRANCH.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockPos top = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
        String why = span(level, top, player, stack);
        if (why != null) {
            player.displayClientMessage(Component.literal(why), true);
        }
        return ItemInteractionResult.CONSUME;
    }

    /**
     * Lays branches from this rack to the nearest one in line with it - the way the player is facing first - with
     * one or two blocks clear between. Returns why not, or null when it is done.
     */
    @Nullable
    private String span(Level level, BlockPos top, Player player, ItemStack branches) {
        List<Direction> order = new ArrayList<>();
        order.add(player.getDirection());
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!order.contains(dir)) {
                order.add(dir);
            }
        }
        String blocked = null;
        for (Direction dir : order) {
            for (int between = 1; between <= MOST_BETWEEN; between++) {
                BlockState there = level.getBlockState(top.relative(dir, between + 1));
                if (!there.is(this) || there.getValue(HALF) != DoubleBlockHalf.UPPER) {
                    continue;
                }
                boolean spanned = false;
                boolean clear = true;
                for (int i = 1; i <= between; i++) {
                    BlockState cell = level.getBlockState(top.relative(dir, i));
                    spanned |= cell.is(ModBlocks.COOKING_SPIT.get());
                    clear &= cell.canBeReplaced();
                }
                if (spanned) {
                    blocked = "There is a spit across these two already.";
                    continue;
                }
                if (!clear) {
                    blocked = "Something is in the way between the racks.";
                    continue;
                }
                if (!player.getAbilities().instabuild && branches.getCount() < between) {
                    return "That gap takes " + between + " workable branches.";
                }
                for (int i = 1; i <= between; i++) {
                    BlockPos cell = top.relative(dir, i);
                    level.setBlock(cell, CookingSpitBlock.across(dir.getAxis(), i == 1, i == between, dir), 3);
                }
                face(level, top, dir);
                face(level, top.relative(dir, between + 1), dir.getOpposite());
                if (!player.getAbilities().instabuild) {
                    branches.shrink(between);
                }
                level.playSound(null, top, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
                player.displayClientMessage(Component.literal("You lay the branch" + (between > 1 ? "es" : "")
                        + " across the forks. Hang meat from it - over a fire, it cooks."), true);
                return null;
            }
        }
        return blocked != null ? blocked
                : "Set a second rack in line with this one, one or two blocks away, then lay the branch across.";
    }

    /** Turns both halves of a rack so the fork opens across the spit. */
    private void face(Level level, BlockPos top, Direction dir) {
        BlockState upper = level.getBlockState(top);
        if (upper.is(this)) {
            level.setBlock(top, upper.setValue(FACING, dir), 2);
        }
        BlockState lower = level.getBlockState(top.below());
        if (lower.is(this)) {
            level.setBlock(top.below(), lower.setValue(FACING, dir), 2);
        }
    }
}
