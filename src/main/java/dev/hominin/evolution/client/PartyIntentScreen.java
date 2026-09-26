package dev.hominin.evolution.client;

import dev.hominin.evolution.band.Parties;
import dev.hominin.evolution.network.OthersActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** "Send a party": what for. Then what they carry, and how many go. */
public class PartyIntentScreen extends Screen {
    private final Screen parent;
    private final String bandId;
    private final String bandName;

    public PartyIntentScreen(Screen parent, String bandId, String bandName) {
        super(Component.literal("Send a party to " + bandName));
        this.parent = parent;
        this.bandId = bandId;
        this.bandName = bandName;
    }

    @Override
    protected void init() {
        int y = Math.max(40, height / 2 - 80);
        for (int i = 0; i < Parties.INTENTS.length; i++) {
            int intent = i;
            Component label = Component.literal(Parties.INTENTS[i]).withStyle(i == Parties.RAID ? ChatFormatting.RED
                    : i == Parties.DEMAND ? ChatFormatting.GOLD : ChatFormatting.WHITE);
            addRenderableWidget(Button.builder(label, b -> PacketDistributor.sendToServer(
                    new OthersActionPayload(bandId, OthersActionPayload.PARTY + intent)))
                    .bounds(width / 2 - 100, y, 200, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> Minecraft.getInstance().setScreen(parent))
                .bounds(width / 2 - 100, y + 6, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, Math.max(40, height / 2 - 80) - 22, 0xE9D8A6);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
