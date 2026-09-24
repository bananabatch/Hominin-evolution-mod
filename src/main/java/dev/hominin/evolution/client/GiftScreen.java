package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.hominin.evolution.network.GiftOfferPayload;
import dev.hominin.evolution.network.GiftStockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Choosing a gift for another band: everything you carry and everything your band near you carries, in
 * rows by whose it is. Left-click to add one more of a thing, right-click to take one back, shift-click for
 * all of it or none; then give it. Food counts for the most, and the more of it, the more it counts.
 */
public class GiftScreen extends Screen {
    private static final int CELL = 20;
    private static final int COLUMNS = 9;

    private final GiftStockPayload stock;
    /** How many of each stack are going, by its place in the stock. */
    private final Map<Integer, Integer> picked = new LinkedHashMap<>();
    private final List<int[]> cells = new ArrayList<>();
    private int contentBottom;
    /** How far the rows are scrolled up, for a band that carries a lot. */
    private int scroll;

    private GiftScreen(GiftStockPayload stock) {
        super(Component.literal("A gift for " + stock.bandName()));
        this.stock = stock;
    }

    public static void open(GiftStockPayload stock) {
        Minecraft.getInstance().setScreen(new GiftScreen(stock));
    }

    @Override
    protected void init() {
        cells.clear();
        int left = width / 2 - COLUMNS * CELL / 2;
        int y = 52;
        String owner = null;
        int column = 0;
        for (int i = 0; i < stock.stacks().size(); i++) {
            if (!stock.owners().get(i).equals(owner)) {
                if (owner != null) {
                    y += CELL + 14;
                }
                owner = stock.owners().get(i);
                column = 0;
            } else if (column == COLUMNS) {
                column = 0;
                y += CELL;
            }
            cells.add(new int[] {i, left + column * CELL, y});
            column++;
        }
        contentBottom = y + CELL;
        addRenderableWidget(Button.builder(Component.literal("Give it"), b -> give())
                .bounds(width / 2 - 104, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(width / 2 + 4, height - 30, 100, 20).build());
    }

    private void give() {
        List<Integer> sources = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (var entry : picked.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            sources.add(stock.sources().get(entry.getKey()));
            slots.add(stock.slots().get(entry.getKey()));
            counts.add(entry.getValue());
        }
        if (!sources.isEmpty()) {
            PacketDistributor.sendToServer(new GiftOfferPayload(stock.band(), sources, slots, counts));
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int[] cell : cells) {
            int top = cell[2] - scroll;
            if (top < 40 || top > height - 60) {
                continue;
            }
            if (mouseX >= cell[1] && mouseX < cell[1] + CELL - 2 && mouseY >= top && mouseY < top + CELL - 2) {
                int most = stock.stacks().get(cell[0]).getCount();
                int now = picked.getOrDefault(cell[0], 0);
                int next;
                if (hasShiftDown()) {
                    next = now > 0 ? 0 : most;
                } else if (button == 1) {
                    next = Math.max(0, now - 1);
                } else {
                    next = Math.min(most, now + 1);
                }
                if (next == 0) {
                    picked.remove(cell[0]);
                } else if (picked.containsKey(cell[0]) || picked.size() < 32) {
                    picked.put(cell[0], next);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xE9D8A6);
        graphics.drawCenteredString(font, Component.literal("Click: one more. Right-click: one less. Shift-click: all or none."),
                width / 2, 26, 0xBBBBBB);
        String owner = null;
        ItemStack hovered = ItemStack.EMPTY;
        for (int[] cell : cells) {
            int i = cell[0];
            int top = cell[2] - scroll;
            boolean newOwner = !stock.owners().get(i).equals(owner);
            owner = stock.owners().get(i);
            if (top < 40 || top > height - 60) {
                continue;
            }
            if (newOwner && top - 11 >= 38) {
                graphics.drawString(font, owner.equals("You") ? "You carry" : owner + " carries", cell[1], top - 11, 0xE9D8A6);
            }
            int going = picked.getOrDefault(i, 0);
            graphics.fill(cell[1], top, cell[1] + CELL - 2, top + CELL - 2, going > 0 ? 0xFF5A7A3A : 0xFF303030);
            ItemStack stack = stock.stacks().get(i);
            graphics.renderItem(stack, cell[1] + 1, top + 1);
            // How many of it are going, in place of how many there are.
            graphics.renderItemDecorations(font, going > 0 ? stack.copyWithCount(going) : stack, cell[1] + 1, top + 1,
                    going > 0 ? "\u00a7a" + going : null);
            if (mouseX >= cell[1] && mouseX < cell[1] + CELL - 2 && mouseY >= top && mouseY < top + CELL - 2) {
                hovered = stack;
            }
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
        int food = 0;
        int things = 0;
        for (var entry : picked.entrySet()) {
            things += entry.getValue();
            if (stock.stacks().get(entry.getKey()).has(DataComponents.FOOD)) {
                food += entry.getValue();
            }
        }
        graphics.drawCenteredString(font, Component.literal(things + (things == 1 ? " thing" : " things") + " picked"
                + (food > 0 ? " - " + food + " of it food" : "")), width / 2, height - 44, 0xBBBBBB);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int most = Math.max(0, contentBottom - (height - 64));
        scroll = Math.max(0, Math.min(most, scroll - (int) (scrollY * CELL)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
