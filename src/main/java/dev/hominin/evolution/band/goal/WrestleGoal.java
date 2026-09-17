package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Wrestling back. Once a bout starts the member closes in and grapples - shoves, a
 * swing, a tumble - and never does any harm. It is play, and play is practice.
 */
public class WrestleGoal extends Goal {
    private static final double REACH_SQR = 2.4D * 2.4D;

    private final BandMember member;
    private Player partner;
    private int cooldown;

    public WrestleGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        partner = member.wrestlePartner();
        return partner != null && !member.isBaby() && !member.shouldFlee() && member.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        cooldown = 10;
    }

    @Override
    public void stop() {
        partner = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        member.getLookControl().setLookAt(partner, 30.0F, 30.0F);
        if (member.distanceToSqr(partner) > REACH_SQR) {
            member.getNavigation().moveTo(partner, 1.2D);
            return;
        }
        member.getNavigation().stop();
        if (--cooldown > 0) {
            return;
        }
        cooldown = 14 + member.getRandom().nextInt(14);
        member.swing(InteractionHand.MAIN_HAND);
        member.playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.7F, 1.3F);
        double dx = partner.getX() - member.getX();
        double dz = partner.getZ() - member.getZ();
        partner.knockback(0.35D, -dx, -dz);
        partner.hurtMarked = true;
        if (member.getRandom().nextInt(3) == 0 && member.onGround()) {
            member.jumpFromGround();
        }
    }
}
