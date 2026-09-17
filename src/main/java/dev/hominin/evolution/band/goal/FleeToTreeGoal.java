package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Running for the nearest tree and climbing it. With no tree in reach, just running.
 *
 * <p>Up the trunk, it clings until the danger has passed. Predators give up on a
 * member out of reach the same way they give up on a player.
 */
public class FleeToTreeGoal extends Goal {
    private static final int TREE_SEARCH_RADIUS = 16;
    private static final double RUN_SPEED = 1.4D;
    /** Longest a member will cling to a trunk before climbing down to see. */
    private static final int MAX_CLING_TICKS = 600;

    private final BandMember member;
    private BlockPos trunk;
    private int clingTicks;
    private int ticks;

    public FleeToTreeGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return member.shouldFlee();
    }

    @Override
    public boolean canContinueToUse() {
        if (trunk != null && clingTicks > 0) {
            // Up the tree: stay while the attack is recent and the clinging not too long.
            return member.shouldFlee() && clingTicks < MAX_CLING_TICKS;
        }
        return member.shouldFlee();
    }

    @Override
    public void start() {
        ticks = 0;
        clingTicks = 0;
        trunk = findTrunk();
        if (trunk != null) {
            member.getNavigation().moveTo(trunk.getX() + 0.5D, trunk.getY(), trunk.getZ() + 0.5D, RUN_SPEED);
        } else {
            runAway();
        }
    }

    @Override
    public void stop() {
        member.setClimbingTree(false);
        trunk = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (trunk == null) {
            if (member.getNavigation().isDone()) {
                runAway();
            }
            return;
        }
        double dx = member.getX() - (trunk.getX() + 0.5D);
        double dz = member.getZ() - (trunk.getZ() + 0.5D);
        boolean atTrunk = dx * dx + dz * dz < 2.25D;
        if (!atTrunk) {
            if (ticks % 20 == 0 && member.getNavigation().isDone()) {
                member.getNavigation().moveTo(trunk.getX() + 0.5D, trunk.getY(), trunk.getZ() + 0.5D, RUN_SPEED);
            }
            if (ticks > 200) {
                // Could not get there. Run instead.
                trunk = null;
                runAway();
            }
            return;
        }
        // Push into the trunk; while climbing, that collision is what lifts it.
        member.getNavigation().stop();
        member.setClimbingTree(true);
        clingTicks++;
        // Out of reach now: whatever was chasing loses interest, as it would with a player.
        if (member.getLastHurtByMob() instanceof Mob chaser && chaser.getTarget() == member
                && member.getY() - chaser.getY() >= 2.5D) {
            chaser.setTarget(null);
            chaser.getNavigation().stop();
        }
        member.getMoveControl().setWantedPosition(trunk.getX() + 0.5D, member.getY() + 2.0D,
                trunk.getZ() + 0.5D, 1.0D);
    }

    private void runAway() {
        LivingEntity threat = member.getLastHurtByMob();
        Vec3 from = threat != null ? threat.position() : member.position();
        Vec3 away = DefaultRandomPos.getPosAway(member, 16, 7, from);
        if (away != null) {
            member.getNavigation().moveTo(away.x, away.y, away.z, RUN_SPEED);
        }
    }

    /** The nearest log with its base near our level - a trunk, not a branch overhead. */
    private BlockPos findTrunk() {
        Level level = member.level();
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-TREE_SEARCH_RADIUS, -2, -TREE_SEARCH_RADIUS),
                origin.offset(TREE_SEARCH_RADIUS, 2, TREE_SEARCH_RADIUS))) {
            if (level.getBlockState(pos).is(BlockTags.LOGS) && !level.getBlockState(pos.below()).is(BlockTags.LOGS)
                    && level.getBlockState(pos.above()).is(BlockTags.LOGS)) {
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
