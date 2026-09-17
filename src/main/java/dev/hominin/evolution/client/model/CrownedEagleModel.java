package dev.hominin.evolution.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/**
 * A bird: wings that beat while it is in the air and fold when it is on the ground, a
 * head that tracks what it is looking at, and a tail that fans as it turns.
 */
public class CrownedEagleModel<T extends Mob> extends EntityModel<T> {
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public CrownedEagleModel(ModelPart root) {
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.tail = body.getChild("tail");
        this.leftWing = body.getChild("left_wing");
        this.rightWing = body.getChild("right_wing");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;

        boolean flying = !entity.onGround();
        if (flying) {
            // A slow, heavy beat - a bird this size does not flutter.
            float beat = Mth.cos(ageInTicks * 0.45F) * 0.55F;
            leftWing.zRot = -0.25F + beat;
            rightWing.zRot = 0.25F - beat;
            leftWing.yRot = 0.0F;
            rightWing.yRot = 0.0F;
            body.xRot = 0.15F + Mth.sin(ageInTicks * 0.2F) * 0.05F;
            // Legs tucked up under it.
            leftLeg.xRot = -1.2F;
            rightLeg.xRot = -1.2F;
            tail.xRot = 0.25F + Mth.sin(ageInTicks * 0.3F) * 0.08F;
        } else {
            // Folded against the body, and shifting its weight now and then.
            leftWing.zRot = -0.05F;
            rightWing.zRot = 0.05F;
            leftWing.yRot = -0.9F;
            rightWing.yRot = 0.9F;
            body.xRot = 0.35F;
            leftLeg.xRot = Mth.cos(limbSwing * 0.8F) * 0.9F * limbSwingAmount;
            rightLeg.xRot = Mth.cos(limbSwing * 0.8F + Mth.PI) * 0.9F * limbSwingAmount;
            tail.xRot = 0.5F;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int colour) {
        body.render(poseStack, buffer, light, overlay, colour);
        head.render(poseStack, buffer, light, overlay, colour);
        leftLeg.render(poseStack, buffer, light, overlay, colour);
        rightLeg.render(poseStack, buffer, light, overlay, colour);
    }
}
