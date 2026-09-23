package dev.hominin.evolution.client;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

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
    /** A carry, a strike - and for tools worked into the ground, the stroke used there instead. */
    private record Held(Supplier<Item> item, ResourceLocation hold, ResourceLocation strike,
            @Nullable ResourceLocation groundStrike) {
        Held(Supplier<Item> item, ResourceLocation hold, ResourceLocation strike) {
            this(item, hold, strike, null);
        }
    }

    /** Looking this far down, a member swinging is working the ground, not fighting. */
    private static final float GROUND_PITCH = 35.0F;

    private static final ResourceLocation CLIMB = id("climb");
    private static final ResourceLocation GRIEVE = id("grieve");
    private static final ResourceLocation DISPLAY = id("threat_display");
    private static final int DISPLAY_TICKS = 30;

    /** Same pairings as the player's held animations. */
    private static final List<Held> HELD = List.of(
            new Held(ModItems.SHARPENED_SPEAR, id("spear_hold"), id("spear_thrust")),
            new Held(ModItems.FIRE_HARDENED_SPEAR, id("spear_hold"), id("spear_thrust")),
            new Held(ModItems.LONG_BRANCH, id("branch_hold"), id("branch_swing")),
            new Held(ModItems.WORKABLE_BRANCH, id("branch_hold"), id("branch_swing")),
            new Held(ModItems.WORKABLE_SHAFT, id("branch_hold"), id("branch_swing")),
            new Held(ModItems.WOODEN_CLUB, id("club_hold"), id("club_swing")),
            new Held(ModItems.SHARPENED_STICK, null, id("stick_stab")),
            new Held(ModItems.POINTY_STICK, null, id("stick_stab")),
            new Held(ModItems.FLAKE, null, id("flake_slash")),
            new Held(ModItems.HAND_AXE, id("hand_axe_hold"), id("hand_axe_slash")),
            new Held(ModItems.CLEAVER, id("cleaver_hold"), id("cleaver_push")),
            new Held(ModItems.CHOPPER, id("cleaver_hold"), id("cleaver_push")),
            new Held(ModItems.DIGGING_STICK, id("digging_stick_hold"), id("digging_stick_jab"),
                    id("digging_stick_dig")));

    /** Tools without a swing of their own slash like a flake. */
    private static final ResourceLocation DEFAULT_SLASH = id("flake_slash");

    /** An animation and how far into it a member is. */
    public record Playing(KeyframeAnimations.Animation animation, float tick) {
    }

    public BandMemberModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(BandMember member, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        super.setupAnim(member, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        Playing playing = playing(member, ageInTicks, attackTime);
        if (playing != null) {
            playing.animation().apply(this, playing.tick());
        }
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        jacket.copyFrom(body);
    }

    /**
     * What a member is playing right now, or null for vanilla's own pose. The renderer asks too:
     * the limbs are posed here, but a lean of the whole body is applied before the model is drawn.
     */
    @Nullable
    public static Playing playing(BandMember member, float ageInTicks, float attackTime) {
        if (member.isGrieving()) {
            return at(GRIEVE, ageInTicks);
        }
        int sinceDisplay = member.tickCount - member.clientDisplayStart;
        if (sinceDisplay >= 0 && sinceDisplay < DISPLAY_TICKS) {
            return at(DISPLAY, sinceDisplay + ageInTicks - member.tickCount);
        }
        if (member.isClimbingTree() || member.isWallClimbing()) {
            return at(CLIMB, ageInTicks);
        }
        return held(member.getMainHandItem(), ageInTicks, attackTime, member.getXRot() > GROUND_PITCH);
    }

    @Nullable
    private static Playing held(ItemStack stack, float ageInTicks, float attackTime, boolean atGround) {
        boolean known = false;
        for (Held held : HELD) {
            known |= stack.is(held.item().get());
        }
        if (!known && attackTime > 0.0F && isSwungTool(stack)) {
            return swing(DEFAULT_SLASH, attackTime);
        }
        for (Held held : HELD) {
            if (!stack.is(held.item().get())) {
                continue;
            }
            // Foraging, a member looks down at the spot it works: that swing is a dig, not a jab.
            ResourceLocation stroke = atGround && held.groundStrike() != null ? held.groundStrike() : held.strike();
            Playing strike = attackTime > 0.0F ? swing(stroke, attackTime) : null;
            if (strike != null) {
                return strike;
            }
            return held.hold() != null ? at(held.hold(), ageInTicks) : null;
        }
        return null;
    }

    /** The model's swing runs 0 to 1; a strike is stretched over it. */
    @Nullable
    private static Playing swing(ResourceLocation id, float attackTime) {
        KeyframeAnimations.Animation animation = KeyframeAnimations.get(id);
        return animation == null ? null : new Playing(animation, attackTime * animation.length());
    }

    /** The same test the player's animations use - kept here so this class never touches Player Animator. */
    private static boolean isSwungTool(ItemStack stack) {
        return !stack.isEmpty() && (stack.isDamageableItem()
                || stack.is(dev.hominin.evolution.ModTags.Items.STONE_TOOLS)
                || stack.is(dev.hominin.evolution.ModTags.Items.CUTTING_EDGE)
                || stack.is(dev.hominin.evolution.ModTags.Items.CHOPPERS)
                || stack.is(dev.hominin.evolution.ModTags.Items.HAMMERSTONES));
    }

    @Nullable
    private static Playing at(ResourceLocation id, float tick) {
        KeyframeAnimations.Animation animation = KeyframeAnimations.get(id);
        return animation == null ? null : new Playing(animation, tick);
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, name);
    }
}
