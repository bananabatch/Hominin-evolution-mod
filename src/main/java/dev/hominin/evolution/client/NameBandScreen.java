package dev.hominin.evolution.client;

import dev.hominin.evolution.network.BandNamePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Naming your band, when a new one forms round you. A name the country suggests is filled in; call
 * them anything you like. Closing the screen keeps the suggestion.
 */
public class NameBandScreen extends Screen {
    private final String suggestion;
    private EditBox field;
    private boolean sent;

    /** Waiting to be shown, if another screen was open when the band formed. */
    private static String waiting;

    private NameBandScreen(String suggestion) {
        super(Component.literal("Name your band"));
        this.suggestion = suggestion;
    }

    public static void prompt(String suggestion) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            waiting = suggestion;
            return;
        }
        mc.setScreen(new NameBandScreen(suggestion));
    }

    /** Client tick: a prompt that had to wait for another screen to close. */
    public static void showWaiting() {
        Minecraft mc = Minecraft.getInstance();
        if (waiting != null && mc.screen == null && mc.player != null) {
            String next = waiting;
            waiting = null;
            mc.setScreen(new NameBandScreen(next));
        }
    }

    @Override
    protected void init() {
        int centre = width / 2;
        field = new EditBox(font, centre - 110, height / 2 - 10, 220, 20, Component.literal("Band name"));
        field.setMaxLength(32);
        field.setValue(suggestion);
        addRenderableWidget(field);
        setInitialFocus(field);
        addRenderableWidget(Button.builder(Component.literal("That is who we are"), b -> send())
                .bounds(centre - 110, height / 2 + 18, 220, 20).build());
    }

    private void send() {
        if (!sent) {
            sent = true;
            String name = field == null ? suggestion : field.getValue().trim();
            PacketDistributor.sendToServer(new BandNamePayload(name.isEmpty() ? suggestion : name));
        }
        super.onClose();
    }

    @Override
    public void onClose() {
        send();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 257 || key == 335) {
            send();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 52, 0xE9D8A6);
        graphics.drawCenteredString(font, Component.literal("A new band has formed round you. What are they called?"),
                width / 2, height / 2 - 36, 0xBBBBBB);
        graphics.drawCenteredString(font, Component.literal("Other bands will know you by it."), width / 2,
                height / 2 - 26, 0x8C8578);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
