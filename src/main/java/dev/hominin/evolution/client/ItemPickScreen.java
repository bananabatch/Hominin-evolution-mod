package dev.hominin.evolution.client;

import java.util.List;

import dev.hominin.evolution.band.FetchKind;
import dev.hominin.evolution.band.Trading;
import dev.hominin.evolution.network.FetchRequestPayload;
import dev.hominin.evolution.network.TakeItemPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Two small pickers that share one layout: what a band member is carrying, to ask for
 * one of them, and what to send someone off to fetch.
 */
public class ItemPickScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int ROW = 22;

    private record Choice(ItemStack icon, Component label, Runnable onPick) {
    }

    private final List<Choice> choices;
    private final Component emptyMessage;

    private ItemPickScreen(Component title, List<Choice> choices, Component emptyMessage) {
        super(title);
        this.choices = choices;
        this.emptyMessage = emptyMessage;
    }

    /** What one member carries, sent from the server. */
    public static void open(int entityId, String name, List<Integer> slots, List<ItemStack> stacks) {
        List<Choice> choices = new java.util.ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            int slot = slots.get(i);
            var player = Minecraft.getInstance().player;
            int tier = Trading.tierOf(stack, player == null ? null : ClientSync.stageOf(player.getUUID()));
            Component label = Component.literal(stack.getHoverName().getString()
                    + (stack.getCount() > 1 ? " x" + stack.getCount() : "")
                    + (tier > 0 ? "  (tier " + tier + ")" : ""));
            choices.add(new Choice(stack, label,
                    () -> PacketDistributor.sendToServer(new TakeItemPayload(entityId, slot))));
        }
        Minecraft.getInstance().setScreen(new ItemPickScreen(Component.literal("Ask " + name + " for..."), choices,
                Component.literal(name + " isn't carrying anything.")));
    }

    /** Things that can be fetched, for one member (entity id) or the band (-1). */
    public static void openFetch(int entityId, Component who) {
        List<Choice> choices = new java.util.ArrayList<>();
        for (FetchKind kind : FetchKind.values()) {
            choices.add(new Choice(kind.icon(), Component.literal(kind.label()),
                    () -> PacketDistributor.sendToServer(new FetchRequestPayload(entityId, kind.ordinal()))));
        }
        Minecraft.getInstance().setScreen(new ItemPickScreen(Component.literal("Ask ").append(who)
                .append(" to get you..."), choices, Component.empty()));
    }

    @Override
    protected void init() {
        int columns = choices.size() > 6 ? 2 : 1;
        int rows = (choices.size() + columns - 1) / columns;
        int totalWidth = columns * BUTTON_WIDTH + (columns - 1) * 8;
        int left = (width - totalWidth) / 2;
        int top = Math.max(30, height / 2 - rows * ROW / 2);
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            int x = left + (i % columns) * (BUTTON_WIDTH + 8);
            int y = top + (i / columns) * ROW;
            addRenderableWidget(Button.builder(choice.label(), b -> {
                choice.onPick().run();
                onClose();
            }).bounds(x + 20, y, BUTTON_WIDTH - 20, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int columns = choices.size() > 6 ? 2 : 1;
        int rows = (choices.size() + columns - 1) / columns;
        int totalWidth = columns * BUTTON_WIDTH + (columns - 1) * 8;
        int left = (width - totalWidth) / 2;
        int top = Math.max(30, height / 2 - rows * ROW / 2);
        graphics.drawCenteredString(font, title, width / 2, top - 18, 0xE9D8A6);
        if (choices.isEmpty()) {
            graphics.drawCenteredString(font, emptyMessage, width / 2, height / 2, 0xBBBBBB);
        }
        for (int i = 0; i < choices.size(); i++) {
            int x = left + (i % columns) * (BUTTON_WIDTH + 8);
            int y = top + (i / columns) * ROW;
            graphics.renderItem(choices.get(i).icon(), x, y + 2);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
