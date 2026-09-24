package dev.hominin.evolution.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * The marker for food-borne illness. It does nothing itself - {@link dev.hominin.evolution.survival.FoodIllness}
 * runs it - and milk does not wash it out: water and small meals do.
 */
public class FoodIllnessEffect extends MobEffect {
    public FoodIllnessEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B7A2A);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        cures.clear();
    }
}
