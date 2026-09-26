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
 * The J screen: who you are, what you know, and what cannot wait.
 *
 * <p>Three tabs, laid out like the H menu. Stats is a page of plain lines. Skills is a list down the left - what you
 * know by name, what you do not as question marks - and picking one shows it on the right: what it is, how to do it
 * again, and what it gives you. Tasks is what wants seeing to right now, and the urgent news of late - everything
 * that went across the top of the screen, so none of it is lost.
 */
public class JournalScreen extends Screen {
    private static final int LINE = 12;
    private static final int LIST_WIDTH = 140;
    private static final int PANEL_WIDTH = 210;
    private static final int TASK_WIDTH = 330;
    private static final int GOLD = 0xE9D8A6;
    private static final int PALE = 0xBBBBBB;

    private static final int STATS = 0;
    private static final int SKILLS = 1;
    private static final int TASKS = 2;
    /** The tab last looked at: the journal opens there again. */
    private static int lastTab = STATS;

    private final JournalPayload journal;
    private int tab;
    private int selected = -1;
    /** First skill row shown: the list scrolls rather than running into the Done button. */
    private int firstRow;
    private static final int ROW = 18;
    /** How far the stats, the tasks, or the chosen skill's text, is scrolled - each in its own box above Done. */
    private int textScroll;
    private int textHeight;

    private JournalScreen(JournalPayload journal, int tab, int selected) {
        super(Component.literal("Journal"));
        this.journal = journal;
        this.tab = tab;
        this.selected = selected;
    }

    public static void open(JournalPayload journal) {
        Minecraft.getInstance().setScreen(new JournalScreen(journal, lastTab, -1));
    }

    private int known() {
        int count = 0;
        for (int flag : journal.flags()) {
            count += flag & 1;
        }
        return count;
    }

    /** Tasks that want doing - the season line is always there, and is not one. */
    private int urgent() {
        int count = 0;
        for (String task : journal.tasks()) {
            if (!task.startsWith("S|")) {
                count++;
            }
        }
        return count;
    }

    private void show(int which) {
        tab = which;
        lastTab = which;
        textScroll = 0;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int top = 28;
        int urgent = urgent();
        String[] labels = {"Stats", "Skills", urgent > 0 ? "Event log (" + urgent + ")" : "Event log"};
        for (int i = 0; i < labels.length; i++) {
            int which = i;
            addRenderableWidget(Button.builder(Component.literal(tab == i ? "> " + labels[i] + " <" : labels[i]),
                    b -> show(which)).bounds(width / 2 - 162 + i * 82, top, 78, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Map"), b -> PacketDistributor.sendToServer(
                new dev.hominin.evolution.network.MapActionPayload(dev.hominin.evolution.network.MapActionPayload.OPEN, -1, "")))
                .bounds(width / 2 - 162 + 3 * 82, top, 78, 20).build());

        if (tab == SKILLS) {
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
        if (tab == SKILLS) {
            renderSkills(graphics);
        } else if (tab == TASKS) {
            renderTasks(graphics);
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

    /** Colours by Alerts.Kind code: need, warning, danger, season, band. */
    private static int colourOf(char code) {
        return switch (code) {
            case 'N' -> 0xE8A83A;
            case 'W' -> 0xE8553A;
            case 'D' -> 0xFF4040;
            case 'S' -> 0x7CC870;
            case 'B' -> 0xC08AF0;
            default -> PALE;
        };
    }

    private void renderTasks(GuiGraphics graphics) {
        int top = 60;
        int left = width / 2 - TASK_WIDTH / 2;
        graphics.enableScissor(0, top - 2, width, boxBottom());
        int y = top - textScroll;
        graphics.drawString(font, "Right now", left, y, GOLD);
        y += 14;
        boolean any = false;
        for (String task : journal.tasks()) {
            y = entry(graphics, task, left, y, 0xFFFFFF);
            any |= !task.startsWith("S|");
        }
        if (!any) {
            graphics.drawString(font, "Nothing urgent. The band is fed, nobody is coming, nothing needs you.", left, y,
                    PALE);
            y += LINE;
        }
        y += 10;
        graphics.drawString(font, "Recent", left, y, GOLD);
        y += 14;
        if (journal.recent().isEmpty()) {
            graphics.drawString(font, "No urgent news yet.", left, y, PALE);
            y += LINE;
        }
        for (String alert : journal.recent()) {
            y = entry(graphics, alert, left, y, PALE);
        }
        graphics.disableScissor();
        textHeight = y + textScroll - top;
        scrollHint(graphics, left + TASK_WIDTH + 6, top);
    }

    /** One line of the Event log: a coloured bar for its kind, the text wrapped beside it. Returns the y below. */
    private int entry(GuiGraphics graphics, String line, int left, int y, int colour) {
        char code = line.length() > 1 && line.charAt(1) == '|' ? line.charAt(0) : '?';
        String text = code == '?' ? line : line.substring(2);
        int start = y;
        for (FormattedCharSequence part : font.split(Component.literal(text), TASK_WIDTH - 10)) {
            graphics.drawString(font, part, left + 8, y, colour);
            y += LINE - 2;
        }
        graphics.fill(left, start - 1, left + 3, y - 1, 0xFF000000 | colourOf(code));
        return y + 4;
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
        boolean overText = tab != SKILLS || mouseX >= panelLeft - 4;
        int room = boxBottom() - (tab == SKILLS ? 72 : 60);
        if (overText && textHeight > room) {
            textScroll = Math.max(0, Math.min(textHeight - room, textScroll - (int) (scrollY * 20)));
            return true;
        }
        if (tab == SKILLS && journal.titles().size() > visibleRows()) {
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
