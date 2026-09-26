package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Starts the evolution cutscene: how much time passed, and what the player has become. With both dates given (years
 * ago, before and after), the years are counted down on screen from one to the other; zero for either, and there is
 * no count - just the words.
 */
public record CutsceneStartPayload(String timePassed, String stage, int fromYearsAgo, int toYearsAgo)
        implements CustomPacketPayload {
    /** The words alone, with no years to count. */
    public CutsceneStartPayload(String timePassed, String stage) {
        this(timePassed, stage, 0, 0);
    }

    public static final CustomPacketPayload.Type<CutsceneStartPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "cutscene_start"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CutsceneStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CutsceneStartPayload::timePassed,
            ByteBufCodecs.STRING_UTF8, CutsceneStartPayload::stage,
            ByteBufCodecs.VAR_INT, CutsceneStartPayload::fromYearsAgo,
            ByteBufCodecs.VAR_INT, CutsceneStartPayload::toYearsAgo,
            CutsceneStartPayload::new);

    public static void handle(CutsceneStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.EvolutionCutscene.start(
                payload.timePassed(), payload.stage(), payload.fromYearsAgo(), payload.toYearsAgo()));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
