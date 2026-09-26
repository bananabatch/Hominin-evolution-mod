package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.network.ChoosePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A page of facts: one member (sex, health, hunger, favourite foods, bond, wants), or the
 * whole band. Scrolls when there is more than fits - a big band is a long list.
 *
 * <p>Two markers from the server: a line starting {@code #} is a section heading, and one
 * starting {@code @id|} names a member - hovered it lights up, clicked it makes them glow and
 * tells you which way they are.
 */
public class MemberInfoScreen extends Screen {
    private static final int LINE = 12;
    private static final int TOP = 34;
    private static final int BOTTOM_MARGIN = 40;
    private static final int HEADING = 0xE9C46A;
    private static final int LINK = 0xA8DADC;
    private static final int LINK_HOVER = 0xFFFFFF;

    /** One line as drawn: its text, whose it is (or -1), and whether it heads a section. */
    private record Line(String text, int entityId, boolean heading) {
    }

    private final List<Line> lines = new ArrayList<>();
    private int scroll;

    private MemberInfoScreen(String name, List<String> raw) {
        super(Component.literal(name));
        for (String line : raw) {
            if (line.startsWith("#")) {
                lines.add(new Line(line.substring(1), -1, true));
            } else if (line.startsWith("@") && line.indexOf('|') > 1) {
                int bar = line.indexOf('|');
                int id;
                try {
                    id = Integer.parseInt(line.substring(1, bar));
                } catch (NumberFormatException e) {
                    id = -1;
                }
                lines.add(new Line(line.substring(bar + 1), id, false));
            } else {
                lines.add(new Line(line, -1, false));
            }
        }
    }

    public static void open(String name, List<String> lines) {
        Minecraft.getInstance().setScreen(new MemberInfoScreen(name, lines));
    }

    private int visibleLines() {
        return Math.max(1, (height - TOP - BOTTOM_MARGIN) / LINE);
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - visibleLines());
    }

    /** Short pages sit in the middle; long ones start at the top and scroll. */
    private int top() {
        return lines.size() <= visibleLines() ? Math.max(TOP, height / 2 - (lines.size() * LINE) / 2 - 10) : TOP;
    }

    @Override
    protected void init() {
        int buttonY = Math.min(height - 28, top() + Math.min(lines.size(), visibleLines()) * LINE + 12);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, buttonY, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    /** The member line under the mouse, if any. */
    private int lineAt(double mouseX, double mouseY) {
        int top = top();
        if (mouseY < top) {
            return -1;
        }
        int row = (int) ((mouseY - top) / LINE);
        int index = scroll + row;
        if (row >= visibleLines() || index >= lines.size()) {
            return -1;
        }
        Line line = lines.get(index);
        int half = font.width(line.text()) / 2;
        return line.entityId() >= 0 && Math.abs(mouseX - width / 2.0D) <= half + 2 ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = lineAt(mouseX, mouseY);
        if (index >= 0 && button == 0) {
            PacketDistributor.sendToServer(new ChoosePayload(lines.get(index).entityId(), ChoosePayload.ACTION_FIND, 0));
            onClose();
            return true;
        }
        if (index >= 0 && button == 1 && title.getString().equals("Your band")) {
            // Keep an eye on them: tracked in the corner of the screen, or no longer.
            PacketDistributor.sendToServer(new ChoosePayload(lines.get(index).entityId(),
                    dev.hominin.evolution.band.Tracking.ACTION_TRACK, 0));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = top();
        graphics.drawCenteredString(font, title, width / 2, top - 18, 0xE9D8A6);
        int hovered = lineAt(mouseX, mouseY);
        int shown = Math.min(lines.size() - scroll, visibleLines());
        for (int i = 0; i < shown; i++) {
            Line line = lines.get(scroll + i);
            int colour = line.heading() ? HEADING : line.entityId() >= 0 ? (scroll + i == hovered ? LINK_HOVER : LINK)
                    : 0xFFFFFF;
            String text = line.heading() ? "- " + line.text() + " -" : line.text();
            graphics.drawCenteredString(font, text, width / 2, top + i * LINE, colour);
            if (scroll + i == hovered) {
                int half = font.width(text) / 2;
                graphics.fill(width / 2 - half, top + i * LINE + 9, width / 2 + half, top + i * LINE + 10, LINK_HOVER | 0xFF000000);
            }
        }
        if (maxScroll() > 0) {
            graphics.drawCenteredString(font, "(scroll for more: " + (scroll + shown) + "/" + lines.size() + ")",
                    width / 2, top + shown * LINE, 0x9A9A9A);
        }
    }

    /** On the band's own list: hover a name and press the work key to say you suspect them of using everyone. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeyMappings.ITEM_INTERACT.matches(keyCode, scanCode) && title.getString().equals("Your band")) {
            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() * width / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * height / mc.getWindow().getScreenHeight();
            int index = lineAt(mouseX, mouseY);
            if (index >= 0) {
                PacketDistributor.sendToServer(new ChoosePayload(lines.get(index).entityId(),
                        dev.hominin.evolution.band.Psychopaths.ACTION_SUSPECT, 0));
                onClose();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
