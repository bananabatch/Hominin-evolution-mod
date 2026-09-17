package dev.hominin.evolution.entity;

import java.util.EnumSet;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.data.HeadTrauma;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The movement of a concussed animal: it still tries to go somewhere, but it
 * cannot hold a heading and it does not look where it is going.
 *
 * <p>The trick is {@code MoveControl.setWantedPosition} rather than a navigation
 * path. Pathfinding would route the mob neatly around obstacles; driving the move
 * control directly makes it walk straight into whatever is in front of it, which
 * is exactly the read we want. The heading also creeps in one direction, so a mob
 * left alone circles instead of wandering off.
 */
public class ConcussedGoal extends Goal {
    private static final int RETARGET_TICKS = 20;
    private static final double STAGGER_DISTANCE = 4.0D;
    /** Degrees the heading creeps each time it is re-picked - this is the circling. */
    private static final float DRIFT_PER_STEP = 35.0F;

    private final Mob mob;
    private final double speed;
    private float heading;
    private int cooldown;

    public ConcussedGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.heading = mob.getRandom().nextFloat() * 360.0F;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private boolean concussed() {
        HeadTrauma trauma = mob.getExistingData(Attachments.HEAD_TRAUMA).orElse(null);
        return trauma != null && trauma.isConcussed();
    }

    @Override
    public boolean canUse() {
        return concussed();
    }

    @Override
    public boolean canContinueToUse() {
        return concussed();
    }

    @Override
    public void start() {
        cooldown = 0;
    }

    @Override
    public void tick() {
        if (cooldown-- > 0) {
            return;
        }
        cooldown = RETARGET_TICKS;
        // Creep the heading, then jitter it, so the path is a wobbling circle.
        heading += DRIFT_PER_STEP + (mob.getRandom().nextFloat() - 0.5F) * 40.0F;
        double radians = Math.toRadians(heading);
        double x = mob.getX() + Math.cos(radians) * STAGGER_DISTANCE;
        double z = mob.getZ() + Math.sin(radians) * STAGGER_DISTANCE;
        mob.getMoveControl().setWantedPosition(x, mob.getY(), z, speed);
        // Look somewhere other than where it is walking.
        mob.getLookControl().setLookAt(
                mob.getX() + Math.cos(radians + Math.PI / 2.0) * 6.0,
                mob.getEyeY(),
                mob.getZ() + Math.sin(radians + Math.PI / 2.0) * 6.0);
    }
}
