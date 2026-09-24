package dev.hominin.evolution.client;

import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.hominin.evolution.build.Blueprint;
import dev.hominin.evolution.build.Footprint;
import dev.hominin.evolution.build.SiteView;
import dev.hominin.evolution.network.BuildActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The ghosts. While a blueprint is being planned, its ghost follows where you look - the way you are facing is
 * the way in, so its doorway faces you - drawn as it will stand, green-white where it fits and red where it does
 * not. The work key sets it down there. Every build that has been marked out keeps a ghost of whatever of it is
 * still missing, until the last block is in.
 */
public final class BuildPlanner {
    /** How far off a blueprint can be put down: where you are looking, within this. */
    private static final double LOOK_REACH = 24.0D;
    private static final double DRAW_RANGE = 96.0D;
    private static final Direction[] SIDES = Direction.values();

    @Nullable
    private static Blueprint planning;
    /** The build being moved, if this is a move: it goes from there to here. */
    private static int moving;
    @Nullable
    private static Footprint preview;
    @Nullable
    private static Footprint.Fit fit;
    private static int ticks;

    public static boolean planning() {
        return planning != null;
    }

    public static void start(Blueprint blueprint) {
        start(blueprint, 0);
    }

    /** Planning where a build already marked out should go instead. */
    public static void start(Blueprint blueprint, int movingSite) {
        moving = movingSite;
        planning = blueprint;
        preview = null;
        fit = null;
        ticks = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("Planning a " + blueprint.name().toLowerCase() + ": walk to "
                    + "where it should stand and look at the ground - its door faces you. "
                    + ModKeyMappings.ITEM_INTERACT.getTranslatedKeyMessage().getString() + " to mark it out, "
                    + ModKeyMappings.BUILD.getTranslatedKeyMessage().getString() + " to put the plan away.")
                    .withStyle(ChatFormatting.AQUA), false);
        }
    }

    public static void stop(boolean quietly) {
        if (planning != null && !quietly && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("Plan put away.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        planning = null;
        preview = null;
        fit = null;
    }

    /** The work key, while planning: mark it out where the ghost is, if it fits. */
    public static void place() {
        Minecraft mc = Minecraft.getInstance();
        if (planning == null || mc.player == null) {
            return;
        }
        if (preview == null || fit == null) {
            mc.player.displayClientMessage(Component.literal("Look at the ground where it should stand.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!fit.ok()) {
            mc.player.displayClientMessage(Component.literal("You can't build it there: " + fit.reason())
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        PacketDistributor.sendToServer(new BuildActionPayload(BuildActionPayload.PLAN, planning.id().toString(),
                preview.origin(), preview.forward().get2DDataValue(), moving));
        stop(true);
    }

    /** Every client tick: move the ghost to where the player is looking, and say whether it fits there. */
    public static void tick(Minecraft mc) {
        if (planning == null || mc.player == null || mc.level == null) {
            return;
        }
        ticks++;
        HitResult hit = mc.player.pick(LOOK_REACH, 1.0F, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            preview = null;
            fit = null;
            if (ticks % 10 == 0) {
                mc.player.displayClientMessage(Component.literal("Look at the ground where it should stand.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        BlockPos anchor = block.getDirection() == Direction.UP ? block.getBlockPos().above()
                : block.getBlockPos().relative(block.getDirection());
        // Looking at the side of something: stand it on the ground in front of it.
        for (int i = 0; i < 4 && mc.level.getBlockState(anchor.below()).canBeReplaced(); i++) {
            anchor = anchor.below();
        }
        Direction forward = mc.player.getDirection();
        if (preview == null || !preview.origin().equals(anchor) || preview.forward() != forward
                || preview.blueprint() != planning || ticks % 10 == 0) {
            preview = new Footprint(planning, anchor, forward);
            // The one being moved does not stand in its own way.
            fit = preview.fit(mc.level, pos -> SiteView.takenOnClient(pos, moving));
        }
        if (ticks % 10 == 0) {
            mc.player.displayClientMessage(fit.ok()
                    ? Component.literal(planning.name() + " - " + ModKeyMappings.ITEM_INTERACT.getTranslatedKeyMessage()
                            .getString() + " to mark it out here").withStyle(ChatFormatting.GREEN)
                    : Component.literal("Can't build here: " + fit.reason()).withStyle(ChatFormatting.RED), true);
        }
    }

    public static void onLoggingOut() {
        stop(true);
        SiteView.forget();
    }

    // ------------------------------------------------------------ setting a block straight into a ghost

    /**
     * Using a block on its ghost puts it there - no need to find something to place it against, which a roof
     * would otherwise need. A real block nearer than the ghost still gets the click.
     */
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || mc.player == null || mc.level == null
                || planning != null) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof BlockItem item)) {
            return;
        }
        Block block = item.getBlock();
        Vec3 eye = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange();
        Vec3 end = eye.add(mc.player.getViewVector(1.0F).scale(reach));
        double best = reach;
        BlockPos target = null;
        int site = -1;
        for (SiteView view : SiteView.known()) {
            Footprint footprint = view.footprint();
            if (footprint == null || !footprint.box().inflatedBy(1).isInside(BlockPos.containing(eye))
                    && eye.distanceToSqr(Vec3.atCenterOf(view.origin())) > 32.0D * 32.0D) {
                continue;
            }
            for (Map.Entry<BlockPos, Blueprint.Cell> entry : footprint.cells().entrySet()) {
                BlockPos pos = entry.getKey();
                if (entry.getValue().block() != block || footprint.filled(mc.level, pos)
                        || !mc.level.getBlockState(pos).canBeReplaced() && !mc.level.getBlockState(pos).isAir()) {
                    continue;
                }
                var clip = new AABB(pos).clip(eye, end);
                if (clip.isPresent()) {
                    double distance = eye.distanceTo(clip.get());
                    if (distance < best) {
                        best = distance;
                        target = pos;
                        site = view.id();
                    }
                }
            }
        }
        if (target == null) {
            return;
        }
        HitResult vanilla = mc.hitResult;
        if (vanilla != null && vanilla.getType() == HitResult.Type.BLOCK && eye.distanceTo(vanilla.getLocation()) < best - 0.05D) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new BuildActionPayload(BuildActionPayload.FILL, "", target, 0, site));
    }

    // ------------------------------------------------------------ drawing

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || (preview == null && SiteView.known().isEmpty())) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = Sheets.translucentCullBlockSheet();
        VertexConsumer consumer = buffers.getBuffer(type);
        boolean drew = false;
        for (SiteView view : SiteView.known()) {
            if (view.built() && view.placed() >= view.total()) {
                continue;
            }
            Footprint footprint = view.footprint();
            if (footprint == null || camera.distanceToSqr(Vec3.atCenterOf(view.origin())) > DRAW_RANGE * DRAW_RANGE) {
                continue;
            }
            if (planning != null && view.id() == moving) {
                continue;
            }
            // Your own a clear pale blue; somebody else's fainter; one of the band's suggestions a warm yellow.
            drew |= view.proposed() ? draw(mc.level, footprint, pose, consumer, camera, 1.0F, 0.88F, 0.45F, 0.5F, true)
                    : draw(mc.level, footprint, pose, consumer, camera, 0.75F, 0.9F, 1.0F, view.mine() ? 0.5F : 0.3F, true);
        }
        if (preview != null && fit != null) {
            // Over something already standing that matches, nothing is drawn: planning over your own build takes it.
            drew |= fit.ok() ? draw(mc.level, preview, pose, consumer, camera, 0.8F, 1.0F, 0.8F, 0.55F, true)
                    : draw(mc.level, preview, pose, consumer, camera, 1.0F, 0.35F, 0.35F, 0.55F, true);
        }
        if (drew) {
            buffers.endBatch(type);
        }
    }

    private static boolean draw(Level level, Footprint footprint, PoseStack pose, VertexConsumer consumer, Vec3 camera,
            float red, float green, float blue, float alpha, boolean missingOnly) {
        var blocks = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create();
        boolean drew = false;
        for (Map.Entry<BlockPos, Blueprint.Cell> entry : footprint.cells().entrySet()) {
            BlockPos pos = entry.getKey();
            if (missingOnly && footprint.filled(level, pos)) {
                continue;
            }
            BlockState look = entry.getValue().look();
            BakedModel model = blocks.getBlockModel(look);
            int light = LevelRenderer.getLightColor(level, pos);
            pose.pushPose();
            pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            for (Direction side : SIDES) {
                if (hidden(level, footprint, pos, side)) {
                    continue;
                }
                random.setSeed(42L);
                for (BakedQuad quad : model.getQuads(look, side, random, ModelData.EMPTY, null)) {
                    consumer.putBulkData(pose.last(), quad, red, green, blue, alpha, light, OverlayTexture.NO_OVERLAY);
                }
            }
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(look, null, random, ModelData.EMPTY, null)) {
                consumer.putBulkData(pose.last(), quad, red, green, blue, alpha, light, OverlayTexture.NO_OVERLAY);
            }
            pose.popPose();
            drew = true;
        }
        return drew;
    }

    /** A face against another whole block of the same ghost, or a solid block in the world, is not drawn. */
    private static boolean hidden(Level level, Footprint footprint, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        Blueprint.Cell cell = footprint.cells().get(next);
        if (cell != null && cell.look().isSolidRender(level, next)) {
            return true;
        }
        return level.getBlockState(next).isSolidRender(level, next);
    }

    private BuildPlanner() {
    }
}
