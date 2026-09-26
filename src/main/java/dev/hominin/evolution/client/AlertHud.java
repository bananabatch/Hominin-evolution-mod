package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.guide.Alerts;
import dev.hominin.evolution.network.AlertPayload;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Urgent news across the top of the screen: a band member's need, something coming for you, the season turning.
 * Up to three at once, newest on top, each for a few seconds - below the waypoint pointer when that is showing.
 */
public final class AlertHud {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "alerts");
    private static final long SHOWN_MS = 7_000L;
    private static final long FADE_MS = 600L;
    private static final int MAX_SHOWN = 3;
    private static final int MAX_LINES = 3;
    /** Colours by Alerts.Kind: need, warning, danger, season, band. */
    private static final int[] COLOURS = {0xFFE8A83A, 0xFFE8553A, 0xFFD02020, 0xFF7CC870, 0xFFC08AF0};

    private record Banner(int kind, String text, long at) {
    }

    private static final List<Banner> shown = new ArrayList<>();

    public static void show(AlertPayload payload) {
        shown.add(0, new Banner(payload.kind(), payload.text(), Util.getMillis()));
        while (shown.size() > MAX_SHOWN) {
            shown.remove(shown.size() - 1);
        }
        Minecraft mc = Minecraft.getInstance();
        if (payload.kind() == Alerts.Kind.WARNING.ordinal() || payload.kind() == Alerts.Kind.DANGER.ordinal()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.9F));
        } else {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.5F));
        }
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ID, AlertHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        shown.removeIf(banner -> now - banner.at > SHOWN_MS);
        if (shown.isEmpty() || mc.player == null || mc.options.hideGui) {
            return;
        }
        int width = Math.min(340, graphics.guiWidth() - 40);
        int centre = graphics.guiWidth() / 2;
        int y = WaypointHud.showing() ? 22 : 4;
        for (Banner banner : shown) {
            long age = now - banner.at;
            float alpha = age > SHOWN_MS - FADE_MS ? (SHOWN_MS - age) / (float) FADE_MS : 1.0F;
            int a = Math.max(8, (int) (alpha * 255));
            int colour = COLOURS[Math.floorMod(banner.kind, COLOURS.length)];
            String label = Alerts.Kind.values()[Math.floorMod(banner.kind, Alerts.Kind.values().length)].label().toUpperCase();
            int labelWidth = mc.font.width(label) + 8;
            List<FormattedCharSequence> lines = mc.font.split(Component.literal(banner.text), width - labelWidth - 12);
            int count = Math.min(MAX_LINES, lines.size());
            int height = count * 10 + 6;
            int left = centre - width / 2;
            graphics.fill(left, y, left + width, y + height, (int) (alpha * 0xB0) << 24 | 0x101010);
            graphics.fill(left, y, left + 3, y + height, (a << 24) | (colour & 0xFFFFFF));
            graphics.drawString(mc.font, label, left + 7, y + 4, (a << 24) | (colour & 0xFFFFFF), true);
            for (int i = 0; i < count; i++) {
                FormattedCharSequence line = lines.get(i);
                if (i == MAX_LINES - 1 && lines.size() > MAX_LINES) {
                    // More than fits: the rest is in chat, and in the journal.
                    graphics.drawString(mc.font, "...", left + width - 14, y + 3 + i * 10, (a << 24) | 0xBBBBBB, true);
                }
                graphics.drawString(mc.font, line, left + labelWidth + 4, y + 3 + i * 10, (a << 24) | 0xF0EAE0, true);
            }
            y += height + 3;
        }
    }

    public static void clear() {
        shown.clear();
    }

    private AlertHud() {
    }
}
