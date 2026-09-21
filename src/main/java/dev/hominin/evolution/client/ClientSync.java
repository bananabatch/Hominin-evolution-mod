package dev.hominin.evolution.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.climb.Climbing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** What the server has told this client about other players, and where it goes. */
public final class ClientSync {
    private static final Map<UUID, ResourceLocation> STAGES = new HashMap<>();
    /** Whether the server says this player is in developer mode. */
    public static boolean devMode;

    @Nullable
    public static ResourceLocation stageOf(UUID player) {
        return STAGES.get(player);
    }

    public static void stage(UUID player, ResourceLocation stage) {
        STAGES.put(player, stage);
    }

    public static void climbState(int entityId, boolean climbing, int regripTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof Player player)) {
            return;
        }
        Climbing.setClimbing(player, climbing);
        if (player == mc.player) {
            ClimbController.overruled(regripTicks);
        }
    }

    public static void bodyAnimation(int entityId, String animation) {
        Minecraft mc = Minecraft.getInstance();
        // Checked by name first, so no Player Animator class is loaded without the library.
        if (mc.level == null || !ModList.get().isLoaded("playeranimator")) {
            return;
        }
        if (mc.level.getEntity(entityId) instanceof AbstractClientPlayer player) {
            BodyAnimationHandler.playOnce(player, animation);
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        STAGES.clear();
    }

    private ClientSync() {
    }
}
