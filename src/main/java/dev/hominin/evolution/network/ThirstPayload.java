package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** How much water the player has left, for the bar above the hunger bar. */
public record ThirstPayload(int thirst) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ThirstPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "thirst"));

    public static final StreamCodec<ByteBuf, ThirstPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ThirstPayload::thirst,
            ThirstPayload::new);

    public static void handle(ThirstPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.ThirstOverlay.accept(payload.thirst()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
