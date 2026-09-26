package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Country told about: map patches for chunks you have never seen, sixteen bytes a chunk, in key order. */
public record RevealPayload(List<Long> chunks, byte[] patches) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RevealPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "reveal"));

    public static final StreamCodec<ByteBuf, RevealPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), RevealPayload::chunks,
            ByteBufCodecs.BYTE_ARRAY, RevealPayload::patches,
            RevealPayload::new);

    public static void handle(RevealPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.ClientMapCache.reveal(payload.chunks(), payload.patches()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
