package dev.hominin.evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.band.BandMember;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Band members share the player model and the players' hominin looks, so a band and
 * its leader read as the same kind of animal.
 */
public class BandMemberRenderer extends HumanoidMobRenderer<BandMember, BandMemberModel> {
    /** How far a seated hominin drops, so the pose meets the ground. */
    private static final float SEATED_DROP = 0.62F;

    public BandMemberRenderer(EntityRendererProvider.Context context) {
        super(context, new BandMemberModel(context.bakeLayer(ModelLayers.PLAYER)), 0.45F);
        addLayer(new HomininFeaturesLayer<>(this, context.getModelSet(), HomininModels.allLooks(),
                member -> HomininModels.lookForStage(member.getStage())));
    }

    @Override
    public ResourceLocation getTextureLocation(BandMember member) {
        return HomininModels.lookForStage(member.getStage()).skin();
    }

    /**
     * The whole-body lean an animation asks for, done exactly as Player Animator does it for a
     * player - shift, then roll, yaw and pitch about a point at the hips - so a band member and
     * its leader lean the same way from the same file.
     */
    @Override
    protected void setupRotations(BandMember member, PoseStack poseStack, float bob, float yBodyRot,
            float partialTick, float scale) {
        super.setupRotations(member, poseStack, bob, yBodyRot, partialTick, scale);
        BandMemberModel.Playing playing = BandMemberModel.playing(member, bob, member.getAttackAnim(partialTick));
        KeyframeAnimations.BodyPose pose = playing == null ? null : playing.animation().bodyPose(playing.tick());
        if (pose == null) {
            return;
        }
        float size = HomininModels.lookForStage(member.getStage()).scale();
        // Player Animator pivots 0.7 blocks up, a player's hips. A seated member's hips are down on
        // the ground, and it hunches over its legs from there.
        float pivot = member.isGrieving() ? 0.75F * size - SEATED_DROP : 0.7F * size;
        poseStack.translate(pose.x() * size, pose.y() * size + pivot, pose.z() * size);
        poseStack.mulPose(Axis.ZP.rotation(pose.roll()));
        poseStack.mulPose(Axis.YP.rotation(pose.yaw()));
        poseStack.mulPose(Axis.XP.rotation(pose.pitch()));
        poseStack.translate(0.0F, -pivot, 0.0F);
    }

    @Override
    protected void scale(BandMember member, PoseStack poseStack, float partialTick) {
        float scale = HomininModels.lookForStage(member.getStage()).scale();
        poseStack.scale(scale, scale, scale);
        if (member.isGrieving()) {
            // This frame is mirrored by the entity renderer, so a positive Y is downwards:
            // it sets a seated pose on the ground instead of hovering at standing height.
            poseStack.translate(0.0F, SEATED_DROP / scale, 0.0F);
        }
    }
}
