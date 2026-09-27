package dev.hominin.evolution.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Down at the knapping station: while the work there plays, you crouch over the mat - the camera comes down with you -
 * and get up again when it is done. It holds the crouch the way the sneak key does, without touching the key, so a
 * toggled sneak and the key itself are left exactly as they were.
 */
public final class KnapCrouch {
    /** Each station move holds the crouch this long; the next one carries it on. */
    private static final int HOLD_TICKS = 44;
    private static long until = -1L;

    public static void hold() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            until = mc.level.getGameTime() + HOLD_TICKS;
        }
    }

    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || event.getEntity() != mc.player) {
            return;
        }
        if (mc.level.getGameTime() < until) {
            event.getInput().shiftKeyDown = true;
        }
    }

    private KnapCrouch() {
    }
}
