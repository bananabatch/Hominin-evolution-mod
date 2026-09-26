package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.network.GoodsChoicePayload;
import dev.hominin.evolution.network.GoodsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Picking goods: yours and your band's on the left, the other band's on the right. Click a row for one more of it,
 * right-click for one fewer, shift-click for all or none. For a party, how many go, and - for a message - what they
 * are to say. Each column scrolls on its own.
 */
public class GoodsScreen extends Screen {
    private static final int ROW = 18;
    private static final int TEXT_WIDTH = 420;

    /** Laid out in init, from the top down, so nothing sits on anything else. */
    private List<FormattedCharSequence> detailLines = List.of();
    private int partyY;
    private String partyLabel = "";
    private int hintY;
    private int top = 70;

    private final GoodsPayload goods;
    private final int[] leftCounts;
    private final int[] rightCounts;
    private int leftScroll;
    private int rightScroll;
    private int partySize;
    private int option;

    private GoodsScreen(GoodsPayload goods) {
        super(Component.literal(goods.title()));
        this.goods = goods;
        this.leftCounts = new int[goods.left().size()];
        this.rightCounts = new int[goods.right().size()];
        this.partySize = Math.min(3, Math.max(0, goods.maxParty()));
    }

    public static void open(GoodsPayload payload) {
        Minecraft.getInstance().setScreen(new GoodsScreen(payload));
    }

    private boolean party() {
        return goods.mode() == dev.hominin.evolution.band.Goods.PARTY;
    }

    private boolean twoColumns() {
        return !goods.left().isEmpty() && !goods.right().isEmpty();
    }

    private int columnWidth() {
        return twoColumns() ? Math.min(230, (width - 30) / 2) : Math.min(300, width - 40);
    }

    private int leftX() {
        return twoColumns() ? width / 2 - columnWidth() - 5 : (width - columnWidth()) / 2;
    }

    private int rightX() {
        return twoColumns() ? width / 2 + 5 : (width - columnWidth()) / 2;
    }

    private int rows() {
        return Math.max(3, (height - top - 44) / ROW);
    }

    private String partyText() {
        return "Party: " + partySize + " of " + goods.maxParty() + " (at least 3)";
    }

