package dev.hominin.evolution.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Your own hands while you knap: what is drawn in them, in first person and third, without it ever being put in your
 * inventory. Everyone else sees the same through the ordinary equipment packet; you are the one it must not reach,
 * or the client puts it in your selected slot - a second tool in your hotbar beside the real one.
 */
public final class KnapHands {
    private static boolean shown;
    private static ItemStack main = ItemStack.EMPTY;
    private static ItemStack off = ItemStack.EMPTY;

    public static void show(ItemStack mainHand, ItemStack offHand) {
        shown = true;
        main = mainHand;
        off = offHand;
    }

    public static void clear() {
        shown = false;
        main = ItemStack.EMPTY;
        off = ItemStack.EMPTY;
    }

    /** What to draw in this hand instead of what is there: only ever for you, only while you knap. */
    @Nullable
    public static ItemStack override(Player player, EquipmentSlot slot) {
        if (!shown || slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) {
            return null;
        }
        if (!player.level().isClientSide() || player != Minecraft.getInstance().player) {
            return null;
        }
        return slot == EquipmentSlot.MAINHAND ? main : off;
    }

    private KnapHands() {
    }
}
