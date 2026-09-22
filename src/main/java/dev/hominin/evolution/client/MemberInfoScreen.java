package dev.hominin.evolution.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A page of facts: one member (sex, health, hunger, favourite foods, bond, wants), or the
 * whole band. Scrolls when there is more than fits - a big band is a long list.
 */
public class MemberInfoScreen extends Screen {
    private static final int LINE = 12;
    private static final int TOP = 34;
    private static final int BOTTOM_MARGIN = 40;

    private final List<String> lines;
    private int scroll;

    private MemberInfoScreen(String name, List<String> lines) {
        super(Component.literal(name));
        this.lines = lines;
    }

    public static void open(String name, List<String> lines) {
        Minecraft.getInstance().setScreen(new MemberInfoScreen(name, lines));
    }

    private int visibleLines() {
        return Math.max(1, (height - TOP - BOTTOM_MARGIN) / LINE);
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - visibleLines());
    }

    /** Short pages sit in the middle; long ones start at the top and scroll. */
    private int top() {
        return lines.size() <= visibleLines() ? Math.max(TOP, height / 2 - (lines.size() * LINE) / 2 - 10) : TOP;
    }

    @Override
    protected void init() {
        int buttonY = Math.min(height - 28, top() + Math.min(lines.size(), visibleLines()) * LINE + 12);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, buttonY, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = top();
        graphics.drawCenteredString(font, title, width / 2, top - 18, 0xE9D8A6);
        int shown = Math.min(lines.size() - scroll, visibleLines());
        for (int i = 0; i < shown; i++) {
            graphics.drawCenteredString(font, lines.get(scroll + i), width / 2, top + i * LINE, 0xFFFFFF);
        }
        if (maxScroll() > 0) {
            graphics.drawCenteredString(font, "(scroll for more: " + (scroll + shown) + "/" + lines.size() + ")",
                    width / 2, top + shown * LINE, 0x9A9A9A);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
