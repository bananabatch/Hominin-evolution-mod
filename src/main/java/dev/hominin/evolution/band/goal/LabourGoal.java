package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/** A mother near her time: off to somewhere quiet, and staying there until it is over. */
public class LabourGoal extends Goal {
    private final BandMember member;

    public LabourGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        return member.isInLabour() && member.getLabourSpot() != null && member.getTarget() == null;
    }

    @Override
    public void tick() {
        BlockPos spot = member.getLabourSpot();
        // Ticked every tick, sometimes without canContinueToUse being asked first.
        if (spot == null) {
            return;
        }
        if (member.blockPosition().closerThan(spot, 2.0D)) {
            member.getNavigation().stop();
        } else if (member.tickCount % 20 == 0 || member.getNavigation().isDone()) {
            member.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.9D);
        }
    }
}
