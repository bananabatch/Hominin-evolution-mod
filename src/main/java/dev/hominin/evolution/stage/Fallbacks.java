package dev.hominin.evolution.stage;

import java.util.Map;

import javax.annotation.Nullable;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.resources.ResourceLocation;

/**
 * The species a line drops back to when it fails.
 *
 * <p>Extinction is rarely a clean stop. A population collapses and what is left is an
 * older, smaller, more marginal form of the same thing - anamensis behind Australopithecus,
 * rudolfensis beside habilis, ergaster behind erectus. That form is a second chance: less
 * is asked of it, and its road leads back to where the line fell from.
 *
 * <p>Fail again there and there is no third form to fall to.
 */
public final class Fallbacks {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, path);
    }

    /** What each stage falls back to when its last band dies. */
    private static final Map<String, ResourceLocation> FALLBACKS = Map.of(
            "australopithecus", id("australopithecus_anamensis"),
            "homo_habilis", id("homo_rudolfensis"),
            "homo_erectus", id("homo_ergaster"));

    /** Bands a fallback species may lose before the line is finished for good. */
    public static final int BANDS_ON_A_FALLBACK = 2;

    @Nullable
    public static ResourceLocation of(ResourceLocation stage) {
        return FALLBACKS.get(stage.getPath());
    }

    /** Whether this stage is itself a fallback - the last footing before the end. */
    public static boolean isFallback(ResourceLocation stage) {
        return FALLBACKS.containsValue(stage);
    }

    /**
     * The rung Extra Effort adds on the way up: to reach a stage you must first be the
     * species that stands behind it.
     */
    @Nullable
    public static ResourceLocation detourTo(ResourceLocation nextStage) {
        return of(nextStage);
    }

    private Fallbacks() {
    }
}
