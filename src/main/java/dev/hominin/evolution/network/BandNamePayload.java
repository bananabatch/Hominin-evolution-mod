package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Naming your own band. From the server, a new band has formed and here is a name for it to start
 * from; from the client, the name chosen.
 */
public record BandNamePayload(String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BandNamePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "band_name"));

    public static final StreamCodec<ByteBuf, BandNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BandNamePayload::name,
            BandNamePayload::new);

    public static void handleOnClient(BandNamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.NameBandScreen.prompt(payload.name()));
    }

    public static void handleOnServer(BandNamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                dev.hominin.evolution.band.Relations.nameOwnBand(player, payload.name());
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
