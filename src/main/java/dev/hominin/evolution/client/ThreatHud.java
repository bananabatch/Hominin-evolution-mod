package dev.hominin.evolution.client;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Building threat: how much of what hunts has learned the ground you stand on, 1 to 10 - drawn over the hunger bar
 * whenever you are on a band's ground, yours or another's. It rises while a band stays put; presence slows it, food
 * piled in the open speeds it, a trusted baboon troop nearby and a great predator killed bring it down.
 */
public final class ThreatHud {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "threat");
    private static final float LABEL_SCALE = 0.7F;

    private static int threat;
    private static String whose = "";

    public static void accept(int value, String owner) {
        threat = value;
        whose = owner;
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, ID, ThreatHud::render);
    }

    /** Pale when all is quiet, through amber, to red when something is sure to come. */
    private static int colour(int pip) {
        if (pip <= 3) {
            return 0xFFD8C878;
        }
        if (pip <= 6) {
            return 0xFFE09838;
        }
        return 0xFFD0402C;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (threat <= 0 || mc.player == null || mc.options.hideGui || mc.player.isCreative() || mc.player.isSpectator()
                || mc.gui == null) {
            return;
        }
        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - mc.gui.rightHeight + 2;
        // Ten pips, right-aligned with the hunger bar; the label before them.
        int pip = 6;
        int gap = 1;
        int barLeft = right - 10 * (pip + gap) + gap;
        for (int i = 0; i < 10; i++) {
            int x = barLeft + i * (pip + gap);
            graphics.fill(x, y, x + pip, y + 5, 0xA0101010);
            if (i < threat) {
                graphics.fill(x + 1, y + 1, x + pip - 1, y + 4, colour(i + 1));
            }
        }
        // Short, so it never runs into the health column: whose ground it is shows on the map and in the journal.
        String label = "Threat " + threat;
        graphics.pose().pushPose();
        graphics.pose().scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
        int width = mc.font.width(label);
        graphics.drawString(mc.font, label, Math.round((barLeft - 3) / LABEL_SCALE) - width,
                Math.round((y - 0.5F) / LABEL_SCALE), 0xE0D0B0, true);
        graphics.pose().popPose();
        mc.gui.rightHeight += 8;
    }

    private ThreatHud() {
    }
}
