package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Whether this player is in developer mode, so the H menu knows to show the Developer tab. */
public record DevFlagPayload(boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DevFlagPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "dev_flag"));

    public static final StreamCodec<ByteBuf, DevFlagPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DevFlagPayload::enabled, DevFlagPayload::new);

    public static void handle(DevFlagPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.ClientSync.devMode = payload.enabled());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
