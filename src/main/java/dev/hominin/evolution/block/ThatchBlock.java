package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import dev.hominin.evolution.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A wall or a roof of bound thatch. Left as it is, weather takes it apart - abandon a
 * settlement and, sooner or later, this is what happens to it. Stretch hide over it and
 * it stops caring: cured, it lasts as long as the block does.
 */
public class ThatchBlock extends Block {
    public static final MapCodec<ThatchBlock> CODEC = simpleCodec(ThatchBlock::new);
    public static final BooleanProperty CURED = BooleanProperty.create("cured");
    /** How often an uncured block checks whether the weather has finally had it. */
    private static final float DECAY_CHANCE = 0.03F;

    public ThatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CURED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CURED);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(CURED);
    }

    @Override
    protected void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
            RandomSource random) {
        if (!state.getValue(CURED) && random.nextFloat() < DECAY_CHANCE && !sheltered(level, pos)) {
            level.destroyBlock(pos, false);
        }
    }

    /** How far up a stack of thatch a hide-covered block still shelters what is under it. */
    private static final int SHELTER_REACH = 24;

    /**
     * Hide stretched over thatch anywhere above, in an unbroken stack of thatch, keeps the weather
     * off everything under it: a hide roof protects the whole wall beneath it.
     */
    public static boolean sheltered(net.minecraft.world.level.LevelReader level, BlockPos pos) {
        BlockPos.MutableBlockPos above = pos.mutable();
        for (int i = 0; i < SHELTER_REACH; i++) {
            above.move(net.minecraft.core.Direction.UP);
            BlockState state = level.getBlockState(above);
            if (!(state.getBlock() instanceof ThatchBlock)) {
                return false;
            }
            if (state.getValue(CURED)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(CURED) || !stack.is(ModItems.HIDE.get())) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(CURED, true), 3);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.literal(
                    "Hide stretched tight over the thatch. It will hold now, whether or not you are here."), true);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
