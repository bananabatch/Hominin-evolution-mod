package dev.hominin.evolution.client;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Eating food that has some bulk to it. Vanilla's eating pose is made for a flat sprite: it swings the item round
 * ninety degrees to show its face, which on a model with depth turns it edge-on. Anything edible drawn with a 3D model
 * is instead brought up to the mouth and eaten - smaller with each bite, a bite for every one of vanilla's chews.
 *
 * <p>In third person the hand goes to the mouth (see {@link HeldAnimationHandler}); that part is universal, for any
 * 3D food. The first-person view is hooked per item, so it covers this mod's foods.
 */
public final class EatingAnimation {
    /** How much of it is left when the last bite goes in. */
    private static final float EATEN_DOWN_TO = 0.5F;

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

    private static final IClientItemExtensions FIRST_PERSON = new IClientItemExtensions() {
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                float partialTick, float equipProcess, float swingProcess) {
            if (!player.isUsingItem() || player.getUseItemRemainingTicks() <= 0 || eatingArm(player) != arm
                    || !is3dFood(stack, player)) {
                return false;
            }
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            int duration = Math.max(1, stack.getUseDuration(player));
            float left = player.getUseItemRemainingTicks() - partialTick + 1.0F;
            // Up to the mouth as quickly as vanilla lifts it.
            float lifted = 1.0F - (float) Math.pow(Mth.clamp(left / duration, 0.0F, 1.0F), 27.0D);
            // Vanilla chews every four ticks once the first seven are past: one bite to each chew.
            float chewing = Math.max(1.0F, duration - 7.0F);
            int bites = Math.max(1, Mth.floor(chewing / 4.0F));
            float progress = Mth.clamp((chewing - left) / chewing, 0.0F, 1.0F) * bites;
            float taken = Mth.floor(progress) / (float) bites;
            float sinceBite = progress - Mth.floor(progress);
            // The jaw closing: a quick push in and squeeze, just after each bite.
            float chomp = progress >= 1.0F && sinceBite < 0.35F ? Mth.sin(sinceBite / 0.35F * Mth.PI) : 0.0F;
            float bob = left / duration < 0.8F ? Mth.abs(Mth.cos(left / 4.0F * Mth.PI) * 0.05F) : 0.0F;

            pose.translate(side * Mth.lerp(lifted, 0.56F, 0.16F),
                    Mth.lerp(lifted, -0.52F + equipProcess * -0.6F, -0.36F) + bob,
                    Mth.lerp(lifted, -0.72F, -0.54F) + chomp * 0.05F);
            // Turned in toward the mouth and tipped up to it.
            pose.mulPose(Axis.YP.rotationDegrees(side * lifted * 25.0F));
            pose.mulPose(Axis.XP.rotationDegrees(lifted * 15.0F));
            float scale = (1.0F - (1.0F - EATEN_DOWN_TO) * taken) * (1.0F - 0.07F * chomp);
            pose.scale(scale, scale, scale);
            return true;
        }
    };

    /** Every food this mod adds eats this way in first person when its model is 3D; flat ones keep vanilla's. */
    public static void register(RegisterClientExtensionsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(HomininEvolutionMod.MODID)
                    && item.components().has(DataComponents.FOOD)) {
                event.registerItem(FIRST_PERSON, item);
            }
        }
    }

    private EatingAnimation() {
    }
}
