package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.knapping.Knapping;
import dev.hominin.evolution.knapping.KnappingChoice;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What the player decided to aim at in the knapping screen. */
public record KnappingChoicePayload(int choice) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<KnappingChoicePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "knapping_choice"));

    public static final StreamCodec<ByteBuf, KnappingChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KnappingChoicePayload::choice,
            KnappingChoicePayload::new);

    public static void handle(KnappingChoicePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Knapping.resolve(player, KnappingChoice.byId(payload.choice()));
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
