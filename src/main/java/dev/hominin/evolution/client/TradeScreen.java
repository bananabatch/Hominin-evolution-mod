package dev.hominin.evolution.client;

import java.util.List;

import dev.hominin.evolution.band.Trading;
import dev.hominin.evolution.network.TradeOpenPayload;
import dev.hominin.evolution.network.TradeRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Trading face to face. Down the middle, everything they carry and what it is worth to
 * them; along the bottom, your hotbar. Pick what you are offering from the hotbar, then
 * pick what you want from them. They take it or they do not - and either way the screen
 * stays open on what they have now.
 */
public class TradeScreen extends Screen {
    private static final int ROW = 22;
    private static final int ITEM_WIDTH = 190;
    /** Remembered between refreshes, so a trade does not reset what you were offering. */
    private static int offerSlot = -1;

    private final TradeOpenPayload trade;
    private final ResourceLocation era;

    private TradeScreen(TradeOpenPayload trade) {
        super(Component.literal("Trade with " + trade.name()));
        this.trade = trade;
        this.era = ResourceLocation.tryParse(trade.stage());
    }

    public static void open(TradeOpenPayload trade) {
        Player player = Minecraft.getInstance().player;
        if (offerSlot < 0 && player != null) {
            offerSlot = player.getInventory().selected;
        }
        Minecraft.getInstance().setScreen(new TradeScreen(trade));
    }

    private int tier(ItemStack stack) {
        return Trading.tierOf(stack, era);
    }

    private static String tierName(int tier) {
        return tier <= 0 ? "worthless to them" : Trading.TIER_NAMES[Math.min(Trading.MAX_TIER, tier)];
    }

    private int listTop() {
        return 44;
    }

    private int hotbarY() {
        return height - 58;
    }

    @Override
    protected void init() {
        int left = (width - ITEM_WIDTH) / 2;
        List<ItemStack> stacks = trade.stacks();
        int maxRows = Math.max(1, (hotbarY() - 24 - listTop()) / ROW);
        for (int i = 0; i < Math.min(stacks.size(), maxRows); i++) {
            ItemStack stack = stacks.get(i);
            int wanted = trade.slots().get(i);
            Component label = Component.literal(stack.getHoverName().getString()
                    + (stack.getCount() > 1 ? " x" + stack.getCount() : "") + "  - " + tierName(tier(stack)));
            addRenderableWidget(Button.builder(label, b -> PacketDistributor.sendToServer(
                    new TradeRequestPayload(trade.entityId(), offerSlot, wanted)))
                    .bounds(left + 20, listTop() + i * ROW, ITEM_WIDTH - 20, 20).build());
        }
        // Your hotbar: what you are putting forward.
        int barLeft = width / 2 - 9 * 22 / 2;
        for (int slot = 0; slot < 9; slot++) {
            int chosen = slot;
            Button button = Button.builder(Component.empty(), b -> {
                offerSlot = chosen;
                rebuildWidgets();
            }).bounds(barLeft + slot * 22, hotbarY(), 20, 20).build();
            button.active = slot != offerSlot;
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xE9D8A6);
        graphics.drawCenteredString(font, "Pick what you offer below, then what you want from them.",
                width / 2, 28, 0xBBBBBB);
        int left = (width - ITEM_WIDTH) / 2;
        List<ItemStack> stacks = trade.stacks();
        if (stacks.isEmpty()) {
            graphics.drawCenteredString(font, trade.name() + " isn't carrying anything.", width / 2, listTop() + 8,
                    0xBBBBBB);
        }
        int maxRows = Math.max(1, (hotbarY() - 24 - listTop()) / ROW);
        for (int i = 0; i < Math.min(stacks.size(), maxRows); i++) {
            graphics.renderItem(stacks.get(i), left, listTop() + i * ROW + 2);
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        int barLeft = width / 2 - 9 * 22 / 2;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            graphics.renderItem(stack, barLeft + slot * 22 + 2, hotbarY() + 2);
            graphics.renderItemDecorations(font, stack, barLeft + slot * 22 + 2, hotbarY() + 2);
        }
        ItemStack offer = offerSlot >= 0 ? player.getInventory().getItem(offerSlot) : ItemStack.EMPTY;
        String offerLine = offer.isEmpty() ? "You offer: nothing - pick a slot"
                : "You offer: " + offer.getHoverName().getString() + " - " + tierName(tier(offer));
        graphics.drawCenteredString(font, offerLine, width / 2, hotbarY() - 12, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
