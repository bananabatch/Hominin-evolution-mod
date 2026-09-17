package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.event.EvolutionEventHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent once the work key has been held long enough with a stick in hand. Timed on
 * the client so the count can be drawn as it runs; the server re-checks the stick.
 */
public record SharpenStickPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SharpenStickPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "sharpen_stick"));

    public static final StreamCodec<ByteBuf, SharpenStickPayload> STREAM_CODEC =
            StreamCodec.unit(new SharpenStickPayload());

    public static void handle(SharpenStickPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            EvolutionEventHandler.sharpenHeldStick(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
