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
     * The giant baboon, built as a baboon stands: forelimbs longer than the hind, so the shoulders ride high under
     * a heavy cape and the head is carried low and forward; a long ridged snout angled down, with the largest
     * canines of any monkey that ever lived; hands and feet flat on the ground; and the tail held up from the rump
     * and dropping away in the "broken" arch every baboon carries. Generated with its texture (scratchpad
     * dino_gen.py) so the UVs match the paint.
     */
    public static LayerDefinition dinopithecus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, -4F, -7.5F, 9F, 8F, 15F), PartPose.offsetAndRotation(0F, 12F, 1.5F, -0.2F, 0F, 0F));
        body.addOrReplaceChild("mane", CubeListBuilder.create().texOffs(49, 0)
                .addBox(-5F, -5.2F, -8.2F, 10F, 7F, 8F), PartPose.offset(0F, 0F, 0F));
        body.addOrReplaceChild("rump", CubeListBuilder.create().texOffs(86, 0)
                .addBox(-4.2F, -4.3F, 4F, 8.4F, 6F, 4F), PartPose.offset(0F, 0F, 0F));
        PartDefinition tail_base = body.addOrReplaceChild("tail_base", CubeListBuilder.create().texOffs(0, 24)
                .addBox(-1.25F, -1.25F, 0F, 2.5F, 2.5F, 5F), PartPose.offsetAndRotation(0F, -2.5F, 7.3F, 0.95F, 0F, 0F));
        PartDefinition tail_mid = tail_base.addOrReplaceChild("tail_mid", CubeListBuilder.create().texOffs(17, 24)
                .addBox(-1F, -1F, 0F, 2F, 2F, 7F), PartPose.offsetAndRotation(0F, 0F, 5F, -1.95F, 0F, 0F));
        tail_mid.addOrReplaceChild("tail_tuft", CubeListBuilder.create().texOffs(36, 24)
                .addBox(-1.3F, -1.3F, 0F, 2.6F, 2.6F, 2.5F), PartPose.offset(0F, 0F, 6.8F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(49, 24)
                .addBox(-3.5F, -3.5F, -6F, 7F, 7F, 6F), PartPose.offset(0F, 9.6F, -6.8F));
        head.addOrReplaceChild("ruff", CubeListBuilder.create().texOffs(76, 24)
                .addBox(-5.5F, -4.6F, -2F, 11F, 9F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(105, 24)
                .addBox(-4F, -4.1F, -6.6F, 8F, 2F, 2F), PartPose.offset(0F, 0F, 0F));
        PartDefinition muzzle = head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(0, 38)
                .addBox(-2.5F, -1.2F, -6.2F, 5F, 4.4F, 6.2F), PartPose.offsetAndRotation(0F, 0.4F, -5.4F, 0.2F, 0F, 0F));
        muzzle.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(25, 38)
                .addBox(-2.4F, -1.4F, -7.1F, 4.8F, 2.8F, 1.3F), PartPose.offset(0F, 0F, 0F));
        muzzle.addOrReplaceChild("lip", CubeListBuilder.create().texOffs(40, 38)
                .addBox(-2.3F, 1.4F, -6.6F, 4.6F, 1.8F, 0.6F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(53, 38)
                .addBox(0.9F, -2.1F, -6.35F, 1.6F, 1.1F, 0.5F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(53, 38)
                .addBox(-2.5F, -2.1F, -6.35F, 1.6F, 1.1F, 0.5F), PartPose.offset(0F, 0F, 0F));
        muzzle.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(60, 38)
                .addBox(-2F, 3.2F, -5.4F, 4F, 1.5F, 5F), PartPose.offset(0F, 0F, 0F));
        muzzle.addOrReplaceChild("left_fang", CubeListBuilder.create().texOffs(79, 38)
                .addBox(0.8F, 3F, -6.2F, 1F, 2.6F, 1F), PartPose.offset(0F, 0F, 0F));
        muzzle.addOrReplaceChild("right_fang", CubeListBuilder.create().texOffs(79, 38)
                .addBox(-1.8F, 3F, -6.2F, 1F, 2.6F, 1F), PartPose.offset(0F, 0F, 0F));
        PartDefinition right_front_leg = root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(84, 38)
                .addBox(-2.25F, 0F, -2.25F, 4.5F, 12F, 4.5F), PartPose.offset(-3.3F, 12F, -5.2F));
        right_front_leg.addOrReplaceChild("right_hand", CubeListBuilder.create().texOffs(105, 38)
                .addBox(-2.5F, 10.4F, -3.8F, 5F, 1.6F, 5.6F), PartPose.offset(0F, 0F, 0F));
        PartDefinition left_front_leg = root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(84, 38)
                .addBox(-2.25F, 0F, -2.25F, 4.5F, 12F, 4.5F), PartPose.offset(3.3F, 12F, -5.2F));
        left_front_leg.addOrReplaceChild("left_hand", CubeListBuilder.create().texOffs(105, 38)
                .addBox(-2.5F, 10.4F, -3.8F, 5F, 1.6F, 5.6F), PartPose.offset(0F, 0F, 0F));
        PartDefinition right_hind_leg = root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(0, 56)
                .addBox(-2.25F, 0F, -2.5F, 4.5F, 10F, 5F), PartPose.offset(-3.3F, 14F, 5.6F));
        right_hind_leg.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(21, 56)
                .addBox(-2.5F, 8.4F, -4.2F, 5F, 1.6F, 6.6F), PartPose.offset(0F, 0F, 0F));
        PartDefinition left_hind_leg = root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(0, 56)
                .addBox(-2.25F, 0F, -2.5F, 4.5F, 10F, 5F), PartPose.offset(3.3F, 14F, 5.6F));
        left_hind_leg.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(21, 56)
                .addBox(-2.5F, 8.4F, -4.2F, 5F, 1.6F, 6.6F), PartPose.offset(0F, 0F, 0F));
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

    /**
     * A bonobo: slighter than a chimpanzee in every way. A small round head with the hair
     * parted down the middle, a flat black face with no real brow, a short muzzle and pale
     * lips, small ears tucked into the hair, a narrow chest, and long thin limbs.
     */
    public static LayerDefinition bonobo() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 20)
                .addBox(-4F, -4F, -6F, 8F, 8F, 12F), PartPose.offsetAndRotation(0F, 12F, 0F, -0.3F, 0F, 0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-3.5F, -3.5F, -5F, 7F, 7F, 6F), PartPose.offset(0F, 7F, -7F));
        head.addOrReplaceChild("face", CubeListBuilder.create().texOffs(28, 0)
                .addBox(-3F, -3F, -5.5F, 6F, 5F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("brow", CubeListBuilder.create().texOffs(44, 0)
                .addBox(-3.5F, -3.8F, -6F, 7F, 1F, 2F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(62, 0)
                .addBox(-2.5F, -0.5F, -7.5F, 5F, 4F, 2F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("lips", CubeListBuilder.create().texOffs(78, 0)
                .addBox(-2F, 2F, -8F, 4F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(28, 8)
                .addBox(3.5F, -2F, -3F, 1F, 3F, 2F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(28, 8)
                .addBox(-4.5F, -2F, -3F, 1F, 3F, 2F), PartPose.offset(0F, 0F, 0F));

        for (String side : new String[] {"right", "left"}) {
            float x = side.equals("right") ? -4F : 4F;
            PartDefinition arm = root.addOrReplaceChild(side + "_front_leg", CubeListBuilder.create().texOffs(0, 42)
                    .addBox(-1.5F, 0F, -1.5F, 3F, 12F, 3F), PartPose.offset(x, 10F, -4F));
            arm.addOrReplaceChild(side + "_hand", CubeListBuilder.create().texOffs(14, 42)
                    .addBox(-1.5F, 12F, -2F, 3F, 2F, 3F), PartPose.offset(0F, 0F, 0F));
            float legX = side.equals("right") ? -2.5F : 2.5F;
            PartDefinition leg = root.addOrReplaceChild(side + "_hind_leg", CubeListBuilder.create().texOffs(28, 42)
                    .addBox(-1.5F, 0F, -1.5F, 3F, 8F, 3F), PartPose.offset(legX, 15F, 4.5F));
            leg.addOrReplaceChild(side + "_foot", CubeListBuilder.create().texOffs(42, 42)
                    .addBox(-1.5F, 8F, -2.5F, 3F, 1F, 4F), PartPose.offset(0F, 0F, 0F));
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

    /**
     * Pelorovis: a buffalo built too big. A deep barrel of a body with a hump over the shoulders,
     * the head carried low, and horns that go out sideways a long way before they curl up - the
     * whole reason the animal has its name.
     */
    public static LayerDefinition pelorovis() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-6F, -5.5F, -9F, 12F, 11F, 18F), PartPose.offset(0F, 9.5F, 1F));
        body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(60, 0)
                .addBox(-5F, -8F, -9F, 10F, 3F, 8F), PartPose.offset(0F, 0F, 0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(96, 0)
                .addBox(-1F, 0F, -1F, 2F, 9F, 2F), PartPose.offsetAndRotation(0F, -4.5F, 9F, 0.25F, 0F, 0F));
        tail.addOrReplaceChild("tuft", CubeListBuilder.create().texOffs(104, 0)
                .addBox(-1.5F, 8.5F, -1.5F, 3F, 3F, 3F), PartPose.offset(0F, 0F, 0F));

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 30)
                .addBox(-3.5F, -3F, -6F, 7F, 8F, 6F), PartPose.offset(0F, 7F, -8F));
        head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(26, 30)
                .addBox(-2.5F, 1F, -9F, 5F, 5F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("boss", CubeListBuilder.create().texOffs(42, 30)
                .addBox(-4.5F, -4.5F, -5F, 9F, 2F, 3F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(86, 30)
                .addBox(3.5F, -1F, -2F, 3F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(86, 30)
                .addBox(-6.5F, -1F, -2F, 3F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        // Out to the side and a little back, then curling up.
        PartDefinition leftHorn = head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(66, 30)
                .addBox(0F, -1F, -1F, 8F, 2F, 2F), PartPose.offsetAndRotation(4.5F, -3.5F, -3.5F, 0F, 0.3F, -0.2F));
        leftHorn.addOrReplaceChild("left_horn_tip", CubeListBuilder.create().texOffs(66, 34)
                .addBox(0F, -1F, -1F, 6F, 2F, 2F), PartPose.offsetAndRotation(7.5F, 0F, 0F, 0F, 0F, -0.95F));
        PartDefinition rightHorn = head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(66, 30)
                .addBox(-8F, -1F, -1F, 8F, 2F, 2F), PartPose.offsetAndRotation(-4.5F, -3.5F, -3.5F, 0F, -0.3F, 0.2F));
        rightHorn.addOrReplaceChild("right_horn_tip", CubeListBuilder.create().texOffs(66, 34)
                .addBox(-6F, -1F, -1F, 6F, 2F, 2F), PartPose.offsetAndRotation(-7.5F, 0F, 0F, 0F, 0F, 0.95F));

        root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(0, 44)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(-3.5F, 14F, -5F));
        root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(0, 44)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(3.5F, 14F, -5F));
        root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(16, 44)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(-3.5F, 14F, 7F));
        root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(16, 44)
                .addBox(-2F, 0F, -2F, 4F, 10F, 4F), PartPose.offset(3.5F, 14F, 7F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    /**
     * Mammuthus subplanifrons: the first mammoth, still an African animal. A great domed head held
     * higher than the shoulders, a heavy body falling away to the rump, pillar legs, ears smaller than an
     * elephant's, a trunk that curls at the tip, and tusks that go down and then sweep forward and up.
     */
    public static LayerDefinition mammuthus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-7F, -7F, -10F, 14F, 14F, 22F), PartPose.offset(0F, 4F, 1F));
        PartDefinition hump = body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(52, 55)
                .addBox(-6F, -10F, -10F, 12F, 4F, 11F), PartPose.offset(0F, 0F, 0F));
        PartDefinition belly = body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 36)
                .addBox(-6F, 5F, -7F, 12F, 3F, 16F), PartPose.offset(0F, 0F, 0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(44, 73)
                .addBox(-0.5F, 0F, 0F, 1F, 9F, 1F), PartPose.offsetAndRotation(0F, -4F, 12F, 0.3F, 0F, 0F));
        PartDefinition tuft = tail.addOrReplaceChild("tuft", CubeListBuilder.create().texOffs(8, 84)
                .addBox(-1F, 8F, -1F, 2F, 3F, 2F), PartPose.offset(0F, 0F, 0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(72, 0)
                .addBox(-5F, -9F, -8F, 10F, 13F, 8F), PartPose.offset(0F, -1F, -9F));
        PartDefinition dome = head.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(96, 73)
                .addBox(-4.5F, -11F, -7F, 9F, 2F, 6F), PartPose.offset(0F, 0F, 0F));
        PartDefinition leftEar = head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(20, 55)
                .addBox(0F, 0F, 0F, 1F, 9F, 7F), PartPose.offsetAndRotation(5F, -7F, -4F, 0F, 0.45F, 0.08F));
        PartDefinition rightEar = head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(36, 55)
                .addBox(-1F, 0F, 0F, 1F, 9F, 7F), PartPose.offsetAndRotation(-5F, -7F, -4F, 0F, -0.45F, -0.08F));
        PartDefinition trunk1 = head.addOrReplaceChild("trunk1", CubeListBuilder.create().texOffs(98, 55)
                .addBox(-2F, 0F, -2F, 4F, 7F, 4F), PartPose.offsetAndRotation(0F, 1F, -8F, 0.15F, 0F, 0F));
        PartDefinition trunk2 = trunk1.addOrReplaceChild("trunk2", CubeListBuilder.create().texOffs(48, 73)
                .addBox(-1.5F, 0F, -1.5F, 3F, 7F, 3F), PartPose.offsetAndRotation(0F, 6.5F, 0F, 0.12F, 0F, 0F));
        PartDefinition trunk3 = trunk2.addOrReplaceChild("trunk3", CubeListBuilder.create().texOffs(0, 84)
                .addBox(-1F, 0F, -1F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, 6.5F, 0F, -0.35F, 0F, 0F));
        PartDefinition leftTusk = head.addOrReplaceChild("left_tusk", CubeListBuilder.create().texOffs(0, 73)
                .addBox(-1F, -1F, -9F, 2F, 2F, 9F), PartPose.offsetAndRotation(3F, 2F, -7F, 0.95F, -0.12F, 0F));
        PartDefinition leftTuskTip = leftTusk.addOrReplaceChild("left_tusk_tip", CubeListBuilder.create().texOffs(60, 73)
                .addBox(-1F, -1F, -7F, 2F, 2F, 7F), PartPose.offsetAndRotation(0F, 0F, -8.5F, -1.05F, -0.3F, 0F));
        PartDefinition rightTusk = head.addOrReplaceChild("right_tusk", CubeListBuilder.create().texOffs(22, 73)
                .addBox(-1F, -1F, -9F, 2F, 2F, 9F), PartPose.offsetAndRotation(-3F, 2F, -7F, 0.95F, 0.12F, 0F));
        PartDefinition rightTuskTip = rightTusk.addOrReplaceChild("right_tusk_tip", CubeListBuilder.create().texOffs(78, 73)
                .addBox(-1F, -1F, -7F, 2F, 2F, 7F), PartPose.offsetAndRotation(0F, 0F, -8.5F, -1.05F, 0.3F, 0F));
        PartDefinition rightFrontLeg = root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(56, 36)
                .addBox(-2.5F, 0F, -2.5F, 5F, 13F, 5F), PartPose.offset(-4.5F, 11F, -6F));
        PartDefinition leftFrontLeg = root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(76, 36)
                .addBox(-2.5F, 0F, -2.5F, 5F, 13F, 5F), PartPose.offset(4.5F, 11F, -6F));
        PartDefinition rightHindLeg = root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(96, 36)
                .addBox(-2.5F, 0F, -2.5F, 5F, 13F, 5F), PartPose.offset(-4.5F, 11F, 8F));
        PartDefinition leftHindLeg = root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(0, 55)
                .addBox(-2.5F, 0F, -2.5F, 5F, 13F, 5F), PartPose.offset(4.5F, 11F, 8F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    /**
     * Megalotragus: a hartebeest the size of a buffalo. High shoulders and a back that slopes away,
     * a long, narrow face carried low, and long horns that rise from a raised base, sweep back and
     * curve round - rufous, with a dark blaze down the face.
     */
    public static LayerDefinition megalotragus() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, -4.5F, -9F, 9F, 9F, 18F), PartPose.offset(0F, 8F, 1F));
        PartDefinition withers = body.addOrReplaceChild("withers", CubeListBuilder.create().texOffs(26, 27)
                .addBox(-4F, -7.5F, -9F, 8F, 3F, 9F), PartPose.offset(0F, 0F, 0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(60, 27)
                .addBox(-0.5F, 0F, 0F, 1F, 8F, 1F), PartPose.offsetAndRotation(0F, -3F, 9.5F, 0.35F, 0F, 0F));
        PartDefinition tuft = tail.addOrReplaceChild("tuft", CubeListBuilder.create().texOffs(92, 27)
                .addBox(-1F, 6F, -1F, 2F, 3F, 2F), PartPose.offset(0F, 0F, 0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(10, 40)
                .addBox(-0.5F, -0.5F, -0.5F, 1F, 1F, 1F), PartPose.offset(0F, 2F, -7F));
        PartDefinition neck = head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(102, 0)
                .addBox(-2F, -8F, -3F, 4F, 9F, 5F), PartPose.offsetAndRotation(0F, 1F, 0F, 0.45F, 0F, 0F));
        PartDefinition skull = neck.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 27)
                .addBox(-2F, -3F, -9F, 4F, 4F, 9F), PartPose.offsetAndRotation(0F, -7F, -1F, 0.6F, 0F, 0F));
        PartDefinition muzzle = skull.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(80, 27)
                .addBox(-1.5F, -2.5F, -3F, 3F, 3F, 3F), PartPose.offset(0F, 0.5F, -8.5F));
        PartDefinition pedicle = skull.addOrReplaceChild("pedicle", CubeListBuilder.create().texOffs(100, 27)
                .addBox(-2F, -2F, -1F, 4F, 2F, 2F), PartPose.offset(0F, -3F, -1.5F));
        PartDefinition leftHorn = pedicle.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(64, 27)
                .addBox(-0.5F, -6F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(1.5F, -1.5F, 0F, -1.2F, 0F, 0.5F));
        PartDefinition leftHornTip = leftHorn.addOrReplaceChild("left_horn_tip", CubeListBuilder.create().texOffs(68, 27)
                .addBox(-0.5F, -6F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, -5.5F, 0F, -0.75F, 0F, -0.35F));
        PartDefinition rightHorn = pedicle.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(72, 27)
                .addBox(-0.5F, -6F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(-1.5F, -1.5F, 0F, -1.2F, 0F, -0.5F));
        PartDefinition rightHornTip = rightHorn.addOrReplaceChild("right_horn_tip", CubeListBuilder.create().texOffs(76, 27)
                .addBox(-0.5F, -6F, -0.5F, 1F, 6F, 1F), PartPose.offsetAndRotation(0F, -5.5F, 0F, -0.75F, 0F, 0.35F));
        PartDefinition leftEar = skull.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(112, 27)
                .addBox(0F, -1F, 0F, 3F, 1F, 2F), PartPose.offsetAndRotation(2F, -2F, -1.5F, 0F, 0F, -0.4F));
        PartDefinition rightEar = skull.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(0, 40)
                .addBox(-3F, -1F, 0F, 3F, 1F, 2F), PartPose.offsetAndRotation(-2F, -2F, -1.5F, 0F, 0F, 0.4F));
        PartDefinition rightFrontLeg = root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(54, 0)
                .addBox(-1.5F, 0F, -1.5F, 3F, 12F, 3F), PartPose.offset(-3F, 12F, -6F));
        PartDefinition leftFrontLeg = root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(66, 0)
                .addBox(-1.5F, 0F, -1.5F, 3F, 12F, 3F), PartPose.offset(3F, 12F, -6F));
        PartDefinition rightHindLeg = root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(78, 0)
                .addBox(-1.5F, 0F, -1.5F, 3F, 12F, 3F), PartPose.offset(-3F, 12F, 7.5F));
        PartDefinition leftHindLeg = root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(90, 0)
                .addBox(-1.5F, 0F, -1.5F, 3F, 12F, 3F), PartPose.offset(3F, 12F, 7.5F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    /**
     * Rusingoryx: a wildebeest-sized antelope with a hollow dome swelling over its snout - a
     * resonating chamber, so its calls carried. Grey-tawny, pale underneath, a dark face, faint bars
     * over the shoulders, and short horns that curve back.
     */
    public static LayerDefinition rusingoryx() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4F, -4F, -8F, 8F, 8F, 16F), PartPose.offset(0F, 9F, 1F));
        PartDefinition shoulders = body.addOrReplaceChild("shoulders", CubeListBuilder.create().texOffs(22, 24)
                .addBox(-3.5F, -5.5F, -8F, 7F, 2F, 7F), PartPose.offset(0F, 0F, 0F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(50, 24)
                .addBox(-0.5F, 0F, 0F, 1F, 7F, 1F), PartPose.offsetAndRotation(0F, -2.5F, 8.5F, 0.3F, 0F, 0F));
        PartDefinition tuft = tail.addOrReplaceChild("tuft", CubeListBuilder.create().texOffs(68, 24)
                .addBox(-1F, 5F, -1F, 2F, 3F, 2F), PartPose.offset(0F, 0F, 0F));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(122, 24)
                .addBox(-0.5F, -0.5F, -0.5F, 1F, 1F, 1F), PartPose.offset(0F, 5F, -6F));
        PartDefinition neck = head.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(96, 0)
                .addBox(-1.5F, -7F, -3F, 3F, 8F, 4F), PartPose.offsetAndRotation(0F, 1F, 0F, 0.4F, 0F, 0F));
        PartDefinition skull = neck.addOrReplaceChild("skull", CubeListBuilder.create().texOffs(0, 24)
                .addBox(-2F, -3F, -7F, 4F, 4F, 7F), PartPose.offsetAndRotation(0F, -6F, -1F, 0.55F, 0F, 0F));
        PartDefinition dome = skull.addOrReplaceChild("dome", CubeListBuilder.create().texOffs(54, 24)
                .addBox(-1.5F, -2F, -3F, 3F, 2F, 4F), PartPose.offset(0F, -3F, -3F));
        PartDefinition muzzle = skull.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(76, 24)
                .addBox(-1.5F, -2.5F, -2F, 3F, 3F, 2F), PartPose.offset(0F, 0.5F, -6.5F));
        PartDefinition leftHorn = skull.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(86, 24)
                .addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(1.2F, -3F, -0.5F, -1.1F, 0F, 0.4F));
        PartDefinition leftHornTip = leftHorn.addOrReplaceChild("left_horn_tip", CubeListBuilder.create().texOffs(94, 24)
                .addBox(-0.5F, -3F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0F, -3.5F, 0F, -0.6F, 0F, -0.3F));
        PartDefinition rightHorn = skull.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(90, 24)
                .addBox(-0.5F, -4F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-1.2F, -3F, -0.5F, -1.1F, 0F, -0.4F));
        PartDefinition rightHornTip = rightHorn.addOrReplaceChild("right_horn_tip", CubeListBuilder.create().texOffs(98, 24)
                .addBox(-0.5F, -3F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(0F, -3.5F, 0F, -0.6F, 0F, 0.3F));
        PartDefinition leftEar = skull.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(102, 24)
                .addBox(0F, -1F, 0F, 3F, 1F, 2F), PartPose.offsetAndRotation(2F, -2F, -1F, 0F, 0F, -0.35F));
        PartDefinition rightEar = skull.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(112, 24)
                .addBox(-3F, -1F, 0F, 3F, 1F, 2F), PartPose.offsetAndRotation(-2F, -2F, -1F, 0F, 0F, 0.35F));
        PartDefinition rightFrontLeg = root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(48, 0)
                .addBox(-1.5F, 0F, -1.5F, 3F, 11F, 3F), PartPose.offset(-2.5F, 13F, -5F));
        PartDefinition leftFrontLeg = root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(60, 0)
                .addBox(-1.5F, 0F, -1.5F, 3F, 11F, 3F), PartPose.offset(2.5F, 13F, -5F));
        PartDefinition rightHindLeg = root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(72, 0)
                .addBox(-1.5F, 0F, -1.5F, 3F, 11F, 3F), PartPose.offset(-2.5F, 13F, 6.5F));
        PartDefinition leftHindLeg = root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(84, 0)
                .addBox(-1.5F, 0F, -1.5F, 3F, 11F, 3F), PartPose.offset(2.5F, 13F, 6.5F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    private WildAnimalLayers() {
    }
}
