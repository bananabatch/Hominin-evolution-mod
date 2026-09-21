package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Narrow the player's view for a moment: something has gone very still and is watching them. */
public record FocusPayload(int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FocusPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "focus"));

    public static final StreamCodec<ByteBuf, FocusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FocusPayload::ticks,
            FocusPayload::new);

    public static void handle(FocusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.FocusCamera.focus(payload.ticks()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
