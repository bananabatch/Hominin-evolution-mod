package dev.hominin.evolution.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Flashes a search result about whether barking ever gets tiring. It does not. */
public final class TiredApesFlash {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "tired_apes");
    private static final ResourceLocation IMAGE =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "textures/gui/tired_apes.png");
    private static final int IMAGE_WIDTH = 1024;
    private static final int IMAGE_HEIGHT = 439;

    private static final long HOLD_MS = 3_000L;
    private static final long FADE_MS = 1_200L;
    /** The words stay on after the picture has gone, long enough to read them. */
    private static final long TEXT_HOLD_MS = 7_000L;
    private static final long TEXT_FADE_MS = 1_500L;
    private static final long TOTAL_MS = TEXT_HOLD_MS + TEXT_FADE_MS;

    private static long startedAt = -1L;

    public static void start() {
        startedAt = Util.getMillis();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ID, TiredApesFlash::render);
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
        // All at once, the whole screen, for three seconds - then it fades away.
        float alpha = elapsed < HOLD_MS ? 1.0F : Math.max(0.0F, 1.0F - (elapsed - HOLD_MS) / (float) FADE_MS);
        float textAlpha = elapsed < TEXT_HOLD_MS ? 1.0F : 1.0F - (elapsed - TEXT_HOLD_MS) / (float) TEXT_FADE_MS;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int drawWidth = width;
        int drawHeight = height;
        int x = 0;
        int y = 0;

        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(IMAGE, x, y, drawWidth, drawHeight, 0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH,
                IMAGE_HEIGHT);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        int a = Math.max(0, Math.min(255, Math.round(textAlpha * 255.0F)));
        if (a >= 5) {
            Font font = Minecraft.getInstance().font;
            String text = "It seems you and your band could do this all day without getting tired.";
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(width / 2.0F, height - 28, 0.0F);
            graphics.drawString(font, text, -font.width(text) / 2, 0, (a << 24) | 0xFFE9A6, true);
            pose.popPose();
        }
    }

    private TiredApesFlash() {
    }
}
