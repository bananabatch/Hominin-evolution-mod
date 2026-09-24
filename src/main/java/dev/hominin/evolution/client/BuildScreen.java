package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.hominin.evolution.build.Blueprint;
import dev.hominin.evolution.build.Blueprints;
import dev.hominin.evolution.build.SiteView;
import dev.hominin.evolution.build.Sites;
import dev.hominin.evolution.network.BuildActionPayload;
import dev.hominin.evolution.network.BuildMenuPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The build key's menu: what you know how to build, a turning look at the one picked out, what it takes, and why
 * you cannot build it yet if you cannot. Below, the builds you have marked out or finished - put a plan away, or
 * decide what a finished one is for.
 */
public class BuildScreen extends Screen {
    private static final int LIST_WIDTH = 120;
    private static final int PREVIEW = 150;
    private static final int GAP = 10;

    private final List<Blueprint> blueprints = new ArrayList<>();
    private final List<String> locks = new ArrayList<>();
    private int selected;

    private BuildScreen(BuildMenuPayload payload) {
        super(Component.literal("Build"));
        for (int i = 0; i < payload.ids().size(); i++) {
            Blueprint blueprint = Blueprints.get(ResourceLocation.parse(payload.ids().get(i)));
            if (blueprint != null) {
                blueprints.add(blueprint);
                locks.add(i < payload.locks().size() ? payload.locks().get(i) : "");
            }
        }
    }

    public static void open(BuildMenuPayload payload) {
        Minecraft.getInstance().setScreen(new BuildScreen(payload));
    }

    private int left() {
        return (width - (LIST_WIDTH + GAP + PREVIEW + GAP + 150)) / 2;
    }

    private int top() {
        return Math.max(24, height / 2 - 118);
    }

    @Nullable
    private Blueprint chosen() {
        return selected >= 0 && selected < blueprints.size() ? blueprints.get(selected) : null;
    }

