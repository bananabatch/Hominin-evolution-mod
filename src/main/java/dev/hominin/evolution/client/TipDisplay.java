package dev.hominin.evolution.client;

import java.util.List;

import dev.hominin.evolution.network.TipPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Showing a tip: a toast in the corner, so it is noticed, and a line in chat, so it can be read
 * again and followed. Double-clicking the chat line opens the guide at the page the tip is about -
 * double, so a stray click while reading chat does not whisk you into a book.
 */
public final class TipDisplay {
    /** The command a tip's chat line runs - on the second click of a double-click only. */
    private static final String COMMAND = "/hominin tips read ";
    private static final long DOUBLE_CLICK_MS = 450L;

    private static String lastClicked;
    private static long lastClickedAt;

    public static void show(TipPayload tip) {
        Minecraft minecraft = Minecraft.getInstance();
        String text = keys(tip.text());
        boolean linked = !tip.entry().isEmpty();

        MutableComponent line = Component.empty();
        if (linked) {
            line.withStyle(Style.EMPTY
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND + tip.entry() + " " + tip.page()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Double-click to open your guide at this page."))));
        }
        line.append(Component.literal("Tip: ").withStyle(tip.urgent() ? ChatFormatting.RED : ChatFormatting.GOLD,
                ChatFormatting.BOLD));
        line.append(Component.literal(tip.title() + ". ").withStyle(tip.urgent() ? ChatFormatting.RED : ChatFormatting.YELLOW));
        line.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        if (linked) {
            line.append(Component.literal(" [Read more]").withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE));
        }
        minecraft.gui.getChat().addMessage(line);
        minecraft.getToasts().addToast(new TipToast(tip.title(), text, tip.urgent(), linked));
    }

    /** The player's own keys, not the defaults: whatever they have bound, the tip says. */
    private static String keys(String text) {
        return text.replace("{P}", key(ModKeyMappings.ITEM_INTERACT))
                .replace("{K}", key(ModKeyMappings.THINK))
                .replace("{O}", key(ModKeyMappings.BUILD))
                .replace("{H}", key(ModKeyMappings.SOCIAL))
                .replace("{G}", key(ModKeyMappings.THREAT_DISPLAY))
                .replace("{J}", key(ModKeyMappings.JOURNAL))
                .replace("{T}", key(Minecraft.getInstance().options.keyChat))
                .replace("{jump}", key(Minecraft.getInstance().options.keyJump))
                .replace("{sneak}", key(Minecraft.getInstance().options.keyShift));
    }

    private static String key(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * A click on a tip in the chat screen. The first is swallowed and remembered; a second on the same
     * tip, quickly, is let through - and the chat screen runs the tip's command, which opens the book.
     */
    public static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen) || event.getButton() != 0) {
            return;
        }
        Style style = Minecraft.getInstance().gui.getChat().getClickedComponentStyleAt(event.getMouseX(), event.getMouseY());
        ClickEvent click = style == null ? null : style.getClickEvent();
        if (click == null || click.getAction() != ClickEvent.Action.RUN_COMMAND || !click.getValue().startsWith(COMMAND)) {
            return;
        }
        long now = Util.getMillis();
        if (click.getValue().equals(lastClicked) && now - lastClickedAt <= DOUBLE_CLICK_MS) {
            lastClicked = null;
            return;
        }
        lastClicked = click.getValue();
        lastClickedAt = now;
        event.setCanceled(true);
    }

    /** The corner card: a book, the tip's title, and what it says - small, so it does not cover the world. */
    private static final class TipToast implements Toast {
        private static final int WIDTH = 170;
        private static final int TEXT_X = 22;
        private static final int MAX_LINES = 5;
        /** The body is set smaller than the title. */
        private static final float TEXT_SCALE = 0.75F;

        private final Component title;
        private final List<FormattedCharSequence> lines;
        private final boolean urgent;
        private final boolean linked;
        private final long showFor;

        TipToast(String title, String text, boolean urgent, boolean linked) {
            Font font = Minecraft.getInstance().font;
            this.title = Component.literal("Tip: " + title);
            List<FormattedCharSequence> split = font.split(Component.literal(text), (int) ((WIDTH - TEXT_X - 6) / TEXT_SCALE));
            this.lines = split.size() > MAX_LINES ? split.subList(0, MAX_LINES) : split;
            this.urgent = urgent;
            this.linked = linked;
            // Long enough to read at an unhurried pace, and longer still when it matters.
            this.showFor = (urgent ? 8000L : 4500L) + 1100L * lines.size();
        }

        @Override
        public int width() {
            return WIDTH;
        }

        @Override
        public int height() {
            return 18 + (int) Math.ceil(lines.size() * 9 * TEXT_SCALE) + (linked ? 8 : 0);
        }

        @Override
        public Visibility render(GuiGraphics graphics, ToastComponent toasts, long timeSinceLastVisible) {
            Font font = Minecraft.getInstance().font;
            graphics.fill(0, 0, width(), height(), 0xE615120E);
            graphics.renderOutline(0, 0, width(), height(), urgent ? 0xFFB23A2E : 0xFF9C7A3C);
            graphics.pose().pushPose();
            graphics.pose().translate(4.0F, 4.0F, 0.0F);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.renderFakeItem(new ItemStack(urgent ? Items.REDSTONE : Items.BOOK), 0, 0);
            graphics.pose().popPose();
            graphics.drawString(font, title, TEXT_X, 5, urgent ? 0xFFFF7A6A : 0xFFFFD27A, false);
            graphics.pose().pushPose();
            graphics.pose().translate(TEXT_X, 16.0F, 0.0F);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            int y = 0;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, 0, y, 0xFFE6E0D6, false);
                y += 9;
            }
            if (linked) {
                graphics.drawString(font, "Double-click it in chat to read more.", 0, y + 1, 0xFF8C8578, false);
            }
            graphics.pose().popPose();
            return timeSinceLastVisible >= showFor * toasts.getNotificationDisplayTimeMultiplier()
                    ? Visibility.HIDE : Visibility.SHOW;
        }
    }

    private TipDisplay() {
    }
}
