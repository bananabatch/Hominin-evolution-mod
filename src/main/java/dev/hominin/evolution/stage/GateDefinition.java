package dev.hominin.evolution.stage;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GateDefinition(GateCriterion required, List<GateCriterion> optionalPool, int chooseCount) {
    public static final Codec<GateDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GateCriterion.CODEC.fieldOf("required").forGetter(GateDefinition::required),
            GateCriterion.CODEC.listOf().fieldOf("optional_pool").forGetter(GateDefinition::optionalPool),
            Codec.INT.fieldOf("choose").forGetter(GateDefinition::chooseCount)
    ).apply(instance, GateDefinition::new));
}
