package dev.hominin.evolution.client;

import dev.hominin.evolution.mind.Thinking;
import dev.hominin.evolution.network.ThreatDisplayPayload;
import dev.hominin.evolution.network.ItemInteractPayload;
import dev.hominin.evolution.network.SharpenStickPayload;
import dev.hominin.evolution.network.ThinkPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * An empty-handed right-click on air is a client-only event - vanilla never
 * sends it to the server - so the empty-handed threat display has to be reported
 * explicitly.
 */
public final class ClientInputHandler {
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        // Fires once per hand; only report the main hand so the server sees one display.
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        PacketDistributor.sendToServer(new ThreatDisplayPayload());
    }

    /** How long the think key has been held, in client ticks. Reset on release. */
    private static int thinkHeld;

    /** Ticks the work key has been held with a stick in hand. Reset on release. */
    private static int sharpenHeld;

    /** How long to hold the work key to gnaw a stick to a point. */
    private static final int SHARPEN_HOLD_TICKS = 30;

    /** Drains the interact key each client tick and reports presses to the server. */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) {
            return;
        }
        while (ModKeyMappings.THREAT_DISPLAY.consumeClick()) {
            if (mc.screen == null) {
                PacketDistributor.sendToServer(new ThreatDisplayPayload());
            }
        }
        while (ModKeyMappings.SOCIAL.consumeClick()) {
            if (mc.screen == null) {
                SocialScreen.open();
            }
        }
        while (ModKeyMappings.ITEM_INTERACT.consumeClick()) {
            PacketDistributor.sendToServer(new ItemInteractPayload());
        }
        tickThink(mc);
        tickSharpen(mc);
    }

    /**
     * The think key is timed here rather than on the server so the countdown can be
     * drawn as it runs. Only the completed hold is reported; the server re-checks
     * everything that matters about it.
     */
    /**
     * Sharpening is a hold on the work key, not a right-click, so it never fires on
     * top of another interaction - right-clicking a termite mound with a stick used
     * to whittle it at the same time. A quick press of the key still does its normal
     * two-handed job; only a sustained hold with a bare stick sharpens.
     */
    private static void tickSharpen(Minecraft mc) {
        if (!ModKeyMappings.ITEM_INTERACT.isDown() || mc.screen != null
                || !mc.player.getMainHandItem().is(Items.STICK)) {
            sharpenHeld = 0;
            return;
        }
        sharpenHeld++;
        if (sharpenHeld < SHARPEN_HOLD_TICKS) {
            // Keep the first few ticks quiet, so an ordinary tap of the key is not
            // announced as the start of a sharpening.
            if (sharpenHeld > 5) {
                int remaining = (SHARPEN_HOLD_TICKS - sharpenHeld + 19) / 20;
                mc.player.displayClientMessage(Component.literal("Sharpening... " + remaining), true);
            }
            return;
        }
        if (sharpenHeld == SHARPEN_HOLD_TICKS) {
            PacketDistributor.sendToServer(new SharpenStickPayload());
        }
    }

    private static void tickThink(Minecraft mc) {
        // Never while a screen is up: the key would otherwise fire under the
        // knapping screen or the inventory.
        if (!ModKeyMappings.THINK.isDown() || mc.screen != null) {
            thinkHeld = 0;
            return;
        }
        thinkHeld++;
        if (thinkHeld < Thinking.HOLD_TICKS) {
            int remaining = (Thinking.HOLD_TICKS - thinkHeld + 19) / 20;
            mc.player.displayClientMessage(
                    Component.literal("Thinking... " + remaining), true);
            return;
        }
        if (thinkHeld == Thinking.HOLD_TICKS) {
            PacketDistributor.sendToServer(new ThinkPayload());
        }
    }

    private ClientInputHandler() {
    }
}
