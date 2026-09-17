package dev.hominin.evolution.stage;

import java.util.List;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What a stage asks before it lets you go.
 *
 * <p>{@code required} is every hard requirement - all of them must be met, and one
 * of them is always time spent at the stage, so evolution cannot be sprinted. The
 * optional pool is where the player gets to choose how they earned the rest.
 */
public record GateDefinition(List<GateCriterion> required, List<GateCriterion> optionalPool, int chooseCount) {
    /** A single object is still accepted, so older stage files keep loading. */
    private static final Codec<List<GateCriterion>> ONE_OR_MANY = Codec.either(
                    GateCriterion.CODEC.listOf(), GateCriterion.CODEC)
            .xmap(either -> either.map(list -> list, List::of), Either::left);

    public static final Codec<GateDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ONE_OR_MANY.fieldOf("required").forGetter(GateDefinition::required),
            GateCriterion.CODEC.listOf().fieldOf("optional_pool").forGetter(GateDefinition::optionalPool),
            Codec.INT.fieldOf("choose").forGetter(GateDefinition::chooseCount)
    ).apply(instance, GateDefinition::new));
}
