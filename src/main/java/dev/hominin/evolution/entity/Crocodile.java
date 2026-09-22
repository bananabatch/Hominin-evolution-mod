package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.combat.Bleeding;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Crocodylus anthropophagus: the reason nobody lingers at the water.
 *
 * <p>It lies still in the shallows and waits. Anything that comes down to drink within
 * reach gets one lunge - fast, and from almost nothing - and if the jaws close it rolls,
 * dragging whatever it has toward deep water and chewing as it goes. It does not chase
 * anything far up the bank; a miss, and it slides back in and waits again.
 *
 * <p>What it has, it keeps until it is hurt enough to let go: hit it hard, or have
 * somebody else hit it for you. Nothing frightens it.
 */
public class Crocodile extends PathfinderMob {
    /** How far it will lunge from, measured from the water's edge. */
    private static final double STRIKE_RANGE = 7.0D;
    /** How long a roll lasts before it gives up on something that will not drown. */
    private static final int HOLD_TICKS = 100;
    /** Damage in one blow that makes it let go. */
    private static final float RELEASE_DAMAGE = 3.0F;

    @Nullable
    private LivingEntity held;
    private int holdTicks;
    private int restTicks;

    public Crocodile(EntityType<? extends Crocodile> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.WATER, 0.0F);
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.04F, 0.45F, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance() && isInWater()) {
            moveRelative(getSpeed(), travelVector);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9D));
        } else {
            super.travel(travelVector);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LungeGoal());
        goalSelector.addGoal(2, new ReturnToWaterGoal());
        goalSelector.addGoal(3, new LurkGoal());
    }

    public boolean isHolding() {
        return held != null;
    }

    // ------------------------------------------------------------ the water

    private static boolean isWater(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    /** Standing in it, or with water within a couple of blocks: close enough to strike. */
    private static boolean nearWater(LivingEntity entity) {
        if (entity.isInWater()) {
            return true;
        }
        BlockPos at = entity.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 0, 2))) {
            if (isWater(entity.level(), pos)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private BlockPos deepWaterNear(int radius) {
        BlockPos at = blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-radius, -3, -radius), at.offset(radius, 2, radius))) {
            if (isWater(level(), pos) && isWater(level(), pos.below())) {
                double distance = pos.distSqr(at);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------ the roll

    private boolean prey(LivingEntity entity) {
        if (entity == this || !entity.isAlive() || entity instanceof Crocodile) {
            return false;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return entity instanceof Player || entity instanceof Animal
                || entity instanceof dev.hominin.evolution.band.BandMember
                || entity instanceof Baboon || entity instanceof Chimpanzee || entity instanceof Bonobo;
    }

    private void grab(LivingEntity target) {
        held = target;
        holdTicks = 0;
        getNavigation().stop();
        playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 0.6F);
        if (target instanceof Player player) {
            player.displayClientMessage(Component.literal("The crocodile has you! Hit it, hard, to break free.")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
        Bleeding.inflict(target, Bleeding.Tier.EXTERNAL);
    }

    private void release(int rest) {
        if (held instanceof Player player && held.isAlive()) {
            player.displayClientMessage(Component.literal("It lets go.").withStyle(ChatFormatting.GOLD), true);
        }
        held = null;
        holdTicks = 0;
        restTicks = rest;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (restTicks > 0) {
            restTicks--;
        }
        if (held == null) {
            return;
        }
        if (!held.isAlive() || held.distanceTo(this) > 6.0F || ++holdTicks > HOLD_TICKS) {
            release(held.isAlive() ? 200 : 600);
            return;
        }
        // Jaws shut on it: it goes where the crocodile goes, and the crocodile goes deeper.
        Vec3 mouth = position().add(getLookAngle().scale(1.6D));
        Vec3 pull = mouth.subtract(held.position()).scale(0.35D);
        held.setDeltaMovement(pull.x, Math.min(pull.y, 0.0D) - (held.isInWater() ? 0.04D : 0.0D), pull.z);
        held.hurtMarked = true;
        held.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 4, false, false));
        if (holdTicks % 10 == 1 && getNavigation().isDone()) {
            BlockPos deep = deepWaterNear(10);
            if (deep != null) {
                getNavigation().moveTo(deep.getX() + 0.5D, deep.getY(), deep.getZ() + 0.5D, 0.8D);
            }
        }
        // The roll: over and over, and every turn tears.
        setYRot(getYRot() + 35.0F);
        yBodyRot = getYRot();
        if (holdTicks % 20 == 0) {
            doHurtTarget(held);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.4D, getZ(), 12, 0.6D, 0.2D, 0.6D, 0.1D);
            }
            playSound(SoundEvents.GENERIC_SPLASH, 1.0F, 0.7F);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && held != null && amount >= RELEASE_DAMAGE) {
            release(300);
        }
        // Hurt on land by something it did not get hold of: back into the water.
        if (hurt && !level().isClientSide() && held == null && !isInWater()) {
            restTicks = Math.max(restTicks, 200);
        }
        return hurt;
    }

    // ------------------------------------------------------------ goals

    /** One lunge from the water at anything that comes close enough to drink. */
    private class LungeGoal extends Goal {
        @Nullable
        private LivingEntity quarry;
        private int ticks;

        LungeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (held != null || restTicks > 0 || !nearWater(Crocodile.this) || getRandom().nextInt(4) != 0) {
                return false;
            }
            List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(STRIKE_RANGE, 3.0D, STRIKE_RANGE),
                    e -> prey(e) && nearWater(e) && hasLineOfSight(e));
            LivingEntity best = null;
            for (LivingEntity candidate : near) {
                if (best == null || candidate.distanceToSqr(Crocodile.this) < best.distanceToSqr(Crocodile.this)) {
                    best = candidate;
                }
            }
            quarry = best;
            return quarry != null;
        }

        @Override
        public boolean canContinueToUse() {
            return quarry != null && quarry.isAlive() && held == null && ticks < 50 && distanceTo(quarry) < 12.0F;
        }

        @Override
        public void start() {
            ticks = 0;
            playSound(SoundEvents.GENERIC_SPLASH, 1.2F, 0.6F);
        }

        @Override
        public void stop() {
            if (held == null) {
                // Missed. Back under, and wait for the next one.
                restTicks = Math.max(restTicks, 160);
            }
            quarry = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (quarry == null) {
                return;
            }
            ticks++;
            getLookControl().setLookAt(quarry, 30.0F, 30.0F);
            if (ticks % 5 == 1) {
                getNavigation().moveTo(quarry, 2.6D);
            }
            if (distanceTo(quarry) < 2.2F && doHurtTarget(quarry)) {
                grab(quarry);
                quarry = null;
            }
        }
    }

    /** Out on the bank with nothing in its jaws: it goes back where it belongs. */
    private class ReturnToWaterGoal extends Goal {
        ReturnToWaterGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (held != null || isInWater()) {
                return false;
            }
            BlockPos water = deepWaterNear(12);
            return water != null && getNavigation().moveTo(water.getX() + 0.5D, water.getY(), water.getZ() + 0.5D, 1.0D);
        }

        @Override
        public boolean canContinueToUse() {
            return held == null && !isInWater() && !getNavigation().isDone();
        }
    }

    /** Still, mostly. Now and then it drifts a little way along the bank. */
    private class LurkGoal extends Goal {
        LurkGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (held != null || !isInWater() || getRandom().nextInt(400) != 0) {
                return false;
            }
            BlockPos at = blockPosition();
            BlockPos spot = at.offset(getRandom().nextInt(13) - 6, 0, getRandom().nextInt(13) - 6);
            return isWater(level(), spot) && getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.6D);
        }

        @Override
        public boolean canContinueToUse() {
            return held == null && !getNavigation().isDone();
        }
    }

    // ------------------------------------------------------------ the rest

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playSound(SoundEvents.EVOKER_FANGS_ATTACK, 0.8F, 0.7F);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return getRandom().nextInt(3) == 0 ? SoundEvents.RAVAGER_AMBIENT : null;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TURTLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TURTLE_DEATH;
    }

    @Override
    public int getMaxHeadYRot() {
        return 20;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return distanceToClosestPlayer > 128.0D * 128.0D;
    }
}