    @Override
    protected void init() {
        int x = left();
        int y = top() + 14;
        for (int i = 0; i < blueprints.size(); i++) {
            int index = i;
            Blueprint blueprint = blueprints.get(i);
            String name = (index == selected ? "> " : "") + blueprint.name() + (locks.get(i).isEmpty() ? "" : " (locked)");
            addRenderableWidget(Button.builder(Component.literal(name), b -> {
                selected = index;
                rebuildWidgets();
            }).bounds(x, y + i * 22, LIST_WIDTH, 20).build());
        }
        Blueprint blueprint = chosen();
        int detailsX = x + LIST_WIDTH + GAP + PREVIEW + GAP;
        if (blueprint != null) {
            boolean locked = !locks.get(selected).isEmpty();
            Button plan = Button.builder(Component.literal("Plan it"), b -> {
                onClose();
                BuildPlanner.start(blueprint);
            }).bounds(detailsX, top() + PREVIEW - 10, 150, 20).build();
            plan.active = !locked;
            addRenderableWidget(plan);
        }
        // Your builds: put a plan away, or say what a finished one is for.
        int rowY = top() + PREVIEW + 34;
        int row = 0;
        for (SiteView view : ordered()) {
            if (!view.mine() || row >= 5) {
                continue;
            }
            int buttonX = x + LIST_WIDTH + GAP + PREVIEW + GAP + 150 - 170;
            Blueprint moved = Blueprints.get(view.blueprint());
            if (view.proposed()) {
                addRenderableWidget(Button.builder(Component.literal("Confirm"), b -> {
                    PacketDistributor.sendToServer(BuildActionPayload.simple(BuildActionPayload.CONFIRM, view.id()));
                    onClose();
                }).bounds(buttonX, rowY + row * 20, 54, 18).build());
                addRenderableWidget(Button.builder(Component.literal("Move"), b -> {
                    onClose();
                    if (moved != null) {
                        BuildPlanner.start(moved, view.id());
                    }
                }).bounds(buttonX + 57, rowY + row * 20, 54, 18).build());
                addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
                    PacketDistributor.sendToServer(BuildActionPayload.simple(BuildActionPayload.ABANDON, view.id()));
                    onClose();
                }).bounds(buttonX + 114, rowY + row * 20, 54, 18).build());
            } else if (!view.built()) {
                if (view.placed() == 0 && moved != null) {
                    // Nothing built of it yet: it can still go somewhere else.
                    addRenderableWidget(Button.builder(Component.literal("Move"), b -> {
                        onClose();
                        BuildPlanner.start(moved, view.id());
                    }).bounds(buttonX + 57, rowY + row * 20, 54, 18).build());
                }
                addRenderableWidget(Button.builder(Component.literal("Put away"), b -> {
                    PacketDistributor.sendToServer(BuildActionPayload.simple(BuildActionPayload.ABANDON, view.id()));
                    onClose();
                }).bounds(buttonX + 114, rowY + row * 20, 54, 18).build());
            } else if (view.useKind() == Sites.Use.NONE) {
                addRenderableWidget(Button.builder(Component.literal("What is it for?"), b -> {
                    onClose();
                    PacketDistributor.sendToServer(BuildActionPayload.simple(BuildActionPayload.DECIDE, view.id()));
                }).bounds(buttonX + 68, rowY + row * 20, 100, 18).build());
            }
            row++;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 - 50, Math.min(height - 24, rowY + Math.max(1, row) * 20 + 8), 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left();
        int top = top();
        graphics.drawString(font, "Build", x, top, 0xE9D8A6);
        if (blueprints.isEmpty()) {
            graphics.drawString(font, "You know of nothing to build.", x, top + 16, 0xBBBBBB);
            return;
        }
        Blueprint blueprint = chosen();
        int previewX = x + LIST_WIDTH + GAP;
        graphics.fill(previewX, top + 14, previewX + PREVIEW, top + 14 + PREVIEW - 24, 0x80101418);
        graphics.renderOutline(previewX, top + 14, PREVIEW, PREVIEW - 24, 0xFF5A4A36);
        if (blueprint != null) {
            renderPreview(graphics, blueprint, previewX + PREVIEW / 2, top + 14 + (PREVIEW - 24) / 2 + 8, PREVIEW - 34);
            int detailsX = previewX + PREVIEW + GAP;
            int y = top + 14;
            graphics.drawString(font, blueprint.name(), detailsX, y, 0xFFD27F);
            y += 12;
            for (FormattedCharSequence line : font.split(Component.literal(blueprint.description()), 150)) {
                graphics.drawString(font, line, detailsX, y, 0xCCCCCC);
                y += 10;
                if (y > top + PREVIEW - 46) {
                    break;
                }
            }
            y = Math.max(y + 4, top + PREVIEW - 46);
            for (FormattedCharSequence line : font.split(Component.literal("Takes: " + blueprint.materialsText()), 150)) {
                graphics.drawString(font, line, detailsX, y, 0x9FD8FF);
                y += 10;
            }
            String lock = locks.get(selected);
            if (!lock.isEmpty()) {
                for (FormattedCharSequence line : font.split(Component.literal(lock), 150)) {
                    graphics.drawString(font, line, previewX, top + PREVIEW - 6, 0xE08060);
                    break;
                }
            }
        }
        int rowY = top + PREVIEW + 20;
        graphics.drawString(font, "Your builds", x, rowY, 0xE9D8A6);
        rowY += 14;
        int row = 0;
        for (SiteView view : ordered()) {
            if (!view.mine() || row >= 5) {
                continue;
            }
            int colour = view.proposed() ? 0xFFE08A : !view.built() ? 0x9FD8FF
                    : view.useKind() == Sites.Use.NONE ? 0xFFD27F : 0xBBBBBB;
            String label = view.label();
            if (Minecraft.getInstance().player != null) {
                int distance = (int) Math.sqrt(Minecraft.getInstance().player.blockPosition().distSqr(view.origin()));
                label += " (" + distance + " away)";
            }
            graphics.drawString(font, font.plainSubstrByWidth(label, LIST_WIDTH + GAP + PREVIEW + GAP - 26), x,
                    rowY + row * 20 + 5, colour);
            row++;
        }
        if (row == 0) {
            graphics.drawString(font, "Nothing yet. Pick a blueprint and plan it.", x, rowY + 5, 0x888888);
        }
    }

    /** Suggestions first - they are waiting on you - then plans, then what stands. */
    private static List<SiteView> ordered() {
        List<SiteView> views = new ArrayList<>(SiteView.known());
        views.sort(java.util.Comparator.comparingInt(v -> v.proposed() ? 0 : !v.built() ? 1 : 2));
        return views;
    }

    /** The blueprint as it will stand, turning slowly. */
    private void renderPreview(GuiGraphics graphics, Blueprint blueprint, int cx, int cy, int size) {
        int span = Math.max(blueprint.width(), Math.max(blueprint.depth(), blueprint.height()));
        float scale = size / (span * 1.55F);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 200.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(28.0F));
        pose.mulPose(Axis.YP.rotationDegrees((net.minecraft.Util.getMillis() / 45L) % 360L));
        // Turn about the middle of it, not its doorway.
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        for (Blueprint.Cell cell : blueprint.cells()) {
            minX = Math.min(minX, cell.local().getX());
            maxX = Math.max(maxX, cell.local().getX());
        }
        pose.translate(-(minX + maxX + 1) / 2.0F, -blueprint.height() / 2.0F, -blueprint.depth() / 2.0F);
        Lighting.setupForEntityInInventory();
        var blocks = Minecraft.getInstance().getBlockRenderer();
        for (Blueprint.Cell cell : blueprint.cells()) {
            BlockPos local = cell.local();
            pose.pushPose();
            pose.translate(local.getX(), local.getY(), local.getZ());
            blocks.renderSingleBlock(cell.look(), pose, graphics.bufferSource(), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        graphics.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeyMappings.BUILD.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
