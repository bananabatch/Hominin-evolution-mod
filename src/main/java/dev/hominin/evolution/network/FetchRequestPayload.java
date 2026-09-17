package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.band.FetchKind;
import dev.hominin.evolution.band.Social;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** "Get me ..." - to one member by entity id, or with -1 to whoever in the band is best placed. */
public record FetchRequestPayload(int entityId, int kind) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FetchRequestPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "fetch_request"));

    public static final StreamCodec<ByteBuf, FetchRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FetchRequestPayload::entityId,
            ByteBufCodecs.VAR_INT, FetchRequestPayload::kind,
            FetchRequestPayload::new);

    public static void handle(FetchRequestPayload payload, IPayloadContext context) {
        FetchKind kind = FetchKind.byId(payload.kind());
        if (kind != null && context.player() instanceof ServerPlayer player) {
            Social.requestFetch(player, payload.entityId(), kind);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
