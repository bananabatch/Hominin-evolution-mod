package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The shape of most small jobs: now and then, when it wants to, a member picks a place, walks over to it and does
 * something there - once, or a few times - and then gets on with its day.
 */
public abstract class WalkAndDoGoal extends Goal {
    protected final BandMember member;
    @Nullable
    protected BlockPos target;
    private final int chance;
    private final double reach;
    private final int giveUpTicks;
    private final int ticksPerGo;
    protected int ticks;

    protected WalkAndDoGoal(BandMember member, int chance, double reach, int giveUpTicks, int ticksPerGo) {
        this.member = member;
        this.chance = chance;
        this.reach = reach;
        this.giveUpTicks = giveUpTicks;
        this.ticksPerGo = ticksPerGo;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Whether it has any reason to go at all. Checked before anything costly. */
    protected abstract boolean wants(ServerLevel level);

    /** Where to go, or null. */
    @Nullable
    protected abstract BlockPos find(ServerLevel level);

    /** One go at it, arrived. True when the job is done. */
    protected abstract boolean act(ServerLevel level, BlockPos at);

    /** Where to stand relative to the target: on it, by default the block above it. */
    protected BlockPos standAt(BlockPos at) {
        return at.above();
    }

    @Override
    public boolean canUse() {
        if (member.isBaby() || member.isSleeping() || member.isUpATree() || member.getTarget() != null
                || member.inDanger() || member.getRandom().nextInt(chance) != 0
                || !(member.level() instanceof ServerLevel level) || !wants(level)) {
            return false;
        }
        target = find(level);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < giveUpTicks && member.getTarget() == null && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
        walk();
    }

    @Override
    public void stop() {
        target = null;
        member.getNavigation().stop();
    }

    private void walk() {
        BlockPos stand = standAt(target);
        member.getNavigation().moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 1.0D);
    }

    @Override
    public void tick() {
        ticks++;
        if (!(member.level() instanceof ServerLevel level)) {
            return;
        }
        member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (member.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) > reach * reach) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        if (ticks % ticksPerGo != 0) {
            return;
        }
        member.swing(InteractionHand.MAIN_HAND);
        if (act(level, target)) {
            target = null;
        }
    }
}
