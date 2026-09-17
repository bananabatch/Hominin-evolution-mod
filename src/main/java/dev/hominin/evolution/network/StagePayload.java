package dev.hominin.evolution.network;

import java.util.UUID;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Which stage a player is at, for every client that can see them - the model a player
 * is drawn with depends on it. Keyed by UUID rather than entity id so it survives the
 * player respawning or changing dimension.
 */
public record StagePayload(UUID player, ResourceLocation stage) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StagePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "stage"));

    public static final StreamCodec<ByteBuf, StagePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, StagePayload::player,
            ResourceLocation.STREAM_CODEC, StagePayload::stage,
            StagePayload::new);

    public static void handle(StagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            dev.hominin.evolution.inventory.InventoryLimits.rememberClientStage(payload.player(), payload.stage());
            dev.hominin.evolution.client.ClientSync.stage(payload.player(), payload.stage());
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
