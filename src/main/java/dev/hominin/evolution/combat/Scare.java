package dev.hominin.evolution.combat;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

import dev.hominin.evolution.ModTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
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

    /** Game time each scared mob stays scared until. Weak, so unloaded mobs are not held. */
    private static final Map<Mob, Long> SCARED_UNTIL = new WeakHashMap<>();
    private static final Map<Mob, Vec3> SCARED_OF = new WeakHashMap<>();

    /** Whether a threat display can frighten this at all. */
    public static boolean canBeScared(PathfinderMob mob) {
        return !mob.getType().is(ModTags.EntityTypes.FEARLESS);
    }

    public static void scare(PathfinderMob mob, Vec3 from, int ticks) {
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

        private void run() {
            Vec3 from = SCARED_OF.getOrDefault(mob, mob.position());
            Vec3 away = DefaultRandomPos.getPosAway(mob, 20, 7, from);
            if (away != null) {
                mob.getNavigation().moveTo(away.x, away.y, away.z, RUN_SPEED);
            }
        }
    }

    private Scare() {
    }
}
