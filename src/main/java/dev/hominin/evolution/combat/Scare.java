package dev.hominin.evolution.combat;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import dev.hominin.evolution.ModTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * Being properly frightened off. A scared mob runs from where the fright came from for a
 * few seconds and cannot pick anyone as a target until it is over.
 *
 * <p>Clearing a target and pointing the navigation away is not enough on its own: a
 * mob's own attack goal simply picks its target back up next tick and walks straight
 * back. So the fright is a goal of its own, at the very top of the mob's priorities, and
 * target changes are refused while it lasts.
 */
public final class Scare {
    private static final double RUN_SPEED = 1.5D;
    /** A satisfied animal does not bolt. It turns and goes, which is somehow worse to watch. */
    private static final double WALK_SPEED = 1.0D;

    /** Game time each scared mob stays scared until. Weak, so unloaded mobs are not held. */
    private static final Map<Mob, Long> SCARED_UNTIL = new WeakHashMap<>();
    private static final Map<Mob, Vec3> SCARED_OF = new WeakHashMap<>();
    private static final Map<Mob, Double> LEAVING_AT = new WeakHashMap<>();

    /** Whether a threat display can frighten this at all. */
    public static boolean canBeScared(PathfinderMob mob) {
        return !mob.getType().is(ModTags.EntityTypes.FEARLESS);
    }

    public static void scare(PathfinderMob mob, Vec3 from, int ticks) {
        leave(mob, from, ticks, RUN_SPEED);
    }

    /**
     * Going, but not in fear. A predator that has eaten, or one whose skull a club has
     * just cracked, leaves whether or not anything could ever frighten it - so this
     * deliberately ignores the fearless tag. That tag means a threat display will not
     * move it, and it still will not: a sabertooth walks away from a full belly, not
     * from shouting.
     */
    public static void depart(PathfinderMob mob, Vec3 from, int ticks) {
        leave(mob, from, ticks, WALK_SPEED);
    }

    private static void leave(PathfinderMob mob, Vec3 from, int ticks, double speed) {
        LEAVING_AT.put(mob, speed);
        // Its own targeting goals live in the target selector, where a goal flag cannot
        // reach them. Dropping the target it is chasing as well as the one it holds is
        // what stops it simply turning round and picking the next hominin along.
        mob.setLastHurtByMob(null);
        // Anyone still swinging at it has no reason to follow it into the grass.
        dev.hominin.evolution.band.Band.standDown(mob);
        SCARED_UNTIL.put(mob, mob.level().getGameTime() + ticks);
        SCARED_OF.put(mob, from);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof RunGoal) {
                return;
            }
        }
        mob.goalSelector.addGoal(0, new RunGoal(mob));
    }

    public static boolean isScared(Mob mob) {
        Long until = SCARED_UNTIL.get(mob);
        return until != null && mob.level().getGameTime() < until;
    }

    /** A frightened mob will not take anyone as a target. */
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getNewAboutToBeSetTarget() != null && isScared(mob)) {
            event.setCanceled(true);
        }
    }

    private static class RunGoal extends Goal {
        private final PathfinderMob mob;

        RunGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            return isScared(mob);
        }

        @Override
        public void start() {
            run();
        }

        @Override
        public void tick() {
            mob.setTarget(null);
            if (mob.getNavigation().isDone()) {
                run();
            }
        }

        /** Keeps its line: a frightened animal runs away, not round and round. */
        private Vec3 heading;

        private void run() {
            Vec3 from = SCARED_OF.getOrDefault(mob, mob.position());
            Vec3 took = dev.hominin.evolution.entity.FleeRoute.run(mob, from, heading, 16,
                    LEAVING_AT.getOrDefault(mob, RUN_SPEED));
            if (took != null) {
                heading = took;
            }
        }
    }

    private Scare() {
    }
}
