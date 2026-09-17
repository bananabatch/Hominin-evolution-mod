package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.List;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Walks over to food or a weapon lying nearby. The pick-up itself is vanilla's: any
 * mob allowed to pick up loot grabs what it walks over, and {@link BandMember#wantsToPickUp}
 * decides what counts.
 */
public class GatherItemsGoal extends Goal {
    private static final double SEARCH_RADIUS = 10.0D;
    /** Things far from the leader are not worth wandering off for. */
    private static final double LEADER_TETHER = 20.0D;
    private static final int GIVE_UP_TICKS = 200;

    private final BandMember member;
    private ItemEntity target;
    private int ticks;

    public GatherItemsGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount % 10 != 0 || member.isUpATree()) {
            return false;
        }
        LivingEntity leader = member.followTarget();
        List<ItemEntity> items = member.level().getEntitiesOfClass(ItemEntity.class,
                member.getBoundingBox().inflate(SEARCH_RADIUS, 3.0D, SEARCH_RADIUS),
                item -> item.isAlive() && !item.hasPickUpDelay() && member.wantsToPickUp(item.getItem())
                        && (leader == null || item.distanceToSqr(leader) < LEADER_TETHER * LEADER_TETHER));
        target = null;
        double best = Double.MAX_VALUE;
        for (ItemEntity item : items) {
            double distance = member.distanceToSqr(item);
            if (distance < best) {
                best = distance;
                target = item;
            }
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && ticks < GIVE_UP_TICKS && !member.getNavigation().isDone();
    }

    @Override
    public void start() {
        ticks = 0;
        member.getNavigation().moveTo(target, 1.1D);
    }

    @Override
    public void tick() {
        ticks++;
        if (ticks % 20 == 0 && target != null) {
            member.getNavigation().moveTo(target, 1.1D);
        }
    }

    @Override
    public void stop() {
        target = null;
    }
}
