package dev.hominin.evolution.client.model;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Draws one of the wild animals with its generated model and texture, at its real size. */
public class WildAnimalRenderer<T extends Mob> extends MobRenderer<T, WildQuadrupedModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public WildAnimalRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, String name,
            float scale, float shadow) {
        super(context, new WildQuadrupedModel<>(context.bakeLayer(layer)), shadow);
        this.texture = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID,
                "textures/entity/" + name + ".png");
        this.scale = scale;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(scale, scale, scale);
    }

    public static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, name), "main");
    }
}
