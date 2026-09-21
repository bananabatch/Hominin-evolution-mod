package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.band.Social;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Turning to the next member of the band to see what they are carrying. */
public record ViewInventoryPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ViewInventoryPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "view_inventory"));

    public static final StreamCodec<ByteBuf, ViewInventoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ViewInventoryPayload::entityId,
            ViewInventoryPayload::new);

    public static void handle(ViewInventoryPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Social.viewInventory(player, payload.entityId());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
