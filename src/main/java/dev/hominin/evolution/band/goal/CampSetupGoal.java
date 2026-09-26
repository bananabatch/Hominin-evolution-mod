package dev.hominin.evolution.band.goal;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.ErectusWork;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.block.CookingRackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Setting the camp up. What a member has made for the camp - a work station, a knapping station, a fire pit - it
 * carries back and puts down there, a few steps from the middle and out of anyone's way; and with two cooking racks
 * and a branch, it stands them either side of the camp's fire and lays the spit across.
 */
public class CampSetupGoal extends WalkAndDoGoal {
    @Nullable
    private Item placing;

    public CampSetupGoal(BandMember member) {
        super(member, 40, 2.8D, 600, 10);
    }

    /** Whether a spit already hangs over a fire pit at this camp. */
    public static boolean rackOverPit(ServerLevel level, BlockPos camp) {
        for (BlockPos pos : BlockPos.betweenClosed(camp.offset(-12, -3, -12), camp.offset(12, 3, 12))) {
            if (level.getBlockState(pos).is(ModBlocks.FIRE_PIT.get())
                    && level.getBlockState(pos.above()).is(ModBlocks.COOKING_SPIT.get())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean wants(ServerLevel level) {
        return ErectusWork.works(member) && (carries(ModItems.WORK_STATION.get()) || carries(ModItems.KNAPPING_STATION.get())
                || carries(ModItems.FIRE_PIT.get())
                || member.count(ModItems.COOKING_RACK.get()) >= 2 && member.count(ModItems.WORKABLE_BRANCH.get()) >= 1);
    }

    private boolean carries(Item item) {
        return member.count(item) > 0;
    }

    @Override
    @Nullable
    protected BlockPos find(ServerLevel level) {
        ServerPlayer leader = ErectusWork.leader(member);
        if (leader == null) {
            return null;
        }
        BlockPos camp = ErectusWork.camp(leader);
        placing = null;
        if (carries(ModItems.FIRE_PIT.get()) && ErectusWork.nearest(level, camp, ModBlocks.FIRE_PIT.get(), 12) == null) {
            placing = ModItems.FIRE_PIT.get();
            return spot(level, camp, 2, 5);
        }
        if (carries(ModItems.WORK_STATION.get()) && ErectusWork.nearest(level, camp, ModBlocks.WORK_STATION.get(), 24) == null) {
            placing = ModItems.WORK_STATION.get();
            return spot(level, camp, 4, 8);
        }
        if (carries(ModItems.KNAPPING_STATION.get())
                && ErectusWork.nearest(level, camp, ModBlocks.KNAPPING_STATION.get(), 24) == null) {
            placing = ModItems.KNAPPING_STATION.get();
            return spot(level, camp, 4, 8);
        }
        if (member.count(ModItems.COOKING_RACK.get()) >= 2 && !rackOverPit(level, camp)) {
            placing = ModItems.COOKING_RACK.get();
            return ErectusWork.nearest(level, camp, ModBlocks.FIRE_PIT.get(), 12);
        }
        return null;
    }

    /** Level open ground this far from the middle of camp, clear above, and nobody's building. */
    @Nullable
    private BlockPos spot(ServerLevel level, BlockPos camp, int min, int max) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int dx = member.getRandom().nextInt(max * 2 + 1) - max;
            int dz = member.getRandom().nextInt(max * 2 + 1) - max;
            if (dx * dx + dz * dz < min * min) {
                continue;
            }
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos pos = camp.offset(dx, dy, dz);
                if (level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
                        && level.getBlockState(pos.above()).canBeReplaced()
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                        && !dev.hominin.evolution.build.Building.anyBuilt(level, pos)) {
                    return pos.immutable();
                }
            }
        }
        return null;
    }

    @Override
    protected BlockPos standAt(BlockPos at) {
        return at;
    }

    @Override
    protected boolean act(ServerLevel level, BlockPos at) {
        if (placing == null) {
            return true;
        }
        if (placing == ModItems.COOKING_RACK.get()) {
            if (member.count(ModItems.COOKING_RACK.get()) >= 2 && member.count(ModItems.WORKABLE_BRANCH.get()) >= 1
                    && CookingRackBlock.raiseOver(level, at)) {
                member.takeOneOf(ModItems.COOKING_RACK.get());
                member.takeOneOf(ModItems.COOKING_RACK.get());
                member.takeOneOf(ModItems.WORKABLE_BRANCH.get());
                Lines.say(member, "made_thing");
            }
            return true;
        }
        if (!level.getBlockState(at).canBeReplaced()) {
            return true;
        }
        Block block = placing == ModItems.FIRE_PIT.get() ? ModBlocks.FIRE_PIT.get()
                : placing == ModItems.WORK_STATION.get() ? ModBlocks.WORK_STATION.get() : ModBlocks.KNAPPING_STATION.get();
        ItemStack taken = member.takeOneOf(placing);
        if (taken.isEmpty()) {
            return true;
        }
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            state = state.setValue(HorizontalDirectionalBlock.FACING, member.getDirection().getOpposite());
        }
        level.setBlock(at, state, 3);
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.NEUTRAL, 0.9F, 0.9F);
        Lines.say(member, "made_thing");
        return true;
    }
}
