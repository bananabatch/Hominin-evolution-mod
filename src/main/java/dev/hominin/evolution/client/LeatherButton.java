package dev.hominin.evolution.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button to go with the leather screens: a strip of worked hide, darker at the edge and lit along the top, that
 * lightens under the cursor. Lettering in cream, scrolling if it is too long, as the game's own buttons do.
 */
public class LeatherButton extends Button {
    private static final int OUTLINE = 0xFF24140A;
    private static final int FACE = 0xFF7E5431;
    private static final int FACE_LIT = 0xFFA0703F;
    private static final int FACE_OFF = 0xFF5E4838;
    private static final int TOP = 0xFF9A6A40;
    private static final int TOP_LIT = 0xFFC08A52;
    private static final int TOP_OFF = 0xFF6E5646;
    private static final int BOTTOM = 0xFF3E2614;
    private static final int TEXT = 0xFFF3E6CC;
    private static final int TEXT_OFF = 0xFF9C8A78;

    public LeatherButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean lit = active && isHoveredOrFocused();
        graphics.fill(x, y, x + w, y + h, OUTLINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, !active ? FACE_OFF : lit ? FACE_LIT : FACE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, !active ? TOP_OFF : lit ? TOP_LIT : TOP);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, BOTTOM);
        renderString(graphics, Minecraft.getInstance().font, active ? TEXT : TEXT_OFF);
    }
}
