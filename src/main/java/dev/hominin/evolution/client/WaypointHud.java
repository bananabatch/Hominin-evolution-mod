package dev.hominin.evolution.client;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.network.WaypointPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The pointer at the top of the screen while you are following something - a band's call, a place on
 * the mental map: an arrow turned the way to walk from where you are looking, what you are heading
 * for, and how far.
 */
public final class WaypointHud {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "waypoint");
    /** Eight arrows, starting straight ahead and turning clockwise. */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static boolean active;
    private static int x;
    private static int z;
    private static String label = "";

    public static void set(WaypointPayload payload) {
        active = payload.active();
        x = payload.x();
        z = payload.z();
        label = payload.label();
    }

    public static void clear() {
        active = false;
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ID, WaypointHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null || mc.options.hideGui) {
            return;
        }
        double dx = x + 0.5D - mc.player.getX();
        double dz = z + 0.5D - mc.player.getZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        // The angle to the target, measured the way the player's yaw is (0 = south, clockwise).
        float toTarget = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float relative = Mth.wrapDegrees(toTarget - mc.player.getYRot());
        int arrow = Math.floorMod(Math.round(relative / 45.0F), 8);
        int centre = graphics.guiWidth() / 2;
        String text = label + " - " + distance + " blocks";
        int width = mc.font.width(text) + 22;
        graphics.fill(centre - width / 2, 3, centre + width / 2, 17, 0x90101010);
        graphics.pose().pushPose();
        graphics.pose().translate(centre - width / 2.0F + 4.0F, 4.0F, 0.0F);
        graphics.pose().scale(1.3F, 1.3F, 1.0F);
        graphics.drawString(mc.font, ARROWS[arrow], 0, 0, relative > -25.0F && relative < 25.0F ? 0xFF7CFF7C : 0xFFFFD27A, true);
        graphics.pose().popPose();
        graphics.drawString(mc.font, text, centre - width / 2 + 18, 6, 0xFFE6E0D6, true);
    }

    private WaypointHud() {
    }
}
