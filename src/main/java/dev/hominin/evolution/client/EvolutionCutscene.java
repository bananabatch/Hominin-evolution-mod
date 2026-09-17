package dev.hominin.evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The evolution cutscene: fade to black, deep time, a new name, fade back in.
 *
 * <p>Drawn as the topmost HUD layer rather than a screen, so the game keeps running
 * underneath - the server moves the player during the dark stretch, and the world has
 * loaded around them by the time the picture comes back. Timed in wall-clock
 * milliseconds so it stays smooth regardless of tick rate.
 */
public final class EvolutionCutscene {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "evolution_cutscene");

    /** Matches Arrival.FADE_TICKS: the screen is fully black by the time the server moves the player. */
    private static final long FADE_IN_MS = 1_000L;
    private static final long HOLD_MS = 5_000L;
    private static final long FADE_OUT_MS = 1_500L;
    private static final long TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;

    /** Delays before each line appears within the dark hold, so they arrive one at a time. */
    private static final long TIME_LINE_AT_MS = FADE_IN_MS + 300L;
    private static final long STAGE_LINE_AT_MS = FADE_IN_MS + 1_600L;
    private static final long TEXT_FADE_MS = 700L;

    private static final int TIME_COLOUR = 0xE9D8A6;
    private static final int STAGE_COLOUR = 0xFFFFFF;
    private static final int DISTANCE_COLOUR = 0x9A9A9A;

    private static long startedAt = -1L;
    private static long arrivedAt = -1L;
    private static String timePassed = "";
    private static String stage = "";
    private static int blocks;

    public static void start(String newTimePassed, String newStage) {
        timePassed = newTimePassed;
        stage = newStage;
        blocks = 0;
        arrivedAt = -1L;
        startedAt = Util.getMillis();
    }

    public static void arrived(int blocksMoved) {
        blocks = blocksMoved;
        arrivedAt = Util.getMillis();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ID, EvolutionCutscene::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (startedAt < 0L) {
            return;
        }
        long elapsed = Util.getMillis() - startedAt;
        if (elapsed >= TOTAL_MS) {
            startedAt = -1L;
            return;
        }
        float dark = elapsed < FADE_IN_MS ? elapsed / (float) FADE_IN_MS
                : elapsed < FADE_IN_MS + HOLD_MS ? 1.0F
                : 1.0F - (elapsed - FADE_IN_MS - HOLD_MS) / (float) FADE_OUT_MS;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, alpha(dark) << 24);

        // Text fades out with the black, so nothing is left hanging over the world.
        float textFade = Math.min(dark, 1.0F);
        Font font = Minecraft.getInstance().font;
        int centre = height / 2;
        drawCentred(graphics, font, timePassed, width / 2, centre - 30, 2.0F, TIME_COLOUR,
                fadeIn(elapsed, TIME_LINE_AT_MS) * textFade);
        drawCentred(graphics, font, stage, width / 2, centre + 2, 1.5F, STAGE_COLOUR,
                fadeIn(elapsed, STAGE_LINE_AT_MS) * textFade);
        if (arrivedAt >= 0L && blocks > 0) {
            long sinceArrival = Util.getMillis() - arrivedAt;
            // Never before the stage line, even if the server was quick.
            long appearAt = Math.max(STAGE_LINE_AT_MS + 900L, arrivedAt - startedAt);
            drawCentred(graphics, font, blocks + " blocks from where your ancestors walked",
                    width / 2, centre + 26, 1.0F, DISTANCE_COLOUR,
                    fadeIn(elapsed, appearAt) * textFade * Math.min(1.0F, sinceArrival / (float) TEXT_FADE_MS));
        }
    }

    private static float fadeIn(long elapsed, long appearAt) {
        return Math.max(0.0F, Math.min(1.0F, (elapsed - appearAt) / (float) TEXT_FADE_MS));
    }

    private static int alpha(float opacity) {
        return Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
    }

    private static void drawCentred(GuiGraphics graphics, Font font, String text, int x, int y,
            float scale, int rgb, float opacity) {
        // Below about 4 the font renderer treats alpha as fully opaque, so skip instead.
        int a = alpha(opacity);
        if (a < 5 || text.isEmpty()) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, (a << 24) | rgb, false);
        pose.popPose();
    }

    private EvolutionCutscene() {
    }
}
