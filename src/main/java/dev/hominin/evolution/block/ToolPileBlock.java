package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.ToolPiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tools laid on the ground: a band's shared store. Hammerstones, flakes, choppers, hand axes, cleavers and
 * multitools, up to eight to a pile, any mix of them - chert on obsidian on basalt, the way they were put
 * down. Walk over it, pick the top one up, or lay another on it.
 *
 * <p>The tools themselves are drawn where they lie by {@link dev.hominin.evolution.client.ToolPileRenderer};
 * the block has no shape of its own to draw.
 */
public class ToolPileBlock extends BaseEntityBlock {
    public static final MapCodec<ToolPileBlock> CODEC = simpleCodec(ToolPileBlock::new);
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 3.0D, 15.0D);

    public ToolPileBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ToolPileBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Another tool laid on the pile. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        // Stone tools on any pile; anything else only onto a pile in your store.
        boolean store = level instanceof ServerLevel server ? dev.hominin.evolution.build.Sites.isStore(server, pos)
                : dev.hominin.evolution.build.SiteView.inOwnStore(pos);
        if (!ToolPiles.layable(stack) && !(store && ToolPiles.storable(stack))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            ToolPiles.layOn(server, pos, stack);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** The top one, picked up. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            ToolPiles.pickUp(server, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile) {
                if (level instanceof ServerLevel server) {
                    ToolPiles.forgetPile(server, pos, pile.owner());
                }
                for (ItemStack tool : pile.takeAll()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D, tool);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
