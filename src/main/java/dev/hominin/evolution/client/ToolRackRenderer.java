package dev.hominin.evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.block.ToolRackBarBlock;
import dev.hominin.evolution.block.ToolRackBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Spears, clubs and branches leaning on a tool rack's bar: butts on the ground a little out from under it, tops
 * resting on the bar, on whichever side each was leaned from - three to a block, facing out so you see them whole.
 * Each model stands along its own height, so it is set with its foot on the ground.
 */
public class ToolRackRenderer implements BlockEntityRenderer<ToolRackBlockEntity> {
    /** End to end, in blocks, at most: about as long as a spear is, next to a person. */
    private static final float LENGTH = 1.9F;
    /** The bar sits a block and a half up; the butts stand on the ground a block below it, this far out. */
    private static final float OUT = 0.42F;
    private static final float GROUND = -0.98F;
    /** How far from upright it leans to reach the bar. */
    private static final float LEAN = 14.0F;

    private final ItemRenderer items;

    public ToolRackRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ToolRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        Direction.Axis axis = rack.getBlockState().hasProperty(ToolRackBarBlock.AXIS)
                ? rack.getBlockState().getValue(ToolRackBarBlock.AXIS) : Direction.Axis.Z;
        for (int i = 0; i < ToolRackBlockEntity.PLACES; i++) {
            ItemStack stack = rack.places().get(i);
            if (stack.isEmpty()) {
                continue;
            }
            net.minecraft.client.resources.model.BakedModel model = items.getModel(stack, rack.getLevel(), null, i);
            float[] b = ToolPileRenderer.bounds(model);
            float scale = Math.min(1.0F, LENGTH / Math.max(0.1F, b[4] - b[1]));
            float along = (i + 0.5F) / ToolRackBlockEntity.PLACES;
            float side = rack.side(i) ? 1.0F : -1.0F;
            // A little different each time, the way nobody leans two spears alike.
            float lean = LEAN + ((rack.getBlockPos().hashCode() + i * 31) & 3) * 0.8F;
            pose.pushPose();
            if (axis == Direction.Axis.Z) {
                pose.translate(0.5F + side * OUT, GROUND, along);
                // Top back towards the bar, across x.
                pose.mulPose(Axis.ZP.rotationDegrees(side * lean));
                // Face out across the bar, so it is seen whole from where it was leaned.
                pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            } else {
                pose.translate(along, GROUND, 0.5F + side * OUT);
                pose.mulPose(Axis.XP.rotationDegrees(-side * lean));
            }
            pose.scale(scale, scale, scale);
            // The model stands along its height: its foot on the ground, centred across.
            pose.translate(0.5F - (b[0] + b[3]) / 2.0F, 0.5F - b[1], 0.5F - (b[2] + b[5]) / 2.0F);
            items.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
            pose.popPose();
        }
    }

    /** It draws down to the ground under the bar and out to either side. */
    @Override
    public AABB getRenderBoundingBox(ToolRackBlockEntity rack) {
        return new AABB(rack.getBlockPos()).inflate(1.0D, 1.2D, 1.0D);
    }
}
