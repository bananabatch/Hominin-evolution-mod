package dev.hominin.evolution.client;

import java.util.List;

import dev.hominin.evolution.knapping.KnappingChoice;
import dev.hominin.evolution.network.KnappingChoicePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The knapping screen: pick what you are trying to make, then commit to it.
 *
 * <p>Deliberately not a container screen. There is nothing to arrange - a hominin
 * knapping a core is making one decision, about where the next blow goes, and a
 * grid of slots would say the opposite. When knapping skill and precision land,
 * this is where they go.
 */
public class KnappingScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 48;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;

    /** Button top to button top. The slack below each one is where its hint sits. */
    private static final int BUTTON_SPACING = 42;

    /** Gap between the stone panel and the first button. */
    private static final int PANEL_GAP = 16;

    /** Drop from a button's bottom edge to its hint line. */
    private static final int HINT_OFFSET = 6;

    private static final int PANEL_BACKGROUND = 0xF0120F0C;
    private static final int PANEL_BORDER = 0xFF5A4B3C;
    private static final int TITLE_COLOUR = 0xFFE9D8A6;
    private static final int HINT_COLOUR = 0xFF9A9A9A;

    private final String stoneName;
    private final boolean goodStone;

    /** Only what the server said this stone can become, in the order it was sent. */
    private final List<KnappingChoice> choices;

    /**
     * One origin for the whole layout, worked out in {@link #init()} and read back
     * when drawing. Letting the buttons and their labels each derive a position
     * independently is how they end up on top of each other.
     */
    private int panelTop;
    private int firstButtonTop;

    /** Set while a choice is in flight, so closing the screen does not send a second. */
    private boolean committed;

    private KnappingScreen(String stoneName, boolean goodStone, List<KnappingChoice> choices) {
        super(Component.translatable("screen.hominin_evolution.knapping"));
        this.stoneName = stoneName;
        this.goodStone = goodStone;
        this.choices = choices;
    }

    /** Called from the network handler, already on the client thread. */
    public static void open(String stoneName, boolean goodStone, List<Integer> choiceIds) {
        // Unknown ids are dropped rather than trusted: a stale client and a newer
        // server must not be able to put a button on screen that does nothing.
        List<KnappingChoice> choices = choiceIds.stream()
                .map(KnappingChoice::byId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!choices.isEmpty()) {
            Minecraft.getInstance().setScreen(new KnappingScreen(stoneName, goodStone, choices));
        }
    }

    @Override
    protected void init() {
        int contentHeight = PANEL_HEIGHT + PANEL_GAP + choices.size() * BUTTON_SPACING;
        panelTop = (this.height - contentHeight) / 2;
        firstButtonTop = panelTop + PANEL_HEIGHT + PANEL_GAP;

        int y = firstButtonTop;
        for (KnappingChoice choice : choices) {
            addRenderableWidget(Button.builder(Component.translatable(choice.titleKey()), press -> commit(choice))
                    .bounds((this.width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += BUTTON_SPACING;
        }
    }

    private void commit(KnappingChoice choice) {
        if (committed) {
            return;
        }
        committed = true;
        PacketDistributor.sendToServer(new KnappingChoicePayload(choice.ordinal()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // super.render() dims the world and draws the buttons. The panel has to go
        // on top of that dim, not under it, or its text is greyed out with the
        // background. Nothing overlaps, because the buttons start below the panel.
        super.render(graphics, mouseX, mouseY, partialTick);

        int panelLeft = (this.width - PANEL_WIDTH) / 2;
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, PANEL_BACKGROUND);
        graphics.renderOutline(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BORDER);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelTop + 8, TITLE_COLOUR);
        graphics.drawCenteredString(this.font, Component.literal(stoneName), this.width / 2, panelTop + 22, 0xFFFFFFFF);
        // The warning is the only tell that limestone is a trap, and it is worth
        // telling: a player who walks into it on purpose gets a better story.
        Component quality = goodStone
                ? Component.translatable("screen.hominin_evolution.knapping.good").withStyle(ChatFormatting.GREEN)
                : Component.translatable("screen.hominin_evolution.knapping.poor").withStyle(ChatFormatting.RED);
        graphics.drawCenteredString(this.font, quality, this.width / 2, panelTop + 34, 0xFFFFFFFF);

        // Hints sit under their own button rather than in a tooltip, because the
        // difference between these three is the entire decision being made.
        int y = firstButtonTop + BUTTON_HEIGHT + HINT_OFFSET;
        for (KnappingChoice choice : choices) {
            graphics.drawCenteredString(this.font, Component.translatable(choice.hintKey()),
                    this.width / 2, y, HINT_COLOUR);
            y += BUTTON_SPACING;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
