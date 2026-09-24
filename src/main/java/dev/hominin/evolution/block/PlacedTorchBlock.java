package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A torch stood up in the ground. It lights the camp round it, and it burns down - as long again as it had
 * left in the hand, then it is gone - and rain puts it out.
 */
public class PlacedTorchBlock extends TorchBlock {
    public static final MapCodec<PlacedTorchBlock> CODEC = simpleCodec(PlacedTorchBlock::new);

    public PlacedTorchBlock(Properties properties) {
        super(ParticleTypes.FLAME, properties);
    }

    @Override
    public MapCodec<PlacedTorchBlock> codec() {
        return CODEC;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        burnOut(level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isRainingAt(pos.above()) && random.nextInt(3) == 0) {
            burnOut(level, pos);
        }
    }

    /** Burnt down, or rained out: a wisp of smoke and nothing left worth picking up. */
    static void burnOut(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D, 6, 0.1D, 0.1D,
                0.1D, 0.01D);
    }
}
