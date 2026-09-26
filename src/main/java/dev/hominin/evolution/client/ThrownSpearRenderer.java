package dev.hominin.evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.entity.ThrownSpear;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A spear in flight, or stuck in something: the spear itself, drawn point first along the way it is going. The
 * spear's model stands up along its own height, point at the top, so it is laid down along the flight.
 */
public class ThrownSpearRenderer extends EntityRenderer<ThrownSpear> {
    /** End to end, in blocks, at most. */
    private static final float LENGTH = 2.0F;

    private final ItemRenderer items;

    public ThrownSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ThrownSpear spear, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light) {
        ItemStack stack = spear.shown();
        BakedModel model = items.getModel(stack, spear.level(), null, spear.getId());
        float[] b = ToolPileRenderer.bounds(model);
        float scale = Math.min(1.0F, LENGTH / Math.max(0.1F, b[4] - b[1]));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, spear.yRotO, spear.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, spear.xRotO, spear.getXRot())));
        // Its height laid along the flight: the point leads.
        pose.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        pose.scale(scale, scale, scale);
        // Centred on its own middle, not the renderer's half block.
        pose.translate(0.5F - (b[0] + b[3]) / 2.0F, 0.5F - (b[1] + b[4]) / 2.0F, 0.5F - (b[2] + b[5]) / 2.0F);
        items.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
        super.render(spear, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownSpear spear) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
