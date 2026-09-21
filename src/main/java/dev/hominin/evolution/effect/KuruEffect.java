package dev.hominin.evolution.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/**
 * The marker for kuru. It does nothing itself - {@link dev.hominin.evolution.survival.Kuru}
 * runs the disease - and nothing cures it, milk included.
 */
public class KuruEffect extends MobEffect {
    public KuruEffect() {
        super(MobEffectCategory.HARMFUL, 0x5A3E4A);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        cures.clear();
    }
}
