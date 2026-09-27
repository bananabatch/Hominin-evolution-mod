package dev.hominin.evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.block.KnappingStationBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The work out on the mat while someone knaps: the stone about to be struck lying in the middle of the hide, and then,
 * the stone broken, the tool it became - its own 3D model - lifted a little off the mat and slowly turning, to be seen.
 */
public class KnappingStationRenderer implements BlockEntityRenderer<KnappingStationBlockEntity> {
    private final ItemRenderer items;

    public KnappingStationRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(KnappingStationBlockEntity station, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        ItemStack shown = station.display();
        if (shown.isEmpty()) {
            return;
        }
        pose.pushPose();
        if (station.displayMade()) {
            // Made: held up off the mat and turned, so it can be looked at.
            float time = (station.getLevel() == null ? 0L : station.getLevel().getGameTime()) + partialTick;
            pose.translate(0.5F, 0.42F + (float) Math.sin(time / 8.0F) * 0.02F, 0.5F);
            pose.mulPose(Axis.YP.rotationDegrees(time * 3.0F % 360.0F));
            pose.scale(0.6F, 0.6F, 0.6F);
            items.renderStatic(shown, ItemDisplayContext.FIXED, light, overlay, pose, buffers, station.getLevel(), 0);
        } else {
            // The stone to be worked, lying in the middle of the hide.
            pose.translate(0.5F, 0.09F, 0.5F);
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.scale(0.5F, 0.5F, 0.5F);
            items.renderStatic(shown, ItemDisplayContext.FIXED, light, overlay, pose, buffers, station.getLevel(),
                    (int) station.getBlockPos().asLong());
        }
        pose.popPose();
    }
}
