package dev.hominin.evolution.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * How a big animal actually fights: in single, committed passes.
 *
 * <p>Vanilla's melee goal bites once a second and never steps back, which on something
 * hitting for twelve is not a fight at all - it is a sequence of two or three hits with
 * no gap to react in. A cat does not do that. It closes, it commits, and then it breaks
 * off to reassess, because a predator that trades blows with something that can hit back
 * is a predator that gets injured, and an injured predator starves.
 *
 * <p>So each bite is followed by a real cooldown and a short withdrawal. That gap is the
 * whole fight: it is when you run, climb, get a spear up, or call the band in. Losing to
 * one of these should be something you watched happen, not something you read afterwards.
 */
public class PredatorAttackGoal extends MeleeAttackGoal {
    /** How far it pulls back to before coming again. */
    private static final int WITHDRAW_DISTANCE = 8;
    private static final int WITHDRAW_HEIGHT = 4;
    private static final double WITHDRAW_SPEED = 1.1D;

    private final int cooldownTicks;
    private final int recoilTicks;
    /** Whether it pulls back between passes, or holds its ground and stares you down. */
    private final boolean withdraws;

    private int nextAttackIn;
    private int recoiling;

    public PredatorAttackGoal(PathfinderMob mob, double speedModifier, int cooldownTicks, int recoilTicks) {
        this(mob, speedModifier, cooldownTicks, recoilTicks, true);
    }

    /**
     * @param withdraws false for an animal that does not give ground at all. It still
     *                  pauses between bites - the gap is what makes it fair - but it
     *                  spends the pause standing its ground rather than backing off,
     *                  because backing off reads as fear, and this has none.
     */
    public PredatorAttackGoal(PathfinderMob mob, double speedModifier, int cooldownTicks, int recoilTicks,
            boolean withdraws) {
        super(mob, speedModifier, true);
        this.cooldownTicks = cooldownTicks;
        this.recoilTicks = recoilTicks;
        this.withdraws = withdraws;
    }

    @Override
    public void start() {
        super.start();
        nextAttackIn = 0;
        recoiling = 0;
    }

    @Override
    public void stop() {
        super.stop();
        recoiling = 0;
    }

    /**
     * The parent's own cooldown lives in a private field and is hardcoded to a second,
     * so rather than fight it this gate sits on top: the parent may think it is ready,
     * and it still does not get to bite until this says so.
     */
    @Override
    protected boolean canPerformAttack(LivingEntity entity) {
        return nextAttackIn <= 0 && super.canPerformAttack(entity);
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        boolean landing = canPerformAttack(target);
        super.checkAndPerformAttack(target);
        if (landing) {
            nextAttackIn = cooldownTicks;
            recoiling = recoilTicks;
        }
    }

    @Override
    public void tick() {
        if (nextAttackIn > 0) {
            nextAttackIn--;
        }
        if (recoiling > 0) {
            recoiling--;
            withdraw();
            // Deliberately not calling super: while it is backing off it is not also
            // pathing back in, which is what makes the gap a real one.
            return;
        }
        super.tick();
    }

    /** Breaking off, and keeping its eyes on you the whole way. */
    private void withdraw() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!withdraws) {
            mob.getNavigation().stop();
            return;
        }
        if (!mob.getNavigation().isDone()) {
            return;
        }
        Vec3 away = DefaultRandomPos.getPosAway(mob, WITHDRAW_DISTANCE, WITHDRAW_HEIGHT, target.position());
        if (away != null) {
            mob.getNavigation().moveTo(away.x, away.y, away.z, WITHDRAW_SPEED);
        }
    }
}
