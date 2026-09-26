package dev.hominin.evolution.client;

import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The evolution cutscene: fade to black, deep time, a new name, fade back in.
 *
 * <p>Given the two dates, the years themselves are counted down on screen - slowly at first, then faster and faster,
 * the stars streaming past into streaks, the ticks running together - until the count slams to a stop on the new age
 * with a flash. Then how long it was, and the new name, written out beneath it. Without dates (the band lost, a line
 * ended) it is the words alone, over the same dark.
 *
 * <p>Drawn as the topmost HUD layer rather than a screen, so the game keeps running underneath - the server moves the
 * player during the dark stretch, and the world has loaded around them by the time the picture comes back. Timed in
 * wall-clock milliseconds so it stays smooth regardless of tick rate.
 */
public final class EvolutionCutscene {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "evolution_cutscene");

    /** Matches Arrival.FADE_TICKS: the screen is fully black by the time the server moves the player. */
    private static final long FADE_IN_MS = 1_000L;
    private static final long FADE_OUT_MS = 1_500L;
    private static final long TEXT_FADE_MS = 700L;

    // ------------------------------------------------------------ with the years counted (evolving)

    private static final long COUNT_AT_MS = FADE_IN_MS + 300L;
    /** From the first slow tick to the stop. */
    private static final long COUNT_MS = 4_200L;
    private static final long LANDED_AT_MS = COUNT_AT_MS + COUNT_MS;
    private static final long LATER_AT_MS = LANDED_AT_MS + 250L;
    private static final long NAME_AT_MS = LANDED_AT_MS + 800L;
    /** How long the new name takes to write itself out. */
    private static final long NAME_WRITE_MS = 700L;
    private static final long COUNTED_TOTAL_MS = 10_500L;

    // ------------------------------------------------------------ words alone

    private static final long PLAIN_TOTAL_MS = 7_500L;
    private static final long TIME_LINE_AT_MS = FADE_IN_MS + 300L;
    private static final long STAGE_LINE_AT_MS = FADE_IN_MS + 1_600L;

    private static final int COUNT_COLOUR = 0xE9D8A6;
    private static final int LANDED_COLOUR = 0xFFD66B;
    private static final int TIME_COLOUR = 0xE9D8A6;
    private static final int STAGE_COLOUR = 0xFFFFFF;
    private static final int DIM_COLOUR = 0x9A9A9A;
    private static final int GLOW_COLOUR = 0x3A2810;
    private static final int STAR_COLOUR = 0xFFF1D0;

    private static final int STARS = 110;
    private static final float[] starAngle = new float[STARS];
    private static final float[] starStart = new float[STARS];
    private static final float[] starPace = new float[STARS];

    private static long startedAt = -1L;
    private static long arrivedAt = -1L;
    private static String timePassed = "";
    private static String stage = "";
    private static int blocks;
    private static int fromYears;
    private static int toYears;
    private static long lastTickAt;
    private static boolean landed;

    public static void start(String newTimePassed, String newStage) {
        start(newTimePassed, newStage, 0, 0);
    }

    public static void start(String newTimePassed, String newStage, int fromYearsAgo, int toYearsAgo) {
        timePassed = newTimePassed;
        stage = newStage;
        fromYears = fromYearsAgo;
        toYears = toYearsAgo;
        blocks = 0;
        arrivedAt = -1L;
        lastTickAt = -1L;
        landed = false;
        RandomSource random = RandomSource.create();
        for (int i = 0; i < STARS; i++) {
            starAngle[i] = random.nextFloat() * Mth.TWO_PI;
            starStart[i] = random.nextFloat();
            starPace[i] = 0.6F + random.nextFloat() * 0.8F;
        }
        startedAt = Util.getMillis();
    }

    public static void arrived(int blocksMoved) {
        blocks = blocksMoved;
        arrivedAt = Util.getMillis();
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ID, EvolutionCutscene::render);
    }

    private static boolean counted() {
        return fromYears > 0 && toYears > 0 && fromYears != toYears;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        if (startedAt < 0L) {
            return;
        }
        long elapsed = Util.getMillis() - startedAt;
        long total = counted() ? COUNTED_TOTAL_MS : PLAIN_TOTAL_MS;
        if (elapsed >= total) {
            startedAt = -1L;
            return;
        }
        long fadeOutAt = total - FADE_OUT_MS;
        float dark = elapsed < FADE_IN_MS ? elapsed / (float) FADE_IN_MS
                : elapsed < fadeOutAt ? 1.0F
                : 1.0F - (elapsed - fadeOutAt) / (float) FADE_OUT_MS;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, alpha(dark) << 24);
        // Text fades out with the black, so nothing is left hanging over the world.
        float shown = Math.min(dark, 1.0F);
        if (counted()) {
            renderCounted(graphics, elapsed, width, height, shown);
        } else {
            renderPlain(graphics, elapsed, width, height, shown);
        }
    }

    // ------------------------------------------------------------ evolving: the years counted down

    /** How far through the count: slow to begin with, faster and faster to the end. */
    private static float countProgress(long elapsed) {
        float u = Mth.clamp((elapsed - COUNT_AT_MS) / (float) COUNT_MS, 0.0F, 1.0F);
        return u * u * u;
    }

    /** How fast the years are going by just now, 0 to 1. */
    private static float countSpeed(long elapsed) {
        if (elapsed < COUNT_AT_MS || elapsed >= LANDED_AT_MS) {
            return 0.0F;
        }
        float u = (elapsed - COUNT_AT_MS) / (float) COUNT_MS;
        return u * u;
    }

    private static void renderCounted(GuiGraphics graphics, long elapsed, int width, int height, float shown) {
        Font font = Minecraft.getInstance().font;
        int cx = width / 2;
        int cy = height / 2;
        float progress = countProgress(elapsed);
        float speed = countSpeed(elapsed);
        glow(graphics, width, cy - 20, 70, shown * (0.6F + 0.4F * speed));
        stars(graphics, width, height, cx, cy - 20, elapsed, progress, speed, shown);

        boolean done = elapsed >= LANDED_AT_MS;
        long years = done ? toYears : Math.round(fromYears + (double) (toYears - fromYears) * progress);
        sounds(elapsed, speed, done);

        // The stop: a flash, and the number jumps a little and settles.
        float sinceLanding = done ? (elapsed - LANDED_AT_MS) / 450.0F : -1.0F;
        if (done && sinceLanding < 1.0F) {
            graphics.fill(0, 0, width, height, (alpha(0.45F * (1.0F - sinceLanding) * shown) << 24) | 0xFFF4DC);
        }
        float pulse = done && sinceLanding < 1.0F ? 1.0F + 0.18F * (1.0F - sinceLanding) * (1.0F - sinceLanding) : 1.0F;
        float countIn = fade(elapsed, FADE_IN_MS) * shown;
        String number = String.format(Locale.ROOT, "%,d", years);
        drawCentred(graphics, font, number, cx, cy - 46, (3.0F + 0.3F * speed) * pulse,
                done ? LANDED_COLOUR : COUNT_COLOUR, countIn, -1);
        drawCentred(graphics, font, "years ago", cx, cy - 14, 1.1F, DIM_COLOUR, countIn, -1);

        // The span of it, and how far across it you have come.
        int barHalf = Math.min(140, width / 3);
        int barY = cy + 2;
        int barAlpha = alpha(countIn * 0.8F);
        if (barAlpha >= 5) {
            graphics.fill(cx - barHalf, barY, cx + barHalf, barY + 1, (barAlpha << 24) | 0x5A4A30);
            int reached = cx - barHalf + Math.round(2 * barHalf * progress);
            graphics.fill(cx - barHalf, barY, reached, barY + 1, (barAlpha << 24) | COUNT_COLOUR);
            graphics.fill(cx - barHalf, barY - 3, cx - barHalf + 1, barY + 4, (barAlpha << 24) | 0x8A7A60);
            graphics.fill(cx + barHalf - 1, barY - 3, cx + barHalf, barY + 4, (barAlpha << 24) | 0x8A7A60);
            graphics.fill(reached - 1, barY - 3, reached + 2, barY + 4, (barAlpha << 24) | (done ? LANDED_COLOUR : 0xFFFFFF));
        }

        drawCentred(graphics, font, timePassed, cx, cy + 14, 1.5F, TIME_COLOUR, fade(elapsed, LATER_AT_MS) * shown, -1);
        // The new name, written out a letter at a time, and a line drawn under it.
        float written = Mth.clamp((elapsed - NAME_AT_MS) / (float) NAME_WRITE_MS, 0.0F, 1.0F);
        int letters = Math.round(stage.length() * written);
        drawCentred(graphics, font, stage, cx, cy + 34, 2.0F, STAGE_COLOUR, elapsed >= NAME_AT_MS ? shown : 0.0F, letters);
        if (written > 0.0F) {
            int underline = Math.round(font.width(stage) * written);
            int lineAlpha = alpha(shown * 0.7F);
            if (lineAlpha >= 5) {
                graphics.fill(cx - underline, cy + 54, cx + underline, cy + 55, (lineAlpha << 24) | LANDED_COLOUR);
            }
        }
        distance(graphics, font, elapsed, cx, cy + 64, NAME_AT_MS + NAME_WRITE_MS, shown);
    }

    /** The count ticks as it goes, the ticks running together and rising; the stop lands with a deep note. */
    private static void sounds(long elapsed, float speed, boolean done) {
        var sound = Minecraft.getInstance().getSoundManager();
        if (done) {
            if (!landed) {
                landed = true;
                sound.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 1.0F));
                sound.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7F, 0.8F));
                sound.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 0.6F));
            }
            return;
        }
        if (elapsed < COUNT_AT_MS) {
            return;
        }
        long gap = Math.round(Mth.lerp(Math.sqrt(speed), 260.0D, 45.0D));
        if (lastTickAt < 0L || elapsed - lastTickAt >= gap) {
            lastTickAt = elapsed;
            sound.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 0.8F + speed, 0.35F));
        }
    }

    /**
     * Stars streaming out from behind the count: drifting at first, then rushing past as the years do, drawn out into
     * streaks at the fastest - and slowing to a drift again once it stops.
     */
    private static void stars(GuiGraphics graphics, int width, int height, int cx, int cy, long elapsed, float progress,
            float speed, float shown) {
        float reach = (float) Math.sqrt(width * width + height * height) / 2.0F;
        // Distance travelled: a slow drift the whole time, and most of it during the count.
        float travel = elapsed / 1000.0F * 0.05F + progress * 3.0F;
        for (int i = 0; i < STARS; i++) {
            float along = (starStart[i] + travel * starPace[i]) % 1.0F;
            // Out from the middle, faster the further out, as if coming toward you.
            float r = along * along * reach;
            float bright = Mth.clamp(along * 2.5F, 0.0F, 1.0F) * shown * (0.35F + 0.65F * starPace[i] / 1.4F);
            int a = alpha(bright);
            if (a < 5) {
                continue;
            }
            float dx = Mth.cos(starAngle[i]);
            float dy = Mth.sin(starAngle[i]);
            int size = along > 0.7F ? 2 : 1;
            // At speed each star leaves a streak behind it, back toward the middle.
            int streak = 1 + Math.round(speed * 14.0F * along * starPace[i]);
            for (int s = 0; s < streak; s++) {
                float back = r - s * 2.0F;
                if (back < 0.0F) {
                    break;
                }
                int x = cx + Math.round(dx * back);
                int y = cy + Math.round(dy * back);
                int fading = alpha(bright * (1.0F - s / (float) streak));
                if (fading >= 5) {
                    graphics.fill(x, y, x + size, y + size, (fading << 24) | STAR_COLOUR);
                }
            }
        }
    }

    /** A low warm glow behind the words, like the last light on a horizon. */
    private static void glow(GuiGraphics graphics, int width, int y, int half, float strength) {
        int a = alpha(strength * 0.55F);
        if (a < 5) {
            return;
        }
        graphics.fillGradient(0, y - half, width, y, GLOW_COLOUR & 0xFFFFFF, (a << 24) | GLOW_COLOUR);
        graphics.fillGradient(0, y, width, y + half, (a << 24) | GLOW_COLOUR, GLOW_COLOUR & 0xFFFFFF);
    }

    // ------------------------------------------------------------ words alone

    private static void renderPlain(GuiGraphics graphics, long elapsed, int width, int height, float shown) {
        Font font = Minecraft.getInstance().font;
        int cx = width / 2;
        int cy = height / 2;
        glow(graphics, width, cy - 10, 60, shown * 0.6F);
        stars(graphics, width, height, cx, cy - 10, elapsed, 0.0F, 0.0F, shown * 0.7F);
        drawCentred(graphics, font, timePassed, cx, cy - 30, 2.0F, TIME_COLOUR, fade(elapsed, TIME_LINE_AT_MS) * shown, -1);
        drawCentred(graphics, font, stage, cx, cy + 2, 1.5F, STAGE_COLOUR, fade(elapsed, STAGE_LINE_AT_MS) * shown, -1);
        distance(graphics, font, elapsed, cx, cy + 26, STAGE_LINE_AT_MS + 900L, shown);
    }

    // ------------------------------------------------------------ shared

    /** How far the band was moved, once the server says - never before the lines above it. */
    private static void distance(GuiGraphics graphics, Font font, long elapsed, int x, int y, long notBefore, float shown) {
        if (arrivedAt < 0L || blocks <= 0) {
            return;
        }
        long sinceArrival = Util.getMillis() - arrivedAt;
        long appearAt = Math.max(notBefore, arrivedAt - startedAt);
        drawCentred(graphics, font, blocks + " blocks from where your ancestors walked", x, y, 1.0F, DIM_COLOUR,
                fade(elapsed, appearAt) * shown * Math.min(1.0F, sinceArrival / (float) TEXT_FADE_MS), -1);
    }

    private static float fade(long elapsed, long appearAt) {
        return Math.max(0.0F, Math.min(1.0F, (elapsed - appearAt) / (float) TEXT_FADE_MS));
    }

    private static int alpha(float opacity) {
        return Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
    }

    /**
     * Text centred on x at a scale. With {@code letters} of 0 or more only that many are drawn - still placed where the
     * whole line will sit, so it writes itself out without shifting.
     */
    private static void drawCentred(GuiGraphics graphics, Font font, String text, int x, int y,
            float scale, int rgb, float opacity, int letters) {
        // Below about 4 the font renderer treats alpha as fully opaque, so skip instead.
        int a = alpha(opacity);
        if (a < 5 || text.isEmpty() || letters == 0) {
            return;
        }
        String drawn = letters < 0 || letters >= text.length() ? text : text.substring(0, letters);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, drawn, -font.width(text) / 2, 0, (a << 24) | rgb, false);
        pose.popPose();
    }

    private EvolutionCutscene() {
    }
}
