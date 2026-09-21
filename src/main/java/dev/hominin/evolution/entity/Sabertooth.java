package dev.hominin.evolution.entity;

import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.band.BandMember;
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
 * A saber-toothed cat - Megantereon, in the country these hominins lived in.
 *
 * <p>It minds its own business. It lies about, wanders, and ignores anything at a
 * sensible distance. Come within a few strides and it attacks, and it does not scare:
 * no display, no noise, nothing but a tree or a fight will save you.
 */
public class Sabertooth extends PathfinderMob {
    /** How close is too close. */
    private static final double TRIGGER_DISTANCE = 7.0D;
    /** Baboons are hunted deliberately, from further off. */
    private static final double BABOON_HUNT_DISTANCE = 16.0D;

    public Sabertooth(EntityType<? extends Sabertooth> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PredatorAttackGoal(this, 1.4D, 50, 20));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.6D, 0.002F));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 5, true, false,
                this::isTooClose));
    }

    private boolean isTooClose(LivingEntity entity) {
        // A baboon is worth crossing open ground for; everything else has to come to it.
        if (entity instanceof Baboon) {
            return entity.distanceToSqr(this) < BABOON_HUNT_DISTANCE * BABOON_HUNT_DISTANCE;
        }
        if (dev.hominin.evolution.hunt.Predation.isGame(entity)) {
            return entity.distanceToSqr(this) < BABOON_HUNT_DISTANCE * BABOON_HUNT_DISTANCE;
        }
        boolean prey = (entity instanceof Player player && !player.isCreative() && !player.isSpectator()
                && dev.hominin.evolution.hunt.Predation.looksWorthAttacking(player))
                || entity instanceof BandMember;
        return prey && entity.distanceToSqr(this) < TRIGGER_DISTANCE * TRIGGER_DISTANCE;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            playSound(ModSounds.SABERTOOTH_ROAR.get(), 1.4F, 1.0F);
        }
        // Those teeth are built for opening a throat. Usually it is a deep wound;
        // rarely it is the last one, and rarely is deliberate - being killed outright
        // by bad luck is only interesting if it almost never happens.
        if (hit && target instanceof LivingEntity bitten) {
            dev.hominin.evolution.combat.Bleeding.inflict(bitten,
                    getRandom().nextFloat() < 0.05F
                            ? dev.hominin.evolution.combat.Bleeding.Tier.CATASTROPHIC
                            : dev.hominin.evolution.combat.Bleeding.Tier.INTERNAL);
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
