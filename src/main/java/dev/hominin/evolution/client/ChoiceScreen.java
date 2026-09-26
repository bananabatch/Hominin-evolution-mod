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

    /** The question, wrapped: some of them are a whole story. */
    private java.util.List<net.minecraft.util.FormattedCharSequence> lines() {
        return font.split(title, Math.max(WIDTH, Math.min(width - 40, 340)));
    }

    private int top() {
        int above = 16 + lines().size() * 10;
        return Math.max(above + 8, height / 2 - choices.labels().size() * ROW / 2);
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
        var lines = lines();
        int y = top() - 10 - lines.size() * 10;
        for (var line : lines) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xE9D8A6);
            y += 10;
        }
        if (choices.labels().isEmpty()) {
            graphics.drawCenteredString(font, "Nothing to choose from yet.", width / 2, top(), 0xBBBBBB);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
