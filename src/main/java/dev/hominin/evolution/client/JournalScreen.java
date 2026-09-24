package dev.hominin.evolution.client;

import java.util.List;

import dev.hominin.evolution.network.JournalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The J screen: who you are, and what you know.
 *
 * <p>Two tabs, laid out like the H menu. Stats is a page of plain lines. Skills is a list
 * down the left - what you know by name, what you do not as question marks - and picking
 * one shows it on the right: what it is, how to do it again, and what it gives you.
 */
public class JournalScreen extends Screen {
    private static final int LINE = 12;
    private static final int LIST_WIDTH = 140;
    private static final int PANEL_WIDTH = 210;
    private static final int GOLD = 0xE9D8A6;
    private static final int PALE = 0xBBBBBB;

    private final JournalPayload journal;
    private boolean skillsTab;
    private int selected = -1;
    /** First skill row shown: the list scrolls rather than running into the Done button. */
    private int firstRow;
    private static final int ROW = 18;
    /** How far the stats, or the chosen skill's text, is scrolled - each in its own box above Done. */
    private int textScroll;
    private int textHeight;

    private JournalScreen(JournalPayload journal, boolean skillsTab, int selected) {
        super(Component.literal("Journal"));
        this.journal = journal;
        this.skillsTab = skillsTab;
        this.selected = selected;
    }

    public static void open(JournalPayload journal) {
        Minecraft.getInstance().setScreen(new JournalScreen(journal, false, -1));
    }

    private int known() {
        int count = 0;
        for (int flag : journal.flags()) {
            count += flag & 1;
        }
        return count;
    }

    @Override
    protected void init() {
        int top = 28;
        addRenderableWidget(Button.builder(Component.literal(skillsTab ? "Stats" : "> Stats <"), b -> {
            skillsTab = false;
            textScroll = 0;
            rebuildWidgets();
        }).bounds(width / 2 - 124, top, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal(skillsTab ? "> Skills <" : "Skills"), b -> {
            skillsTab = true;
            textScroll = 0;
            rebuildWidgets();
        }).bounds(width / 2 - 40, top, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Map"), b -> PacketDistributor.sendToServer(
                new dev.hominin.evolution.network.MapActionPayload(dev.hominin.evolution.network.MapActionPayload.OPEN, -1, "")))
                .bounds(width / 2 + 44, top, 80, 20).build());

