package dev.hominin.evolution.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/** Rot straight into an open wound. See survival.Diseases. Milk does nothing for it. */
public class ToxicShockEffect extends MobEffect {
    public ToxicShockEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A7A20);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        cures.clear();
    }
}
