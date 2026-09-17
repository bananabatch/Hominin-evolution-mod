package dev.hominin.evolution.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Social;
import dev.hominin.evolution.band.SocialSelection;
import dev.hominin.evolution.network.SocialCommandPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The talk menu. Opened with H near a band to speak to all of it, or within a few
 * seconds of sneak-using one member to speak to just them.
 *
 * <p>Said in two steps - what it is about, then what to say - so the list stays short
 * however much there is to say.
 */
public class SocialScreen extends Screen {
    /** How long after picking someone out the menu still talks to just them. */
    private static final long SELECTION_MS = 5_000L;

    private static final int BUTTON_WIDTH = 210;
    private static final int ROW = 24;

    private final int targetId;
    private final boolean otherBand;
    private final boolean otherBandNear;
    private final Component who;

    /** Which list is open, or null while the topics themselves are showing. */
    @Nullable
    private Social.Topic topic;

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        BandMember target = null;
        if (SocialSelection.entityId >= 0 && Util.getMillis() - SocialSelection.selectedAtMillis <= SELECTION_MS
                && mc.level.getEntity(SocialSelection.entityId) instanceof BandMember member && member.isAlive()) {
            target = member;
        }
        SocialSelection.entityId = -1;
        if (target != null) {
            mc.setScreen(new SocialScreen(target.getId(), target.getName(), target.isOtherBand(),
                    target.isOtherBand()));
            return;
        }
        BandMember nearest = nearest(mc.player);
        if (nearest == null) {
            mc.player.displayClientMessage(Component.literal("There is nobody near enough to talk to."), true);
            return;
        }
        mc.setScreen(new SocialScreen(-1, Component.literal(nearest.isOtherBand() ? "the other band" : "your band"),
                nearest.isOtherBand(), otherBandNear(mc.player)));
    }

    @Nullable
    private static BandMember nearest(LocalPlayer player) {
        BandMember best = null;
        for (BandMember member : player.level().getEntitiesOfClass(BandMember.class,
                player.getBoundingBox().inflate(16.0D))) {
            // Your own band is always who you mean if they are there at all.
            if (best == null || (best.isOtherBand() && !member.isOtherBand())
                    || (best.isOtherBand() == member.isOtherBand()
                            && member.distanceToSqr(player) < best.distanceToSqr(player))) {
                best = member;
            }
        }
        return best;
    }

    private SocialScreen(int targetId, Component name, boolean otherBand, boolean otherBandNear) {
        super(Component.literal("Talk to ").append(name));
        this.targetId = targetId;
        this.otherBand = otherBand;
        this.otherBandNear = otherBandNear;
        this.who = name;
    }

    /** Another band close enough to ask along - offered even while your own band is here too. */
    private static boolean otherBandNear(LocalPlayer player) {
        return !player.level().getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(16.0D),
                BandMember::isOtherBand).isEmpty();
    }

    /** Whether this can be said at all, to whoever the menu is aimed at. */
    private boolean offered(Social.Command command) {
        if (command == Social.Command.TRAVEL) {
            return otherBandNear;
        }
        if (command == Social.Command.INFO || command == Social.Command.GROOM) {
            // Both are about one hominin, not a crowd - and only your own will let you that close.
            return targetId >= 0 && (command == Social.Command.GROOM || !otherBand);
        }
        boolean ownBandOnly = command == Social.Command.ITEM || command == Social.Command.HUNT
                || command == Social.Command.NO_HUNT || command == Social.Command.CLIMB;
        return !(ownBandOnly && otherBand);
    }

    private List<Social.Command> commandsIn(Social.Topic wanted) {
        List<Social.Command> commands = new ArrayList<>();
        for (Social.Command command : Social.Command.values()) {
            if (command.topic() == wanted && offered(command)) {
                commands.add(command);
            }
        }
        return commands;
    }

    /** The errand list is a screen of its own, and belongs with the other asking-for-things. */
    private boolean hasFetch(Social.Topic wanted) {
        return wanted == Social.Topic.THINGS && !otherBand;
    }

    @Override
    protected void init() {
        LocalPlayer player = Minecraft.getInstance().player;
        int x = (width - BUTTON_WIDTH) / 2;
        int y = top();
        if (topic == null) {
            for (Social.Topic candidate : Social.Topic.values()) {
                if (commandsIn(candidate).isEmpty() && !hasFetch(candidate)) {
                    continue;
                }
                addRenderableWidget(Button.builder(Component.literal(candidate.label()), b -> {
                    topic = candidate;
                    rebuildWidgets();
                }).bounds(x, y, BUTTON_WIDTH, 20).build());
                y += ROW;
            }
            return;
        }
        for (Social.Command command : commandsIn(topic)) {
            Button button = Button.builder(Component.literal(command.label()), b -> {
                PacketDistributor.sendToServer(new SocialCommandPayload(targetId, command.ordinal()));
                onClose();
            }).bounds(x, y, BUTTON_WIDTH, 20).build();
            if (player != null) {
                if (command == Social.Command.FOOD) {
                    button.active = player.getFoodData().needsFood();
                } else if (command == Social.Command.HURT) {
                    button.active = player.getHealth() < player.getMaxHealth();
                } else if (command == Social.Command.CLIMB) {
                    var stage = ClientSync.stageOf(player.getUUID());
                    String path = stage == null ? "" : stage.getPath();
                    button.active = path.equals("ardipithecus") || path.equals("australopithecus")
                            || path.equals("homo_habilis");
                }
            }
            addRenderableWidget(button);
            y += ROW;
        }
        if (hasFetch(topic)) {
            addRenderableWidget(Button.builder(Component.literal("Get me..."), b -> ItemPickScreen.openFetch(
                    targetId, targetId >= 0 ? who : Component.literal("your band")))
                    .bounds(x, y, BUTTON_WIDTH, 20).build());
            y += ROW;
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            topic = null;
            rebuildWidgets();
        }).bounds(x, y + 6, BUTTON_WIDTH, 20).build());
    }

    private int top() {
        return Math.max(40, height / 2 - 80);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top() - 30, 0xE9D8A6);
        if (topic != null) {
            graphics.drawCenteredString(font, Component.literal(topic.label()), width / 2, top() - 16, 0xBFBFBF);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Backspace goes up a level rather than out of the menu entirely.
        if (key == 259 && topic != null) {
            topic = null;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
