package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Something done on the mental map: open it, remember where you stand (with a name, if you gave one),
 * forget a memory, follow a marker, stop following, or ask the band what they remember.
 */
public record MapActionPayload(int action, int index, String text) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MapActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "map_action"));

    public static final int OPEN = 0;
    public static final int REMEMBER = 1;
    public static final int FORGET = 2;
    public static final int LEAD = 3;
    public static final int STOP = 4;
    public static final int ASK = 5;
    public static final int PACK_UP = 6;
    public static final int SETTLE = 7;

    public static final StreamCodec<ByteBuf, MapActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MapActionPayload::action,
            ByteBufCodecs.VAR_INT, MapActionPayload::index,
            ByteBufCodecs.STRING_UTF8, MapActionPayload::text,
            MapActionPayload::new);

    public static void handle(MapActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                dev.hominin.evolution.mind.MentalMap.act(player, payload.action(), payload.index(),
                        payload.text().length() > 40 ? payload.text().substring(0, 40) : payload.text());
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
