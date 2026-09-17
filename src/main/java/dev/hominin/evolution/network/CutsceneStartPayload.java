package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Starts the evolution cutscene: how much time passed, and what the player has become. */
public record CutsceneStartPayload(String timePassed, String stage) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CutsceneStartPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "cutscene_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CutsceneStartPayload::timePassed,
            ByteBufCodecs.STRING_UTF8, CutsceneStartPayload::stage,
            CutsceneStartPayload::new);

    public static void handle(CutsceneStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.EvolutionCutscene.start(
                payload.timePassed(), payload.stage()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
