package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The band's cohesion and whether you have promised to do better, so the H menu offers only what fits. */
public record CohesionPayload(int cohesion, boolean promised) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CohesionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "cohesion"));

    public static final StreamCodec<ByteBuf, CohesionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CohesionPayload::cohesion,
            ByteBufCodecs.BOOL, CohesionPayload::promised,
            CohesionPayload::new);

    public static void handle(CohesionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            dev.hominin.evolution.client.ClientSync.cohesion = payload.cohesion();
            dev.hominin.evolution.client.ClientSync.promised = payload.promised();
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
