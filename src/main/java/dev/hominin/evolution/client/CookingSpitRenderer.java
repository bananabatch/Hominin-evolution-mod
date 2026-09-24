package dev.hominin.evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.block.CookingSpitBlock;
import dev.hominin.evolution.block.CookingSpitBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Meat hanging from a spit: each hook's piece dangling under the bar, turned side-on so you see it from either side
 * of the fire, and swaying a little in the heat. Two or more on a hook hang one behind the other.
 */
public class CookingSpitRenderer implements BlockEntityRenderer<CookingSpitBlockEntity> {
    /** Where the bar is, in the block: the pieces hang from just under it. */
    private static final float BAR_Y = 6.75F / 16.0F;
    private static final float SIZE = 0.42F;
    private final ItemRenderer items;

    public CookingSpitRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(CookingSpitBlockEntity spit, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        boolean alongZ = spit.getBlockState().getValue(CookingSpitBlock.AXIS) == Direction.Axis.Z;
        NonNullList<ItemStack> hooks = spit.hooks();
        long time = spit.getLevel() == null ? 0L : spit.getLevel().getGameTime();
        int seed = (int) spit.getBlockPos().asLong();
        for (int i = 0; i < hooks.size(); i++) {
            ItemStack stack = hooks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            float along = (i + 0.5F) / hooks.size();
            float sway = (float) Math.sin((time + partialTick) * 0.06F + i * 1.7F + seed) * 5.0F;
            int shown = Math.min(2, stack.getCount());
            for (int copy = 0; copy < shown; copy++) {
                pose.pushPose();
                pose.translate(alongZ ? 0.5F : along, BAR_Y, alongZ ? along : 0.5F);
                // Face side-on to the bar, then swing from the hook.
                pose.mulPose(Axis.YP.rotationDegrees(alongZ ? 90.0F : 0.0F));
                pose.mulPose(Axis.XP.rotationDegrees(sway));
                pose.translate(copy * 0.06F, -SIZE * 0.5F - copy * 0.03F, copy * 0.04F - 0.02F);
                pose.mulPose(Axis.ZP.rotationDegrees(copy == 0 ? 0.0F : 18.0F));
                pose.scale(SIZE, SIZE, SIZE);
                items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, spit.getLevel(),
                        seed + i * 7 + copy);
                pose.popPose();
            }
        }
    }
}
