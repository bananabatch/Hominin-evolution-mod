package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.List;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.block.KnappingStationBlockEntity;
import dev.hominin.evolution.item.AcheuleanToolItem;
import dev.hominin.evolution.knapping.Acheulean;
import dev.hominin.evolution.knapping.KnappingChoice;
import dev.hominin.evolution.knapping.KnappingStationMenu;
import dev.hominin.evolution.knapping.StationKnapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The knapping station: hammer and bone slots, four rows of stone, and beside them the
 * industries you can work in. Pick one along the top, then what to make; the panel shows
 * what it costs and - for the Acheulean - your chances of each tier with the stone laid out.
 */
public class KnappingStationScreen extends AbstractContainerScreen<KnappingStationMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "textures/gui/knapping_station.png");
    private static final int TEXTURE_WIDTH = 288;
    private static final int TEXTURE_HEIGHT = 222;
    private static final int PANEL_X = 180;
    private static final int PANEL_WIDTH = 102;
    private static final int[] TIER_COLOURS = {0xE08CFF, 0x7FE8FF, 0x8CE07A, 0xE8D86A, 0xB0B0B0};

    private enum Industry {
        OLDOWAN("Oldowan"),
        ACHEULEAN("Acheulean");

        private final String label;

        Industry(String label) {
            this.label = label;
        }
    }

    private Industry industry = Industry.OLDOWAN;
    private final List<Button> toolButtons = new ArrayList<>();

    public KnappingStationScreen(KnappingStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = TEXTURE_WIDTH;
        imageHeight = TEXTURE_HEIGHT;
        inventoryLabelY = KnappingStationMenu.INVENTORY_Y - 11;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        // Open on the Acheulean for anyone who can work it - that is what the station is for.
        if (menu.canWorkAcheulean() && industry == Industry.OLDOWAN && toolButtons.isEmpty()) {
            industry = Industry.ACHEULEAN;
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        toolButtons.clear();
        int x = leftPos + PANEL_X;
        int tabWidth = PANEL_WIDTH / 2 - 1;
        for (Industry each : Industry.values()) {
            Button tab = Button.builder(Component.literal(each.label), b -> {
                industry = each;
                rebuild();
            }).bounds(x + each.ordinal() * (tabWidth + 2), topPos + 4, tabWidth, 16).build();
            tab.active = each != industry;
            addRenderableWidget(tab);
        }
        List<KnappingChoice> choices = industry == Industry.ACHEULEAN ? Acheulean.CHOICES : StationKnapping.OLDOWAN;
        int y = topPos + 26;
        for (KnappingChoice choice : choices) {
            Button button = Button.builder(Component.translatable(choice.titleKey()),
                    b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, choice.ordinal()))
                    .bounds(x, y, PANEL_WIDTH, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(choice.hintKey())))
                    .build();
            button.active = industry == Industry.OLDOWAN || menu.canWorkAcheulean();
            toolButtons.add(button);
            addRenderableWidget(button);
            y += 20;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x3F3F3F, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x3F3F3F, false);
        graphics.drawString(font, "Hammerstone", 48, KnappingStationMenu.TOOL_SLOT_Y + 1, 0x3F3F3F, false);
        graphics.drawString(font, "Bopper (bone)", 48, KnappingStationMenu.TOOL_SLOT_Y + 10, 0x5A5A5A, false);
        graphics.drawString(font, "Stone to work", 8, KnappingStationMenu.STORAGE_Y - 10, 0x3F3F3F, false);
        drawPanel(graphics);
    }

    /** What the stone laid out first will make, and the odds of each tier. */
    private void drawPanel(GuiGraphics graphics) {
        int x = PANEL_X + 2;
        int rows = industry == Industry.ACHEULEAN ? Acheulean.CHOICES.size() : StationKnapping.OLDOWAN.size();
        int y = 26 + rows * 20 + 4;
        ItemStack stone = menu.workingStone();
        boolean hammer = !menu.station().getItem(KnappingStationBlockEntity.HAMMER).isEmpty();
        boolean bopper = !menu.station().getItem(KnappingStationBlockEntity.BOPPER).isEmpty();
        if (!hammer) {
            graphics.drawString(font, "No hammerstone", x, y, 0xC04040, false);
            y += 10;
        }
        if (industry == Industry.ACHEULEAN && !bopper) {
            graphics.drawString(font, "No bopper (bone)", x, y, 0xC04040, false);
            y += 10;
        }
        graphics.drawString(font, stone.isEmpty() ? "No stone laid out" : "Working: " + trim(stone.getHoverName().getString()),
                x, y, 0x3F3F3F, false);
        y += 11;
        if (industry == Industry.OLDOWAN) {
            graphics.drawString(font, "No quality tiers.", x, y, 0x5A5A5A, false);
            graphics.drawString(font, "Hammer only.", x, y + 10, 0x5A5A5A, false);
            return;
        }
        if (!menu.canWorkAcheulean()) {
            graphics.drawString(font, "Erectus hands only.", x, y, 0xC04040, false);
            return;
        }
        int level = menu.knappingLevel();
        graphics.drawString(font, "Your skill: level " + level, x, y, 0x3F3F3F, false);
        y += 11;
        if (stone.isEmpty()) {
            return;
        }
        double[] odds = Acheulean.odds(level, stone);
        for (int tier = 0; tier <= 4; tier++) {
            if (odds[tier] <= 0.0D) {
                continue;
            }
            String line = "T" + tier + " " + AcheuleanToolItem.TIER_NAMES[tier] + " " + Math.round(odds[tier] * 100) + "%";
            graphics.drawString(font, line, x, y, TIER_COLOURS[tier], true);
            y += 10;
        }
        if (stone.is(ModItems.LIMESTONE_ROCK.get())) {
            graphics.drawString(font, "Limestone: crude only.", x, y + 2, 0x5A5A5A, false);
        }
    }

    private String trim(String name) {
        return font.width(name) > PANEL_WIDTH - 50 ? font.plainSubstrByWidth(name, PANEL_WIDTH - 54) + "." : name;
    }

    /** Skill and stage arrive a moment after the screen opens, so the buttons catch up here. */
    @Override
    protected void containerTick() {
        super.containerTick();
        for (Button button : toolButtons) {
            button.active = industry == Industry.OLDOWAN || menu.canWorkAcheulean();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
