package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.MemberSurvival;
import dev.hominin.evolution.survival.Thirst;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Going for a drink. Thirsty, a member walks to the nearest water and drinks its fill, a mouthful at a time, and
 * dips any empty shells it carries while it is there. Bleeding out, or sick with bad meat, it runs - and keeps
 * drinking for as long as it takes, because that is the only thing that helps. A spring's water counts double.
 */
public class DrinkGoal extends Goal {
    private static final int SEARCH = 24;
    private static final int URGENT_SEARCH = 40;
    private static final int TICKS_PER_SIP = 20;
    private static final int GIVE_UP_TICKS = 900;

    private final BandMember member;
    @Nullable
    private BlockPos water;
    private int ticks;
    /** After a search that found nothing, when to look again: the ground round about is a lot of blocks. */
    private long nextSearch;

    public DrinkGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.isBaby() || member.isSleeping() || member.isUpATree()) {
            return false;
        }
        boolean urgent = MemberSurvival.needsWaterNow(member);
        if (!urgent && (!MemberSurvival.isThirsty(member) || member.getTarget() != null || member.inDanger()
                || member.getRandom().nextInt(20) != 0)) {
            return false;
        }
        long now = member.level().getGameTime();
        if (now < nextSearch) {
            return false;
        }
        water = findWater(urgent ? URGENT_SEARCH : SEARCH);
        if (water == null) {
            nextSearch = now + 100L;
        }
        return water != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (water == null || ticks > GIVE_UP_TICKS) {
            return false;
        }
        // Bleeding out, or sick: keep drinking while it is doing any good. Otherwise, until full.
        return MemberSurvival.needsWaterNow(member) || MemberSurvival.thirst(member) < Thirst.MAX;
    }

    @Override
    public void start() {
        ticks = 0;
        walk();
    }

    @Override
    public void stop() {
        water = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(water.getX() + 0.5D, water.getY() + 0.5D, water.getZ() + 0.5D);
        boolean there = member.isInWater()
                || member.distanceToSqr(water.getX() + 0.5D, water.getY() + 0.5D, water.getZ() + 0.5D) < 2.5D * 2.5D;
        if (!there) {
            if (ticks % 20 == 1 || member.getNavigation().isDone()) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        if (ticks % TICKS_PER_SIP != 0) {
            return;
        }
        member.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        boolean spring = member.level() instanceof ServerLevel server && dev.hominin.evolution.world.Pois.isSpring(server, water);
        MemberSurvival.drink(member, Thirst.DRINK_FROM_SOURCE * (spring ? 2 : 1));
        MemberSurvival.fillShells(member);
    }

    private void walk() {
        double speed = MemberSurvival.needsWaterNow(member) ? 1.4D : 1.0D;
        member.getNavigation().moveTo(water.getX() + 0.5D, water.getY(), water.getZ() + 0.5D, speed);
    }

    /** The nearest open water - a source with air over it - within this far. */
    @Nullable
    private BlockPos findWater(int radius) {
        BlockPos origin = member.blockPosition();
        if (member.isInWater() && member.level().getFluidState(origin).is(FluidTags.WATER)) {
            return origin;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -4, -radius), origin.offset(radius, 3, radius))) {
            if (!member.level().getFluidState(pos).isSource() || !member.level().getFluidState(pos).is(FluidTags.WATER)
                    || !member.level().getBlockState(pos.above()).isAir()) {
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
}
