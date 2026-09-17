package dev.hominin.evolution.client;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.survival.Thirst;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** The water bar, drawn in the row above the hunger bar. */
public final class ThirstOverlay {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "thirst_bar");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "textures/gui/thirst.png");

    private static final int ICONS = 10;
    private static final int ICON = 9;

    private static int thirst = Thirst.MAX;

    public static void accept(int value) {
        thirst = value;
    }

    public static void register(RegisterGuiLayersEvent event) {
        // After the air bar, so the row lands above whatever vanilla has already drawn.
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, ID, ThirstOverlay::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isCreative() || mc.player.isSpectator()
                || mc.gui == null) {
            return;
        }
        // Water sits above the health bar rather than the hunger bar: thirst belongs with
        // what the body is, not with what it has eaten, and the right-hand column was
        // already carrying hunger and air.
        int left = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - mc.gui.leftHeight;
        RenderSystem.enableBlend();
        for (int i = 0; i < ICONS; i++) {
            int x = left + i * 8;
            // Two points of thirst to an icon, so the bar reads like the hunger bar beside it.
            int filled = Math.max(0, Math.min(2, thirst - i * 2));
            graphics.blit(TEXTURE, x, y, 0, 0, ICON, ICON, 27, 9);
            if (filled > 0) {
                graphics.blit(TEXTURE, x, y, filled == 2 ? 18 : 9, 0, ICON, ICON, 27, 9);
            }
        }
        RenderSystem.disableBlend();
        mc.gui.leftHeight += 10;
    }

    private ThirstOverlay() {
    }
}
