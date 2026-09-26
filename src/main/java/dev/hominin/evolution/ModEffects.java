package dev.hominin.evolution;

import dev.hominin.evolution.effect.BleedingEffect;
import dev.hominin.evolution.effect.BrainBleedEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, HomininEvolutionMod.MODID);

    /** An open cut from a sharp edge. Deepens with each new wound. */
    public static final Holder<MobEffect> BLEEDING =
            MOB_EFFECTS.register("bleeding", BleedingEffect::new);

    /** A bleed inside the skull: slow, unstoppable, and fatal if left alone. */
    public static final Holder<MobEffect> BRAIN_BLEED =
            MOB_EFFECTS.register("brain_bleed", BrainBleedEffect::new);

    /** Prion disease from eating a brain. Incurable; see survival.Kuru. */
    public static final Holder<MobEffect> KURU =
            MOB_EFFECTS.register("kuru", dev.hominin.evolution.effect.KuruEffect::new);

    /** Sick from meat that had turned. See survival.FoodIllness. */
    public static final Holder<MobEffect> FOOD_ILLNESS =
            MOB_EFFECTS.register("food_illness", dev.hominin.evolution.effect.FoodIllnessEffect::new);

    /** Fed past wanting, after a feast: hunger falls at a quarter of its pace. See band.Feast. */
    public static final Holder<MobEffect> FULL =
            MOB_EFFECTS.register("full", dev.hominin.evolution.effect.FullEffect::new);

    /** A tooth gone bad from a diet of roots. Two days, and it goes into the blood. */
    public static final Holder<MobEffect> DENTAL_ABSCESS =
            MOB_EFFECTS.register("dental_abscess", dev.hominin.evolution.effect.DentalAbscessEffect::new);

    /** Poison in the blood. A minute, and then death. */
    public static final Holder<MobEffect> SEPTIC_SHOCK =
            MOB_EFFECTS.register("septic_shock", dev.hominin.evolution.effect.SepticShockEffect::new);

    /** Spoiled meat eaten with a wound torn open. Comes with septic shock. */
    public static final Holder<MobEffect> TOXIC_SHOCK =
            MOB_EFFECTS.register("toxic_shock", dev.hominin.evolution.effect.ToxicShockEffect::new);

    private ModEffects() {
    }
}
