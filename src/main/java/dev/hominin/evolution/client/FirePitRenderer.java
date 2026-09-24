package dev.hominin.evolution.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.block.FirePitBlock;
import dev.hominin.evolution.block.FirePitBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Meat laid round the edge of a fire pit, one piece to each side, the way a campfire shows it. */
public class FirePitRenderer implements BlockEntityRenderer<FirePitBlockEntity> {
    private final ItemRenderer items;

    public FirePitRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(FirePitBlockEntity pit, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        Direction facing = pit.getBlockState().getValue(FirePitBlock.FACING);
        NonNullList<ItemStack> cooking = pit.cooking();
        int seed = (int) pit.getBlockPos().asLong();
        for (int i = 0; i < cooking.size(); i++) {
            ItemStack stack = cooking.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5F, 0.21F, 0.5F);
            Direction side = Direction.from2DDataValue((i + facing.get2DDataValue()) % 4);
            pose.mulPose(Axis.YP.rotationDegrees(-side.toYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.translate(-0.25F, -0.25F, 0.0F);
            pose.scale(0.36F, 0.36F, 0.36F);
            items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, pit.getLevel(), seed + i);
            pose.popPose();
        }
    }
}
