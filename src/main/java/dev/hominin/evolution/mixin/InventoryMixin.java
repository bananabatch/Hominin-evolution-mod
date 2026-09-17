package dev.hominin.evolution.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hominin.evolution.inventory.InventoryLimits;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps picked-up and given items out of the slots a player's stage has not opened. Every
 * way into the inventory - picking up, crafting, being handed something - finds its slot
 * through these two methods.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public NonNullList<ItemStack> items;

    @Shadow
    @Final
    public Player player;

    @Shadow
    public int selected;

    @Shadow
    private boolean hasRemainingSpaceForItem(ItemStack destination, ItemStack origin) {
        throw new AssertionError();
    }

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void hominin$freeSlotWithinLimit(CallbackInfoReturnable<Integer> cir) {
        int unlocked = InventoryLimits.unlockedSlots(player);
        if (unlocked >= items.size()) {
            return;
        }
        for (int i = 0; i < unlocked; i++) {
            if (items.get(i).isEmpty()) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void hominin$stackWithinLimit(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        int unlocked = InventoryLimits.unlockedSlots(player);
        if (unlocked >= items.size()) {
            return;
        }
        Inventory self = (Inventory) (Object) this;
        if (hasRemainingSpaceForItem(self.getItem(selected), stack)) {
            cir.setReturnValue(selected);
            return;
        }
        if (hasRemainingSpaceForItem(self.getItem(Inventory.SLOT_OFFHAND), stack)) {
            cir.setReturnValue(Inventory.SLOT_OFFHAND);
            return;
        }
        for (int i = 0; i < unlocked; i++) {
            if (hasRemainingSpaceForItem(items.get(i), stack)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }
}
