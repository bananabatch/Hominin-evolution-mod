package dev.hominin.evolution.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * An open cut. Each new wound deepens it, and a deep enough one bleeds fast.
 *
 * <p>This is the other half of a sharp edge. A hominin cannot out-fight a large
 * animal, but a cut that keeps bleeding while the animal runs means it only has to
 * keep up, not win - which is the whole logic of persistence hunting.
 */
public class BleedingEffect extends MobEffect {
    /** Ticks between damage at the lightest wound; each level of severity shortens it. */
    private static final int BASE_INTERVAL = 40;
    private static final int INTERVAL_STEP = 10;
    private static final int MIN_INTERVAL = 15;

    private static final float DAMAGE_PER_TICK = 1.0F;

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x9E1B1B);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = Math.max(MIN_INTERVAL, BASE_INTERVAL - INTERVAL_STEP * amplifier);
        return duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Bypasses armour: the wound is already open.
        entity.hurt(entity.damageSources().magic(), DAMAGE_PER_TICK);
        return true;
    }
}
