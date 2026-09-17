package dev.hominin.evolution.client.model;

import javax.annotation.Nullable;

import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/** Four legs, a head that turns, and a tail that swings as the animal walks. */
public class WildQuadrupedModel<T extends Mob> extends QuadrupedModel<T> {
    @Nullable
    private final ModelPart tail;
    private final float tailRest;

    public WildQuadrupedModel(ModelPart root) {
        super(root, false, 10.0F, 4.0F, 2.0F, 2.0F, 24);
        ModelPart body = root.getChild("body");
        this.tail = body.hasChild("tail") ? body.getChild("tail") : null;
        this.tailRest = tail == null ? 0.0F : tail.xRot;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (tail != null) {
            tail.yRot = Mth.cos(limbSwing * 0.6662F) * 0.5F * limbSwingAmount
                    + Mth.sin(ageInTicks * 0.08F) * 0.08F;
            tail.xRot = tailRest;
        }
    }
}
