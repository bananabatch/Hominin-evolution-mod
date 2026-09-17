package dev.hominin.evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;

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
    public BandMemberRenderer(EntityRendererProvider.Context context) {
        super(context, new BandMemberModel(context.bakeLayer(ModelLayers.PLAYER)), 0.45F);
        addLayer(new HomininFeaturesLayer<>(this, context.getModelSet(), HomininModels.allLooks(),
                member -> HomininModels.lookForStage(member.getStage())));
    }

    @Override
    public ResourceLocation getTextureLocation(BandMember member) {
        return HomininModels.lookForStage(member.getStage()).skin();
    }

    @Override
    protected void scale(BandMember member, PoseStack poseStack, float partialTick) {
        float scale = HomininModels.lookForStage(member.getStage()).scale();
        poseStack.scale(scale, scale, scale);
    }
}
