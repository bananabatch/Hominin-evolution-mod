package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.mind.Thinking;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent once the think key has been held long enough. The hold is timed on the
 * client so it can draw the count down; the server still decides whether any of
 * it was allowed.
 */
public record ThinkPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ThinkPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "think"));

    public static final StreamCodec<ByteBuf, ThinkPayload> STREAM_CODEC =
            StreamCodec.unit(new ThinkPayload());

    public static void handle(ThinkPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Thinking.think(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
