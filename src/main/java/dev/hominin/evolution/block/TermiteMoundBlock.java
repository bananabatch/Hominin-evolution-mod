package dev.hominin.evolution.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The vented earth of a termite mound. Most are ordinary mounds; the vents of a super colony - three
 * great mounds raised by one vast colony - are marked, and are redder with the deep laterite they bring
 * up. See {@link dev.hominin.evolution.survival.Termites}.
 */
public class TermiteMoundBlock extends Block {
    public static final BooleanProperty COLONY = BooleanProperty.create("colony");

    public TermiteMoundBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLONY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, net.minecraft.world.level.block.state.BlockState> builder) {
        builder.add(COLONY);
    }
}
