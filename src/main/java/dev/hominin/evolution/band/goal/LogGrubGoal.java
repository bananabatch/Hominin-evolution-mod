package dev.hominin.evolution.band.goal;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.survival.DeadWood;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Grubs out of a dead tree. A member wanting food, with a decaying log about that has not given everything it had,
 * goes and pulls it open - the same logs, the same odds, as a player's hands.
 */
public class LogGrubGoal extends WalkAndDoGoal {
    private static final int SEARCH = 16;

    public LogGrubGoal(BandMember member) {
        super(member, 200, 2.5D, 400, 15);
    }

    @Override
    protected boolean wants(ServerLevel level) {
        return member.isHungry() || member.getHunger() < BandMember.MAX_HUNGER - 4 && member.getRandom().nextInt(3) == 0;
    }

    @Override
    @Nullable
    protected BlockPos find(ServerLevel level) {
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH, -2, -SEARCH), origin.offset(SEARCH, 3, SEARCH))) {
            if (!level.getBlockState(pos).is(ModBlocks.DECAYING_LOG.get()) || !DeadWood.worthCracking(level, pos)) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    @Override
    protected BlockPos standAt(BlockPos at) {
        return at;
    }

    @Override
    protected boolean act(ServerLevel level, BlockPos at) {
        int grubs = DeadWood.crackFor(level, at);
        if (grubs > 0) {
            member.addToInventory(new ItemStack(ModItems.GRUB.get(), grubs));
            Lines.say(member, "log_grubs");
        }
        return true;
    }
}
