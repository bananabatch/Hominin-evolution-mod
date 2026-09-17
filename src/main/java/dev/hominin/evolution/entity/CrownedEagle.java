package dev.hominin.evolution.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * A crowned eagle: the bird that hunts monkeys, and the only thing here that takes a
 * child out of the middle of a band.
 *
 * <p>The Taung Child - the first australopithecine ever found - has puncture marks in its
 * eye sockets that match a large raptor's talons. Something like this took it. So this one
 * circles, watches for a young one that has wandered clear of the adults, and stoops.
 *
 * <p>The defence is not a weapon. It is not letting the young out of arm's reach.
 */
public class CrownedEagle extends PathfinderMob {
    /** How far a child must be from any adult before it is worth the stoop. */
    private static final double UNGUARDED_RANGE = 7.0D;
    private static final double HUNT_RANGE = 28.0D;
    /** It will not press an attack among grown hominins. */
    private static final double BREAK_OFF_RANGE = 4.0D;

    public CrownedEagle(EntityType<? extends CrownedEagle> type, Level level) {
        super(type, level);
        moveControl = new FlyingMoveControl(this, 10, false);
        setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        setPathfindingMalus(PathType.WATER, -1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(false);
        navigation.setCanPassDoors(false);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new StoopGoal(this));
        goalSelector.addGoal(3, new CircleGoal(this));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    /** Young, small, and nobody grown standing over it. */
    public boolean isSnatchable(LivingEntity target) {
        if (target instanceof BandMember member) {
            return member.isBaby() && isUnguarded(member);
        }
        return target instanceof Animal animal && animal.isBaby() && isUnguarded(animal);
    }

    private boolean isUnguarded(LivingEntity target) {
        for (LivingEntity other : target.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(UNGUARDED_RANGE))) {
            boolean grown = (other instanceof BandMember member && !member.isBaby()) || other instanceof Player;
            if (grown && other.isAlive()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private LivingEntity findPrey() {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(HUNT_RANGE), this::isSnatchable)) {
            double distance = candidate.distanceToSqr(this);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playSound(SoundEvents.PHANTOM_BITE, 1.2F, 1.6F);
            // A bird this size does not wrestle. It hits, lifts, and lets go.
            target.setDeltaMovement(target.getDeltaMovement().add(0.0D, 0.45D, 0.0D));
            if (target instanceof BandMember member && member.isBaby()) {
                dev.hominin.evolution.band.Band.announceDiscovery(member, " is struck out of the open by talons!");
            }
        }
        return hit;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state,
            BlockPos pos) {
    }

    @Override
    public boolean isFlapping() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    /** Wheeling about high up, waiting for something below to be left alone. */
    private static class CircleGoal extends Goal {
        private final CrownedEagle eagle;

        CircleGoal(CrownedEagle eagle) {
            this.eagle = eagle;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return eagle.getNavigation().isDone() && eagle.getRandom().nextInt(10) == 0;
        }

        @Override
        public void start() {
            // High enough that it is a shape against the sky rather than a bird in a tree.
            Vec3 around = eagle.position();
            double angle = eagle.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 8.0D + eagle.getRandom().nextInt(12);
            double x = around.x + Math.cos(angle) * radius;
            double z = around.z + Math.sin(angle) * radius;
            int ground = eagle.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    (int) x, (int) z);
            eagle.getNavigation().moveTo(x, ground + 12 + eagle.getRandom().nextInt(8), z, 1.0D);
        }
    }

    /** The stoop: a dive onto something small that has been left on its own. */
    private static class StoopGoal extends Goal {
        private final CrownedEagle eagle;
        @Nullable
        private LivingEntity prey;
        private int ticks;

        StoopGoal(CrownedEagle eagle) {
            this.eagle = eagle;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (eagle.tickCount % 20 != 0) {
                return false;
            }
            prey = eagle.findPrey();
            return prey != null;
        }

        @Override
        public boolean canContinueToUse() {
            return prey != null && prey.isAlive() && ticks < 300 && eagle.isSnatchable(prey);
        }

        @Override
        public void start() {
            ticks = 0;
            eagle.setTarget(prey);
            eagle.playSound(SoundEvents.PHANTOM_SWOOP, 1.4F, 1.5F);
        }

        @Override
        public void stop() {
            prey = null;
            eagle.setTarget(null);
            eagle.getNavigation().stop();
        }

        @Override
        public void tick() {
            ticks++;
            if (prey == null) {
                return;
            }
            eagle.getLookControl().setLookAt(prey, 30.0F, 30.0F);
            eagle.getNavigation().moveTo(prey, 1.4D);
            if (eagle.distanceToSqr(prey) < 2.5D) {
                eagle.doHurtTarget(prey);
                ticks += 40;
                // Away, before the band closes in.
                eagle.getNavigation().moveTo(eagle.getX(), eagle.getY() + 8.0D, eagle.getZ(), 1.5D);
            }
            // Anything grown coming over is enough to send it up.
            for (Mob mob : eagle.level().getEntitiesOfClass(Mob.class, eagle.getBoundingBox().inflate(BREAK_OFF_RANGE))) {
                if (mob instanceof BandMember member && !member.isBaby()) {
                    ticks = 300;
                    return;
                }
            }
        }
    }
}
