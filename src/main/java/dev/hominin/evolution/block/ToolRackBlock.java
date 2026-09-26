package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One end of a tool rack: a forked post, two blocks tall, like a cooking rack's. Set two of them one or two blocks
 * apart and use a workable branch on one: the branch lies across the forks ({@link ToolRackBarBlock}), and spears,
 * clubs and branches lean against it.
 */
public class ToolRackBlock extends CookingRackBlock {
    public static final MapCodec<ToolRackBlock> TOOL_CODEC = simpleCodec(ToolRackBlock::new);

    public ToolRackBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return TOOL_CODEC;
    }

    @Override
    protected boolean isBar(BlockState state) {
        return state.is(ModBlocks.TOOL_RACK_BAR.get());
    }

    @Override
    protected BlockState bar(Direction.Axis axis, boolean first, boolean last, Direction dir) {
        return ToolRackBarBlock.across(axis, first, last, dir);
    }

    @Override
    protected String laidText() {
        return "Lean spears, clubs and branches against it.";
    }
}
