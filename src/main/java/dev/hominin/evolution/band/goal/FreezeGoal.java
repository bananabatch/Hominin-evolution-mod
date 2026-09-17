package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Frozen with fear: neither fighting nor running. The member stands rooted to the spot
 * until it passes - or until the band sees what is happening and comes for it.
 */
public class FreezeGoal extends Goal {
    private final BandMember member;

    public FreezeGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return member.isFrozen();
    }

    @Override
    public void start() {
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        member.getNavigation().stop();
        member.setDeltaMovement(0.0D, member.getDeltaMovement().y, 0.0D);
    }
}
