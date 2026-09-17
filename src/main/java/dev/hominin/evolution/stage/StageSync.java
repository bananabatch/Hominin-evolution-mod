package dev.hominin.evolution.stage;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.network.StagePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells clients which stage each player is at. The stage lives in server-side save
 * data, but what a player looks like - and how fast they climb - is decided on every
 * client that can see them.
 */
public final class StageSync {
    /** To the player and to everyone currently watching them. Call whenever the stage changes. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payloadFor(player));
    }

    private static StagePayload payloadFor(ServerPlayer player) {
        return new StagePayload(player.getUUID(), player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    /** A respawn or a portal makes a fresh client-side player, which starts out knowing nothing. */
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
            PacketDistributor.sendToPlayer(watcher, payloadFor(target));
        }
    }

    private StageSync() {
    }
}
