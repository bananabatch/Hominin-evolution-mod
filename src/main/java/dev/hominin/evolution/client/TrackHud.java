package dev.hominin.evolution.client;

import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The members you are keeping an eye on (tribe stats: right-click a name), in the top right corner: which way and
 * how far, health, hunger, what they are doing - and under each, their skills.
 */
public final class TrackHud {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "tracked");
    private static final float SCALE = 0.75F;

    private static List<String> lines = List.of();

    public static void set(List<String> tracked) {
        lines = List.copyOf(tracked);
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ID, TrackHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (lines.isEmpty() || mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, mc.font.width(line));
        }
        graphics.pose().pushPose();
        graphics.pose().scale(SCALE, SCALE, 1.0F);
        int right = Math.round(graphics.guiWidth() / SCALE) - 4;
        int x = right - widest;
        int y = 4;
        graphics.fill(x - 3, y - 2, right + 2, y + lines.size() * 10, 0x70000000);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean detail = line.startsWith("  ");
            graphics.drawString(mc.font, line, x, y + i * 10, detail ? 0xA0A0A0 : 0xF0D890, true);
        }
        graphics.pose().popPose();
    }

    private TrackHud() {
    }
}
