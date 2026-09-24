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
        if (!night() || member.inDanger() || member.isUpATree() || member.isSleeping() || member.isOnWatch()) {
            return false;
        }
        nest = findNest();
        return nest != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (nest == null || !night() || member.inDanger() || member.isOnWatch()) {
            return false;
        }
        // Somebody pulled the nest apart while they were in it.
        return (member.level().getBlockState(nest).is(ModBlocks.NEST.get())
                || member.level().getBlockState(nest).is(ModBlocks.THATCH_BEDDING.get()))
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

    /** From dusk, once they have turned in; full dark otherwise. Till just before dawn. */
    private boolean night() {
        long time = member.level().getDayTime() % 24000L;
        return member.isTurnedIn() ? time >= 11500L && time < 23200L : member.level().isNight();
    }

    /**
     * The nearest finished nest of their own that nobody else is already lying in - their own, their mate's, or
     * one nobody is about to claim. Your mate goes to yours, if you have one.
     */
    @Nullable
    private BlockPos findNest() {
        // A roof of their own comes before any nest out in the open.
        if (member.level() instanceof net.minecraft.server.level.ServerLevel server) {
            dev.hominin.evolution.build.Sites.Site room = dev.hominin.evolution.build.Building.roomFor(member);
            BlockPos bed = room != null ? dev.hominin.evolution.build.Building.bedIn(server, room, member) : null;
            if (bed != null && !taken(bed)) {
                return bed;
            }
        }
        BlockPos origin = member.blockPosition();
        net.minecraft.world.entity.player.Player leader = member.leaderPlayer();
        boolean mate = leader != null && member.isMateOf(leader.getUUID()) && member.distanceToSqr(leader) < 32.0D * 32.0D;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        java.util.List<BlockPos> around = new java.util.ArrayList<>();
        around.add(origin);
        if (mate) {
            around.add(leader.blockPosition());
        }
        for (BlockPos centre : around) {
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-SEARCH_RADIUS, -4, -SEARCH_RADIUS),
                    centre.offset(SEARCH_RADIUS, 4, SEARCH_RADIUS))) {
                var state = member.level().getBlockState(pos);
                // A finished nest - or thatch bedding, the erectus bed.
                boolean bed = state.is(ModBlocks.THATCH_BEDDING.get()) || state.is(ModBlocks.NEST.get())
                        && (Nests.isComplete(member.level(), pos) || dev.hominin.evolution.build.Building.inRoom(member.level(), pos));
                if (!bed || taken(pos) || !dev.hominin.evolution.block.NestOwners.mayUse(member, pos)) {
                    continue;
                }
                double distance = pos.distSqr(origin);
                if (mate && member.level() instanceof net.minecraft.server.level.ServerLevel level
                        && leader.getUUID().equals(dev.hominin.evolution.block.NestOwners.ownerOf(level, pos))) {
                    // Beside you, whatever else is nearer.
                    distance -= 100000.0D;
                }
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
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
        // Nor the spot you are lying on yourself: a mate lies beside you, not on you.
        return !member.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                new net.minecraft.world.phys.AABB(pos).inflate(0.5D), net.minecraft.world.entity.player.Player::isSleeping)
                .isEmpty();
    }
}
