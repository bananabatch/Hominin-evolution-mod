package dev.hominin.evolution.client;

import dev.hominin.evolution.hunt.HuntParty;
import dev.hominin.evolution.network.HuntPayload;
import dev.hominin.evolution.network.HuntStartPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Let's hunt": everything about worth going after, what each is (prey that runs, a fighter, a predator, megafauna),
 * how far and how many. Pick one, how many of the band go and what they carry - and for megafauna, whether you go
 * with the attackers, who bring the chosen one down, or the chasers, who draw the rest of the herd off it.
 */
public class HuntScreen extends Screen {
    private static final int ROW = 16;
    private static final int TOP = 40;
    private static final int WIDTH = 320;

    private final HuntPayload hunt;
    private int selected = -1;
    private int scroll;
    private int members;
    private int weapon;
    private int team;
    private int style;

    private HuntScreen(HuntPayload hunt) {
        super(Component.literal("Let's hunt"));
        this.hunt = hunt;
        this.members = Math.min(3, hunt.maxMembers());
    }

    public static void open(HuntPayload payload) {
        Minecraft.getInstance().setScreen(new HuntScreen(payload));
    }

    private int rows() {
        return Math.max(3, (height - TOP - 90) / ROW);
    }

    private boolean mega() {
        return selected >= 0 && hunt.targets().get(selected).mega();
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int y = TOP + rows() * ROW + 8;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> members = Math.max(0, members - 1))
                .bounds(left, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> members = Math.min(hunt.maxMembers(), members + 1))
                .bounds(left + 90, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Carry: " + HuntParty.WEAPONS[weapon]), b -> {
            weapon = (weapon + 1) % HuntParty.WEAPONS.length;
            rebuildWidgets();
        }).bounds(left + 116, y, WIDTH - 116, 18).build());
        y += 22;
        addRenderableWidget(Button.builder(Component.literal("Go: " + HuntParty.STYLES[style]), b -> {
            style = (style + 1) % HuntParty.STYLES.length;
            rebuildWidgets();
        }).bounds(left, y, WIDTH, 18).build());
        y += 22;
        if (mega()) {
            addRenderableWidget(Button.builder(Component.literal(team == HuntParty.ATTACKERS
                    ? "You go with: the attackers (bring the chosen one down)"
                    : "You go with: the chasers (draw the herd off it)"), b -> {
                team = 1 - team;
                rebuildWidgets();
            }).bounds(left, y, WIDTH, 18).build());
            y += 22;
        }
        Button go = Button.builder(Component.literal("Hunt it"), b -> {
            PacketDistributor.sendToServer(new HuntStartPayload(hunt.targets().get(selected).entityId(), members, weapon,
                    team, style));
            onClose();
        }).bounds(width / 2 - 104, y + 4, 100, 20).build();
        go.active = selected >= 0;
        addRenderableWidget(go);
        addRenderableWidget(Button.builder(Component.literal("Not now"), b -> onClose())
                .bounds(width / 2 + 4, y + 4, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        graphics.drawCenteredString(font, title, width / 2, 12, 0xE9D8A6);
        graphics.drawCenteredString(font, hunt.targets().isEmpty() ? "Nothing worth hunting is about."
                : "Pick what to go after. (Megafauna: pick the one to bring down.)", width / 2, 24, 0xBBBBBB);
        for (int i = scroll; i < Math.min(hunt.targets().size(), scroll + rows()); i++) {
            HuntPayload.Target target = hunt.targets().get(i);
            int y = TOP + (i - scroll) * ROW;
            graphics.fill(left, y, left + WIDTH, y + ROW - 1, i == selected ? 0x6030A030 : 0x40101010);
            String line = target.name() + (target.herd() > 1 ? " (herd of " + target.herd() + ")" : "") + " - "
                    + target.kind() + ", " + target.distance() + " blocks";
            graphics.drawString(font, line, left + 4, y + 4, target.mega() ? 0xFFD27A : target.kind().startsWith("predator")
                    ? 0xFF8C6C : 0xFFFFFF);
        }
        if (hunt.targets().size() > rows()) {
            graphics.drawString(font, "(scroll for more)", left + WIDTH - 90, TOP + rows() * ROW - 10, 0x8C8578);
        }
        int y = TOP + rows() * ROW + 8;
        graphics.drawString(font, members + " of " + hunt.maxMembers(), left + 26, y + 5,
                hunt.maxMembers() == 0 ? 0xE06040 : 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - WIDTH) / 2;
        if (mouseX >= left && mouseX < left + WIDTH && mouseY >= TOP && mouseY < TOP + rows() * ROW) {
            int i = scroll + (int) ((mouseY - TOP) / ROW);
            if (i < hunt.targets().size()) {
                selected = i;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, hunt.targets().size() - rows()), scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
