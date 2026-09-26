package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The player's pick from a {@link ChoicesPayload}. */
public record ChoosePayload(int entityId, int action, int value) implements CustomPacketPayload {
    /** A name clicked in the band list. */
    public static final int ACTION_FIND = 3;

    public static final CustomPacketPayload.Type<ChoosePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "choose"));

    public static final StreamCodec<ByteBuf, ChoosePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ChoosePayload::entityId,
            ByteBufCodecs.VAR_INT, ChoosePayload::action,
            ByteBufCodecs.VAR_INT, ChoosePayload::value,
            ChoosePayload::new);

    public static void handle(ChoosePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (payload.action() == dev.hominin.evolution.mind.Teaching.ACTION_TEACH) {
            dev.hominin.evolution.mind.Teaching.choose(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Commissions.ACTION_KNAP) {
            dev.hominin.evolution.band.Commissions.choose(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.build.Building.ACTION_USE) {
            dev.hominin.evolution.build.Building.choose(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.build.Building.ACTION_GIVE) {
            dev.hominin.evolution.build.Building.give(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Psychopaths.ACTION_SUSPECT) {
            dev.hominin.evolution.band.Psychopaths.suspect(player, payload.entityId());
        } else if (payload.action() == dev.hominin.evolution.band.Psychopaths.ACTION_VERDICT) {
            dev.hominin.evolution.band.Psychopaths.verdict(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Intruders.ACTION) {
            dev.hominin.evolution.band.Intruders.choose(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Tracking.ACTION_TRACK) {
            dev.hominin.evolution.band.Tracking.toggle(player, payload.entityId());
        } else if (payload.action() == dev.hominin.evolution.stage.Lineage.ACTION) {
            dev.hominin.evolution.stage.Lineage.choose(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.SacredPile.ACTION_WHERE) {
            dev.hominin.evolution.band.SacredPile.chooseWhere(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Refugees.ACTION) {
            dev.hominin.evolution.band.Refugees.choose(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Refugees.ACTION_MERGE) {
            dev.hominin.evolution.band.Refugees.chooseMerge(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Postures.ACTION) {
            dev.hominin.evolution.band.Postures.choose(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Newcomers.ACTION_PLAYERS) {
            dev.hominin.evolution.band.Newcomers.choosePlayer(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Newcomers.ACTION_REQUEST) {
            dev.hominin.evolution.band.Newcomers.answerRequest(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.stage.Intermission.ACTION) {
            dev.hominin.evolution.stage.Intermission.choose(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.stage.Intermission.ACTION_SPLIT) {
            dev.hominin.evolution.stage.Intermission.chooseSplit(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.Newcomers.ACTION) {
            dev.hominin.evolution.band.Newcomers.choose(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.PileAsk.ACTION) {
            dev.hominin.evolution.band.PileAsk.choose(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.world.Havens.ACTION_CONQUERED) {
            dev.hominin.evolution.world.Havens.choose(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.PlayerMenu.ACTION_MENU) {
            dev.hominin.evolution.band.PlayerMenu.choose(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.PlayerMenu.ACTION_TEACH) {
            dev.hominin.evolution.band.PlayerMenu.chooseSkill(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.PlayerMenu.ACTION_ANSWER) {
            dev.hominin.evolution.band.PlayerMenu.answer(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.PilePlayers.ACTION) {
            dev.hominin.evolution.band.PilePlayers.choose(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.BandRoles.ACTION_MENU) {
            dev.hominin.evolution.band.BandRoles.choose(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.BandRoles.ACTION_PARTY) {
            dev.hominin.evolution.band.BandRoles.answerParty(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.BandRoles.ACTION_ROLE) {
            dev.hominin.evolution.band.BandRoles.setRole(player, payload.entityId(), payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.BandRoles.ACTION_CONFIRM) {
            dev.hominin.evolution.band.BandRoles.confirm(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.BandViews.ACTION_LIST) {
            dev.hominin.evolution.band.BandViews.chooseBand(player, payload.value());
        } else if (payload.action() == dev.hominin.evolution.band.BandViews.ACTION_SET) {
            dev.hominin.evolution.band.BandViews.set(player, payload.value());
        } else if (payload.action() == ACTION_FIND) {
            dev.hominin.evolution.band.Social.find(player, payload.entityId());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
