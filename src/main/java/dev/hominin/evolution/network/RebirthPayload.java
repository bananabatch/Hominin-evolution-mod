package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The player has respawned as a member of their band: play the eyes-opening cutscene. */
public record RebirthPayload(String name) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RebirthPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "rebirth"));

    public static final StreamCodec<ByteBuf, RebirthPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RebirthPayload::name,
            RebirthPayload::new);

    public static void handle(RebirthPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.RebirthCutscene.start(payload.name()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
