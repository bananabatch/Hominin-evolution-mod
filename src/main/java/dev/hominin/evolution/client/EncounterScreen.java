package dev.hominin.evolution.client;

import dev.hominin.evolution.band.Claims;
import dev.hominin.evolution.network.EncounterChoicePayload;
import dev.hominin.evolution.network.EncounterPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Another band has walked up to you with something to say. Their name in the colour of where you stand, how
 * desperate they are, what they say, what they offer or want - and your answer: accept or decline an offer;
 * to a threat, give in, fight, or flee.
 */
public class EncounterScreen extends Screen {
    private static final int WIDTH = 260;

    private final EncounterPayload encounter;
    /** Where the panel starts, worked out from how much there is to say, so the answers sit just below it. */
    private int top;

    private EncounterScreen(EncounterPayload encounter) {
        super(Component.literal(encounter.band()));
        this.encounter = encounter;
    }

    public static void open(EncounterPayload payload) {
        Minecraft.getInstance().setScreen(new EncounterScreen(payload));
    }

    private boolean threat() {
        return encounter.kind() != Claims.Kind.OFFER.ordinal() && encounter.kind() != Claims.Kind.HELP.ordinal()
                && encounter.kind() != Claims.Kind.TRADE.ordinal() && encounter.kind() != Claims.Kind.VISIT.ordinal()
                && encounter.kind() != Claims.Kind.JOIN.ordinal();
    }

    @Override
    protected void init() {
        Claims.Kind kind = Claims.Kind.values()[Math.max(0, Math.min(Claims.Kind.values().length - 1, encounter.kind()))];
        int[] choices = Claims.choicesFor(kind);
        int gap = 6;
        // More than three answers: two to a row, so every label fits.
        int perRow = choices.length > 3 ? 2 : choices.length;
        int each = (WIDTH - gap * (perRow - 1)) / perRow;
        int left = (width - WIDTH) / 2;
        int content = 26 + 16 + 11 + font.split(Component.literal("\"" + encounter.line() + "\""), WIDTH).size() * 10 + 6
                + (encounter.items().isEmpty() ? 0 : 22) + font.split(Component.literal(encounter.detail()), WIDTH).size() * 10;
        top = Math.max(14, (height - content - 34) / 2);
        int y = top + content + 10;
        for (int i = 0; i < choices.length; i++) {
            int choice = choices[i];
            Component label = Component.literal(Claims.choiceLabel(choice, encounter.kind())).withStyle(
                    choice == Claims.FIGHT ? ChatFormatting.RED : choice == Claims.FLEE ? ChatFormatting.GOLD
                            : choice == Claims.DECLINE ? ChatFormatting.GRAY
                            : choice == Claims.COUNTER || choice == Claims.BETTER ? ChatFormatting.AQUA : ChatFormatting.GREEN);
            addRenderableWidget(Button.builder(label, b -> {
                PacketDistributor.sendToServer(new EncounterChoicePayload(choice));
                onClose();
            }).bounds(left + (i % perRow) * (each + gap), y + (i / perRow) * 24, each, 20).build());
        }
    }

    /** The panel goes under everything, buttons included. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        int bottom = children().isEmpty() ? height / 2 + 90
                : ((Button) children().get(children().size() - 1)).getY() + 28;
        graphics.fill(left - 8, top - 8, left + WIDTH + 8, bottom, 0xC0101010);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        int colour = encounter.standing() >= 45 ? 0x7CC8FF : encounter.standing() >= 35 ? 0x7CD07C
                : encounter.standing() > 20 ? 0xC8C8C8 : encounter.standing() > 10 ? 0xE06040 : 0xC03030;
        graphics.drawCenteredString(font, Component.literal(encounter.band()), width / 2, top, colour);
        graphics.drawCenteredString(font, Component.literal(threat() ? "- a threat -" : "- an offer -"), width / 2, top + 11,
                threat() ? 0xE06040 : 0x7CD07C);
        // How desperate: five boxes, filling up.
        int barY = top + 26;
        graphics.drawString(font, "Desperation", left, barY, 0xBBBBBB);
        for (int i = 0; i < 5; i++) {
            int x = left + 70 + i * 14;
            graphics.fill(x, barY, x + 11, barY + 8, 0xFF2A2A2A);
            if (i < encounter.desperation()) {
                int shade = 0xFF000000 | (0x60 + i * 0x24) << 16 | (0x90 - i * 0x18) << 8 | 0x30;
                graphics.fill(x + 1, barY + 1, x + 10, barY + 7, shade);
            }
        }
        graphics.drawString(font, Claims.desperationLabel(encounter.desperation()), left + 145, barY, 0xBBBBBB);
        int y = barY + 16;
        graphics.drawString(font, Component.literal(encounter.speaker() + ":").withStyle(ChatFormatting.ITALIC), left, y,
                colour);
        y += 11;
        for (FormattedCharSequence part : font.split(Component.literal("\"" + encounter.line() + "\""), WIDTH)) {
            graphics.drawString(font, part, left, y, 0xFFFFFF);
            y += 10;
        }
        y += 6;
        if (!encounter.items().isEmpty()) {
            graphics.drawString(font, threat() ? "They want:" : "They offer:", left, y + 4, 0xE9D8A6);
            int x = left + 70;
            ItemStack hovered = ItemStack.EMPTY;
            for (ItemStack stack : encounter.items()) {
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    hovered = stack;
                }
                x += 20;
            }
            y += 22;
            if (!hovered.isEmpty()) {
                graphics.renderTooltip(font, hovered, mouseX, mouseY);
            }
        }
        for (FormattedCharSequence part : font.split(Component.literal(encounter.detail()), WIDTH)) {
            graphics.drawString(font, part, left, y, 0x9C9C9C);
            y += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
