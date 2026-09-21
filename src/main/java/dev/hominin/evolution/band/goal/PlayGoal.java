package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Play, which is practice.
 *
 * <p>Every young mammal that will one day have to run or fight spends its safe hours
 * doing both for fun: chasing and being chased, grappling and being pinned. It looks
 * like wasted effort and it is the opposite - it is the rehearsal, done when a mistake
 * costs nothing, for the day a mistake costs everything.
 *
 * <p>So a round of tag makes the next flight last longer, and a round of wrestling makes
 * the next fight last longer. Children play on their own whenever it is quiet.
 */
public class PlayGoal extends Goal {
    /** Tag: the chaser swaps this often. */
    private static final int TURN_TICKS = 40;
    private static final double REACH_SQR = 2.2D * 2.2D;
    /** How often a child, left alone somewhere quiet, starts something. */
    private static final int CHILD_PLAY_ODDS = 1500;
    private static final int CHILD_PLAY_TICKS = 200;

    private final BandMember member;
    private int ticks;

    public PlayGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.inDanger() || member.isUpATree() || member.getTarget() != null) {
            return false;
        }
        if (member.isPlaying()) {
            return true;
        }
        // Nobody has to tell a child to play.
        if (member.isBaby() && member.getRandom().nextInt(CHILD_PLAY_ODDS) == 0) {
            BandMember partner = findPlaymate();
            if (partner != null) {
                int kind = member.getRandom().nextBoolean() ? BandMember.PLAY_TAG : BandMember.PLAY_WRESTLE;
                member.startPlay(kind, partner, CHILD_PLAY_TICKS);
                partner.startPlay(kind, member, CHILD_PLAY_TICKS);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return member.isPlaying() && !member.inDanger() && member.getTarget() == null;
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        member.getNavigation().stop();
        // Interrupted by something real: the game is off, and nothing was learned.
        if (member.isPlaying() && member.inDanger()) {
            member.stopPlay(false);
        }
    }

    @Override
    public void tick() {
        ticks++;
        BandMember partner = member.playPartner();
        if (partner == null || !partner.isAlive()) {
            member.stopPlay(false);
            return;
        }
        member.getLookControl().setLookAt(partner, 30.0F, 30.0F);
        if (member.playKind() == BandMember.PLAY_WRESTLE) {
            wrestle(partner);
        } else {
            chase(partner);
        }
        member.tickPlayClock();
    }

    /** Close in, grapple, break, close in again. Nobody gets hurt; that is the point. */
    private void wrestle(BandMember partner) {
        if (member.distanceToSqr(partner) > REACH_SQR) {
            member.getNavigation().moveTo(partner, 1.1D);
            return;
        }
        member.getNavigation().stop();
        if (ticks % 12 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
            if (member.onGround() && member.getRandom().nextInt(3) == 0) {
                member.jumpFromGround();
            }
        }
    }

    /** One chases and one runs, and every couple of seconds they swap. */
    private void chase(BandMember partner) {
        boolean chasing = ((ticks / TURN_TICKS) + (member.getId() < partner.getId() ? 0 : 1)) % 2 == 0;
        if (chasing) {
            member.getNavigation().moveTo(partner, 1.3D);
            return;
        }
        if (member.getNavigation().isDone() || ticks % 20 == 0) {
            Vec3 away = DefaultRandomPos.getPosAway(member, 8, 3, partner.position());
            if (away != null) {
                member.getNavigation().moveTo(away.x, away.y, away.z, 1.3D);
            }
        }
    }

    @Nullable
    private BandMember findPlaymate() {
        BandMember best = null;
        for (BandMember other : Band.near(member, 12.0D)) {
            if (other == member || other.isPlaying() || other.inDanger() || !other.isAlliedTo(member)) {
                continue;
            }
            // Another child first; failing that, an adult who will put up with it.
            if (other.isBaby()) {
                return other;
            }
            if (best == null) {
                best = other;
            }
        }
        return best;
    }
}
