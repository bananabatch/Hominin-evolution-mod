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
    private static final int RETARGET_TICKS = 300;

    private final BandMember member;
    private int ticks;

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
        BlockPos target = member.getExcursionTarget();
        if (target == null) {
            return;
        }
        boolean arrived = member.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) < 9.0D;
        if (arrived && ticks % RETARGET_TICKS == 0) {
            // Poke about the area instead of standing on one spot.
            int x = target.getX() + member.getRandom().nextInt(17) - 8;
            int z = target.getZ() + member.getRandom().nextInt(17) - 8;
            if (member.level().hasChunk(x >> 4, z >> 4)) {
                int y = member.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                member.setExcursionTarget(new BlockPos(x, y, z));
            }
        }
        if (member.getNavigation().isDone() && ticks % 20 == 0) {
            walk();
        }
    }

    private void walk() {
        BlockPos target = member.getExcursionTarget();
        if (target != null) {
            member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.9D);
        }
    }
}
