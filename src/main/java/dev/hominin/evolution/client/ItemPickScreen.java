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
    /** The band to page through, and where this member sits in it. Empty for the fetch list. */
    private final List<Integer> band;
    private final int current;

    private ItemPickScreen(Component title, List<Choice> choices, Component emptyMessage) {
        this(title, choices, emptyMessage, List.of(), -1);
    }

    private ItemPickScreen(Component title, List<Choice> choices, Component emptyMessage,
            List<Integer> band, int current) {
        super(title);
        this.choices = choices;
        this.emptyMessage = emptyMessage;
        this.band = band;
        this.current = current;
    }

    /** What one member carries, sent from the server. */
    public static void open(int entityId, String name, List<Integer> slots, List<ItemStack> stacks,
            List<Integer> band) {
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
        int index = band.indexOf(entityId);
        String position = band.size() > 1 && index >= 0 ? "  (" + (index + 1) + "/" + band.size() + ")" : "";
        Minecraft.getInstance().setScreen(new ItemPickScreen(Component.literal("Ask " + name + " for..." + position),
                choices, Component.literal(name + " isn't carrying anything."), band, index));
    }

    /** Things that can be fetched, for one member (entity id) or the band (-1). */
    public static void openFetch(int entityId, Component who) {
        List<Choice> choices = new java.util.ArrayList<>();
        var self = Minecraft.getInstance().player;
        boolean erectus = self != null && dev.hominin.evolution.band.Bands.erectusOn(ClientSync.stageOf(self.getUUID()));
        for (FetchKind kind : FetchKind.values()) {
            if (kind.erectusOnly() && !erectus) {
                continue;
            }
            choices.add(new Choice(kind.icon(), Component.literal(kind.label()),
                    () -> PacketDistributor.sendToServer(new FetchRequestPayload(entityId, kind.ordinal()))));
        }
        Minecraft.getInstance().setScreen(new ItemPickScreen(Component.literal("Ask ").append(who)
                .append(" to get you..."), choices, Component.empty()));
    }

    /** The first row shown: a long list scrolls instead of running off the screen. */
    private int firstRow;

    private int columns() {
        return choices.size() > 6 ? 2 : 1;
    }

    private int rows() {
        return (choices.size() + columns() - 1) / columns();
    }

    /** Rows that fit between the title and the bottom of the screen. */
    private int visibleRows() {
        return Math.max(3, (height - 30 - 40) / ROW);
    }

    private int top() {
        return Math.max(30, height / 2 - Math.min(rows(), visibleRows()) * ROW / 2);
    }

    private boolean shown(int i) {
        int row = i / columns();
        return row >= firstRow && row < firstRow + visibleRows();
    }

    @Override
    protected void init() {
        int columns = columns();
        int totalWidth = columns * BUTTON_WIDTH + (columns - 1) * 8;
        int left = (width - totalWidth) / 2;
        int top = top();
        int rows = Math.min(rows(), visibleRows());
        firstRow = Math.max(0, Math.min(firstRow, rows() - visibleRows()));
        for (int i = 0; i < choices.size(); i++) {
            if (!shown(i)) {
                continue;
            }
            Choice choice = choices.get(i);
            int x = left + (i % columns) * (BUTTON_WIDTH + 8);
            int y = top + (i / columns - firstRow) * ROW;
            addRenderableWidget(Button.builder(choice.label(), b -> {
                choice.onPick().run();
                onClose();
            }).bounds(x + 20, y, BUTTON_WIDTH - 20, 20).build());
        }
        // Walking round the band: previous and next, wrapping at the ends.
        if (band.size() > 1 && current >= 0) {
            int y = Math.min(height - 28, top + Math.max(rows, 1) * ROW + 10);
            addRenderableWidget(Button.builder(Component.literal("< Previous"), b -> page(-1))
                    .bounds(width / 2 - 104, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Next >"), b -> page(1))
                    .bounds(width / 2 + 4, y, 100, 20).build());
        }
    }

    private void page(int step) {
        int next = Math.floorMod(current + step, band.size());
        PacketDistributor.sendToServer(new dev.hominin.evolution.network.ViewInventoryPayload(band.get(next)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int columns = columns();
        int totalWidth = columns * BUTTON_WIDTH + (columns - 1) * 8;
        int left = (width - totalWidth) / 2;
        int top = top();
        graphics.drawCenteredString(font, title, width / 2, top - 18, 0xE9D8A6);
        if (choices.isEmpty()) {
            graphics.drawCenteredString(font, emptyMessage, width / 2, height / 2, 0xBBBBBB);
        }
        for (int i = 0; i < choices.size(); i++) {
            if (!shown(i)) {
                continue;
            }
            int x = left + (i % columns) * (BUTTON_WIDTH + 8);
            int y = top + (i / columns - firstRow) * ROW;
            graphics.renderItem(choices.get(i).icon(), x, y + 2);
        }
        if (rows() > visibleRows()) {
            if (firstRow > 0) {
                graphics.drawCenteredString(font, "^ more (scroll)", width / 2, top - 8, 0x8C8578);
            }
            if (firstRow + visibleRows() < rows()) {
                graphics.drawCenteredString(font, "v more (scroll)", width / 2, top + visibleRows() * ROW + 2, 0x8C8578);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (rows() > visibleRows()) {
            firstRow = Math.max(0, Math.min(rows() - visibleRows(), firstRow - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
