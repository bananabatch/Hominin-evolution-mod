package dev.hominin.evolution.client.model;

import javax.annotation.Nullable;

import dev.hominin.evolution.entity.Megafauna;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The later megafauna's model. Everything a wild quadruped does, and the tell before a charge: the
 * head comes up, the trunk (on a mammoth) goes up with it, and a forefoot stamps. That is the moment
 * to get out of the way.
 */
public class MegafaunaModel<T extends Megafauna> extends WildQuadrupedModel<T> {
    @Nullable
    private final ModelPart trunk;
    @Nullable
    private final ModelPart trunkTip;
    private final float trunkRest;

    public MegafaunaModel(ModelPart root) {
        super(root);
        ModelPart head = root.getChild("head");
        this.trunk = head.hasChild("trunk1") ? head.getChild("trunk1") : null;
        this.trunkTip = trunk != null && trunk.hasChild("trunk2") ? trunk.getChild("trunk2") : null;
        this.trunkRest = trunk == null ? 0.0F : trunk.xRot;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
            float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        boolean winding = entity.isWindingUp();
        if (trunk != null) {
            // A trunk is never quite still.
            trunk.xRot = trunkRest + Mth.sin(ageInTicks * 0.07F) * 0.06F;
            trunk.zRot = Mth.sin(ageInTicks * 0.05F + 1.0F) * 0.05F;
        }
        if (!winding) {
            return;
        }
        head.xRot -= 0.45F;
        if (trunk != null) {
            trunk.xRot = trunkRest - 1.4F;
            if (trunkTip != null) {
                trunkTip.xRot = -0.5F;
            }
        }
        // Stamping: one forefoot, hard and fast.
        rightFrontLeg.xRot = Math.max(0.0F, Mth.sin(ageInTicks * 0.9F)) * -0.7F;
    }
}
