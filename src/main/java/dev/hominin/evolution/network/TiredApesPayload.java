package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Three displays at nothing in a row. Flash the evidence. */
public record TiredApesPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TiredApesPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "tired_apes"));

    public static final StreamCodec<ByteBuf, TiredApesPayload> STREAM_CODEC = StreamCodec.unit(new TiredApesPayload());

    public static void handle(TiredApesPayload payload, IPayloadContext context) {
        context.enqueueWork(dev.hominin.evolution.client.TiredApesFlash::start);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
