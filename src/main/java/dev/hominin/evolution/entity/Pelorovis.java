package dev.hominin.evolution.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pelorovis, the "monstrous sheep": a buffalo bigger than any alive, with horns that spanned
 * two metres. It grazed the same grassland as erectus for a million years, and at Olorgesailie
 * a whole herd of its relatives was butchered where they fell - megafauna, brought down by
 * people with hand axes.
 *
 * <p>It does not start fights, but it finishes them. Strike a healthy one and it turns on you,
 * and the herd comes with it. Wear it down past half its strength and it breaks and runs - and
 * then it is a persistence hunt like any other, only with a great deal more at the end of it.
 */
public class Pelorovis extends Animal {
    /** Below this share of its health, it stops fighting and runs. */
    private static final float BREAKS_AT = 0.45F;

    public Pelorovis(EntityType<? extends Pelorovis> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        // Head down and straight at whatever hurt it - while it still has the strength to.
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.45D, true) {
            @Override
            public boolean canUse() {
                return standsGround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return standsGround() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.6D, 0.003F));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        // One is struck, and the herd answers.
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    /** Healthy enough to fight back. A worn-down one runs instead. */
    public boolean standsGround() {
        return getHealth() > getMaxHealth() * BREAKS_AT;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity gored && random.nextFloat() < 0.4F) {
            dev.hominin.evolution.combat.Bleeding.inflict(gored, dev.hominin.evolution.combat.Bleeding.Tier.EXTERNAL);
        }
        return hit;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
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
        playSound(SoundEvents.COW_STEP, 0.3F, 0.7F);
    }

    /** Deeper than a cow, by a good way. */
    @Override
    public float getVoicePitch() {
        return 0.55F + random.nextFloat() * 0.1F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.4F;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }
}
