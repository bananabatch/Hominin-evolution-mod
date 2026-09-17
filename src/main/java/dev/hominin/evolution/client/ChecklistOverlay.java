package dev.hominin.evolution.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The standing evolution checklist, drawn down the left of the screen.
 *
 * <p>This is a HUD layer rather than chat messages: a checklist that is "always
 * there and updates" cannot be chat, because chat only appends and scrolls away.
 * Ticked lines stay visible so progress reads at a glance.
 *
 * <p>It renders at a fraction of the usual font size. The list has to sit on
 * screen permanently, so it is sized to be readable while still leaving the view
 * clear - full-size text takes the whole top of the screen.
 */
public final class ChecklistOverlay {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "evolution_checklist");

    /** Fraction of normal font size. Below about 0.6 the font stops being legible. */
    private static final float SCALE = 0.7F;

    private static final int LEFT = 5;
    private static final int TOP = 5;
    private static final int LINE_HEIGHT = 9;
    private static final int TITLE_COLOUR = 0xFFE9D8A6;
    private static final int DONE_COLOUR = 0xFF7FC97F;
    private static final int TODO_COLOUR = 0xFFBFBFBF;

    /** Line markers the server puts in front of each line; never drawn as text. */
    private static final char MARK_DONE = '+';
    private static final char MARK_READY = '!';

    private static String title = "";
    private static List<String> lines = List.of();

    /** Called from the network thread's handler; both fields are only read on render. */
    public static void accept(String newTitle, List<String> newLines) {
        title = newTitle;
        lines = List.copyOf(newLines);
    }

    /** Leaving a world clears it: these fields are static, and the next world is not this one. */
    public static void onLoggingOut(
            net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        title = "";
        lines = List.of();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ID, ChecklistOverlay::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (lines.isEmpty() || mc.options.hideGui || mc.screen != null) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(SCALE, SCALE, 1.0F);
        // Everything below is in scaled space, so the margins have to be divided
        // back out to land where they were asked for in real screen pixels.
        int x = Math.round(LEFT / SCALE);
        int y = Math.round(TOP / SCALE);

        graphics.drawString(mc.font, title, x, y, TITLE_COLOUR, true);
        y += LINE_HEIGHT + 2;
        for (String line : lines) {
            char marker = line.charAt(0);
            graphics.drawString(mc.font, glyph(marker) + line.substring(1), x, y, colour(marker), true);
            y += LINE_HEIGHT;
        }
        pose.popPose();
    }

    private static String glyph(char marker) {
        if (marker == MARK_DONE) {
            return "\u2714 ";
        }
        return marker == MARK_READY ? "\u00BB " : "\u2717 ";
    }

    private static int colour(char marker) {
        if (marker == MARK_DONE) {
            return DONE_COLOUR;
        }
        return marker == MARK_READY ? TITLE_COLOUR : TODO_COLOUR;
    }

    private ChecklistOverlay() {
    }
}
