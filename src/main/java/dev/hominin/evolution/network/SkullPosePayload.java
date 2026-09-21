package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The skull vow: flash the picture and the words. */
public record SkullPosePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkullPosePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "skull_pose"));

    public static final StreamCodec<ByteBuf, SkullPosePayload> STREAM_CODEC = StreamCodec.unit(new SkullPosePayload());

    public static void handle(SkullPosePayload payload, IPayloadContext context) {
        context.enqueueWork(dev.hominin.evolution.client.SkullPoseFlash::start);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
