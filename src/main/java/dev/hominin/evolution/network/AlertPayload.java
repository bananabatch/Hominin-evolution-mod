package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Something urgent, for the banner across the top of the screen. {@code kind} is an {@code Alerts.Kind} ordinal. */
public record AlertPayload(int kind, String text) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AlertPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "alert"));

    public static final StreamCodec<ByteBuf, AlertPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AlertPayload::kind,
            ByteBufCodecs.STRING_UTF8, AlertPayload::text,
            AlertPayload::new);

    public static void handle(AlertPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.AlertHud.show(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
