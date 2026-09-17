package dev.hominin.evolution.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hominin.evolution.client.HomininModels;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

/**
 * Gives a player their stage's skin. Everything that draws a player - the body, the
 * first-person arm, the tab list face - asks this one method, so changing the answer
 * here changes it everywhere at once.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void hominin$stageSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin original = cir.getReturnValue();
        PlayerSkin replaced = HomininModels.skinFor((AbstractClientPlayer) (Object) this, original);
        if (replaced != original) {
            cir.setReturnValue(replaced);
        }
    }
}
