package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Talking to hominins. There are no words yet - a call, a gesture, food held out - but
 * a band understands a few things well enough: come and forage, I am hungry, I need
 * something, I am hurt, and, to another band, walk with us today.
 *
 * <p>Said to everyone nearby, or to one member picked out first.
 */
public final class Social {
    public enum Command {
        FORAGE("Let's forage"),
        FOOD("I'm hungry, can you get me food?"),
        ITEM("I need an item"),
        HURT("I'm hurt, look after me"),
        TRAVEL("Travel with us today");

        private final String label;

        Command(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Nullable
        public static Command byId(int id) {
            Command[] values = values();
            return id >= 0 && id < values.length ? values[id] : null;
        }
    }

    /** How far a call to the band reaches. */
    private static final double GROUP_RADIUS = 16.0D;
    /** How far away a picked-out member can be and still hear you. */
    private static final double INDIVIDUAL_RADIUS = 16.0D;

    private static final long HURT_COOLDOWN_TICKS = 3 * 60 * 20;
    /** How long the band stays watchful after being told you are hurt. */
    private static final long GUARD_TICKS = 3 * 60 * 20;
    private static final int GUARD_BUFF_TICKS = 15 * 20;

    private static final Map<UUID, Long> lastHurtCall = new HashMap<>();
    private static final Map<UUID, Long> guardUntil = new HashMap<>();

    /** Runs a command, said to one member (by entity id) or, with -1, to whoever is nearby. */
    public static void perform(ServerPlayer player, int entityId, Command command) {
        List<BandMember> listeners = listeners(player, entityId);
        if (listeners.isEmpty()) {
            player.displayClientMessage(Component.literal("Nobody is close enough to hear you."), true);
            return;
        }
        boolean individual = entityId >= 0;
        BandMember first = listeners.get(0);
        String who = individual ? first.getName().getString() : first.isWild() ? "The other band" : "Your band";
        switch (command) {
            case FORAGE -> {
                for (BandMember member : listeners) {
                    member.forageAlongside(player.blockPosition());
                }
                say(player, who + (individual ? " comes" : " come") + " to forage beside you.");
            }
            case FOOD -> askForFood(player, listeners, individual, who);
            case ITEM -> askForItem(player, listeners, who);
            case HURT -> askForCare(player, listeners, who);
            case TRAVEL -> askToTravel(player, first);
        }
    }

    private static List<BandMember> listeners(ServerPlayer player, int entityId) {
        if (entityId >= 0) {
            if (player.level().getEntity(entityId) instanceof BandMember member && member.isAlive()
                    && member.distanceToSqr(player) <= INDIVIDUAL_RADIUS * INDIVIDUAL_RADIUS) {
                return List.of(member);
            }
            return List.of();
        }
        // Your own band first. With nobody of yours nearby, whichever other band is closest.
        List<BandMember> own = Band.companionsNear(player, GROUP_RADIUS);
        if (!own.isEmpty()) {
            return own;
        }
        BandMember nearest = null;
        for (BandMember member : Band.near(player, GROUP_RADIUS)) {
            if (member.isWild() && (nearest == null || member.distanceToSqr(player) < nearest.distanceToSqr(player))) {
                nearest = member;
            }
        }
        if (nearest == null) {
            return List.of();
        }
        UUID band = nearest.getBandId();
        List<BandMember> theirs = new ArrayList<>();
        theirs.add(nearest);
        for (BandMember member : Band.near(nearest, 24.0D)) {
            if (member != nearest && band != null && band.equals(member.getBandId())) {
                theirs.add(member);
            }
        }
        return theirs;
    }

    private static void askForFood(ServerPlayer player, List<BandMember> listeners, boolean individual, String who) {
        if (!player.getFoodData().needsFood()) {
            say(player, "You are not hungry.");
            return;
        }
        boolean strangers = listeners.get(0).isWild() && !listeners.get(0).isGuestOf(player);
        if (strangers) {
            say(player, "They are not about to feed a stranger. Offer them something first.");
            return;
        }
        int handed = 0;
        for (BandMember member : listeners) {
            if (member.isBaby()) {
                continue;
            }
            if (member.giveFoodTo(player)) {
                handed++;
                if (!individual) {
                    // One mouthful each is plenty; the rest goes looking.
                    continue;
                }
                return;
            }
            member.fetchFoodFor(player);
        }
        say(player, handed > 0
                ? who + " shares what they have, and the rest go looking for more."
                : who + " goes looking for something for you to eat.");
    }

    private static void askForItem(ServerPlayer player, List<BandMember> listeners, String who) {
        if (listeners.get(0).isWild()) {
            say(player, "They keep hold of their things. Trade for them instead.");
            return;
        }
        BandMember giver = null;
        for (BandMember member : listeners) {
            if (giver == null || member.valueOfBestTool() > giver.valueOfBestTool()) {
                giver = member;
            }
        }
        ItemStack item = giver == null ? ItemStack.EMPTY : giver.mostValuableTool();
        if (item.isEmpty()) {
            say(player, who + " has nothing to spare.");
            return;
        }
        say(player, giver.getName().getString() + " hands you " + item.getHoverName().getString() + ".");
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
    }

    private static void askForCare(ServerPlayer player, List<BandMember> listeners, String who) {
        if (player.getHealth() >= player.getMaxHealth()) {
            say(player, "You are not hurt.");
            return;
        }
        long now = player.level().getGameTime();
        Long last = lastHurtCall.get(player.getUUID());
        if (last != null && now - last < HURT_COOLDOWN_TICKS) {
            say(player, "They are already watching over you. (" + (HURT_COOLDOWN_TICKS - (now - last)) / 20 + "s)");
            return;
        }
        lastHurtCall.put(player.getUUID(), now);
        guardUntil.put(player.getUUID(), now + GUARD_TICKS);
        for (BandMember member : listeners) {
            member.forageAlongside(player.blockPosition());
        }
        say(player, who + " gathers close around you, watching for trouble.");
    }

    /** While the band is watching over a hurt leader, the first blow against them sends it wild. */
    public static void onLeaderHit(ServerPlayer player) {
        Long until = guardUntil.get(player.getUUID());
        if (until == null || player.level().getGameTime() > until) {
            return;
        }
        guardUntil.remove(player.getUUID());
        for (BandMember member : Band.companionsNear(player, 24.0D)) {
            member.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, GUARD_BUFF_TICKS, 0));
            member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, GUARD_BUFF_TICKS, 0));
        }
        player.displayClientMessage(Component.literal("Your band rushes to your defence!")
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static void askToTravel(ServerPlayer player, BandMember member) {
        if (!member.isWild()) {
            say(player, "They already go where you go.");
            return;
        }
        if (member.isGuestOf(player)) {
            say(player, "They are already travelling with you.");
            return;
        }
        if (player.level().isNight()) {
            say(player, "It is getting dark. They are staying where they are tonight.");
            return;
        }
        UUID band = member.getBandId();
        BandMember alpha = member;
        for (BandMember other : Band.near(member, 32.0D)) {
            if (band != null && band.equals(other.getBandId())) {
                other.travelWith(player);
                if (other.isAlpha()) {
                    alpha = other;
                }
            }
        }
        say(player, alpha.getName().getString() + "'s band will travel with you until nightfall.");
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    public static void forget(UUID player) {
        lastHurtCall.remove(player);
        guardUntil.remove(player);
    }

    private Social() {
    }
}
