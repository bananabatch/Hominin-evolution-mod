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
 * Death in a band is not the end of you. The screen is dark - your eyes are closed -
 * and then they open as someone else's: two lids parting on the world, as one of the
 * band you were leading a moment ago.
 */
public final class RebirthCutscene {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "rebirth_cutscene");

    private static final long CLOSED_MS = 1_600L;
    private static final long NAME_MS = 2_400L;
    private static final long OPEN_MS = 1_800L;
    private static final long TOTAL_MS = CLOSED_MS + NAME_MS + OPEN_MS;
    private static final long TEXT_FADE_MS = 600L;

    private static long startedAt = -1L;
    private static String name = "";

    public static void start(String heirName) {
        name = heirName;
        startedAt = Util.getMillis();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ID, RebirthCutscene::render);
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
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Font font = Minecraft.getInstance().font;

        if (elapsed < CLOSED_MS + NAME_MS) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            drawCentred(graphics, font, "You close your eyes...", width / 2, height / 2 - 14, 1.5F, 0xBBBBBB,
                    fade(elapsed, 200L, CLOSED_MS + NAME_MS - 300L));
            drawCentred(graphics, font, "...and open them as " + name + ".", width / 2, height / 2 + 8, 1.5F,
                    0xE9D8A6, fade(elapsed, CLOSED_MS, CLOSED_MS + NAME_MS - 300L));
            return;
        }
        // Eyelids: two black bands parting from the middle, with a soft blink at the start.
        float t = (elapsed - CLOSED_MS - NAME_MS) / (float) OPEN_MS;
        float open = t < 0.25F ? t / 0.25F * 0.35F
                : t < 0.4F ? 0.35F - (t - 0.25F) / 0.15F * 0.2F
                : 0.15F + (t - 0.4F) / 0.6F * 0.85F;
        open = open * open * (3.0F - 2.0F * open);
        int gap = Math.round(height / 2.0F * open);
        graphics.fill(0, 0, width, height / 2 - gap, 0xFF000000);
        graphics.fill(0, height / 2 + gap, width, height, 0xFF000000);
    }

    private static float fade(long elapsed, long appearAt, long disappearAt) {
        float in = Math.max(0.0F, Math.min(1.0F, (elapsed - appearAt) / (float) TEXT_FADE_MS));
        float out = Math.max(0.0F, Math.min(1.0F, (disappearAt + TEXT_FADE_MS - elapsed) / (float) TEXT_FADE_MS));
        return Math.min(in, out);
    }

    private static void drawCentred(GuiGraphics graphics, Font font, String text, int x, int y, float scale, int rgb,
            float opacity) {
        int a = Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
        if (a < 5) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, (a << 24) | rgb, false);
        pose.popPose();
    }

    private RebirthCutscene() {
    }
}
