package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.network.OthersActionPayload;
import dev.hominin.evolution.network.OthersPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "The others": the bands you know of, one at a time - nearest first, the arrows cycling through the
 * rest. What they are, where they are, how they stand with you, and what can be done: follow them,
 * and, when some of them are close, trade, ask them along, give them something, or pay what they ask.
 */
public class OthersScreen extends Screen {
    private static final int WIDTH = 240;

    private final List<OthersPayload.View> bands;
    private int index;
    /** The top of the buttons: the details scroll in the space above them. */
    private int linesBottom;
    private int linesScroll;
    private int linesHeight;

    /** The talk menu this was opened from: Done goes back to it, not out of everything. */
    @javax.annotation.Nullable
    private final Screen parent;

    private OthersScreen(List<OthersPayload.View> bands, int index, @javax.annotation.Nullable Screen parent) {
        super(Component.literal("The others"));
        this.bands = bands;
        this.index = index;
        this.parent = parent;
    }

    public static void open(OthersPayload payload) {
        Screen current = Minecraft.getInstance().screen;
        Minecraft.getInstance().setScreen(new OthersScreen(payload.bands(), 0, current instanceof SocialScreen ? current : null));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int bottom = height - 30;
        linesBottom = bottom - 6;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, bottom, 100, 20).build());
        if (bands.isEmpty()) {
            return;
        }
        if (bands.size() > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1)).bounds(left, 36, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(1)).bounds(left + WIDTH - 20, 36, 20, 20).build());
        }
        OthersPayload.View band = bands.get(index);
        if (!band.nomadic()) {
            // Small, and off to the side under the standing bar: it never sits on the details.
            addRenderableWidget(Button.builder(Component.literal(showWays ? "What we know" : "Their ways"),
                    b -> {
                        showWays = !showWays;
                        linesScroll = 0;
                        rebuildWidgets();
                    }).bounds(left + WIDTH - 76, 76, 76, 12).build());
        }
        // Every action, three to a row, from the bottom up: as many rows as there are things to do, and the details
        // above get everything that is left.
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.builder(Component.literal("Lead me there"), b -> act(band, OthersActionPayload.LEAD)).build());
        if (!band.nomadic()) {
            buttons.add(Button.builder(Component.literal("Send a party"),
                    b -> Minecraft.getInstance().setScreen(new PartyIntentScreen(this, band.id(), band.name()))).build());
        }
        if (band.ransom()) {
            buttons.add(Button.builder(Component.literal("Pay them"), b -> act(band, OthersActionPayload.RANSOM)).build());
        }
        if (band.near()) {
            buttons.add(Button.builder(Component.literal("Offer a gift"), b -> act(band, OthersActionPayload.GIFT)).build());
            buttons.add(Button.builder(Component.literal("Trade"), b -> act(band, OthersActionPayload.TRADE)).build());
            if (!band.nomadic()) {
                Button travel = Button.builder(Component.literal("Travel with us"),
                        b -> act(band, OthersActionPayload.TRAVEL)).build();
                travel.active = band.canTravel();
                buttons.add(travel);
                // What you know of the country is worth something to them.
                Button tell = Button.builder(Component.literal("Tell of places"),
                        b -> act(band, OthersActionPayload.TELL_PLACES)).build();
                tell.active = band.standing() > 20;
                buttons.add(tell);
                // And what they know of it is worth something to you.
                Button ask = Button.builder(Component.literal("What they know"),
                        b -> act(band, OthersActionPayload.ASK_PLACES)).build();
                ask.active = band.standing() >= 30;
                buttons.add(ask);
                // What they know how to do - one thing a day, to friends.
                Button learn = Button.builder(Component.literal("Learn their skills"),
                        b -> act(band, OthersActionPayload.LEARN)).build();
                learn.active = band.standing() >= 35;
                buttons.add(learn);
                buttons.add(Button.builder(Component.literal("How do you fight?"),
                        b -> act(band, OthersActionPayload.ASK_POSTURE)).build());
                if (band.canJoin()) {
                    // Down to two: better one band than two halves of nothing.
                    buttons.add(Button.builder(Component.literal("Join them").withStyle(ChatFormatting.GOLD),
                            b -> act(band, OthersActionPayload.JOIN_THEM)).build());
                }
                // Leaning on them: only worth offering to a band that does not already count you a friend.
                if (band.standing() < 35) {
                    buttons.add(Button.builder(Component.literal("Demand tribute"),
                            b -> act(band, OthersActionPayload.DEMAND)).build());
                    buttons.add(Button.builder(Component.literal(raidArmed ? "Sure? Raid!" : "Raid them")
                            .withStyle(ChatFormatting.RED), b -> confirmRaid(band)).build());
                }
            }
        }
        int columns = 3;
        int gap = 3;
        int cell = (WIDTH - gap * (columns - 1)) / columns;
        int rows = (buttons.size() + columns - 1) / columns;
        int top = bottom - 4 - rows * 18;
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            button.setRectangle(cell, 16, left + (i % columns) * (cell + gap), top + (i / columns) * 18);
            addRenderableWidget(button);
        }
        linesBottom = top - 4;
    }

    /** A raid is not something to click by accident: the button asks once. */
    private boolean raidArmed;

    private void confirmRaid(OthersPayload.View band) {
        if (!raidArmed) {
            raidArmed = true;
            rebuildWidgets();
            return;
        }
        raidArmed = false;
        act(band, OthersActionPayload.RAID);
    }

    /** Showing their ways of life instead of the rest. */
    private boolean showWays;

    private void cycle(int step) {
        raidArmed = false;
        showWays = false;
        linesScroll = 0;
        index = Math.floorMod(index + step, bands.size());
        rebuildWidgets();
    }

    private void act(OthersPayload.View band, int action) {
        PacketDistributor.sendToServer(new OthersActionPayload(band.id(), action));
        if (action != OthersActionPayload.GIFT) {
            // Something was done: out of the menus altogether.
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xE9D8A6);
        if (bands.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("You know of no other band yet."), width / 2, 60, 0xFFFFFF);
            graphics.drawCenteredString(font, Component.literal("Listen for their calls, and walk the country."), width / 2,
                    74, 0xBBBBBB);
            return;
        }
        OthersPayload.View band = bands.get(index);
        int left = (width - WIDTH) / 2;
        String name = Character.toUpperCase(band.name().charAt(0)) + band.name().substring(1);
        graphics.drawCenteredString(font, Component.literal(name), width / 2, 42, colour(band.standing()));
        if (bands.size() > 1) {
            graphics.drawCenteredString(font, Component.literal((index + 1) + " of " + bands.size()), width / 2, 54, 0x8C8578);
        }
        // Standing, as a bar: dire red to allied blue.
        int barY = 68;
        graphics.fill(left, barY, left + WIDTH, barY + 5, 0xFF2A2A2A);
        graphics.fill(left, barY, left + WIDTH * band.standing() / 50, barY + 5, 0xFF000000 | colour(band.standing()));
        for (int mark : new int[] {10, 20, 30, 35, 45}) {
            int x = left + WIDTH * mark / 50;
            graphics.fill(x, barY - 1, x + 1, barY + 6, 0xFF8C8578);
        }
        int y = barY + 24;
        int boxTop = y;
        graphics.enableScissor(left - 2, boxTop - 2, left + WIDTH + 2, linesBottom);
        y -= linesScroll;
        if (!band.nomadic()) {
            // How desperate they are: five boxes, filling up red.
            graphics.drawString(font, "Desperation", left, y, 0xBBBBBB);
            for (int i = 0; i < 5; i++) {
                int x = left + 66 + i * 12;
                graphics.fill(x, y, x + 9, y + 8, 0xFF2A2A2A);
                if (i < band.desperation()) {
                    graphics.fill(x + 1, y + 1, x + 8, y + 7, 0xFF000000 | (0x70 + i * 0x20) << 16 | (0x90 - i * 0x18) << 8 | 0x30);
                }
            }
            y += 14;
        }
        List<String> shown = showWays ? (band.ways().isEmpty()
                ? List.of("They hold no rules of their kind - nothing you could share, or hold against them.")
                : band.ways()) : band.lines();
        if (showWays && !band.ways().isEmpty()) {
            graphics.drawString(font, "Their ways:", left, y, 0xE9D8A6);
            y += 12;
        }
        for (String line : shown) {
            boolean shared = line.startsWith("Shared: ");
            for (FormattedCharSequence part : font.split(Component.literal(showWays && !band.ways().isEmpty()
                    ? "- " + (shared ? line.substring(8) + " (you hold it too)" : line) : line), WIDTH)) {
                graphics.drawString(font, part, left, y, shared ? 0x7CD07C : 0xDDDDDD);
                y += 10;
            }
            y += 2;
        }
        if (showWays && !band.ways().isEmpty()) {
            for (FormattedCharSequence part : font.split(Component.literal("Ways you share warm them to you. Different "
                    + "ones they do not mind - until they are looking for a reason."), WIDTH)) {
                graphics.drawString(font, part, left, y + 4, 0x8C8578);
                y += 10;
            }
        }
        graphics.disableScissor();
        linesHeight = y + linesScroll - boxTop;
        int room = linesBottom - boxTop;
        if (linesHeight > room) {
            if (linesScroll < linesHeight - room) {
                graphics.drawString(font, "v more (scroll)", left + WIDTH - 80, linesBottom - 9, 0x8C8578);
            }
            if (linesScroll > 0) {
                graphics.drawString(font, "^", left + WIDTH - 8, boxTop, 0x8C8578);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int room = linesBottom - 92;
        if (linesHeight > room) {
            linesScroll = Math.max(0, Math.min(linesHeight - room, linesScroll - (int) (scrollY * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static int colour(int standing) {
        return standing <= 10 ? 0xC03030 : standing <= 20 ? 0xE06040 : standing < 35 ? 0xC8C8C8 : standing < 45 ? 0x7CD07C
                : 0x7CC8FF;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
