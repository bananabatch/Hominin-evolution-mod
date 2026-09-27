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
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Going for a drink. Thirsty, a member walks to the nearest water and drinks its fill, a mouthful at a time, and
 * dips any empty shells it carries while it is there. Bleeding out, or sick with bad meat, it runs - and keeps
 * drinking for as long as it takes, because that is the only thing that helps. A spring's water counts double.
 */
public class DrinkGoal extends Goal {
    private static final int SEARCH = 32;
    /** Parched, bleeding out or sick: they will go a long way for water. */
    private static final int URGENT_SEARCH = 96;
    private static final int TICKS_PER_SIP = 20;
    private static final int GIVE_UP_TICKS = 900;
    private static final int URGENT_GIVE_UP_TICKS = 4800;
    /** Paths are only planned so far ahead: a longer walk goes in legs of about this. */
    private static final double LEG = 16.0D;
    /** Set while a member is off for water, so nothing hauls it back to the band on the way. */
    public static final String SEEKING = "HomininSeekingWater";

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
            nextSearch = now + (urgent ? 60L : 200L);
        }
        return water != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (water == null || ticks > (MemberSurvival.needsWaterNow(member) ? URGENT_GIVE_UP_TICKS : GIVE_UP_TICKS)) {
            return false;
        }
        // Bleeding out, or sick: keep drinking while it is doing any good. Otherwise, until full.
        return MemberSurvival.needsWaterNow(member) || MemberSurvival.thirst(member) < Thirst.MAX;
    }

    @Override
    public void start() {
        ticks = 0;
        // Until when: a trip cut short by the world closing does not leave them unrecallable ever after.
        member.getPersistentData().putLong(SEEKING, member.level().getGameTime() + URGENT_GIVE_UP_TICKS);
        walk();
    }

    @Override
    public void stop() {
        water = null;
        member.getPersistentData().remove(SEEKING);
        member.getNavigation().stop();
    }

    /** Whether this member is on its way to water - left to get there, not called back. */
    public static boolean seeking(BandMember member) {
        return member.level().getGameTime() < member.getPersistentData().getLong(SEEKING);
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(water.getX() + 0.5D, water.getY() + 0.5D, water.getZ() + 0.5D);
        boolean there = member.isInWater()
                || member.distanceToSqr(water.getX() + 0.5D, water.getY() + 0.5D, water.getZ() + 0.5D) < 2.5D * 2.5D;
        if (!there) {
            if (ticks % 40 == 1 || member.getNavigation().isDone()) {
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
        Vec3 goal = Vec3.atBottomCenterOf(water);
        if (member.position().distanceTo(goal) > LEG + 4.0D) {
            // Too far to plan in one go: a leg of the way towards it, then the next.
            Vec3 leg = DefaultRandomPos.getPosTowards(member, (int) LEG, 7, goal, Math.PI / 2.0D);
            if (leg == null) {
                Vec3 toward = goal.subtract(member.position()).normalize().scale(LEG);
                leg = member.position().add(toward);
            }
            member.getNavigation().moveTo(leg.x, leg.y, leg.z, speed);
            return;
        }
        member.getNavigation().moveTo(goal.x, goal.y, goal.z, speed);
    }

    /**
     * The nearest open water - a source with air over it, at the surface - within this far, looked for ring by ring
     * outwards so the nearest turns up first. Only ground already loaded is looked at.
     */
    @Nullable
    private BlockPos findWater(int radius) {
        Level level = member.level();
        BlockPos origin = member.blockPosition();
        if (member.isInWater() && level.getFluidState(origin).is(FluidTags.WATER)) {
            return origin;
        }
        for (int ring = 0; ring <= radius; ring++) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    BlockPos pos = surfaceWater(level, origin.getX() + dx, origin.getZ() + dz, origin.getY());
                    if (pos != null && pos.distSqr(origin) < bestDistance) {
                        bestDistance = pos.distSqr(origin);
                        best = pos;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /** Standing water at the top of this column - not far above or below where the member stands - or null. */
    @Nullable
    private static BlockPos surfaceWater(Level level, int x, int z, int nearY) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (Math.abs(top - nearY) > 16) {
            return null;
        }
        BlockPos pos = new BlockPos(x, top, z);
        return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource()
                && level.getBlockState(pos.above()).isAir() ? pos : null;
    }
}
