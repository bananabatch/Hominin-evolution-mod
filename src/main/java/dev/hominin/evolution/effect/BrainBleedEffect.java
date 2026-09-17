package dev.hominin.evolution.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * A slow bleed inside the skull. Unlike poison it does not stop short of killing:
 * an animal that takes one and is left alone will die of it, which is the whole
 * point - a hominin with a stick cannot win a straight fight, but it can land
 * enough blows to walk away and collect the carcass later.
 */
public class BrainBleedEffect extends MobEffect {
    /** Ticks between damage instances, halving with each amplifier level. */
    private static final int BASE_INTERVAL = 40;

    private static final float DAMAGE_PER_TICK = 1.0F;

    public BrainBleedEffect() {
        super(MobEffectCategory.HARMFUL, 0x6E1B1B);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(10, BASE_INTERVAL >> amplifier);
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Bypasses armour deliberately - the injury is already inside the skull.
        entity.hurt(entity.damageSources().magic(), DAMAGE_PER_TICK);
        return true;
    }
}
