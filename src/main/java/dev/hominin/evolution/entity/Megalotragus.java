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
 * Megalotragus: a hartebeest the size of a buffalo, with horns as long as a man's arm swept back
 * from a raised base. It lived beside Homo for two million years. It is quick to run, but struck
 * while strong it snorts, drops its head, and hooks with the horns - a short, fast lunge. Its hide
 * turns a pointed stick; bring a fire-hardened spear or a hand axe.
 */
public class Megalotragus extends Megafauna {
    public Megalotragus(EntityType<? extends Megalotragus> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.26D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.ARMOR, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected float breaksAt() {
        return 0.5F;
    }

    @Override
    protected boolean thickHide() {
        return true;
    }

    @Override
    protected int windUpTicks() {
        return 16;
    }

    @Override
    protected float unwarnedChance() {
        return 0.3F;
    }

    @Override
    protected SoundEvent warningSound() {
        return SoundEvents.COW_HURT;
    }

    @Override
    protected float warningPitch() {
        return 0.55F;
    }

    @Override
    protected double chargeSpeed() {
        return 1.8D;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity hooked && random.nextFloat() < 0.4F) {
            dev.hominin.evolution.combat.Bleeding.inflict(hooked, dev.hominin.evolution.combat.Bleeding.Tier.EXTERNAL);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COW_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.COW_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COW_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.HORSE_STEP, 0.3F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + random.nextFloat() * 0.1F;
    }
}
