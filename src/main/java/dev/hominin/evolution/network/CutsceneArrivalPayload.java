package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent once the player has actually been moved, while the screen is still dark, so the
 * cutscene can say how far. Zero means no safe place was found and they stayed put.
 */
public record CutsceneArrivalPayload(int blocks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CutsceneArrivalPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "cutscene_arrival"));

    public static final StreamCodec<ByteBuf, CutsceneArrivalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CutsceneArrivalPayload::blocks,
            CutsceneArrivalPayload::new);

    public static void handle(CutsceneArrivalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.EvolutionCutscene.arrived(payload.blocks()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