    @Override
    protected void init() {
        // The title, then the detail in up to three lines, then the party's size, then what they say, then the hint,
        // then the two columns - each on its own row.
        detailLines = font.split(Component.literal(goods.detail()), Math.min(TEXT_WIDTH, width - 30));
        if (detailLines.size() > 3) {
            detailLines = detailLines.subList(0, 3);
        }
        int y = 20 + detailLines.size() * 10 + 4;
        if (party()) {
            partyY = y;
            // As wide as it will ever get, so the buttons never sit on it.
            partyLabel = "Party: " + goods.maxParty() + " of " + goods.maxParty() + " (at least 3)";
            int half = font.width(partyLabel) / 2;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                partySize = Math.max(3, partySize - 1);
            }).bounds(width / 2 - half - 26, y, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                partySize = Math.min(goods.maxParty(), partySize + 1);
            }).bounds(width / 2 + half + 6, y, 20, 18).build());
            y += 22;
            if (!goods.options().isEmpty()) {
                int w = Math.min(280, width - 40);
                addRenderableWidget(Button.builder(Component.literal(trim(goods.options().get(option), w - 10)), b -> {
                    option = (option + 1) % goods.options().size();
                    rebuildWidgets();
                }).bounds(width / 2 - w / 2, y, w, 18).build());
                y += 22;
            }
        }
        hintY = y + 2;
        top = hintY + 26;
        int bottom = height - 26;
        String send = goods.mode() == dev.hominin.evolution.band.Goods.COUNTER ? "Put it to them" : "Send them";
        Button go = Button.builder(Component.literal(send), b -> {
            List<Integer> left = new ArrayList<>();
            for (int count : leftCounts) {
                left.add(count);
            }
            List<Integer> right = new ArrayList<>();
            for (int count : rightCounts) {
                right.add(count);
            }
            PacketDistributor.sendToServer(new GoodsChoicePayload(goods.mode(), partySize, option, left, right));
            onClose();
        }).bounds(width / 2 - 104, bottom, 100, 20).build();
        go.active = !party() || partySize >= 3 && partySize <= goods.maxParty();
        addRenderableWidget(go);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(width / 2 + 4, bottom, 100, 20).build());
    }

    private String trim(String text, int max) {
        return font.width(text) <= max ? text : font.plainSubstrByWidth(text, max - 8) + "...";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 6, 0xE9D8A6);
        int y = 20;
        for (FormattedCharSequence line : detailLines) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xBBBBBB);
            y += 10;
        }
        if (party()) {
            graphics.drawCenteredString(font, partyText(), width / 2, partyY + 5,
                    goods.maxParty() < 3 ? 0xE06040 : 0xFFFFFF);
        }
        if (!goods.left().isEmpty() || !goods.right().isEmpty()) {
            graphics.drawCenteredString(font, "Click: one more.  Right-click: one fewer.  Shift-click: all or none.",
                    width / 2, hintY, 0x8C8578);
        }
        if (!goods.left().isEmpty()) {
            column(graphics, goods.left(), goods.leftOwners(), leftCounts, leftX(), leftScroll, "Yours and your band's",
                    mouseX, mouseY);
        }
        if (!goods.right().isEmpty()) {
            column(graphics, goods.right(), goods.rightOwners(), rightCounts, rightX(), rightScroll, "Theirs", mouseX,
                    mouseY);
        }
        if (goods.left().isEmpty() && goods.right().isEmpty()) {
            graphics.drawCenteredString(font, "Nothing to choose - they will go as they are.", width / 2, top + 10, 0xBBBBBB);
        }
    }

    private void column(GuiGraphics graphics, List<ItemStack> stacks, List<String> owners, int[] counts, int x, int scroll,
            String heading, int mouseX, int mouseY) {
        int w = columnWidth();
        graphics.drawString(font, trim(heading, w), x, top - 12, 0xE9D8A6);
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = scroll; i < Math.min(stacks.size(), scroll + rows()); i++) {
            int y = top + (i - scroll) * ROW;
            ItemStack stack = stacks.get(i);
            boolean picked = counts[i] > 0;
            graphics.fill(x, y, x + w, y + ROW - 1, picked ? 0x6030A030 : 0x40101010);
            graphics.renderItem(stack, x + 1, y);
            String label = stack.getHoverName().getString() + " x" + stack.getCount() + " - " + owners.get(i);
            graphics.drawString(font, trim(label, w - 60), x + 20, y + 5, 0xFFFFFF);
            if (picked) {
                graphics.drawString(font, "[" + counts[i] + "]", x + w - 30, y + 5, 0x9CFF9C);
            }
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                hovered = stack;
            }
        }
        if (stacks.size() > rows()) {
            graphics.drawString(font, (scroll + 1) + "-" + Math.min(stacks.size(), scroll + rows()) + " of " + stacks.size()
                    + " (scroll)", x, top + rows() * ROW + 2, 0x8C8578);
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pick(goods.left(), leftCounts, leftX(), leftScroll, mouseX, mouseY, button)
                || pick(goods.right(), rightCounts, rightX(), rightScroll, mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean pick(List<ItemStack> stacks, int[] counts, int x, int scroll, double mouseX, double mouseY, int button) {
        if (stacks.isEmpty() || mouseX < x || mouseX >= x + columnWidth() || mouseY < top) {
            return false;
        }
        int i = scroll + (int) ((mouseY - top) / ROW);
        if (i < scroll || i >= Math.min(stacks.size(), scroll + rows())) {
            return false;
        }
        int max = stacks.get(i).getCount();
        if (hasShiftDown()) {
            counts[i] = counts[i] > 0 ? 0 : max;
        } else if (button == 1) {
            counts[i] = Math.max(0, counts[i] - 1);
        } else {
            counts[i] = Math.min(max, counts[i] + 1);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int step = (int) -Math.signum(scrollY);
        if (!goods.right().isEmpty() && mouseX >= rightX() && mouseX < rightX() + columnWidth()) {
            rightScroll = Math.max(0, Math.min(Math.max(0, goods.right().size() - rows()), rightScroll + step));
            return true;
        }
        if (!goods.left().isEmpty()) {
            leftScroll = Math.max(0, Math.min(Math.max(0, goods.left().size() - rows()), leftScroll + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
