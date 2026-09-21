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

    /**
     * The giant baboon. Built on the same plan as its smaller cousin and wrong in every
     * proportion: deeper chest, heavier shoulders, a muzzle carrying the largest canines
     * of any monkey that has ever lived - and the tail, which on a baboon is a thin thing
     * it carries in an arch and on this is a counterweight as long as the animal is.
     */
    public static LayerDefinition dinopithecus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 13)
                .addBox(-4F, -3.5F, -7F, 8F, 7F, 14F), PartPose.offsetAndRotation(0F, 13F, 0.5F, -0.26F, 0F, 0F));
        // The shoulder cape an adult male carries, and the ridge of the spine under it.
        body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(46, 13)
                .addBox(-4.5F, -4.6F, -7.5F, 9F, 6F, 6F), PartPose.offset(0F, 0F, 0F));
        body.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(78, 13)
                .addBox(-1F, -5F, -1.5F, 2F, 2F, 9F), PartPose.offset(0F, 0F, 0F));

        // Three segments rather than two, each one longer, and held in a high arch.
        PartDefinition tail_base = body.addOrReplaceChild("tail_base", CubeListBuilder.create().texOffs(0, 35)
                .addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F), PartPose.offsetAndRotation(0F, -2.5F, 7F, 1.05F, 0F, 0F));
        PartDefinition tail_mid = tail_base.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(20, 35)
                .addBox(-1F, -1F, 0F, 2F, 2F, 7F), PartPose.offsetAndRotation(0F, 0F, 6F, -2.0F, 0F, 0F));
        tail_mid.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(40, 35)
                .addBox(-0.5F, -0.5F, 0F, 1F, 1F, 6F), PartPose.offsetAndRotation(0F, 0F, 7F, 0.35F, 0F, 0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -3.5F, -5F, 7F, 7F, 5F), PartPose.offset(0F, 10F, -5.5F));
        head.addOrReplaceChild("ruff", CubeListBuilder.create().texOffs(26, 0)
                .addBox(-5F, -4F, -3F, 10F, 8F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(100, 0)
                .addBox(-3.5F, -4F, -5.5F, 7F, 1.5F, 2F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("muzzle_base", CubeListBuilder.create().texOffs(54, 0)
                .addBox(-2.5F, -1F, -9F, 5F, 5F, 4F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("muzzle_tip", CubeListBuilder.create().texOffs(74, 0)
                .addBox(-2F, -0.5F, -12F, 4F, 4F, 3F), PartPose.offset(0F, 0F, 0F));
        // The canines, which are the entire reason anybody remembers this animal.
        // Two canines, one each side of the jaw with a gap between them - not a single
        // block of tooth across the front.
        head.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(90, 0)
                .addBox(0.6F, 2.5F, -11.5F, 1F, 3F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(90, 0)
                .addBox(-1.6F, 2.5F, -11.5F, 1F, 3F, 1F), PartPose.offset(0F, 0F, 0F));

        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(56, 35)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(-2.5F, 14F, -4.5F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(56, 35)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(2.5F, 14F, -4.5F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(74, 35)
                .addBox(-2F, 0F, -2F, 4F, 8F, 4F), PartPose.offset(-2.5F, 16F, 4.5F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(74, 35)
                .addBox(-2F, 0F, -2F, 4F, 8F, 4F), PartPose.offset(2.5F, 16F, 4.5F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /**
     * A chimpanzee, knuckle-walking: long arms planted on the knuckles in front, short legs
     * behind, so the body rides high at a shaggy shoulder hump and slopes to the hips.
     *
     * <p>The face is built in pieces because that is where a chimp is recognisable: a bare
     * skin mask under a heavy jutting brow, a long muzzle with a big mobile upper lip and a
     * short chin with grey whiskers painted on, and wide round ears set low on the sides of the head.
     * The hair is long at the shoulders and elbows; the hands and feet are bare.
     */
    public static LayerDefinition chimpanzee() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 18)
                .addBox(-5F, -4.5F, -7F, 10F, 9F, 14F), PartPose.offsetAndRotation(0F, 11.5F, 0F, -0.4F, 0F, 0F));
        body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(48, 18)
                .addBox(-5.5F, -5.8F, -7.5F, 11F, 6F, 7F), PartPose.offset(0F, 0F, 0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4F, -4F, -6F, 8F, 8F, 7F), PartPose.offset(0F, 6F, -8F));
        head.addOrReplaceChild("face", CubeListBuilder.create().texOffs(32, 0)
                .addBox(-3.5F, -3.5F, -6.5F, 7F, 5F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(50, 0)
                .addBox(-4F, -4.5F, -8F, 8F, 2F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(74, 0)
                .addBox(-3F, -0.5F, -9F, 6F, 5F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("upper_lip", CubeListBuilder.create().texOffs(96, 0)
                .addBox(-2.5F, 1.5F, -10F, 5F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("chin", CubeListBuilder.create().texOffs(96, 6)
                .addBox(-2F, 3.5F, -8.5F, 4F, 2F, 2F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(32, 8)
                .addBox(4F, -3F, -4F, 1F, 5F, 4F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(32, 8)
                .addBox(-5F, -3F, -4F, 1F, 5F, 4F), PartPose.offset(0F, 0F, 0F));

        for (String side : new String[] {"right", "left"}) {
            float x = side.equals("right") ? -4.5F : 4.5F;
            PartDefinition arm = root.addOrReplaceChild(side + "_front_leg", CubeListBuilder.create().texOffs(0, 42)
                    .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(x, 11F, -5.5F));
            arm.addOrReplaceChild(side + "_hand", CubeListBuilder.create().texOffs(18, 42)
                    .addBox(-1.5F, 10F, -2.5F, 3F, 3F, 4F), PartPose.offset(0F, 0F, 0F));
            arm.addOrReplaceChild(side + "_elbow_hair", CubeListBuilder.create().texOffs(34, 42)
                    .addBox(-2.5F, 0F, -2.5F, 5F, 4F, 5F), PartPose.offset(0F, 0F, 0F));
            float legX = side.equals("right") ? -3.2F : 3.2F;
            PartDefinition leg = root.addOrReplaceChild(side + "_hind_leg", CubeListBuilder.create().texOffs(56, 42)
                    .addBox(-2F, 0F, -2F, 4F, 6F, 4F), PartPose.offset(legX, 16F, 5.5F));
            leg.addOrReplaceChild(side + "_foot", CubeListBuilder.create().texOffs(74, 42)
                    .addBox(-2F, 6F, -3F, 4F, 2F, 5F), PartPose.offset(0F, 0F, 0F));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    /**
     * A crocodile: long and low, belly almost on the ground, legs splayed out to the sides.
     * The snout is a separate long wedge with its own lower jaw, the eyes sit up on bumps so
     * they clear the water, and a ridge of armour runs down the back into a three-part tail.
     */
    public static LayerDefinition crocodile() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4F, -2F, -8F, 8F, 4F, 16F), PartPose.offset(0F, 20F, 0F));
        body.addOrReplaceChild("ridge", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-2F, -3F, -7F, 4F, 1F, 14F), PartPose.offset(0F, 0F, 0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(36, 20)
                .addBox(-3F, -1.5F, 0F, 6F, 3F, 8F), PartPose.offset(0F, -0.5F, 8F));
        PartDefinition tailMid = tail.addOrReplaceChild("tail_mid", CubeListBuilder.create().texOffs(64, 20)
                .addBox(-2F, -1F, 0F, 4F, 2F, 8F), PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0.12F, 0F));
        tailMid.addOrReplaceChild("tail_tip", CubeListBuilder.create().texOffs(88, 20)
                .addBox(-1F, -0.5F, 0F, 2F, 1F, 6F), PartPose.offsetAndRotation(0F, 0F, 8F, 0F, 0.18F, 0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-3F, -2F, -6F, 6F, 4F, 6F), PartPose.offset(0F, 20F, -8F));
        head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(72, 0)
                .addBox(-2F, -1F, -14F, 4F, 2F, 8F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(96, 0)
                .addBox(-2F, 1F, -13F, 4F, 1F, 7F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(48, 10)
                .addBox(-2.5F, -3F, -4F, 2F, 1F, 2F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(48, 10)
                .addBox(0.5F, -3F, -4F, 2F, 1F, 2F), PartPose.offset(0F, 0F, 0F));

        for (String side : new String[] {"right", "left"}) {
            float x = side.equals("right") ? -4.5F : 4.5F;
            for (String end : new String[] {"front", "hind"}) {
                float z = end.equals("front") ? -5F : 5F;
                PartDefinition leg = root.addOrReplaceChild(side + "_" + end + "_leg", CubeListBuilder.create()
                        .texOffs(0, 36).addBox(-1.5F, 0F, -1.5F, 3F, 3F, 3F), PartPose.offset(x, 21F, z));
                leg.addOrReplaceChild(side + "_" + end + "_foot", CubeListBuilder.create().texOffs(12, 36)
                        .addBox(-2F, 2F, -3F, 4F, 1F, 4F), PartPose.offset(0F, 0F, 0F));
            }
        }
        return LayerDefinition.create(mesh, 128, 64);
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
