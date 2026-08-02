package dev.hominin.evolution.stage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class MilestoneHandlers {
    private static final Map<ResourceLocation, BiConsumer<ServerPlayer, StageDefinition>> HANDLERS = new HashMap<>();

    private MilestoneHandlers() {
    }

    public static void register(ResourceLocation type, BiConsumer<ServerPlayer, StageDefinition> handler) {
        HANDLERS.put(type, handler);
    }

    public static Optional<BiConsumer<ServerPlayer, StageDefinition>> get(ResourceLocation type) {
        return Optional.ofNullable(HANDLERS.get(type));
    }
}
