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

/**
 * The skull vow: a hominin holding a skull up to look it in the eye, and a promise. Fills
 * the screen for three seconds, like the arms-race card.
 */
public final class SkullPoseFlash {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "skull_pose");
    private static final ResourceLocation IMAGE =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "textures/gui/skull_pose.png");
    /** Fitted to the screen rather than stretched. */
    private static final int IMAGE_WIDTH = 1200;
    private static final int IMAGE_HEIGHT = 747;

    private static final long HOLD_MS = 3_000L;
    private static final long FADE_MS = 1_000L;
    private static final long TOTAL_MS = HOLD_MS + FADE_MS;

    private static long startedAt = -1L;

    public static void start() {
        startedAt = Util.getMillis();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ID, SkullPoseFlash::render);
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
        float alpha = elapsed < HOLD_MS ? 1.0F : 1.0F - (elapsed - HOLD_MS) / (float) FADE_MS;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));

        // Black behind it, so the picture reads as a title card.
        graphics.fill(0, 0, width, height, (a << 24));

        // Fit the whole image on screen without squashing it.
        float scale = Math.min(width / (float) IMAGE_WIDTH, height / (float) IMAGE_HEIGHT);
        int drawWidth = Math.round(IMAGE_WIDTH * scale);
        int drawHeight = Math.round(IMAGE_HEIGHT * scale);
        int x = (width - drawWidth) / 2;
        int y = (height - drawHeight) / 2;

        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(IMAGE, x, y, drawWidth, drawHeight, 0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH,
                IMAGE_HEIGHT);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (a >= 5) {
            Font font = Minecraft.getInstance().font;
            String text = "Whatever took you - a predator, another band, disease - you will be avenged.";
            // The picture is on white, so the words get a dark band of their own.
            graphics.fill(0, height - 36, width, height - 12, ((a * 3 / 4) << 24));
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(width / 2.0F, height - 28, 0.0F);
            graphics.drawString(font, text, -font.width(text) / 2, 0, (a << 24) | 0xFFE9A6, true);
            pose.popPose();
        }
    }

    private SkullPoseFlash() {
    }
}
