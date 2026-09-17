package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.data.HeadTrauma;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A cut animal runs, and keeps running from whoever cut it.
 *
 * <p>Vanilla's panic is a few seconds of scattering in random directions. This is
 * directed and it lasts: the animal flees the hunter for as long as it is bleeding,
 * re-picking a route away whenever the hunter closes in. And it tires. The longer
 * the hunter stays on it, the slower it gets - so a hunter who cannot catch it at
 * the start can still run it down, which is how a slow, weak, sweating primate
 * brought down animals faster than itself.
 */
public class WoundedFleeGoal extends Goal {
    /** How far away the animal tries to put itself each time it re-routes. */
    private static final int FLEE_DISTANCE = 16;
    private static final int FLEE_VERTICAL = 7;
    private static final double FLEE_SPEED = 1.4D;

    /** Re-route this often, or at once if the hunter gets this close. */
    private static final int REROUTE_TICKS = 30;
    private static final double PANIC_DISTANCE_SQR = 8.0D * 8.0D;

    /** Beyond this the hunter has lost the trail, and the animal starts to recover. */
    private static final double PURSUIT_DISTANCE_SQR = 32.0D * 32.0D;

    /** Keeps running a while after the bleeding stops - it does not know it is safe. */
    private static final int AFTERSHOCK_TICKS = 100;

    /** Seconds of sustained pursuit before the animal starts to flag, then to fail. */
    private static final int TIRING_TICKS = 20 * 12;
    private static final int SPENT_TICKS = 20 * 25;

    private final PathfinderMob mob;
    @Nullable
    private UUID hunterId;
    private int reroute;
    private int aftershock;
    private int pursuit;

    public WoundedFleeGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** Points the flight at whoever made the latest cut. */
    public void woundedBy(Player hunter) {
        this.hunterId = hunter.getUUID();
        this.aftershock = AFTERSHOCK_TICKS;
    }

    @Nullable
    private Player hunter() {
        return hunterId == null ? null : mob.level().getPlayerByUUID(hunterId);
    }

    private boolean bleeding() {
        return mob.hasEffect(ModEffects.BLEEDING);
    }

    /** A concussed animal cannot run straight - its own stagger wins. */
    private boolean concussed() {
        HeadTrauma trauma = mob.getExistingData(Attachments.HEAD_TRAUMA).orElse(null);
        return trauma != null && trauma.isConcussed();
    }

    @Override
    public boolean canUse() {
        return hunter() != null && (bleeding() || aftershock > 0) && !concussed();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        reroute = 0;
    }

    @Override
    public void stop() {
        pursuit = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!bleeding()) {
            aftershock--;
        }
        Player hunter = hunter();
        if (hunter == null) {
            return;
        }
        double distanceSqr = mob.distanceToSqr(hunter);
        tire(distanceSqr);

        reroute--;
        boolean cornered = distanceSqr < PANIC_DISTANCE_SQR;
        if (reroute > 0 && !cornered && !mob.getNavigation().isDone()) {
            return;
        }
        reroute = REROUTE_TICKS;
        Vec3 away = DefaultRandomPos.getPosAway(mob, FLEE_DISTANCE, FLEE_VERTICAL, hunter.position());
        if (away != null) {
            mob.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
        }
    }

    /**
     * Pursuit builds while the hunter stays in range and bleeds away when it falls
     * behind. Past the thresholds the animal is slowed, and slowed harder - refreshed
     * every tick, so it lifts as soon as the chase is broken off.
     */
    private void tire(double distanceSqr) {
        if (distanceSqr <= PURSUIT_DISTANCE_SQR) {
            pursuit++;
        } else {
            pursuit = Math.max(0, pursuit - 2);
        }
        if (pursuit >= SPENT_TICKS) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false, true));
        } else if (pursuit >= TIRING_TICKS) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, false, true));
        }
    }
}
