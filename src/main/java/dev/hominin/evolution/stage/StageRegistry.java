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

    /**
     * The stage a player is working through: their stage - or, where the line splits, the file for the path they
     * chose ({@code homo_heidelbergensis/sapiens}), whose goals are that path's own.
     */
    @Nullable
    public static StageDefinition current(dev.hominin.evolution.data.PlayerEvolutionData data) {
        ResourceLocation stage = data.getStage();
        String suffix = Lineage.suffix(Lineage.of(data));
        if (suffix != null) {
            StageDefinition path = stages.get(stage.withPath(stage.getPath() + "/" + suffix));
            if (path != null) {
                return path;
            }
        }
        return stages.get(stage);
    }

    public static Map<ResourceLocation, StageDefinition> all() {
        return stages;
    }
}
