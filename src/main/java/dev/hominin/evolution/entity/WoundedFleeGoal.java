package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.data.HeadTrauma;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * An animal that has been hurt runs, and keeps running - from whoever hurt it, hunter or band member or cat.
 *
 * <p>Vanilla's panic is a few seconds of scattering to random spots, which is why a struck animal used to stand
 * about, stop dead after a few strides, or run rings round the one chasing it. This is a flight: it holds a line
 * away from the hunter (see {@link FleeRoute}), sprints flat out at first, and does not stop until the hunter is
 * far behind. Only a club to the head stops it - a concussed animal staggers where it is.
 *
 * <p>And it tires. The longer the hunter stays on it, the slower it gets - so a hunter who cannot catch it at the
 * start can still run it down, which is how a slow, weak, sweating primate brought down animals faster than
 * itself.
 */
public class WoundedFleeGoal extends Goal {
    /** How far ahead each leg of the run aims. Within an animal's usual follow range. */
    private static final int LEG = 14;
    /** Flat out, at first. */
    private static final double SPRINT_SPEED = 1.75D;
    private static final int SPRINT_TICKS = 5 * 20;
    /** Then a hard run, for as long as it has to. */
    private static final double RUN_SPEED = 1.45D;

    /** A new leg this often at the most, or at once when the old one runs out or the hunter closes in. */
    private static final int REROUTE_TICKS = 30;
    private static final double PANIC_DISTANCE_SQR = 10.0D * 10.0D;

    /** Beyond this the hunter has lost the trail, and the animal starts to recover. */
    private static final double PURSUIT_DISTANCE_SQR = 32.0D * 32.0D;
    /** Far enough to stop running: out of sight, out of smell. */
    private static final double SAFE_DISTANCE_SQR = 48.0D * 48.0D;

    /** Keeps running a while after the bleeding stops - it does not know it is safe. */
    private static final int AFTERSHOCK_TICKS = 160;
    /** A struck animal runs for this long even with no wound to keep it going. */
    private static final int STARTLED_TICKS = 400;

    /** Seconds of sustained pursuit before the animal starts to flag, then to fail. */
    private static final int TIRING_TICKS = 20 * 12;
    private static final int SPENT_TICKS = 20 * 25;

    private final PathfinderMob mob;
    @Nullable
    private UUID hunterId;
    private int reroute;
    private int aftershock;
    private int pursuit;
    private int running;
    /** Running, and not yet safe. */
    private boolean fleeing;
    @Nullable
    private Vec3 heading;
    @Nullable
    private Vec3 lastPos;
    private int stuck;

