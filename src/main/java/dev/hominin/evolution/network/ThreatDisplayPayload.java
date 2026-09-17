package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.combat.ThreatDisplay;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent for an empty-handed threat display. This needs a packet at all because
 * vanilla never tells the server about an empty-handed right-click on air -
 * {@code Minecraft#startUseItem} drops it unless the hand holds something.
 */
public record ThreatDisplayPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ThreatDisplayPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "threat_display"));

    public static final StreamCodec<ByteBuf, ThreatDisplayPayload> STREAM_CODEC =
            StreamCodec.unit(new ThreatDisplayPayload());

    public static void handle(ThreatDisplayPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ThreatDisplay.scream(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
