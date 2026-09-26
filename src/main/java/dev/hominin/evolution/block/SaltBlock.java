package dev.hominin.evolution.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Salt, crusted in the ground where a lick is: lick it bare-handed, or strike a chunk of it off with a hammerstone to
 * carry away. There are only two chunks worth taking in each block - after that the crust is too thin.
 */
public class SaltBlock extends Block {
    public static final int CHUNKS = 2;
    public static final IntegerProperty STRUCK = IntegerProperty.create("struck", 0, CHUNKS);

    public SaltBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STRUCK, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STRUCK);
    }

    /** Chunks still to be had from this block. */
    public static int left(BlockState state) {
        return state.hasProperty(STRUCK) ? CHUNKS - state.getValue(STRUCK) : 0;
    }
}
