package dev.hominin.evolution.stage;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * One hominin stage, loaded from {@code data/<ns>/hominin/stages}.
 *
 * @param yearsAgo     roughly when this stage lived, for the "MYA" beside its name and the
 *                     "years later" of the evolution cutscene; 0 leaves both out
 * @param arrivalItems what a descendant carries when they arrive at this stage - evolving
 *                     is generations, not a costume change, so the old kit does not come along
 */
public record StageDefinition(String displayName, GateDefinition gate, MilestoneDefinition milestone,
        Optional<ResourceLocation> nextStage, List<String> behaviorsGained, List<String> behaviorsLost,
        int yearsAgo, List<ItemStack> arrivalItems, String warning) {
    public static final Codec<StageDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("display_name").forGetter(StageDefinition::displayName),
            GateDefinition.CODEC.fieldOf("gate").forGetter(StageDefinition::gate),
            MilestoneDefinition.CODEC.fieldOf("milestone").forGetter(StageDefinition::milestone),
            ResourceLocation.CODEC.optionalFieldOf("next_stage").forGetter(StageDefinition::nextStage),
            Codec.STRING.listOf().optionalFieldOf("behaviors_gained", List.of()).forGetter(StageDefinition::behaviorsGained),
            Codec.STRING.listOf().optionalFieldOf("behaviors_lost", List.of()).forGetter(StageDefinition::behaviorsLost),
            Codec.INT.optionalFieldOf("years_ago", 0).forGetter(StageDefinition::yearsAgo),
            ItemStack.CODEC.listOf().optionalFieldOf("arrival_items", List.of()).forGetter(StageDefinition::arrivalItems),
            Codec.STRING.optionalFieldOf("warning", "").forGetter(StageDefinition::warning)
    ).apply(instance, StageDefinition::new));
}
