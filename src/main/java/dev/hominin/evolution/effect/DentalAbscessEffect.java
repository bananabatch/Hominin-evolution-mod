package dev.hominin.evolution.effect;

import java.util.Set;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/** A tooth gone bad from living on roots. See survival.Diseases. Milk does nothing for it. */
public class DentalAbscessEffect extends MobEffect {
    public DentalAbscessEffect() {
        super(MobEffectCategory.HARMFUL, 0xC89060);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
        cures.clear();
    }
}
