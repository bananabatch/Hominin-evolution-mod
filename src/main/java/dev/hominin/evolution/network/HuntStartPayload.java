package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The hunt, set: which animal, how many go, what they carry, and - for megafauna - which group you are with. */
public record HuntStartPayload(int entityId, int members, int weapon, int team, int style)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HuntStartPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "hunt_start"));

    public static final StreamCodec<ByteBuf, HuntStartPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HuntStartPayload::entityId,
            ByteBufCodecs.VAR_INT, HuntStartPayload::members,
            ByteBufCodecs.VAR_INT, HuntStartPayload::weapon,
            ByteBufCodecs.VAR_INT, HuntStartPayload::team,
            ByteBufCodecs.VAR_INT, HuntStartPayload::style,
            HuntStartPayload::new);

    public static void handle(HuntStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                dev.hominin.evolution.hunt.HuntParty.start(player, payload.entityId(), payload.members(), payload.weapon(),
                        payload.team(), payload.style());
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
