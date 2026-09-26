package dev.hominin.evolution.client;

import dev.hominin.evolution.band.PileMenu;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.network.PileActionPayload;
import dev.hominin.evolution.network.PilePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A pile, looked over: everything in it, top first - what each thing is, its stone, its quality and wear, who laid
 * it down - with Take where you may, and for your own things who they are for.
 */
public class PileScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int ROW = 24;

    private final PilePayload pile;

    private PileScreen(PilePayload pile) {
        super(Component.literal(pile.header().isEmpty() ? "A pile" : pile.header().get(0)));
        this.pile = pile;
    }

    public static void open(PilePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.stacks().isEmpty()) {
            // Emptied, or gone: close it if it is the one open.
            if (mc.screen instanceof PileScreen open && open.pile.pos().equals(payload.pos())) {
                open.onClose();
            }
            return;
        }
        if (mc.screen == null || mc.screen instanceof PileScreen) {
            mc.setScreen(new PileScreen(payload));
        }
    }

    public static void request(net.minecraft.core.BlockPos pos) {
        PacketDistributor.sendToServer(new PileActionPayload(pos, PileMenu.OPEN, -1));
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return Math.max(8, height / 2 - (pile.stacks().size() * ROW + 70) / 2);
    }

    private int rowY(int row) {
        return top() + 30 + row * ROW;
    }

    @Override
    protected void init() {
        int count = pile.stacks().size();
        for (int row = 0; row < count; row++) {
            int code = pile.codes().get(count - 1 - row);
            // The server says which slot each row is: racks have gaps.
            int slot = code >> PileMenu.SLOT_SHIFT;
            int y = rowY(row);
            if ((code & PileMenu.YOURS) != 0) {
                int mark = code & 3;
                Button markButton = Button.builder(Component.literal(markLabel(mark)), b -> send(PileMenu.MARK, slot))
                        .bounds(left() + WIDTH - 164, y + 2, 116, 18).build();
                markButton.setTooltip(Tooltip.create(Component.literal("Who in your band may take it: everyone, you "
                        + "alone, or those close to you (bond 4 and up). Click to change.")));
                addRenderableWidget(markButton);
            }
            Button take = Button.builder(Component.literal("Take"), b -> send(PileMenu.TAKE, slot))
                    .bounds(left() + WIDTH - 44, y + 2, 44, 18).build();
            take.active = (code & PileMenu.MAY_TAKE) != 0;
            if (!take.active) {
                take.setTooltip(Tooltip.create(Component.literal("Marked for someone else.")));
            }
            addRenderableWidget(take);
        }
        int footer = rowY(count) + 8;
        addRenderableWidget(Button.builder(Component.literal("Take all you may"), b -> send(PileMenu.TAKE_ALL, -1))
                .bounds(width / 2 - 170, footer, 110, 20).build());
        boolean rack = pile.header().size() > 3 && pile.header().get(3).equals("rack");
        Button relocate = Button.builder(Component.literal("Relocate"), b -> send(PileMenu.RELOCATE, -1))
                .bounds(width / 2 - 55, footer, 110, 20).build();
        relocate.visible = !rack;
        relocate.active = pile.header().size() > 2 && pile.header().get(2).equals("1");
        relocate.setTooltip(Tooltip.create(Component.literal(relocate.active
                ? "Gather the whole pile up as one thing, to set down somewhere else. Set it down soon if any of it is "
                        + "other people's - the band minds."
                : "Not your band's to move.")));
        addRenderableWidget(relocate);
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(width / 2 + 60, footer, 110, 20).build());
    }

    private static String markLabel(int mark) {
        return switch (mark) {
            case ToolPileBlockEntity.FOR_ME -> "For: only me";
            case ToolPileBlockEntity.FOR_CLOSE -> "For: bond 4+";
            default -> "For: everyone";
        };
    }

    private void send(int action, int slot) {
        PacketDistributor.sendToServer(new PileActionPayload(pile.pos(), action, slot));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left();
        int count = pile.stacks().size();
        graphics.fill(x - 6, top() - 6, x + WIDTH + 6, rowY(count) + 34, 0xB0101418);
        graphics.drawString(font, title, x, top(), 0xFFD27F);
        if (pile.header().size() > 1) {
            graphics.drawString(font, pile.header().get(1), x, top() + 12, 0xAAAAAA);
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int row = 0; row < count; row++) {
            int slot = count - 1 - row;
            int y = rowY(row);
            ItemStack stack = pile.stacks().get(slot);
            int code = pile.codes().get(slot);
            if (row % 2 == 0) {
                graphics.fill(x - 2, y, x + WIDTH + 2, y + ROW - 1, 0x30FFFFFF);
            }
            graphics.renderItem(stack, x, y + 3);
            graphics.renderItemDecorations(font, stack, x, y + 3);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 3 && mouseY < y + 19) {
                hovered = stack;
            }
            int textWidth = WIDTH - 20 - ((code & PileMenu.YOURS) != 0 ? 170 : 50);
            String name = stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : "");
            int nameColour = (code & PileMenu.MAY_TAKE) != 0 ? 0xFFFFFF : 0x9A9A9A;
            graphics.drawString(font, font.plainSubstrByWidth(name, textWidth), x + 20, y + 3, nameColour);
            String line = pile.details().get(slot);
            String by = "laid by " + pile.by().get(slot);
            if ((code & PileMenu.YOURS) == 0 && (code & 3) != ToolPileBlockEntity.FOR_EVERYONE) {
                by += ", " + PileMenu.markText(code & 3).replace("you", "them").replace("your", "their");
            }
            String second = line.isEmpty() ? by : line + " - " + by;
            graphics.drawString(font, font.plainSubstrByWidth(second, textWidth), x + 20, y + 13, 0x9FB8C8);
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ModKeyMappings.ITEM_INTERACT.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
