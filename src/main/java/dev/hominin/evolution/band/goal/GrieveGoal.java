package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Sitting where it happened with its head in its hands. It does not forage, it does not
 * flee, and it will not move until somebody it knows comes back for it.
 */
public class GrieveGoal extends Goal {
    private final BandMember member;

    public GrieveGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return member.isGrieving();
    }

    @Override
    public void start() {
        member.getNavigation().stop();
        member.setTarget(null);
    }

    @Override
    public void tick() {
        member.getNavigation().stop();
        member.setDeltaMovement(0.0D, member.getDeltaMovement().y, 0.0D);
    }
}
