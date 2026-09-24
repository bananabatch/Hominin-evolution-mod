package dev.hominin.evolution.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mammuthus subplanifrons, the first mammoth: still an African animal, and as big as any elephant
 * alive. Small family groups keep to the water. It will not start anything - but strike one while it
 * is strong and it lifts its head and trunk, trumpets, and comes for you, and a blow from it throws a
 * grown hominin a long way. Only a fire-hardened spear or a hand axe gets through the hide.
 */
public class Mammuthus extends Megafauna {
    public Mammuthus(EntityType<? extends Mammuthus> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 160.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 2.5D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.STEP_HEIGHT, 1.1D);
    }

    @Override
    protected float breaksAt() {
        return 0.35F;
    }

    @Override
    protected boolean thickHide() {
        return true;
    }

    @Override
    protected int windUpTicks() {
        return 28;
    }

    @Override
    protected SoundEvent warningSound() {
        return SoundEvents.RAVAGER_ROAR;
    }

    @Override
    protected float warningPitch() {
        return 1.5F;
    }

    @Override
    protected double chargeSpeed() {
        return 1.5D;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity struck && random.nextFloat() < 0.35F) {
            // A tusk, driven in.
            dev.hominin.evolution.combat.Bleeding.inflict(struck, dev.hominin.evolution.combat.Bleeding.Tier.INTERNAL);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.7F);
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + random.nextFloat() * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }
}
