package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Where the player is walking to, for the pointer at the top of the screen - or nowhere. */
public record WaypointPayload(boolean active, int x, int z, String label) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WaypointPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "waypoint"));

    public static final StreamCodec<ByteBuf, WaypointPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, WaypointPayload::active,
            ByteBufCodecs.VAR_INT, WaypointPayload::x,
            ByteBufCodecs.VAR_INT, WaypointPayload::z,
            ByteBufCodecs.STRING_UTF8, WaypointPayload::label,
            WaypointPayload::new);

    public static void handle(WaypointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.hominin.evolution.client.WaypointHud.set(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
