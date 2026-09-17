package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Grooming;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Sitting with another of the band and picking through their hair. Apes spend hours of
 * every day on this: it is the glue of the group, and a band that grooms is a band that
 * stays together.
 */
public class GroomGoal extends Goal {
    private static final double SEARCH_RADIUS = 10.0D;
    private static final int SESSION_TICKS = 140;
    private static final int GIVE_UP_TICKS = 400;
    private static final int COOLDOWN = 1800;

    private final BandMember member;
    @Nullable
    private BandMember partner;
    private int ticks;
    private int grooming;
    private int nextTry;

    public GroomGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry || member.isBaby() || member.inDanger() || member.isUpATree()
                || member.isHungry() || member.getRandom().nextInt(60) != 0) {
            return false;
        }
        partner = findPartner();
        if (partner == null) {
            nextTry = member.tickCount + COOLDOWN / 2;
        }
        return partner != null;
    }

    @Nullable
    private BandMember findPartner() {
        BandMember best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BandMember other : Band.near(member, SEARCH_RADIUS)) {
            if (other == member || !other.isAlliedTo(member) || other.inDanger() || other.isUpATree()
                    || Grooming.wasGroomedRecently(other)) {
                continue;
            }
            double distance = other.distanceToSqr(member);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return partner != null && partner.isAlive() && grooming < SESSION_TICKS && ticks < GIVE_UP_TICKS
                && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
        grooming = 0;
        member.getNavigation().moveTo(partner, 1.0D);
    }

    @Override
    public void stop() {
        if (partner != null && grooming >= SESSION_TICKS) {
            Grooming.betweenMembers(member, partner);
            Band.announce(member, " sits with " + partner.getName().getString() + ", picking through their hair.");
        }
        partner = null;
        member.getNavigation().stop();
        nextTry = member.tickCount + COOLDOWN + member.getRandom().nextInt(COOLDOWN);
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(partner, 30.0F, 30.0F);
        if (member.distanceToSqr(partner) > 4.0D) {
            if (ticks % 20 == 0) {
                member.getNavigation().moveTo(partner, 1.0D);
            }
            return;
        }
        member.getNavigation().stop();
        partner.getNavigation().stop();
        partner.beingGroomed(20);
        if (++grooming % 20 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
            member.playSound(SoundEvents.WOOL_HIT, 0.4F, 1.4F);
        }
    }
}