        if (skillsTab) {
            int left = width / 2 - (LIST_WIDTH + PANEL_WIDTH + 12) / 2;
            int y = top + 44;
            List<String> titles = journal.titles();
            int rows = visibleRows();
            firstRow = Math.max(0, Math.min(firstRow, titles.size() - rows));
            for (int i = firstRow; i < Math.min(titles.size(), firstRow + rows); i++) {
                int index = i;
                boolean known = (journal.flags().get(i) & 1) != 0;
                Component label = Component.literal(known ? titles.get(i) : "???");
                Button button = Button.builder(label, b -> {
                    selected = index;
                    textScroll = 0;
                    rebuildWidgets();
                }).bounds(left, y + (i - firstRow) * ROW, LIST_WIDTH, 16).build();
                button.active = index != selected;
                addRenderableWidget(button);
            }
            if (titles.size() > rows) {
                addRenderableWidget(Button.builder(Component.literal("^"), b -> {
                    firstRow = Math.max(0, firstRow - rows + 1);
                    rebuildWidgets();
                }).bounds(left - 18, y, 16, 16).build());
                addRenderableWidget(Button.builder(Component.literal("v"), b -> {
                    firstRow = Math.min(titles.size() - rows, firstRow + rows - 1);
                    rebuildWidgets();
                }).bounds(left - 18, y + (rows - 1) * ROW, 16, 16).build());
            }
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.literal("Journal - " + journal.heading()), width / 2, 12, GOLD);
        if (skillsTab) {
            renderSkills(graphics);
        } else {
            renderStats(graphics);
        }
    }

    /** The bottom of the scrolling text box: clear of the Done button. */
    private int boxBottom() {
        return height - 34;
    }

    private void renderStats(GuiGraphics graphics) {
        int top = 60;
        graphics.enableScissor(0, top - 2, width, boxBottom());
        int y = top - textScroll;
        for (String line : journal.stats()) {
            graphics.drawCenteredString(font, line, width / 2, y, line.startsWith("   ") ? PALE : 0xFFFFFF);
            y += LINE;
        }
        graphics.disableScissor();
        textHeight = y + textScroll - top;
        scrollHint(graphics, width / 2 + 150, top);
    }

    /** A small arrow when there is more above or below. */
    private void scrollHint(GuiGraphics graphics, int x, int top) {
        int room = boxBottom() - top;
        if (textHeight <= room) {
            return;
        }
        if (textScroll > 0) {
            graphics.drawString(font, "^", x, top, PALE);
        }
        if (textScroll < textHeight - room) {
            graphics.drawString(font, "v (scroll)", x, boxBottom() - 9, PALE);
        }
    }

    private void renderSkills(GuiGraphics graphics) {
        int total = journal.titles().size();
        graphics.drawCenteredString(font, "Skills known: " + known() + " / " + total, width / 2, 56, PALE);
        int panelLeft = width / 2 - (LIST_WIDTH + PANEL_WIDTH + 12) / 2 + LIST_WIDTH + 12;
        int y = 72;
        if (selected < 0) {
            wrap(graphics, "Pick a skill on the left. What you know is named; what you have not worked out yet is not.",
                    panelLeft, y, PALE);
            return;
        }
        boolean known = (journal.flags().get(selected) & 1) != 0;
        boolean carries = (journal.flags().get(selected) & 2) != 0;
        if (!known) {
            graphics.drawString(font, "???", panelLeft, y, GOLD);
            wrap(graphics, "You have not worked this out yet. Keep doing things - and now and then, stop and think.",
                    panelLeft, y + 16, PALE);
            return;
        }
        String[] parts = journal.bodies().get(selected).split("\n", 3);
        int top = y;
        graphics.enableScissor(panelLeft - 2, top - 2, panelLeft + PANEL_WIDTH + 4, boxBottom());
        y -= textScroll;
        graphics.drawString(font, journal.titles().get(selected), panelLeft, y, GOLD);
        y = wrap(graphics, parts[0], panelLeft, y + 16, 0xFFFFFF) + 8;
        graphics.drawString(font, "How to do it again", panelLeft, y, GOLD);
        y = wrap(graphics, parts.length > 1 ? parts[1] : "", panelLeft, y + 12, 0xFFFFFF) + 8;
        graphics.drawString(font, "What it gives you", panelLeft, y, GOLD);
        y = wrap(graphics, parts.length > 2 ? parts[2] : "", panelLeft, y + 12, 0xFFFFFF) + 8;
        y = wrap(graphics, carries ? "Knowledge: this stays with you when you evolve."
                : "The body's own: this is lost when you evolve, and must be learned again.",
                panelLeft, y, carries ? 0x8FD18F : 0xD8A07A);
        graphics.disableScissor();
        textHeight = y + textScroll - top;
        scrollHint(graphics, panelLeft + PANEL_WIDTH - 50, top);
    }

    /** Draws wrapped text and returns the y below it. */
    private int wrap(GuiGraphics graphics, String text, int x, int y, int colour) {
        for (FormattedCharSequence line : font.split(Component.literal(text), PANEL_WIDTH)) {
            graphics.drawString(font, line, x, y, colour);
            y += LINE - 2;
        }
        return y;
    }

    /** Skill rows that fit between the tabs and the Done button. */
    private int visibleRows() {
        return Math.max(3, (height - 28 - 72 - 8) / ROW);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int panelLeft = width / 2 - (LIST_WIDTH + PANEL_WIDTH + 12) / 2 + LIST_WIDTH + 12;
        boolean overText = !skillsTab || mouseX >= panelLeft - 4;
        int room = boxBottom() - (skillsTab ? 72 : 60);
        if (overText && textHeight > room) {
            textScroll = Math.max(0, Math.min(textHeight - room, textScroll - (int) (scrollY * 20)));
            return true;
        }
        if (skillsTab && journal.titles().size() > visibleRows()) {
            firstRow = Math.max(0, Math.min(journal.titles().size() - visibleRows(), firstRow - (int) Math.signum(scrollY)));
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
