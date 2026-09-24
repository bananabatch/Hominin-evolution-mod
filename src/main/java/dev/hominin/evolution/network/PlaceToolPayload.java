package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.band.ToolPiles;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sneak-use at the ground with a stone tool in hand: lay it down there, if this is your ground. */
public record PlaceToolPayload(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlaceToolPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "place_tool"));

    public static final StreamCodec<ByteBuf, PlaceToolPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PlaceToolPayload::pos, PlaceToolPayload::new);

    public static void handle(PlaceToolPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ToolPiles.placeRequest(player, payload.pos());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
