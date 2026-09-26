package dev.hominin.evolution.network;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The members you are keeping an eye on, two lines each, for the corner of the screen. */
public record TrackPayload(List<String> lines) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TrackPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "track"));

    public static final StreamCodec<ByteBuf, TrackPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TrackPayload::lines,
            TrackPayload::new);

    public static void handle(TrackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.TrackHud.set(payload.lines()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
