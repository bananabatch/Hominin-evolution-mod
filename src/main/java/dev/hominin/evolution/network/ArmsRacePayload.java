package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A thrown stone has killed something, and the client should know about it in full screen. */
public record ArmsRacePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArmsRacePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "arms_race"));

    public static final StreamCodec<ByteBuf, ArmsRacePayload> STREAM_CODEC =
            StreamCodec.unit(new ArmsRacePayload());

    public static void handle(ArmsRacePayload payload, IPayloadContext context) {
        context.enqueueWork(dev.hominin.evolution.client.ArmsRaceFlash::start);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
