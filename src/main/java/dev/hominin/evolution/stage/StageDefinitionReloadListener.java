package dev.hominin.evolution.stage;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public class StageDefinitionReloadListener extends SimpleJsonResourceReloadListener {
    public StageDefinitionReloadListener() {
        super(new Gson(), "hominin/stages");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, StageDefinition> stages = new HashMap<>();
        object.forEach((id, json) -> StageDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> HomininEvolutionMod.LOGGER.error("Failed to parse hominin evolution stage {}: {}", id, error))
                .ifPresent(stage -> stages.put(id, stage)));
        StageRegistry.setStages(Map.copyOf(stages));
        HomininEvolutionMod.LOGGER.info("Loaded {} hominin evolution stage definitions", stages.size());
    }
}
