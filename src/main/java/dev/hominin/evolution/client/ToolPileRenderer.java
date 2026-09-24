package dev.hominin.evolution.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.block.ToolPileBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The tools in a pile, lying where they were put down. The ground inside the block is four places, and each
 * holds its own little stack: whatever is laid down lies flat on its thinnest side, at its own angle, on top of
 * what is already in that place - as high as what is under it is actually thick, so nothing sinks into anything
 * else. Each thing is measured from its own model, and made small enough to keep inside its place, so a long
 * cleaver never reaches into the hand axe beside it.
 */
public class ToolPileRenderer implements BlockEntityRenderer<ToolPileBlockEntity> {
    /** How wide each of the four places is. */
    private static final float CELL = 0.46F;
    /** How big things are drawn, at most. */
    private static final float SCALE = 0.42F;
    /** The order the places fill in: corner to corner first, so two things read as a pile, not a row. */
    private static final int[] PLACES = {0, 3, 1, 2};
    private static final Direction[] SIDES = Direction.values();
    /** Each model's extent, worked out once: {minX, minY, minZ, maxX, maxY, maxZ}. */
    private static final Map<BakedModel, float[]> BOUNDS = Collections.synchronizedMap(new WeakHashMap<>());

    private final ItemRenderer items;

    public ToolPileRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ToolPileBlockEntity pile, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
            int overlay) {
        List<ItemStack> stacks = pile.contents();
        long seed = pile.getBlockPos().asLong();
        float[] height = new float[4];
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            int place = PLACES[i % 4];
            RandomSource random = RandomSource.create(seed * 31L + i * 7919L);
            BakedModel model = items.getModel(stack, pile.getLevel(), null, (int) seed + i);
            float[] b = bounds(model);
            float ex = b[3] - b[0];
            float ey = b[4] - b[1];
            float ez = b[5] - b[2];
            // Which way is thinnest: that way is up.
            int thin = ex <= ey && ex <= ez ? 0 : ey <= ez ? 1 : 2;
            float across = thin == 0 ? ey : ex;
            float along = thin == 2 ? ey : ez;
            float thick = thin == 0 ? ex : thin == 1 ? ey : ez;
            float centreX = 0.25F + (place % 2) * 0.5F;
            float centreZ = 0.25F + (place / 2) * 0.5F;
            // A heap - of berries, of bones - is a few of them, one on another.
            int copies = stack.getCount() <= 1 ? 1 : stack.getCount() < 8 ? 2 : 3;
            for (int copy = 0; copy < copies; copy++) {
                float angle = (random.nextBoolean() ? 0.0F : 90.0F) + (random.nextFloat() - 0.5F) * 50.0F;
                double radians = Math.toRadians(angle);
                float cos = (float) Math.abs(Math.cos(radians));
                float sin = (float) Math.abs(Math.sin(radians));
                float wideX = across * cos + along * sin;
                float wideZ = across * sin + along * cos;
                float scale = Math.min(SCALE, CELL / Math.max(0.05F, Math.max(wideX, wideZ)));
                float slackX = Math.max(0.0F, (CELL - wideX * scale) / 2.0F);
                float slackZ = Math.max(0.0F, (CELL - wideZ * scale) / 2.0F);
                float x = centreX + (random.nextFloat() * 2.0F - 1.0F) * slackX;
                float z = centreZ + (random.nextFloat() * 2.0F - 1.0F) * slackZ;
                float t = thick * scale;
                pose.pushPose();
                pose.translate(x, height[place] + t / 2.0F + 0.002F, z);
                pose.mulPose(Axis.YP.rotationDegrees(angle));
                if (thin == 0) {
                    pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
                } else if (thin == 2) {
                    pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                }
                pose.scale(scale, scale, scale);
                // The renderer moves the model by half a block; centre it on its own middle instead.
                pose.translate(0.5F - (b[0] + b[3]) / 2.0F, 0.5F - (b[1] + b[4]) / 2.0F, 0.5F - (b[2] + b[5]) / 2.0F);
                items.render(stack, ItemDisplayContext.NONE, false, pose, buffers, light, overlay, model);
                pose.popPose();
                height[place] += t + 0.004F;
            }
        }
    }

    /** How far a model reaches each way, from its quads. A model with none is taken as a whole block. */
    private static float[] bounds(BakedModel model) {
        return BOUNDS.computeIfAbsent(model, m -> {
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
                    -Float.MAX_VALUE};
            RandomSource random = RandomSource.create(42L);
            boolean any = false;
            for (int s = 0; s <= SIDES.length; s++) {
                Direction side = s < SIDES.length ? SIDES[s] : null;
                random.setSeed(42L);
                for (BakedQuad quad : m.getQuads(null, side, random)) {
                    int[] vertices = quad.getVertices();
                    int stride = vertices.length / 4;
                    for (int v = 0; v < 4; v++) {
                        float px = Float.intBitsToFloat(vertices[v * stride]);
                        float py = Float.intBitsToFloat(vertices[v * stride + 1]);
                        float pz = Float.intBitsToFloat(vertices[v * stride + 2]);
                        b[0] = Math.min(b[0], px);
                        b[1] = Math.min(b[1], py);
                        b[2] = Math.min(b[2], pz);
                        b[3] = Math.max(b[3], px);
                        b[4] = Math.max(b[4], py);
                        b[5] = Math.max(b[5], pz);
                        any = true;
                    }
                }
            }
            if (!any) {
                return new float[] {0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
            }
            // Nothing is thinner than a sliver.
            for (int axis = 0; axis < 3; axis++) {
                if (b[axis + 3] - b[axis] < 0.02F) {
                    float mid = (b[axis] + b[axis + 3]) / 2.0F;
                    b[axis] = mid - 0.01F;
                    b[axis + 3] = mid + 0.01F;
                }
            }
            return b;
        });
    }
}
