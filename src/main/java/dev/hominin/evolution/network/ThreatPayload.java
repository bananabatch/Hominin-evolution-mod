package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The building threat of the ground you stand on, 1 to 10 - or 0 off anyone's ground - and whose ground it is, for the
 * bar above the hunger bar.
 */
public record ThreatPayload(int threat, String whose) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ThreatPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "threat"));

    public static final StreamCodec<ByteBuf, ThreatPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ThreatPayload::threat,
            ByteBufCodecs.STRING_UTF8, ThreatPayload::whose,
            ThreatPayload::new);

    public static void handle(ThreatPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.ThreatHud.accept(payload.threat(), payload.whose()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
