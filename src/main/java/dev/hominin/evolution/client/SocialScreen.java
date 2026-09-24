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
    /** Inside the Developer tab: which section is open, or null while the sections are showing. */
    @Nullable
    private Social.DevSection devSection;

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
            // Alone: nobody to talk to but the others, far off - what you know of them.
            PacketDistributor.sendToServer(new dev.hominin.evolution.network.OthersActionPayload("",
                    dev.hominin.evolution.network.OthersActionPayload.OPEN));
            return;
        }
        boolean paranthropus = dev.hominin.evolution.band.Paranthropus.is(nearest);
        mc.setScreen(new SocialScreen(-1, Component.literal(paranthropus ? "the Paranthropus"
                : nearest.isOtherBand() ? "the other band" : "your band"),
                nearest.isOtherBand(), otherBandNear(mc.player), paranthropus));
    }

    /** The nearest of another band's people, hominins before Paranthropus - who "the other band" means. */
    @Nullable
    private static BandMember nearestOther(LocalPlayer player) {
        BandMember best = null;
        for (BandMember member : player.level().getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(16.0D),
                BandMember::isOtherBand)) {
            boolean troop = dev.hominin.evolution.band.Paranthropus.is(member);
            boolean bestTroop = best != null && dev.hominin.evolution.band.Paranthropus.is(best);
            if (best == null || (bestTroop && !troop)
                    || (bestTroop == troop && member.distanceToSqr(player) < best.distanceToSqr(player))) {
                best = member;
            }
        }
        return best;
    }

    private static boolean ownBandNear(LocalPlayer player) {
        return !player.level().getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(16.0D),
                m -> !m.isOtherBand()).isEmpty();
    }

    /**
     * Whether the title can be clicked to talk to the other side instead: your band and another are both
     * here, and you are talking to one of them as a whole.
     */
    private boolean swappable() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && targetId < 0 && ownBandNear(player) && nearestOther(player) != null;
    }

    /** "Talk to your band" becomes "Talk to the other band", and back. */
    private void swap() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (otherBand) {
            minecraft.setScreen(new SocialScreen(-1, Component.literal("your band"), false, otherBandNear(player), false));
            return;
        }
        BandMember other = nearestOther(player);
        if (other == null) {
            return;
        }
        boolean troop = dev.hominin.evolution.band.Paranthropus.is(other);
        minecraft.setScreen(new SocialScreen(Social.OTHER_BAND, Component.literal(troop ? "the Paranthropus" : "the other band"),
                true, !troop, troop));
    }

    private int titleY() {
        return top() - 30;
    }

    private boolean overTitle(double mouseX, double mouseY) {
        int half = font.width(title) / 2 + 4;
        return mouseX >= width / 2.0D - half && mouseX <= width / 2.0D + half && mouseY >= titleY() - 3
                && mouseY <= titleY() + 11;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overTitle(mouseX, mouseY) && swappable()) {
            swap();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        if (command == Social.Command.PROMISE) {
            // Only worth saying when they want to hear it: tipping, not past promises, not already promised.
            return targetId < 0 && !otherBand && ClientSync.cohesion < 30 && ClientSync.cohesion > 20 && !ClientSync.promised;
        }
        if (command == Social.Command.TRIBE || command == Social.Command.ASK_MEMORIES) {
            return targetId < 0 && !otherBand;
        }
        LocalPlayer self = Minecraft.getInstance().player;
        if (command == Social.Command.FOOD) {
            return self != null && self.getFoodData().needsFood();
        }
        if (command == Social.Command.HURT) {
            return !otherBand && self != null && self.getHealth() < self.getMaxHealth();
        }
        if (command == Social.Command.CLIMB) {
            var stage = self == null ? null : ClientSync.stageOf(self.getUUID());
            String path = stage == null ? "" : stage.getPath();
            if (!path.equals("ardipithecus") && !path.equals("australopithecus") && !path.equals("homo_habilis")) {
                return false;
            }
        }
        if (command == Social.Command.SHARE) {
            return !otherBand;
        }
        if (command == Social.Command.SHUN) {
            return targetId >= 0 && !otherBand;
        }
        if (command == Social.Command.PASS_AROUND) {
            // Splitting food: only with food in hand.
            return !otherBand && self != null && self.getMainHandItem().has(net.minecraft.core.component.DataComponents.FOOD);
        }
        if (command == Social.Command.KNAP) {
            // Asked of one member of your own band, picked out.
            return targetId >= 0 && !otherBand;
        }
        if (command == Social.Command.HAVE_CHILD || command == Social.Command.MAKE_MATE) {
            return targetId >= 0 && !otherBand;
        }
        if (command == Social.Command.WATCH) {
            // Only someone close to you stays up for you: bond 4 and up.
            return targetId >= 0 && !otherBand && Minecraft.getInstance().level != null
                    && Minecraft.getInstance().level.getEntity(targetId) instanceof BandMember member
                    && member.getBond() >= 4 && !member.isBaby();
        }
        if (command == Social.Command.SWAP) {
            // Only someone close to you: bond 3 and up.
            return targetId >= 0 && !otherBand && Minecraft.getInstance().level != null
                    && Minecraft.getInstance().level.getEntity(targetId) instanceof BandMember member
                    && member.getBond() >= 3 && !member.isBaby();
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
                // The others are always there to ask about, near or far.
                if (candidate != Social.Topic.OTHERS && commandsIn(candidate).isEmpty() && !hasFetch(candidate)) {
                    continue;
                }
                addRenderableWidget(Button.builder(Component.literal(candidate.label()), b -> {
                    if (candidate == Social.Topic.CULTURE) {
                        // Culture is a screen of its own: straight there.
                        PacketDistributor.sendToServer(new SocialCommandPayload(-1, Social.Command.CULTURE.ordinal()));
                        return;
                    }
                    if (candidate == Social.Topic.OTHERS) {
                        PacketDistributor.sendToServer(new dev.hominin.evolution.network.OthersActionPayload("",
                                dev.hominin.evolution.network.OthersActionPayload.OPEN));
                        return;
                    }
                    topic = candidate;
                    devSection = null;
                    rebuildWidgets();
                }).bounds(x, y, BUTTON_WIDTH, 20).build());
                y += ROW;
            }
            return;
        }
        if (topic == Social.Topic.DEVELOPER) {
            initDeveloper(x, y);
            return;
        }
        for (Social.Command command : commandsIn(topic)) {
            Button button = Button.builder(Component.literal(command.label()), b -> {
                PacketDistributor.sendToServer(new SocialCommandPayload(targetId, command.ordinal()));
                onClose();
            }).bounds(x, y, BUTTON_WIDTH, 20).build();
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

    /**
     * The Developer tab: the sections, then the chosen section's commands in two columns of small buttons - there
     * are too many for one list.
     */
    private void initDeveloper(int x, int y) {
        if (devSection == null) {
            for (Social.DevSection section : Social.DevSection.values()) {
                addRenderableWidget(Button.builder(Component.literal(section.label()), b -> {
                    devSection = section;
                    rebuildWidgets();
                }).bounds(x, y, BUTTON_WIDTH, 20).build());
                y += ROW;
            }
        } else {
            int column = 150;
            int left = width / 2 - column - 3;
            int i = 0;
            for (Social.Command command : Social.Command.values()) {
                if (command.section() != devSection) {
                    continue;
                }
                int cx = left + (i % 2) * (column + 6);
                int cy = y + (i / 2) * 20;
                addRenderableWidget(Button.builder(Component.literal(command.label()), b -> {
                    PacketDistributor.sendToServer(new SocialCommandPayload(targetId, command.ordinal()));
                    // Left open: trying things is several clicks in a row.
                }).bounds(cx, cy, column, 18).build());
                i++;
            }
            y += ((i + 1) / 2) * 20;
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            if (devSection != null) {
                devSection = null;
            } else {
                topic = null;
            }
            rebuildWidgets();
        }).bounds(x, y + 6, BUTTON_WIDTH, 20).build());
    }

    private int top() {
        return Math.max(40, height / 2 - 80);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        boolean swap = swappable();
        boolean over = swap && overTitle(mouseX, mouseY);
        graphics.drawCenteredString(font, over ? title.copy().withStyle(net.minecraft.ChatFormatting.UNDERLINE) : title,
                width / 2, titleY(), swap ? (over ? 0xFFF3B0 : 0x9FD8FF) : 0xE9D8A6);
        if (swap && topic == null) {
            graphics.drawCenteredString(font, Component.literal(otherBand ? "(click to talk to your band)"
                    : "(click to talk to the other band)"), width / 2, titleY() + 11, 0x8C8578);
        }
        if (topic != null) {
            graphics.drawCenteredString(font, Component.literal(topic.label() + (devSection != null ? " - "
                    + devSection.label() : "")), width / 2, top() - 16, 0xBFBFBF);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // Backspace goes up a level rather than out of the menu entirely.
        if (key == 259 && topic != null) {
            if (devSection != null) {
                devSection = null;
            } else {
                topic = null;
            }
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
