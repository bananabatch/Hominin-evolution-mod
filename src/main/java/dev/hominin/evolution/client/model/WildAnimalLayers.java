package dev.hominin.evolution.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Model geometry for the wild animals. Generated together with their textures from one
 * spec, so every box's UV layout matches the paint - edit both through the generator.
 */
public final class WildAnimalLayers {
    public static LayerDefinition baboon() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 18)
                .addBox(-3F, -3F, -6F, 6F, 6F, 12F), PartPose.offsetAndRotation(0F, 13.5F, 0.5F, -0.22F, 0F, 0F));
        body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(0, 36)
                .addBox(-3.5F, -3.8F, -6.5F, 7F, 5F, 5F), PartPose.offset(0F, 0F, 0F));
        PartDefinition tail_base = body.addOrReplaceChild("tail_base", CubeListBuilder.create().texOffs(48, 18)
                .addBox(-1F, -1F, 0F, 2F, 2F, 4F), PartPose.offsetAndRotation(0F, -2F, 6F, 0.9F, 0F, 0F));
        tail_base.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(48, 24)
                .addBox(-1F, -1F, 0F, 2F, 2F, 5F), PartPose.offsetAndRotation(0F, 0F, 4F, -1.9F, 0F, 0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3F, -3F, -5F, 6F, 6F, 5F), PartPose.offset(0F, 10.5F, -5F));
        head.addOrReplaceChild("ruff", CubeListBuilder.create().texOffs(22, 7)
                .addBox(-4F, -3.5F, -3F, 8F, 7F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-3F, -3.5F, -5.5F, 6F, 1F, 2F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("muzzle_base", CubeListBuilder.create().texOffs(22, 0)
                .addBox(-2F, -1F, -8F, 4F, 4F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("muzzle_tip", CubeListBuilder.create().texOffs(36, 0)
                .addBox(-1.5F, -0.5F, -11F, 3F, 3F, 3F), PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(44, 6)
                .addBox(-1.5F, 0F, -1.5F, 3F, 9F, 3F), PartPose.offset(-2F, 15F, -4F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(44, 6)
                .addBox(-1.5F, 0F, -1.5F, 3F, 9F, 3F), PartPose.offset(2F, 15F, -4F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(36, 18)
                .addBox(-1.5F, 0F, -1.5F, 3F, 7F, 3F), PartPose.offset(-2F, 17F, 4F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(36, 18)
                .addBox(-1.5F, 0F, -1.5F, 3F, 7F, 3F), PartPose.offset(2F, 17F, 4F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition pachycrocuta() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16)
                .addBox(-4F, -4F, -8F, 8F, 9F, 16F), PartPose.offsetAndRotation(0F, 11F, 0F, -0.12F, 0F, 0F));
        body.addOrReplaceChild("ridge", CubeListBuilder.create().texOffs(0, 42)
                .addBox(-1F, -6F, -8F, 2F, 2F, 12F), PartPose.offset(0F, 0F, 0F));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(56, 42)
                .addBox(-1F, 0F, 0F, 2F, 7F, 2F), PartPose.offsetAndRotation(0F, -3F, 8F, 0.4F, 0F, 0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4F, -4F, -6F, 8F, 8F, 6F), PartPose.offset(0F, 7F, -8F));
        head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(30, 0)
                .addBox(-2.5F, 0F, -10F, 5F, 4F, 4F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(50, 0)
                .addBox(1.5F, -6F, -2F, 2F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(56, 0)
                .addBox(-3.5F, -6F, -2F, 2F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(32, 51)
                .addBox(-2F, 0F, -2F, 4F, 9F, 4F), PartPose.offset(-2.5F, 15F, -5F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(32, 51)
                .addBox(-2F, 0F, -2F, 4F, 9F, 4F), PartPose.offset(2.5F, 15F, -5F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(48, 51)
                .addBox(-2F, 0F, -2F, 4F, 7F, 4F), PartPose.offset(-2.5F, 17F, 6F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(48, 51)
                .addBox(-2F, 0F, -2F, 4F, 7F, 4F), PartPose.offset(2.5F, 17F, 6F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition sabertooth() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-4.5F, -4.5F, -10F, 9F, 9F, 20F), PartPose.offset(0F, 10F, 0F));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(30, 8)
                .addBox(-1F, 0F, 0F, 2F, 2F, 5F), PartPose.offsetAndRotation(0F, -3F, 10F, -0.7F, 0F, 0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4F, -4F, -7F, 8F, 7F, 7F), PartPose.offset(0F, 7F, -10F));
        head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(30, 0)
                .addBox(-2.5F, -1F, -10F, 5F, 4F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(46, 0)
                .addBox(1F, 3F, -9.5F, 1F, 5F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(46, 0)
                .addBox(-2F, 3F, -9.5F, 1F, 5F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(50, 0)
                .addBox(2F, -6F, -3F, 2F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(50, 0)
                .addBox(-4F, -6F, -3F, 2F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(0, 50)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(-3F, 14F, -7F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(0, 50)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(3F, 14F, -7F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(0, 50)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(-3F, 14F, 8F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(0, 50)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(3F, 14F, 8F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition homotherium() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-4.5F, -5F, -9F, 9F, 9F, 18F), PartPose.offsetAndRotation(0F, 9F, 0F, 0.08F, 0F, 0F));
        body.addOrReplaceChild("shoulders", CubeListBuilder.create().texOffs(0, 48)
                .addBox(-5F, -6.5F, -8F, 10F, 4F, 8F), PartPose.offset(0F, 0F, 0F));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(30, 8)
                .addBox(-1F, 0F, 0F, 2F, 2F, 4F), PartPose.offsetAndRotation(0F, -4F, 9F, -0.5F, 0F, 0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4F, -4F, -7F, 8F, 7F, 7F), PartPose.offset(0F, 6F, -9F));
        head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(30, 0)
                .addBox(-2.5F, -1F, -10F, 5F, 4F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(46, 0)
                .addBox(1F, 2.5F, -9.5F, 1F, 3F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(46, 0)
                .addBox(-2F, 2.5F, -9.5F, 1F, 3F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(50, 0)
                .addBox(2F, -6F, -3F, 2F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(50, 0)
                .addBox(-4F, -6F, -3F, 2F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(0, 50)
                .addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.offset(-3F, 12F, -6F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(0, 50)
                .addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.offset(3F, 12F, -6F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(0, 50)
                .addBox(-2F, 0F, -2F, 4F, 11F, 4F), PartPose.offset(-3F, 13F, 7F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(0, 50)
                .addBox(-2F, 0F, -2F, 4F, 11F, 4F), PartPose.offset(3F, 13F, 7F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition crownedEagle() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-3.5F, -3F, -5F, 7F, 7F, 11F), PartPose.offsetAndRotation(0F, 15F, 0F, 0.15F, 0F, 0F));
        body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(30, 20)
                .addBox(-3F, -1F, 0F, 6F, 1F, 9F), PartPose.offsetAndRotation(0F, 2F, 5.5F, 0.25F, 0F, 0F));
        body.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 40)
                .addBox(0F, -1F, -5F, 13F, 1F, 10F), PartPose.offset(3.5F, -2F, 0F));
        body.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(0, 40)
                .addBox(-13F, -1F, -5F, 13F, 1F, 10F), PartPose.offset(-3.5F, -2F, 0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-2.5F, -3F, -3F, 5F, 5F, 5F), PartPose.offset(0F, 12.5F, -4.5F));
        head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(22, 0)
                .addBox(-2.5F, -5.5F, -1F, 5F, 3F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(40, 0)
                .addBox(-1F, -0.5F, -6F, 2F, 2F, 3F), PartPose.offset(0F, 0F, 0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(44, 10)
                .addBox(-1F, 0F, -1F, 2F, 4F, 2F), PartPose.offset(2F, 18F, -1F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(44, 10)
                .addBox(-1F, 0F, -1F, 2F, 4F, 2F), PartPose.offset(-2F, 18F, -1F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private WildAnimalLayers() {
    }
}
