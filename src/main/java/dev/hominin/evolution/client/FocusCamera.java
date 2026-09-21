package dev.hominin.evolution.client;

import net.minecraft.Util;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * The world narrowing to one thing. When a troop goes silent and stares at you, you stop
 * seeing the rest of the savanna - so for those seconds the view tightens, the way it
 * does when everything you care about is one animal and what it does next.
 */
public final class FocusCamera {
    /** How far the view closes in. Enough to feel, not enough to lose your bearings. */
    private static final float NARROWED = 0.82F;
    private static long until;

    public static void focus(int ticks) {
        until = Util.getMillis() + ticks * 50L;
    }

    public static void onComputeFov(ComputeFovModifierEvent event) {
        long left = until - Util.getMillis();
        if (left <= 0) {
            return;
        }
        // Ease back out over the last half-second rather than snapping.
        float ease = Math.min(1.0F, left / 500.0F);
        event.setNewFovModifier(event.getNewFovModifier() * (1.0F - (1.0F - NARROWED) * ease));
    }

    private FocusCamera() {
    }
}
