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

    private ModEffects() {
    }
}
