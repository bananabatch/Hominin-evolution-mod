package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * An empty-handed right-click at water. Vanilla's own pick ignores fluids, so the click
 * lands on nothing and the server never hears about it - this reports it.
 */
public record DrinkPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DrinkPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "drink"));

    public static final StreamCodec<ByteBuf, DrinkPayload> STREAM_CODEC = StreamCodec.unit(new DrinkPayload());

    public static void handle(DrinkPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            dev.hominin.evolution.survival.Drinking.tryDrink(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
