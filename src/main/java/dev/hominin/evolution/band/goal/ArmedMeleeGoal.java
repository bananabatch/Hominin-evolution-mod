package dev.hominin.evolution.band.goal;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Fights back with something in hand. Bare-handed, a hominin runs - unless the fight is
 * in defence of its leader, when it goes in with its fists.
 */
public class ArmedMeleeGoal extends MeleeAttackGoal {
    private final BandMember member;

    public ArmedMeleeGoal(BandMember member, double speed) {
        super(member, speed, true);
        this.member = member;
    }

    private boolean mayFight() {
        return !member.isBaby() && (member.hasWeapon() || member.isDefending() || member.isHunting());
    }

    @Override
    public boolean canUse() {
        return mayFight() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return mayFight() && super.canContinueToUse();
    }
}
