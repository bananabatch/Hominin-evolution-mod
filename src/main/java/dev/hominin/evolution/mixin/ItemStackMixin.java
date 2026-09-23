package dev.hominin.evolution.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hominin.evolution.item.AcheuleanToolItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Tags belong to items, not to single stacks - but a flawless hand axe is a multitool and an
 * ordinary one is not. So a flawless one answers yes to the multitool's tags wherever anything
 * asks, and every check in the mod that wants a flake or a hammerstone takes it as one.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void hominin$flawlessMultitool(TagKey<Item> tag, CallbackInfoReturnable<Boolean> cir) {
        ItemStack self = (ItemStack) (Object) this;
        if (self.getItem() instanceof AcheuleanToolItem && AcheuleanToolItem.isMultitoolRole(tag)
                && AcheuleanToolItem.isMultitool(self)) {
            cir.setReturnValue(true);
        }
    }
}
