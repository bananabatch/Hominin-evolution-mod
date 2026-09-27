package dev.hominin.evolution.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hominin.evolution.client.KnapHands;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * While you knap, your hands are drawn holding the work - the first-person hands and your body in third person both
 * ask this one method - while your inventory, and the hotbar drawn from it, stay exactly as they are.
 */
@Mixin(Player.class)
public abstract class PlayerHeldItemMixin {
    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
    private void hominin$knapHands(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack shown = KnapHands.override((Player) (Object) this, slot);
        if (shown != null) {
            cir.setReturnValue(shown);
        }
    }
}
