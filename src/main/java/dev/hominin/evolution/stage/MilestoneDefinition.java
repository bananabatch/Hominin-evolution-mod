package dev.hominin.evolution.stage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record MilestoneDefinition(ResourceLocation type, String description) {
    public static final Codec<MilestoneDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(MilestoneDefinition::type),
            Codec.STRING.fieldOf("description").forGetter(MilestoneDefinition::description)
    ).apply(instance, MilestoneDefinition::new));
}
