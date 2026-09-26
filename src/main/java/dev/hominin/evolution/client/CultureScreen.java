package dev.hominin.evolution.client;

import java.util.List;

import dev.hominin.evolution.band.Morals;
import dev.hominin.evolution.network.MoralActionPayload;
import dev.hominin.evolution.network.MoralsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Culture tab: your people's ways. Every moral a band can hold, whether yours holds it,
 * whether it binds in the times you are in now, and a button to take it up or let it go.
 *
 * <p>It fits whatever window it is opened in: as many rows as there is room for, and the rest
 * a scroll away - so it keeps working however many ways a people ends up with.
 */
public class CultureScreen extends Screen {
    private static final int MAX_WIDTH = 380;
    private static final int ROW = 46;
    private static final int BUTTON_WIDTH = 64;
    /** Room kept above the list for the title and the season, and below it for Done. */
    private static final int HEADER = 44;
    private static final int FOOTER = 34;

    private MoralsPayload data;
    /** The first row showing. */
    private int scroll;

    private CultureScreen(MoralsPayload data) {
        super(Component.literal("Your people's ways"));
        this.data = data;
    }

    /** Opens the tab, or - if it is already open - refreshes it in place, keeping the scroll. */
    public static void show(MoralsPayload data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CultureScreen open) {
            open.data = data;
            open.rebuildWidgets();
            return;
        }
        mc.setScreen(new CultureScreen(data));
    }

    private int panelWidth() {
        return Math.min(MAX_WIDTH, width - 24);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    private int listTop() {
        return HEADER;
    }

    private int rowsVisible() {
        return Math.max(1, (height - HEADER - FOOTER) / ROW);
    }

    /** The ways shown: all but those the band has no reason for yet. */
    private Morals.Moral[] shown() {
        return java.util.Arrays.stream(Morals.Moral.values())
                .filter(m -> data.states().get(m.ordinal()) != Morals.LOCKED).toArray(Morals.Moral[]::new);
    }

    private int maxScroll() {
        return Math.max(0, shown().length - rowsVisible());
    }

    @Override
    protected void init() {
        scroll = Math.min(scroll, maxScroll());
        Morals.Moral[] morals = shown();
        int y = listTop();
        for (int i = scroll; i < Math.min(morals.length, scroll + rowsVisible()); i++) {
            Morals.Moral moral = morals[i];
            int state = data.states().get(moral.ordinal());
            boolean held = state == Morals.HELD;
            String label = switch (state) {
                case Morals.HELD -> "Let go";
                case Morals.FADING -> "Keep it";
                case Morals.COOLING -> "Too soon";
                default -> "Adopt";
            };
            Button button = Button.builder(Component.literal(label), b -> PacketDistributor.sendToServer(
                    new MoralActionPayload(moral.ordinal(), !held)))
                    .bounds(left() + panelWidth() - BUTTON_WIDTH, y, BUTTON_WIDTH, 18)
                    .tooltip(Tooltip.create(Component.literal(moral.description())))
                    .build();
            button.active = state != Morals.COOLING;
            addRenderableWidget(button);
            y += ROW;
        }
        int bottom = height - FOOTER + 8;
        if (maxScroll() > 0) {
            Button up = Button.builder(Component.literal("▲"), b -> scrollBy(-1))
                    .bounds(left(), bottom, 20, 20).build();
            up.active = scroll > 0;
            addRenderableWidget(up);
            Button down = Button.builder(Component.literal("▼"), b -> scrollBy(1))
                    .bounds(left() + 24, bottom, 20, 20).build();
            down.active = scroll < maxScroll();
            addRenderableWidget(down);
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, bottom, 100, 20).build());
    }

    private void scrollBy(int rows) {
        int next = Math.max(0, Math.min(maxScroll(), scroll + rows));
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && maxScroll() > 0) {
            scrollBy(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left();
        graphics.drawCenteredString(font, title, width / 2, 10, 0xE9D8A6);
        String now = data.season() + ", " + data.daysLeft() + (data.daysLeft() == 1 ? " day" : " days") + " left"
                + (data.dryDay() ? " - and a dry day" : "");
        graphics.drawCenteredString(font, now, width / 2, 24, data.season().startsWith("Dry") ? 0xE0A040 : 0x7FD86A);
        Morals.Moral[] morals = shown();
        int y = listTop();
        int textWidth = panelWidth() - BUTTON_WIDTH - 8;
        for (int i = scroll; i < Math.min(morals.length, scroll + rowsVisible()); i++) {
            Morals.Moral moral = morals[i];
            int state = data.states().get(moral.ordinal());
            boolean binding = data.binding().get(moral.ordinal()) == 1;
            int colour = switch (state) {
                case Morals.HELD -> binding ? 0xF2D25C : 0xC8B070;
                case Morals.FADING -> 0xA89060;
                default -> 0xBFBFBF;
            };
            graphics.drawString(font, font.plainSubstrByWidth(moral.title(), textWidth), x, y, colour, true);
            String status = switch (state) {
                case Morals.HELD -> binding ? "Held - binding now" : "Held - not binding now";
                case Morals.FADING -> "Fading - gone in " + time(data.minutes().get(moral.ordinal()));
                case Morals.COOLING -> "Let go - can return in " + time(data.minutes().get(moral.ordinal()));
                default -> moral.when().label();
            };
            graphics.drawString(font, font.plainSubstrByWidth(status, textWidth), x, y + 10,
                    state == Morals.NONE ? 0x8C8C8C : 0xD8C8A0, false);
            List<FormattedCharSequence> lines = font.split(Component.literal(moral.description()), textWidth);
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                graphics.drawString(font, lines.get(line), x, y + 20 + line * 9, 0x9A9A9A, false);
            }
            y += ROW;
        }
        if (maxScroll() > 0) {
            String where = (scroll + 1) + "-" + Math.min(morals.length, scroll + rowsVisible()) + " of "
                    + morals.length;
            graphics.drawString(font, where, left() + panelWidth() - font.width(where), height - FOOTER + 14,
                    0x8C8C8C, false);
        }
    }

    /** Game minutes as the tab shows them: a day is twenty. */
    private static String time(int minutes) {
        if (minutes >= 20) {
            int days = minutes / 20;
            int hours = (minutes % 20) * 24 / 20;
            return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        }
        return Math.max(1, minutes * 24 / 20) + "h";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
