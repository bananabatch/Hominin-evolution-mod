package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Parties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/** Sent to another band with a party: walk there, and - when it is done - walk back. Nothing else matters meanwhile. */
public class MissionGoal extends Goal {
    private final BandMember member;
    private int ticks;

    public MissionGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !member.level().isClientSide() && Parties.away(member);
    }

    @Override
    public boolean canContinueToUse() {
        return Parties.away(member);
    }

    @Override
    public void start() {
        ticks = 0;
        if (member.isSleeping()) {
            member.stopSleeping();
        }
    }

    @Override
    public void stop() {
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (++ticks % 40 != 1) {
            return;
        }
        BlockPos to = Parties.headingFor(member);
        if (to == null || member.distanceToSqr(to.getX() + 0.5D, to.getY(), to.getZ() + 0.5D) < 36.0D) {
            member.getNavigation().stop();
            return;
        }
        member.getNavigation().moveTo(to.getX() + 0.5D, to.getY(), to.getZ() + 0.5D, 1.1D);
    }
}
