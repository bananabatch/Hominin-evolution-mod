package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Your answer to a band's offer or threat: one of {@link dev.hominin.evolution.band.Claims}'s choices. */
public record EncounterChoicePayload(int choice) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EncounterChoicePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "encounter_choice"));

    public static final StreamCodec<ByteBuf, EncounterChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EncounterChoicePayload::choice, EncounterChoicePayload::new);

    public static void handle(EncounterChoicePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                dev.hominin.evolution.band.Claims.answer(player, payload.choice());
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
