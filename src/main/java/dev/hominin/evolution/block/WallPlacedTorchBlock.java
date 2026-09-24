package dev.hominin.evolution.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A torch wedged into a wall or a trunk. It burns down and rains out like one stood in the ground. */
public class WallPlacedTorchBlock extends WallTorchBlock {
    public static final MapCodec<WallPlacedTorchBlock> CODEC = simpleCodec(WallPlacedTorchBlock::new);

    public WallPlacedTorchBlock(Properties properties) {
        super(ParticleTypes.FLAME, properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<WallTorchBlock> codec() {
        return (MapCodec<WallTorchBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        PlacedTorchBlock.burnOut(level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.isRainingAt(pos) && random.nextInt(3) == 0) {
            PlacedTorchBlock.burnOut(level, pos);
        }
    }
}
