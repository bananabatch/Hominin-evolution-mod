package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Something done about another band from "The others": follow them, ask them along, trade, pay, give. */
public record OthersActionPayload(String band, int action) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OthersActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "others_action"));

    public static final int OPEN = 0;
    public static final int LEAD = 1;
    public static final int TRAVEL = 2;
    public static final int TRADE = 3;
    public static final int RANSOM = 4;
    public static final int GIFT = 5;
    public static final int DEMAND = 6;
    public static final int RAID = 7;
    public static final int TELL_PLACES = 8;
    public static final int ASK_PLACES = 9;
    /** Down to two: ask to join them. */
    public static final int JOIN_THEM = 10;
    /** "Teach me what you know." */
    public static final int LEARN = 11;
    /** "What do you do when something comes for you?" */
    public static final int ASK_POSTURE = 12;
    /** Send a party: PARTY + the intent (see Parties). */
    public static final int PARTY = 20;

    public static final StreamCodec<ByteBuf, OthersActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OthersActionPayload::band,
            ByteBufCodecs.VAR_INT, OthersActionPayload::action,
            OthersActionPayload::new);

    public static void handle(OthersActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                dev.hominin.evolution.band.Relations.act(player, payload.band(), payload.action());
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
