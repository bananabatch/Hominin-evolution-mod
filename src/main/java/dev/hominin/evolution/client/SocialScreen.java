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
                    target.isOtherBand() && !dev.hominin.evolution.band.Paranthropus.is(target),
                    dev.hominin.evolution.band.Paranthropus.is(target)));
            return;
        }
        BandMember nearest = nearest(mc.player);
        if (nearest == null) {
            mc.player.displayClientMessage(Component.literal("There is nobody near enough to talk to."), true);
            return;
        }
        boolean paranthropus = dev.hominin.evolution.band.Paranthropus.is(nearest);
        mc.setScreen(new SocialScreen(-1, Component.literal(paranthropus ? "the Paranthropus"
                : nearest.isOtherBand() ? "the other band" : "your band"),
                nearest.isOtherBand(), otherBandNear(mc.player), paranthropus));
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

    private final boolean paranthropus;

    private SocialScreen(int targetId, Component name, boolean otherBand, boolean otherBandNear, boolean paranthropus) {
        super(Component.literal("Talk to ").append(name));
        this.paranthropus = paranthropus;
        this.targetId = targetId;
        this.otherBand = otherBand;
        this.otherBandNear = otherBandNear;
        this.who = name;
    }

    /** Another band close enough to ask along - offered even while your own band is here too. */
    private static boolean otherBandNear(LocalPlayer player) {
        return !player.level().getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(16.0D),
                m -> m.isOtherBand() && !dev.hominin.evolution.band.Paranthropus.is(m)).isEmpty();
    }

    /** Whether this can be said at all, to whoever the menu is aimed at. */
    private boolean offered(Social.Command command) {
        if (command.topic() == Social.Topic.DEVELOPER) {
            return ClientSync.devMode;
        }
        boolean guiding = command == Social.Command.LEAD_STONE || command == Social.Command.LEAD_OBSIDIAN;
        if (paranthropus) {
            // Not much in common to talk about: a trade, or being shown the way.
            return command == Social.Command.TRADE || guiding;
        }
        if (guiding) {
            return false;
        }
        if (command == Social.Command.TRIBE || command == Social.Command.PROMISE) {
            return targetId < 0 && !otherBand;
        }
        if (command == Social.Command.SHUN) {
            return targetId >= 0 && !otherBand;
        }
        if (command == Social.Command.PASS_AROUND) {
            return !otherBand;
        }
        if (command == Social.Command.KNAP) {
            // Asked of one member of your own band, picked out.
            return targetId >= 0 && !otherBand;
        }
        if (command == Social.Command.HAVE_CHILD || command == Social.Command.MAKE_MATE) {
            return targetId >= 0 && !otherBand;
        }
        if (command == Social.Command.CULTURE) {
            // A rule for your own band, and only for a mind that can hold one: erectus on.
            net.minecraft.resources.ResourceLocation stage = Minecraft.getInstance().player == null ? null
                    : ClientSync.stageOf(Minecraft.getInstance().player.getUUID());
            String era = stage == null ? "" : stage.getPath();
            return !otherBand && !era.isEmpty() && !era.startsWith("australopithecus") && !era.equals("ardipithecus")
                    && !era.equals("homo_habilis") && !era.equals("homo_rudolfensis");
        }
        if (command == Social.Command.GIVE) {
            // To one you picked out - a stray you are winning over, a guest - or to your own band.
            return targetId >= 0 || !otherBand;
        }
        if (command == Social.Command.TRAVEL) {
            return otherBandNear;
        }
        if (command == Social.Command.INFO || command == Social.Command.GROOM) {
            // Both are about one hominin, not a crowd - and only your own will let you that close.
            return targetId >= 0 && (command == Social.Command.GROOM || !otherBand);
        }
        if (command.topic() == Social.Topic.DEVELOPER) {
            return ClientSync.devMode;
        }
        if (command == Social.Command.TEACH) {
            return !otherBand;
        }
        if (command == Social.Command.TRADE) {
            // One hominin at a time: the one you picked out, or the nearest of another band.
            return targetId >= 0 || otherBand;
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
        return wanted == Social.Topic.THINGS && !otherBand && !paranthropus;
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
                    if (candidate == Social.Topic.CULTURE) {
                        // Culture is a screen of its own: straight there.
                        PacketDistributor.sendToServer(new SocialCommandPayload(-1, Social.Command.CULTURE.ordinal()));
                        return;
                    }
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
