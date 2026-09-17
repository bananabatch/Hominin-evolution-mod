package dev.hominin.evolution.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hominin.evolution.inventory.InventoryLimits;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A locked inventory slot takes nothing, and cannot be clicked or hovered. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    private boolean hominin$locked() {
        Slot self = (Slot) (Object) this;
        return self.container instanceof Inventory inventory
                && InventoryLimits.isLocked(inventory.player, self.getContainerSlot());
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void hominin$lockedMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (hominin$locked()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void hominin$lockedIsActive(CallbackInfoReturnable<Boolean> cir) {
        if (hominin$locked()) {
            cir.setReturnValue(false);
        }
    }
}
