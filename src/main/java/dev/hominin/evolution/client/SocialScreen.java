package dev.hominin.evolution.client;

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
 */
public class SocialScreen extends Screen {
    /** How long after picking someone out the menu still talks to just them. */
    private static final long SELECTION_MS = 5_000L;


    private final int targetId;
    private final boolean otherBand;
    private final boolean otherBandNear;

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
    }

    /** Another band close enough to ask along - offered even while your own band is here too. */
    private static boolean otherBandNear(LocalPlayer player) {
        return !player.level().getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(16.0D),
                BandMember::isOtherBand).isEmpty();
    }

    @Override
    protected void init() {
        LocalPlayer player = Minecraft.getInstance().player;
        int buttonWidth = 200;
        int x = (width - buttonWidth) / 2;
        int y = height / 2 - 100;
        for (Social.Command command : Social.Command.values()) {
            if (command == Social.Command.TRAVEL && !otherBandNear) {
                continue;
            }
            boolean ownBandOnly = command == Social.Command.ITEM || command == Social.Command.HUNT
                    || command == Social.Command.NO_HUNT || command == Social.Command.CLIMB;
            if (ownBandOnly && otherBand) {
                continue;
            }
            Button button = Button.builder(Component.literal(command.label()), b -> {
                PacketDistributor.sendToServer(new SocialCommandPayload(targetId, command.ordinal()));
                onClose();
            }).bounds(x, y, buttonWidth, 20).build();
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
            y += 24;
        }
        if (!otherBand) {
            addRenderableWidget(Button.builder(Component.literal("Get me..."), b -> ItemPickScreen.openFetch(
                    targetId, targetId >= 0 ? Component.literal(title.getString().replaceFirst("^Talk to ", ""))
                            : Component.literal("your band")))
                    .bounds(x, y, buttonWidth, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 118, 0xE9D8A6);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
