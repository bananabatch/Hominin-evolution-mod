package dev.hominin.evolution.world.feature;

import com.mojang.serialization.Codec;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.block.CarcassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * An old bone bed: something died out here a long time ago and was picked over by
 * everything that found it. Rare, and worth the walk - there is a season's worth of
 * bone in one.
 */
public class CarcassFeature extends Feature<NoneFeatureConfiguration> {
    public CarcassFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int y = SurfaceSite.groundY(level, origin.getX(), origin.getZ());
        if (y == SurfaceSite.NO_GROUND) {
            return false;
        }
        BlockPos pos = new BlockPos(origin.getX(), y + 1, origin.getZ());
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockState state = ModBlocks.CARCASS.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(CarcassBlock.LARGE, true);
        if (!state.canSurvive(level, pos)) {
            return false;
        }
        level.setBlock(pos, state, 2);
        return true;
    }
}
