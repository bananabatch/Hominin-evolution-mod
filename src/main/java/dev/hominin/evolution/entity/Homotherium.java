package dev.hominin.evolution.entity;

import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.hunt.Predation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Homotherium, the scimitar cat: long-legged, built for running things down in the open,
 * and awake in the daylight when everything else is resting.
 *
 * <p>It is the answer to the question "why did anything ever leave the trees at night" -
 * it hunts by day, in the open, in pairs, and it does not need to ambush. Against an
 * australopithecine there is no answer but a trunk. Against a habilis band with spears it
 * thinks twice. Against erectus it thinks better of it altogether: by then the primate is
 * the thing with the reach.
 */
public class Homotherium extends PathfinderMob {
    private static final double HUNT_RANGE = 24.0D;

    public Homotherium(EntityType<? extends Homotherium> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 38.0D)
                // Faster over open ground than anything on two legs. Trees are the answer.
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 36.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PredatorAttackGoal(this, 1.3D, 40, 16));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D, 0.004F));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 14.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Hominins first, and it will take a baboon out of a troop as readily - which is
        // exactly why baboons mob anything with teeth.
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                this::isPrey));
    }

    private boolean isPrey(LivingEntity entity) {
        if (entity.distanceToSqr(this) > HUNT_RANGE * HUNT_RANGE) {
            return false;
        }
        if (entity instanceof Baboon) {
            return true;
        }
        if (entity instanceof BandMember) {
            return true;
        }
        // A scimitar cat is built to run down grazing animals in the open. That is the
        // day job; hominins are the exception, not the diet.
        if (Predation.isGame(entity)) {
            return true;
        }
        // A cat this size reads what it is looking at. Erectus with something in its hands
        // is not worth the wound, unless the cat is desperate.
        return entity instanceof Player player && !player.isCreative() && !player.isSpectator()
                && Predation.looksWorthAttacking(player);
    }

    /** Pairs hunt together, so one that finds something calls the other in. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity bitten) {
            dev.hominin.evolution.combat.Bleeding.inflict(bitten,
                    dev.hominin.evolution.combat.Bleeding.Tier.INTERNAL);
        }
        if (hit && target instanceof LivingEntity living) {
            playSound(ModSounds.SABERTOOTH_ROAR.get(), 1.2F, 1.25F);
            for (Homotherium other : level().getEntitiesOfClass(Homotherium.class, getBoundingBox().inflate(24.0D))) {
                if (other != this && other.getTarget() == null) {
                    other.setTarget(living);
                }
            }
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SABERTOOTH_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SABERTOOTH_HURT.get();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }
}
