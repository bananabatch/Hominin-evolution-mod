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

    /** The homeland's mounds: one to a 48-block cell, six cells in ten, and one mound cell in twenty a super colony. */
    public static final DeferredHolder<Feature<?>, TermiteMoundFeature> TERMITE_MOUND =
            FEATURES.register("termite_mound", () -> new TermiteMoundFeature(NoneFeatureConfiguration.CODEC,
                    48, 0.6F, true, 0.05F));

    /** Everywhere else: the odd mound, far apart. */
    public static final DeferredHolder<Feature<?>, TermiteMoundFeature> TERMITE_MOUND_SPARSE =
            FEATURES.register("termite_mound_sparse", () -> new TermiteMoundFeature(NoneFeatureConfiguration.CODEC,
                    112, 0.45F, false, 0.0F));

    /** Old bones out in the country: rare, and worth finding. */
    public static final java.util.function.Supplier<CarcassFeature> CARCASS =
            FEATURES.register("carcass", () -> new CarcassFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, OutcropFeature> OUTCROP =
            FEATURES.register("outcrop", () -> new OutcropFeature(BlockStateConfiguration.CODEC));

    /** Loose basalt for forty blocks round any lava, and a little obsidian at its edge. */
    public static final DeferredHolder<Feature<?>, BasaltScatterFeature> BASALT_SCATTER =
            FEATURES.register("basalt_scatter", () -> new BasaltScatterFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {
    }
}
