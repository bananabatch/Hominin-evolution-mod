package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Something done at a pile's menu: open it, take one thing or everything, or change what one of yours is for. */
public record PileActionPayload(BlockPos pos, int action, int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PileActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "pile_action"));

    public static final StreamCodec<ByteBuf, PileActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PileActionPayload::pos,
            ByteBufCodecs.VAR_INT, PileActionPayload::action,
            ByteBufCodecs.VAR_INT, PileActionPayload::slot,
            PileActionPayload::new);

    public static void handle(PileActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> dev.hominin.evolution.band.PileMenu.handle(player, payload.pos(), payload.action(),
                    payload.slot()));
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
