package dev.hominin.evolution.stage;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * The marginal kinds stand beside the ones they are named with. Wherever the game asks "is this an early hominin, a
 * habilis, an erectus?" - how much it can carry, how it climbs, whether it throws or heaves, what predators make of
 * it - a fallback answers as the kind beside it: anamensis as Australopithecus afarensis, rudolfensis as habilis,
 * ergaster as erectus. What its hands can and cannot make is a different matter: see
 * {@link dev.hominin.evolution.band.Species}.
 */
public final class Kinds {
    /** The main-line kind this one stands beside - itself, if it is not a fallback. */
    public static String line(String path) {
        return switch (path) {
            case "australopithecus_anamensis" -> "australopithecus";
            case "homo_rudolfensis" -> "homo_habilis";
            case "homo_ergaster" -> "homo_erectus";
            default -> path;
        };
    }

    public static String line(@Nullable ResourceLocation stage) {
        return stage == null ? "" : line(stage.getPath());
    }

    @Nullable
    public static ResourceLocation lineOf(@Nullable ResourceLocation stage) {
        return stage == null ? null : ResourceLocation.fromNamespaceAndPath(stage.getNamespace(), line(stage.getPath()));
    }

    private Kinds() {
    }
}
