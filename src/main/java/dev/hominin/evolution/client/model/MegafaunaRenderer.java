package dev.hominin.evolution.client.model;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.entity.Megafauna;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws one of the later megafauna with its generated model and texture, at its real size. */
public class MegafaunaRenderer<T extends Megafauna> extends MobRenderer<T, MegafaunaModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public MegafaunaRenderer(EntityRendererProvider.Context context, String name, float scale, float shadow) {
        super(context, new MegafaunaModel<>(context.bakeLayer(WildAnimalRenderer.layer(name))), shadow);
        this.texture = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "textures/entity/" + name + ".png");
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
}
