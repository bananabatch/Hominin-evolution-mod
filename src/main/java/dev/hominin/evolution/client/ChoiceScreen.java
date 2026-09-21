package dev.hominin.evolution.client;

import dev.hominin.evolution.network.ChoicesPayload;
import dev.hominin.evolution.network.ChoosePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** A plain list of choices, sent from the server: pick one and it goes back. */
public class ChoiceScreen extends Screen {
    private static final int WIDTH = 180;
    private static final int ROW = 22;

    private final ChoicesPayload choices;

    private ChoiceScreen(ChoicesPayload choices) {
        super(Component.literal(choices.title()));
        this.choices = choices;
    }

    public static void open(ChoicesPayload choices) {
        Minecraft.getInstance().setScreen(new ChoiceScreen(choices));
    }

    private int top() {
        return Math.max(40, height / 2 - choices.labels().size() * ROW / 2);
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = top();
        for (int i = 0; i < choices.labels().size(); i++) {
            int value = choices.values().get(i);
            addRenderableWidget(Button.builder(Component.literal(choices.labels().get(i)), b -> {
                PacketDistributor.sendToServer(new ChoosePayload(choices.entityId(), choices.action(), value));
                onClose();
            }).bounds(x, y + i * ROW, WIDTH, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x, y + choices.labels().size() * ROW + 6, WIDTH, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top() - 18, 0xE9D8A6);
        if (choices.labels().isEmpty()) {
            graphics.drawCenteredString(font, "Nothing to choose from yet.", width / 2, top(), 0xBBBBBB);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
