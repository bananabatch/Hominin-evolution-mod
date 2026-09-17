package dev.hominin.evolution.stage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Nothing touches you while the screen is black.
 *
 * <p>Every cutscene takes the controls away for a few seconds, and dying to something you
 * could not see, while you could not move, is not a lesson about anything.
 */
public final class CutsceneGuard {
    private static final Map<UUID, Long> protectedUntil = new HashMap<>();

    public static void protect(ServerPlayer player, int ticks) {
        protectedUntil.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    /**
     * Claims the screen for a cutscene. Returns false if one is already running, or if the
     * player is in no state to watch one - dead, or on the respawn screen. Nothing queues:
     * a cutscene that cannot play now simply does not play, and whatever it was announcing
     * says so in chat instead.
     */
    public static boolean tryStart(ServerPlayer player, int ticks) {
        if (isProtected(player) || !player.isAlive()) {
            return false;
        }
        protect(player, ticks);
        return true;
    }

    public static boolean isProtected(ServerPlayer player) {
        Long until = protectedUntil.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isProtected(player)) {
            event.setCanceled(true);
        }
    }

    public static void forget(UUID player) {
        protectedUntil.remove(player);
    }

    private CutsceneGuard() {
    }
}
