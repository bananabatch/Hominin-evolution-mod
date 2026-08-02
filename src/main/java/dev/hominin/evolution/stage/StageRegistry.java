package dev.hominin.evolution.stage;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

public final class StageRegistry {
    private static Map<ResourceLocation, StageDefinition> stages = Map.of();

    private StageRegistry() {
    }

    static void setStages(Map<ResourceLocation, StageDefinition> newStages) {
        stages = newStages;
    }

    @Nullable
    public static StageDefinition get(ResourceLocation id) {
        return stages.get(id);
    }

    public static Map<ResourceLocation, StageDefinition> all() {
        return stages;
    }
}
