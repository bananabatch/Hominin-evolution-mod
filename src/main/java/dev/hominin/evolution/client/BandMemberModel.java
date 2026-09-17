package dev.hominin.evolution.client;

import java.util.List;
import java.util.function.Supplier;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The player model, posed with the same animations a player gets: the climb, the threat
 * display, and each weapon's carry and strike.
 */
public class BandMemberModel extends PlayerModel<BandMember> {
    private record Held(Supplier<Item> item, ResourceLocation hold, ResourceLocation strike) {
    }

    private static final ResourceLocation CLIMB = id("climb");
    private static final ResourceLocation DISPLAY = id("threat_display");
    private static final int DISPLAY_TICKS = 30;

    /** Same pairings as the player's held animations. */
    private static final List<Held> HELD = List.of(
            new Held(ModItems.SHARPENED_SPEAR, id("spear_hold"), id("spear_thrust")),
            new Held(ModItems.FIRE_HARDENED_SPEAR, id("spear_hold"), id("spear_thrust")),
            new Held(ModItems.LONG_BRANCH, id("branch_hold"), id("branch_swing")),
            new Held(ModItems.SHARPENED_STICK, null, id("stick_stab")),
            new Held(ModItems.POINTY_STICK, null, id("stick_stab")),
            new Held(ModItems.FLAKE, null, id("flake_slash")));

    public BandMemberModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(BandMember member, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        super.setupAnim(member, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float partial = ageInTicks - member.tickCount;
        int sinceDisplay = member.tickCount - member.clientDisplayStart;
        if (sinceDisplay >= 0 && sinceDisplay < DISPLAY_TICKS) {
            play(DISPLAY, sinceDisplay + partial);
        } else if (member.isClimbingTree() || member.isWallClimbing()) {
            play(CLIMB, ageInTicks);
        } else {
            playHeld(member.getMainHandItem(), ageInTicks);
        }
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        jacket.copyFrom(body);
    }

    private void playHeld(ItemStack stack, float ageInTicks) {
        for (Held held : HELD) {
            if (!stack.is(held.item().get())) {
                continue;
            }
            KeyframeAnimations.Animation strike = KeyframeAnimations.get(held.strike());
            if (attackTime > 0.0F && strike != null) {
                // The model's swing runs 0 to 1; stretch the strike over it.
                strike.apply(this, attackTime * strike.length());
            } else if (held.hold() != null) {
                play(held.hold(), ageInTicks);
            }
            return;
        }
    }

    private void play(ResourceLocation id, float tick) {
        KeyframeAnimations.Animation animation = KeyframeAnimations.get(id);
        if (animation != null) {
            animation.apply(this, tick);
        }
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, name);
    }
}
