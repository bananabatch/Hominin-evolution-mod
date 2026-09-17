package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Going off alone for a while. The member walks out to somewhere away from the band
 * and pokes about there - foraging, pulling branches, finding stones, whatever the
 * other goals make of the place - until the trip is over and it comes back.
 */
public class ExcursionGoal extends Goal {
    private final BandMember member;
    private int ticks;
    private int idle;
    private int pause = 40;
    private int stalled;
    private net.minecraft.world.phys.Vec3 lastPos = net.minecraft.world.phys.Vec3.ZERO;

    public ExcursionGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return member.isOnExcursion() && member.getExcursionTarget() != null && !member.isUpATree();
    }

    @Override
    public void start() {
        ticks = 0;
        walk();
    }

    @Override
    public void tick() {
        ticks++;
        // A path that goes nowhere: still "walking", but not moving. Drop it and pick somewhere else.
        if (!member.getNavigation().isDone()) {
            if (member.position().distanceToSqr(lastPos) < 0.01D) {
                if (++stalled >= 60) {
                    stalled = 0;
                    member.getNavigation().stop();
                }
            } else {
                stalled = 0;
            }
            lastPos = member.position();
        }
        if (member.getExcursionTarget() == null || !member.getNavigation().isDone()) {
            idle = 0;
            return;
        }
        // Stopped - arrived, or the way was blocked. Pause a moment, then poke about somewhere
        // else nearby, so an explorer never just stands on one spot.
        if (++idle < pause) {
            return;
        }
        idle = 0;
        pause = 30 + member.getRandom().nextInt(70);
        for (int attempt = 0; attempt < 6; attempt++) {
            int x = member.getBlockX() + member.getRandom().nextInt(25) - 12;
            int z = member.getBlockZ() + member.getRandom().nextInt(25) - 12;
            if (!member.level().hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos next = new BlockPos(x, member.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            member.setExcursionTarget(next);
            if (walk()) {
                return;
            }
        }
    }

    private boolean walk() {
        BlockPos target = member.getExcursionTarget();
        return target != null
                && member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.9D);
    }
}
