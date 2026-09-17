package dev.hominin.evolution.world.feature;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own worldgen feature types, used by the configured features under data/. */
public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, HomininEvolutionMod.MODID);

    public static final DeferredHolder<Feature<?>, TermiteMoundFeature> TERMITE_MOUND =
            FEATURES.register("termite_mound", () -> new TermiteMoundFeature(NoneFeatureConfiguration.CODEC));

    /** Old bones out in the country: rare, and worth finding. */
    public static final java.util.function.Supplier<CarcassFeature> CARCASS =
            FEATURES.register("carcass", () -> new CarcassFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, OutcropFeature> OUTCROP =
            FEATURES.register("outcrop", () -> new OutcropFeature(BlockStateConfiguration.CODEC));

    private ModFeatures() {
    }
}
