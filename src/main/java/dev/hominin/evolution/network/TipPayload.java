package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A tip, for the player's screen: a toast, and a chat line that opens the guide at {@code entry}
 * (empty for none) and {@code page} when double-clicked.
 */
public record TipPayload(String title, String text, String entry, int page, boolean urgent) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TipPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "tip"));

    public static final StreamCodec<ByteBuf, TipPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TipPayload::title,
            ByteBufCodecs.STRING_UTF8, TipPayload::text,
            ByteBufCodecs.STRING_UTF8, TipPayload::entry,
            ByteBufCodecs.VAR_INT, TipPayload::page,
            ByteBufCodecs.BOOL, TipPayload::urgent,
            TipPayload::new);

    public static void handle(TipPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.TipDisplay.show(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
