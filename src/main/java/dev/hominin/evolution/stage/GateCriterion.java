package dev.hominin.evolution.stage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GateCriterion(String id, String description, int requiredCount) {
    public static final Codec<GateCriterion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(GateCriterion::id),
            Codec.STRING.fieldOf("description").forGetter(GateCriterion::description),
            Codec.INT.optionalFieldOf("count", 1).forGetter(GateCriterion::requiredCount)
    ).apply(instance, GateCriterion::new));
}
