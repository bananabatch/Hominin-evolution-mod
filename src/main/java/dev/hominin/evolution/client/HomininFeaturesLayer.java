package dev.hominin.evolution.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.function.Function;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The parts of an early hominin face a flat skin cannot show: a brow ridge that stands
 * out over the eyes, and a jaw that pushes forward of them. Attached to the head, so
 * they turn, nod and animate with it.
 */
public class HomininFeaturesLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final int TEXTURE_WIDTH = 32;
    private static final int TEXTURE_HEIGHT = 16;

    private static final float BABY_HEAD_SCALE = 1.5F / 2.0F;
    private static final float BABY_HEAD_Y_OFFSET = 16.0F / 16.0F;

    private final Map<ModelLayerLocation, ModelPart> parts = new HashMap<>();
    private final Function<T, HomininModels.Look> lookOf;

    public HomininFeaturesLayer(RenderLayerParent<T, M> parent, EntityModelSet models,
            Collection<HomininModels.Look> looks, Function<T, HomininModels.Look> lookOf) {
        super(parent);
        this.lookOf = lookOf;
        for (HomininModels.Look look : looks) {
            parts.put(look.layer(), models.bakeLayer(look.layer()));
        }
    }

    /** Heavy brow, and a muzzle that juts two pixels proud of the face. */
    public static LayerDefinition australopithecus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -6.0F, -5.0F, 8, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 2)
                .addBox(-3.0F, -4.0F, -6.0F, 6, 4, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /** Still a strong brow, but the face has pulled back: a smaller jaw, half as far forward. */
    public static LayerDefinition habilis() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -6.0F, -4.7F, 8, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 2)
                .addBox(-2.0F, -3.5F, -5.0F, 4, 3, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /**
     * Anamensis, the older form behind Australopithecus: a lower brow, and a muzzle that pushes out further and
     * deeper than Lucy's, over a heavy jaw.
     */
    public static LayerDefinition anamensis() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -5.5F, -5.0F, 8, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 2)
                .addBox(-3.0F, -4.0F, -7.0F, 6, 4, 3), PartPose.ZERO);
        root.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(18, 2)
                .addBox(-2.5F, -0.5F, -6.5F, 5, 1, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /**
     * Rudolfensis: a long face, broad and flat - not pushed forward like a muzzle but wide, standing only a little
     * proud of the face below the eyes - under a heavy brow, and a skull a size bigger than habilis's: the vault
     * stands up over the head. The eyes stay clear between the two.
     */
    public static LayerDefinition rudolfensis() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -6.0F, -4.7F, 8, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("face", CubeListBuilder.create().texOffs(0, 2)
                .addBox(-3.0F, -4.0F, -4.5F, 6, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("vault", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-3.5F, -8.8F, -3.5F, 7, 1, 7), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /**
     * Ergaster: much like erectus, but lighter built - a thinner brow ridge, a longer, narrower nose, and a smaller
     * mouth.
     */
    public static LayerDefinition ergaster() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -6.0F, -4.4F, 7, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 2)
                .addBox(-1.0F, -5.0F, -4.9F, 2, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(0, 6)
                .addBox(-2.5F, -2.2F, -4.3F, 5, 2, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /**
     * Paranthropus boisei, "Nutcracker Man": a ridge of bone along the top of the skull for
     * the chewing muscles to anchor on, cheekbones flaring out to the sides, and a broad,
     * flat, deep face with a jaw built for grinding.
     */
    public static LayerDefinition paranthropus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -6.0F, -5.0F, 8, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 2)
                .addBox(-3.5F, -4.0F, -5.8F, 7, 4, 2), PartPose.ZERO);
        root.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(18, 0)
                .addBox(-0.5F, -9.0F, -4.0F, 1, 1, 6), PartPose.ZERO);
        root.addOrReplaceChild("right_cheek", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-5.0F, -4.5F, -4.5F, 1, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("left_cheek", CubeListBuilder.create().texOffs(0, 8)
                .addBox(4.0F, -4.5F, -4.5F, 1, 2, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /**
     * Erectus: the brow ridge is still there, but the face under it has pulled back nearly flat -
     * a nose that stands out from it (erectus is the first hominin thought to have had one), over a
     * mouth that projects only a little. Both boxes sit mostly inside the head, so only a sliver
     * of each stands proud.
     */
    public static LayerDefinition erectus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.0F, -6.0F, -4.6F, 8, 1, 1), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(0, 2)
                .addBox(-1.0F, -4.5F, -4.8F, 2, 2, 1), PartPose.ZERO);
        root.addOrReplaceChild("mouth", CubeListBuilder.create().texOffs(0, 5)
                .addBox(-3.0F, -2.5F, -4.4F, 6, 2, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T player,
            float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
            float headPitch) {
        HomininModels.Look look = lookOf.apply(player);
        ModelPart head = getParentModel().head;
        if (look == null || player.isInvisible() || !head.visible) {
            return;
        }
        ModelPart features = parts.get(look.layer());
        if (features == null) {
            return;
        }
        poseStack.pushPose();
        if (getParentModel().young) {
            // Same numbers HumanoidModel hands AgeableListModel for its young head.
            poseStack.scale(BABY_HEAD_SCALE, BABY_HEAD_SCALE, BABY_HEAD_SCALE);
            poseStack.translate(0.0F, BABY_HEAD_Y_OFFSET, 0.0F);
        }
        head.translateAndRotate(poseStack);
        features.render(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(look.features())),
                packedLight, LivingEntityRenderer.getOverlayCoords(player, 0.0F));
        poseStack.popPose();
    }
}
