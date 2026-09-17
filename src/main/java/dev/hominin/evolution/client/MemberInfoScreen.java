package dev.hominin.evolution.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Who a band member is: sex, health, hunger, favourite foods, bond, and what they want. */
public class MemberInfoScreen extends Screen {
    private static final int LINE = 14;

    private final List<String> lines;

    private MemberInfoScreen(String name, List<String> lines) {
        super(Component.literal(name));
        this.lines = lines;
    }

    public static void open(String name, List<String> lines) {
        Minecraft.getInstance().setScreen(new MemberInfoScreen(name, lines));
    }

    private int top() {
        return Math.max(30, height / 2 - (lines.size() * LINE) / 2 - 20);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, top() + lines.size() * LINE + 16, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = top();
        graphics.drawCenteredString(font, title, width / 2, top - 20, 0xE9D8A6);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawCenteredString(font, lines.get(i), width / 2, top + i * LINE, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
