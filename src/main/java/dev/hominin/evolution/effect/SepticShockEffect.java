package dev.hominin.evolution.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/** Poison in the blood: death at the end of it. See survival.Diseases. Milk does nothing for it. */
public class SepticShockEffect extends MobEffect {
    public SepticShockEffect() {
        super(MobEffectCategory.HARMFUL, 0x6A1010);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        cures.clear();
    }
}
