package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.combat.ItemInteractions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent when the player presses the interact-with-held-items key. It carries no
 * data - both hands are already known to the server - it just tells the server
 * the key went down, which vanilla never reports for a bare keybind.
 */
public record ItemInteractPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ItemInteractPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "item_interact"));

    public static final StreamCodec<ByteBuf, ItemInteractPayload> STREAM_CODEC =
            StreamCodec.unit(new ItemInteractPayload());

    public static void handle(ItemInteractPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ItemInteractions.interact(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
