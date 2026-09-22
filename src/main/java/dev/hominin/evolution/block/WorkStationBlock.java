package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.craft.WorkStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The primitive work station: a branch driven upright into a base of rocks, hide lashed round
 * it to work on. From erectus on, this is where a workable branch becomes a club or a proper
 * spear, where thatch is bound into blocks and bedding, and where a digging stick is lashed
 * together properly rather than just gnawed to a point.
 */
public class WorkStationBlock extends Block {
    public static final MapCodec<WorkStationBlock> CODEC = simpleCodec(WorkStationBlock::new);
    private static final Component TITLE = Component.literal("Work Station");
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);

    public WorkStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(getMenuProvider(state, level, pos));
        return InteractionResult.CONSUME;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, who) -> new WorkStationMenu(id, inventory,
                ContainerLevelAccess.create(level, pos)), TITLE);
    }
}
