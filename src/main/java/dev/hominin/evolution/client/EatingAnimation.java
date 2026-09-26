package dev.hominin.evolution.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * Eating food that has some bulk to it. In third person the hand is held up at the mouth, bobbing with each bite (see
 * {@link HeldAnimationHandler}) - for any food drawn with a 3D model. First person keeps vanilla's eating view.
 */
public final class EatingAnimation {
    /** Whether this is food, eaten the ordinary way, and drawn with a model rather than a flat sprite. */
    public static boolean is3dFood(ItemStack stack, @Nullable LivingEntity holder) {
        if (stack.isEmpty() || stack.getUseAnimation() != UseAnim.EAT) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        // Flat item sprites bake as 2D; element models (the marrow, the grub, the roots) bake as 3D.
        return mc.getItemRenderer().getModel(stack, mc.level, holder, 0).isGui3d();
    }

    /** Which arm is holding what this player is eating. */
    public static HumanoidArm eatingArm(LivingEntity eater) {
        return eater.getUsedItemHand() == InteractionHand.MAIN_HAND ? eater.getMainArm()
                : eater.getMainArm().getOpposite();
    }

    private EatingAnimation() {
    }
}
