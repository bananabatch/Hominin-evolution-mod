package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Keeps up with the band's leader. A member that falls very far behind - left on the
 * other side of a ravine, or outpaced on a long walk - catches up out of sight, the
 * way a pet does, rather than being lost for good.
 */
public class FollowLeaderGoal extends Goal {
    private static final double CATCH_UP_DISTANCE = 40.0D;

    private final BandMember member;
    private final double speed;
    private final float startDistance;
    private final float stopDistance;
    private LivingEntity leader;
    private int repathTicks;

    public FollowLeaderGoal(BandMember member, double speed, float startDistance, float stopDistance) {
        this.member = member;
        this.speed = speed;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.getLeavePos() != null) {
            // Leaving for the night: head off, and the rest of the band follows the alpha.
            if (member.getNavigation().isDone()) {
                BlockPos away = member.getLeavePos();
                member.getNavigation().moveTo(away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, 1.0D);
            }
            return false;
        }
        LivingEntity target = member.followTarget();
        if (target == null || target.isSpectator() || member.isUpATree() || member.isOnExcursion()) {
            return false;
        }
        // A pair-bonded mate keeps close - within a few steps, not the band's usual loose ten.
        float start = target instanceof net.minecraft.world.entity.player.Player partner
                && member.isMateOf(partner.getUUID())
                && dev.hominin.evolution.band.Mating.pairBonds(member.getStage()) ? 4.0F : startDistance;
        if (member.distanceToSqr(target) < start * start) {
            return false;
        }
        leader = target;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        float stop = leader == null ? stopDistance : member.followStopDistance(leader, stopDistance);
        return leader != null && leader.isAlive() && !member.getNavigation().isDone()
                && member.distanceToSqr(leader) > stop * stop;
    }

    @Override
    public void start() {
        repathTicks = 0;
    }

    @Override
    public void stop() {
        leader = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        member.getLookControl().setLookAt(leader, 10.0F, member.getMaxHeadXRot());
        if (--repathTicks > 0) {
            return;
        }
        repathTicks = 10;
        // Only a player's band catches up out of sight; a wild band just walks.
        if (leader instanceof Player && member.distanceToSqr(leader) > CATCH_UP_DISTANCE * CATCH_UP_DISTANCE) {
            catchUp();
        } else {
            member.getNavigation().moveTo(leader, speed);
        }
    }

    private void catchUp() {
        BlockPos around = leader.blockPosition();
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos pos = around.offset(member.getRandom().nextInt(7) - 3, member.getRandom().nextInt(3) - 1,
                    member.getRandom().nextInt(7) - 3);
            if (Math.abs(pos.getX() - around.getX()) < 2 && Math.abs(pos.getZ() - around.getZ()) < 2) {
                continue;
            }
            if (WalkNodeEvaluator.getPathTypeStatic(member, pos) == PathType.WALKABLE
                    && member.level().noCollision(member, member.getBoundingBox().move(pos.getBottomCenter()
                            .subtract(member.position())))) {
                member.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, member.getYRot(), member.getXRot());
                member.getNavigation().stop();
                return;
            }
        }
    }
}
