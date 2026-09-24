package dev.hominin.evolution.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rusingoryx: a wildebeest-sized antelope with a hollow dome swelling over its snout - a chamber that
 * made its calls carry, the way a hadrosaur's crest did. It never fights. Struck, it honks, the whole
 * herd honks and goes, and it runs further than anything else out there: the persistence hunter's
 * animal, and a whole herd of them was once brought down together at the edge of a lake.
 */
public class Rusingoryx extends Megafauna {
    public Rusingoryx(EntityType<? extends Rusingoryx> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected float breaksAt() {
        return 0.0F;
    }

    @Override
    protected boolean thickHide() {
        return false;
    }

    @Override
    protected int windUpTicks() {
        return 0;
    }

    @Override
    protected SoundEvent warningSound() {
        return SoundEvents.DONKEY_ANGRY;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && isAlive()) {
            // The honk that carries: the herd hears it and goes.
            playSound(SoundEvents.DONKEY_ANGRY, 2.2F, 0.55F);
        }
        return hurt;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.DONKEY_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DONKEY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.DONKEY_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.HORSE_STEP, 0.25F, 1.0F);
    }

    /** Deep and nasal: the dome is a resonating chamber. */
    @Override
    public float getVoicePitch() {
        return 0.5F + random.nextFloat() * 0.1F;
    }
}
