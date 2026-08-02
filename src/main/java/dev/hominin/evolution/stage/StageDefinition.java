package dev.hominin.evolution.stage;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record StageDefinition(String displayName, GateDefinition gate, MilestoneDefinition milestone,
        Optional<ResourceLocation> nextStage, List<String> behaviorsGained, List<String> behaviorsLost) {
    public static final Codec<StageDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(StageDefinition::displayName),
            GateDefinition.CODEC.fieldOf("gate").forGetter(StageDefinition::gate),
            MilestoneDefinition.CODEC.fieldOf("milestone").forGetter(StageDefinition::milestone),
            ResourceLocation.CODEC.optionalFieldOf("next_stage").forGetter(StageDefinition::nextStage),
            Codec.STRING.listOf().optionalFieldOf("behaviors_gained", List.of()).forGetter(StageDefinition::behaviorsGained),
            Codec.STRING.listOf().optionalFieldOf("behaviors_lost", List.of()).forGetter(StageDefinition::behaviorsLost)
    ).apply(instance, StageDefinition::new));
}