    /**
     * Sets this mob running from whoever hurt it, adding the goal if it has not fled before. The run outlasts the
     * sprint that starts it: a struck animal keeps going long after it has stopped being fast.
     */
    public static void makeFlee(PathfinderMob mob, LivingEntity hunter) {
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof WoundedFleeGoal flee) {
                flee.woundedBy(hunter);
                flee.aftershock = Math.max(flee.aftershock, STARTLED_TICKS);
                return;
            }
        }
        WoundedFleeGoal flee = new WoundedFleeGoal(mob);
        flee.woundedBy(hunter);
        flee.aftershock = STARTLED_TICKS;
        mob.goalSelector.addGoal(0, flee);
        if (mob.getTarget() == hunter) {
            mob.setTarget(null);
        }
    }

    /** Animals that fight rather than run: a cat, a baboon with its troop behind it, a buffalo that has turned. */
    public static boolean standsGround(LivingEntity animal) {
        return dev.hominin.evolution.hunt.MobClass.stillFights(animal) || animal.getType().is(ModTags.EntityTypes.FEARLESS)
                || (animal instanceof Baboon baboon && baboon.hasTroopBehindIt())
                || (animal instanceof Pelorovis pelorovis && pelorovis.standsGround())
                || (animal instanceof Megafauna giant && giant.standsGround());
    }

    /** Something hurt a grazing animal: it runs. Hooked to every hit, whoever or whatever landed it. */
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        // Baboons, chimpanzees and bonobos keep their own counsel: a troop decides whether to run or fight.
        if (!(event.getEntity() instanceof PathfinderMob mob) || mob.level().isClientSide() || !mob.isAlive()
                || mob instanceof TroopAnimal || !dev.hominin.evolution.hunt.Predation.isGame(mob) || standsGround(mob)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == mob) {
            if (event.getEntity() instanceof PathfinderMob fighter && !fighter.level().isClientSide()
                    && dev.hominin.evolution.hunt.MobClass.stillFights(fighter) && event.getSource().getEntity() instanceof LivingEntity attacker
                    && attacker != fighter) {
                // Defensive and predators: it turns on whoever did it.
                dev.hominin.evolution.hunt.MobClass.turnOn(fighter, attacker);
            }
            return;
        }
        makeFlee(mob, attacker);
    }

    public WoundedFleeGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    /** Points the flight at whoever made the latest cut. */
    public void woundedBy(LivingEntity hunter) {
        this.hunterId = hunter.getUUID();
        this.aftershock = Math.max(aftershock, AFTERSHOCK_TICKS);
        this.fleeing = true;
    }

    @Nullable
    private LivingEntity hunter() {
        if (hunterId == null || !(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        return level.getEntity(hunterId) instanceof LivingEntity hunter && hunter.isAlive() && !hunter.isSpectator()
                ? hunter : null;
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
        if (!fleeing || concussed() || dev.hominin.evolution.hunt.Quarry.holdsItsGround(mob)) {
            return false;
        }
        LivingEntity hunter = hunter();
        if (hunter == null) {
            fleeing = false;
            return false;
        }
        // Bleeding or still frightened, it runs; otherwise it runs until the hunter is far enough behind.
        return bleeding() || aftershock > 0 || mob.distanceToSqr(hunter) < SAFE_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        reroute = 0;
        running = 0;
        stuck = 0;
        lastPos = mob.position();
    }

    @Override
    public void stop() {
        pursuit = 0;
        heading = null;
        mob.getNavigation().stop();
        LivingEntity hunter = hunter();
        if (hunter == null || mob.distanceToSqr(hunter) >= SAFE_DISTANCE_SQR) {
            fleeing = false;
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!bleeding()) {
            aftershock--;
        }
        running++;
        LivingEntity hunter = hunter();
        if (hunter == null) {
            return;
        }
        double distanceSqr = mob.distanceToSqr(hunter);
        tire(distanceSqr);
        double speed = running < SPRINT_TICKS ? SPRINT_SPEED : RUN_SPEED;
        mob.getNavigation().setSpeedModifier(speed);

        // Going nowhere - wedged against something, or running on the spot: a new line, off to one side.
        if (running % 20 == 0) {
            Vec3 now = mob.position();
            if (lastPos != null && now.distanceToSqr(lastPos) < 0.8D && !mob.getNavigation().isDone()) {
                stuck++;
            } else {
                stuck = 0;
            }
            lastPos = now;
        }
        reroute--;
        boolean cornered = distanceSqr < PANIC_DISTANCE_SQR;
        boolean nearlyThere = mob.getNavigation().getPath() == null || mob.getNavigation().isDone()
                || mob.getNavigation().getPath().getNextNodeIndex() >= mob.getNavigation().getPath().getNodeCount() - 3;
        if (!nearlyThere && stuck == 0 && (reroute > 0 || !cornered)) {
            return;
        }
        reroute = REROUTE_TICKS;
        if (stuck > 0 && heading != null) {
            // Blocked straight on: swing the line hard to one side.
            heading = heading.yRot((float) Math.toRadians(mob.getRandom().nextBoolean() ? 80.0F : -80.0F));
            stuck = 0;
        }
        Vec3 took = FleeRoute.run(mob, hunter.position(), heading, LEG, speed);
        if (took != null) {
            heading = took;
        }
    }

    /**
     * Pursuit builds while the hunter stays in range and bleeds away when it falls behind. Past the thresholds
     * the animal is slowed, and slowed harder - refreshed every tick, so it lifts as soon as the chase is
     * broken off.
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
