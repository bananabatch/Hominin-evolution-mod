package dev.hominin.evolution.client;

import dev.hominin.evolution.inventory.InventoryLimits;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

/** Greys out the inventory slots the player's stage has not opened yet. */
public final class LockedSlotOverlay {
    private static final int SHADE = 0xC0202020;
    private static final int MARK = 0xFF555555;

    public static void onRender(ContainerScreenEvent.Render.Foreground event) {
        GuiGraphics graphics = event.getGuiGraphics();
        for (Slot slot : event.getContainerScreen().getMenu().slots) {
            if (slot.container instanceof Inventory inventory
                    && InventoryLimits.isLocked(inventory.player, slot.getContainerSlot())) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, SHADE);
                // A small cross, so it reads as closed rather than empty.
                for (int i = 4; i < 12; i++) {
                    graphics.fill(slot.x + i, slot.y + i, slot.x + i + 1, slot.y + i + 1, MARK);
                    graphics.fill(slot.x + 15 - i, slot.y + i, slot.x + 16 - i, slot.y + i + 1, MARK);
                }
            }
        }
    }

    private LockedSlotOverlay() {
    }
}
