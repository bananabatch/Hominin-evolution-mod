package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.block.Nests;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Actually lying down in it. A band that spent the evening weaving nests and then stood
 * around beside them all night was doing the work for nothing - the nest is the point,
 * and a hominin asleep in one is the oldest picture there is of a night survived.
 *
 * <p>So once it is dark and there is a finished nest within reach, they go and get in it,
 * and they stay until morning or until something wakes them.
 */
public class SleepInNestGoal extends Goal {
    private static final int SEARCH_RADIUS = 16;
    /** Close enough to climb in from. */
    private static final double REACH = 2.0D;
    private static final int GIVE_UP_TICKS = 400;

    private final BandMember member;
    @Nullable
    private BlockPos nest;
    private int ticks;

    public SleepInNestGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!night() || member.inDanger() || member.isUpATree() || member.isSleeping()) {
            return false;
        }
        nest = findNest();
        return nest != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (nest == null || !night() || member.inDanger()) {
            return false;
        }
        // Somebody pulled the nest apart while they were in it.
        return member.level().getBlockState(nest).is(ModBlocks.NEST.get())
                && (member.isSleeping() || ticks < GIVE_UP_TICKS);
    }

    @Override
    public void start() {
        ticks = 0;
        walk();
    }

    @Override
    public void stop() {
        if (member.isSleeping()) {
            member.stopSleeping();
        }
        nest = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (member.isSleeping()) {
            return;
        }
        if (member.distanceToSqr(nest.getX() + 0.5D, nest.getY(), nest.getZ() + 0.5D) > REACH * REACH) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        member.startSleeping(nest);
    }

    private void walk() {
        member.getNavigation().moveTo(nest.getX() + 0.5D, nest.getY(), nest.getZ() + 0.5D, 1.0D);
    }

    private boolean night() {
        return member.level().isNight();
    }

    /** The nearest finished nest nobody else is already lying in. */
    @Nullable
    private BlockPos findNest() {
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -4, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 4, SEARCH_RADIUS))) {
            if (!member.level().getBlockState(pos).is(ModBlocks.NEST.get())
                    || !Nests.isComplete(member.level(), pos) || taken(pos)) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** One body to a nest: a spot somebody else is already asleep on is not free. */
    private boolean taken(BlockPos pos) {
        for (BandMember other : member.level().getEntitiesOfClass(BandMember.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1.0D))) {
            if (other != member && other.isSleeping()) {
                return true;
            }
        }
        return false;
    }
}
