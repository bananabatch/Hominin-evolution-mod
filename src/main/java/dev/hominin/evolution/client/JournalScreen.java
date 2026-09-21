package dev.hominin.evolution.client;

import java.util.List;

import dev.hominin.evolution.network.JournalPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
            rebuildWidgets();
        }).bounds(width / 2 - 104, top, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(skillsTab ? "> Skills <" : "Skills"), b -> {
            skillsTab = true;
            rebuildWidgets();
        }).bounds(width / 2 + 4, top, 100, 20).build());

        if (skillsTab) {
            int left = width / 2 - (LIST_WIDTH + PANEL_WIDTH + 12) / 2;
            int y = top + 44;
            List<String> titles = journal.titles();
            for (int i = 0; i < titles.size(); i++) {
                int index = i;
                boolean known = (journal.flags().get(i) & 1) != 0;
                Component label = Component.literal(known ? titles.get(i) : "???");
                Button button = Button.builder(label, b -> {
                    selected = index;
                    rebuildWidgets();
                }).bounds(left, y + i * 22, LIST_WIDTH, 20).build();
                button.active = index != selected;
                addRenderableWidget(button);
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

    private void renderStats(GuiGraphics graphics) {
        int y = 60;
        for (String line : journal.stats()) {
            if (y > height - 40) {
                break;
            }
            graphics.drawCenteredString(font, line, width / 2, y, line.startsWith("   ") ? PALE : 0xFFFFFF);
            y += LINE;
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
        graphics.drawString(font, journal.titles().get(selected), panelLeft, y, GOLD);
        y = wrap(graphics, parts[0], panelLeft, y + 16, 0xFFFFFF) + 8;
        graphics.drawString(font, "How to do it again", panelLeft, y, GOLD);
        y = wrap(graphics, parts.length > 1 ? parts[1] : "", panelLeft, y + 12, 0xFFFFFF) + 8;
        graphics.drawString(font, "What it gives you", panelLeft, y, GOLD);
        y = wrap(graphics, parts.length > 2 ? parts[2] : "", panelLeft, y + 12, 0xFFFFFF) + 8;
        wrap(graphics, carries ? "Knowledge: this stays with you when you evolve."
                : "The body's own: this is lost when you evolve, and must be learned again.",
                panelLeft, y, carries ? 0x8FD18F : 0xD8A07A);
    }

    /** Draws wrapped text and returns the y below it. */
    private int wrap(GuiGraphics graphics, String text, int x, int y, int colour) {
        for (FormattedCharSequence line : font.split(Component.literal(text), PANEL_WIDTH)) {
            graphics.drawString(font, line, x, y, colour);
            y += LINE - 2;
        }
        return y;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
