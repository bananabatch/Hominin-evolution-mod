package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.band.Newcomers.Role;
import dev.hominin.evolution.network.ChoicesPayload;
import dev.hominin.evolution.network.OthersActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Who may do what in a band led by more than one player, and how the leadership changes hands.
 *
 * <p><b>Roles.</b> The band's leader - and any co-leader, who may do all the leader may - sets each of the others: a
 * <b>co-leader</b>, an <b>influential</b> one (calls that reach the whole band: a hunt, asking for things, dealing with
 * other bands - though a party goes out only with a co-leader's leave, and gifts only to bands that are friendly), or a
 * <b>member</b> (what passes between them and one member at a time).
 *
 * <p><b>Taking the band.</b> Anyone who leads with somebody else can try to take people from them. Members with a bond
 * of 3 or more with them come for certain; the rest, one in five. When the band barely holds together (cohesion under
 * 20) more come - unless it keeps "we don't betray our own", which holds until cohesion falls to 10. They can
 * <b>split off</b> with whoever follows them, a band of their own - or start a <b>civil conflict</b> for the leadership
 * itself, which needs more than half the band behind them, and costs them if it fails.
 */
public final class BandRoles {
    public static final int ACTION_MENU = 85;
    public static final int ACTION_PARTY = 86;
    public static final int ACTION_ROLE = 87;
    public static final int ACTION_CONFIRM = 88;

    private static final int SPLIT = -3;
    private static final int CIVIL = -4;
    private static final int CANCEL = -5;
    private static final int OK = -6;
    private static final int VIEWS = -7;
    private static final int ALLOW = 1;

    /** Bond at which a member follows someone without a second thought. */
    public static final int SURE_BOND = 3;
    /** The chance anyone else follows, all things being equal. */
    private static final float BASE_JOIN = 0.2F;
    /** A party asked for and allowed: how long the leave lasts. */
    private static final long LEAVE_TICKS = 2 * 60 * 20L;

    /** The players listed in each player's open menu: a pick is an index into it. */
    private static final Map<UUID, List<UUID>> listed = new HashMap<>();
    /** Parties asked for, waiting on a co-leader's leave: by who asked - which band, which kind. */
    private record Asked(UUID band, int intent) {
    }

    private static final Map<UUID, Asked> asked = new HashMap<>();
    /** Parties allowed: "player/band/intent" until when. */
    private static final Map<String, Long> leave = new HashMap<>();

    // ------------------------------------------------------------ who may

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /** Whether this player's role reaches that far. A band's own leader always may. */
    public static boolean allows(ServerPlayer player, Role needed) {
        Role role = Newcomers.roleOf(player);
        return role.ordinal() <= needed.ordinal();
    }

    /** As {@link #allows}, telling them why not. */
    public static boolean check(ServerPlayer player, Role needed, String what) {
        if (allows(player, needed)) {
            return true;
        }
        ServerPlayer lead = Newcomers.leadOf(player);
        player.displayClientMessage(Component.literal("As " + (Newcomers.roleOf(player) == Role.MEMBER ? "a member"
                : "someone influential") + " of " + (lead == player ? "the" : name(lead) + "'s") + " band, you cannot "
                + what + ". (Only a " + (needed == Role.CO_LEADER ? "co-leader" : "co-leader or influential one")
                + " can - ask for it in Roles and leadership.)").withStyle(ChatFormatting.RED), true);
        return false;
    }

    /** What a word from the H menu needs. Anything said to one member is anyone's; the band as a whole, more. */
    public static Role needed(Social.Command command, int entityId) {
        return switch (command) {
            case CULTURE, POSTURE, PROMISE -> Role.CO_LEADER;
            case HUNT, NO_HUNT, ITEM, TRAVEL -> Role.INFLUENTIAL;
            case FORAGE, CLIMB, SHARE -> entityId < 0 ? Role.INFLUENTIAL : Role.MEMBER;
            default -> Role.MEMBER;
        };
    }

    /** What something done from The others needs. Gifts and parties have their own conditions (see below). */
    public static Role needed(int othersAction) {
        return switch (othersAction) {
            case OthersActionPayload.DEMAND, OthersActionPayload.RAID, OthersActionPayload.JOIN_THEM -> Role.CO_LEADER;
            case OthersActionPayload.TRADE, OthersActionPayload.TRAVEL, OthersActionPayload.GIFT -> Role.INFLUENTIAL;
            default -> othersAction >= OthersActionPayload.PARTY ? Role.INFLUENTIAL : Role.MEMBER;
        };
    }

    /** Someone influential gives only to a band that is friendly; a co-leader, to anyone. */
    public static boolean mayGift(ServerPlayer player, Bands.Record band) {
        if (allows(player, Role.CO_LEADER) || Relations.standing(player, band) >= Relations.FRIENDLY) {
            return true;
        }
        player.displayClientMessage(Component.literal("Only a co-leader gives to a band that is not friendly with you.")
                .withStyle(ChatFormatting.RED), true);
        return false;
    }

    // ------------------------------------------------------------ a party needs leave

    /**
     * Whether this player may send a party now. A co-leader, always. Someone influential asks the leader (or any
     * co-leader about) - and with their leave, has two minutes to send it.
     */
    public static boolean mayParty(ServerPlayer player, Bands.Record band, int intent) {
        if (allows(player, Role.CO_LEADER)) {
            return true;
        }
        if (!check(player, Role.INFLUENTIAL, "send a party")) {
            return false;
        }
        String key = player.getUUID() + "/" + band.id + "/" + intent;
        Long until = leave.get(key);
        if (until != null && player.level().getGameTime() <= until) {
            leave.remove(key);
            return true;
        }
        List<ServerPlayer> deciders = new ArrayList<>();
        ServerPlayer lead = Newcomers.leadOf(player);
        if (lead != player) {
            deciders.add(lead);
            for (ServerPlayer co : Newcomers.coLeaders(lead)) {
                if (co != player && Newcomers.roleOf(co) == Role.CO_LEADER) {
                    deciders.add(co);
                }
            }
        }
        if (deciders.isEmpty()) {
            player.displayClientMessage(Component.literal("There is nobody about who can give you leave to send a party.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        asked.put(player.getUUID(), new Asked(band.id, intent));
        String what = intent >= 0 && intent < Parties.INTENTS.length ? Parties.INTENTS[intent].toLowerCase() : "a party";
        for (ServerPlayer decider : deciders) {
            PacketDistributor.sendToPlayer(decider, new ChoicesPayload(player.getId(), ACTION_PARTY, name(player)
                    + " wants to send a party to " + band.name + " (" + what + "). Give them leave?",
                    List.of("Give them leave", "No"), List.of(ALLOW, 0)));
        }
        player.displayClientMessage(Component.literal("You ask for leave to send the party. Waiting...")
                .withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    public static void answerParty(ServerPlayer decider, int askerId, int value) {
        if (!(decider.level().getEntity(askerId) instanceof ServerPlayer asker) || !allows(decider, Role.CO_LEADER)
                || !Newcomers.sameBand(decider, asker)) {
            return;
        }
        Asked request = asked.remove(asker.getUUID());
        if (request == null) {
            return;
        }
        if (value != ALLOW) {
            asker.sendSystemMessage(Component.literal(name(decider) + " will not let the party go.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        leave.put(asker.getUUID() + "/" + request.band() + "/" + request.intent(),
                asker.level().getGameTime() + LEAVE_TICKS);
        asker.sendSystemMessage(Component.literal(name(decider) + " gives you leave to send the party.")
                .withStyle(ChatFormatting.GREEN));
        Bands.Record band = Bands.get(asker.serverLevel(), request.band());
        if (band != null) {
            Parties.open(asker, band, request.intent());
        }
    }

    // ------------------------------------------------------------ the menu

    /** "Roles and leadership": set the others' roles (a co-leader), and - leading with someone else - take the band. */
    public static void open(ServerPlayer player) {
        ServerPlayer lead = Newcomers.leadOf(player);
        boolean leader = Newcomers.hostOf(player) == null;
        Role role = Newcomers.roleOf(player);
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<UUID> people = new ArrayList<>();
        if (role == Role.CO_LEADER) {
            ServerPlayer band = leader ? player : lead;
            for (ServerPlayer co : Newcomers.coLeaders(band)) {
                if (co == player) {
                    continue;
                }
                labels.add(name(co) + ": " + Newcomers.roleOf(co).label() + " - change");
                values.add(people.size());
                people.add(co.getUUID());
            }
        }
        listed.put(player.getUUID(), people);
        if (role == Role.CO_LEADER && player.server.getPlayerList().getPlayerCount() > 1) {
            labels.add("How we see other players' bands...");
            values.add(VIEWS);
        }
        String title;
        if (leader) {
            title = people.isEmpty() ? "You lead your band. Nobody else leads it with you."
                    : "You lead your band. Set what the others who lead it with you may do.";
        } else {
            title = "You lead " + name(lead) + "'s band with them, as " + (role == Role.CO_LEADER ? "a co-leader"
                    : role == Role.INFLUENTIAL ? "someone influential" : "a member") + ".";
            Support split = support(player, true);
            Support civil = support(player, false);
            labels.add("Split off with those who follow me (" + split.describe() + ")");
            values.add(SPLIT);
            labels.add("Challenge " + name(lead) + " for the band (" + civil.describe() + " of " + civil.of + ")");
            values.add(CIVIL);
        }
        if (labels.isEmpty()) {
            labels.add("OK");
            values.add(OK);
        }
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION_MENU, title, labels, values));
    }

    public static void choose(ServerPlayer player, int value) {
        if (value == OK || value == CANCEL) {
            return;
        }
        if (value == VIEWS) {
            BandViews.open(player);
            return;
        }
        if (value == SPLIT || value == CIVIL) {
            ServerPlayer lead = Newcomers.leadOf(player);
            String what = value == SPLIT
                    ? "Split off, taking whoever will follow you? Those who do become your own band, here."
                    : "Challenge " + name(lead) + " for the band? You need more than half of it behind you. Fail, and "
                            + "you are a member of it after, and they will not forget it.";
            PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION_CONFIRM, what,
                    List.of(value == SPLIT ? "Split off" : "Challenge them", "Not now"), List.of(value, CANCEL)));
            return;
        }
        List<UUID> people = listed.getOrDefault(player.getUUID(), List.of());
        if (value < 0 || value >= people.size() || !allows(player, Role.CO_LEADER)
                || !(player.server.getPlayerList().getPlayer(people.get(value)) instanceof ServerPlayer other)
                || !Newcomers.sameBand(player, other)) {
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (Role role : Role.values()) {
            labels.add((Newcomers.roleOf(other) == role ? "[x] " : "") + role.label());
            values.add(role.ordinal());
        }
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(other.getId(), ACTION_ROLE, name(other) + ": what may "
                + "they do? A co-leader, all you may. Influential: the hunt, asking for things, other bands (parties "
                + "with leave, gifts only to friends). A member: one member at a time.", labels, values));
    }

    public static void setRole(ServerPlayer player, int entityId, int ordinal) {
        Role[] roles = Role.values();
        if (ordinal < 0 || ordinal >= roles.length || !allows(player, Role.CO_LEADER)
                || !(player.level().getEntity(entityId) instanceof ServerPlayer other) || other == player
                || Newcomers.hostOf(other) == null || !Newcomers.sameBand(player, other)) {
            return;
        }
        Newcomers.setRole(other, roles[ordinal]);
        player.sendSystemMessage(Component.literal(name(other) + " is " + (roles[ordinal] == Role.CO_LEADER
                ? "a co-leader" : roles[ordinal] == Role.INFLUENTIAL ? "influential" : "a member") + " of the band now.")
                .withStyle(ChatFormatting.GOLD));
        other.sendSystemMessage(Component.literal(name(player) + " has made you " + (roles[ordinal] == Role.CO_LEADER
                ? "a co-leader" : roles[ordinal] == Role.INFLUENTIAL ? "influential" : "a member") + " of the band.")
                .withStyle(ChatFormatting.GOLD));
        open(player);
    }

    public static void confirm(ServerPlayer player, int value) {
        if (Newcomers.hostOf(player) == null) {
            return;
        }
        if (value == SPLIT) {
            splitOff(player);
        } else if (value == CIVIL) {
            challenge(player);
        }
    }

    // ------------------------------------------------------------ who would follow

    /** The chance someone without a close bond follows this player - leaving with them, or rising against the leader. */
    private static float joinChance(ServerPlayer usurper, boolean split) {
        float chance = BASE_JOIN;
        ServerPlayer lead = Newcomers.leadOf(usurper);
        int cohesion = lead != usurper ? Cohesion.get(lead) : 30;
        // "We don't betray our own": a band that keeps it holds together through hard times - down to 10.
        boolean loyal = lead != usurper && Morals.holds(lead, Morals.Moral.NO_BETRAYAL) && cohesion > 10;
        // A band coming apart is easier to take people from - most of all for a member, who has nothing to lose.
        boolean hard = split || Newcomers.roleOf(usurper) == Role.MEMBER;
        if (hard && cohesion < 20 && !loyal) {
            chance += (20 - cohesion) * 0.02F;
        }
        if (split) {
            // Walking away is easier than turning on the leader.
            chance += 0.1F;
        }
        return Math.min(0.8F, chance);
    }

    /** The band's grown members, as far as they are about: everyone led by this player's leader. */
    private static List<BandMember> bandOf(ServerPlayer usurper) {
        UUID leaderId = Newcomers.hostOf(usurper);
        if (leaderId == null) {
            return List.of();
        }
        return new ArrayList<>(usurper.serverLevel().getEntities(ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && !m.isBaby() && leaderId.equals(m.getLeader())));
    }

    private record Support(int sure, float maybe, int of) {
        String describe() {
            int likely = Math.round(maybe);
            return sure + (sure == 1 ? " would" : " would") + (likely > 0 ? ", maybe " + likely + " more" : "");
        }
    }

    private static Support support(ServerPlayer usurper, boolean split) {
        List<BandMember> band = bandOf(usurper);
        float chance = joinChance(usurper, split);
        int sure = 0;
        float maybe = 0.0F;
        for (BandMember member : band) {
            if (member.bondWith(usurper) >= SURE_BOND) {
                sure++;
            } else {
                maybe += chance;
            }
        }
        return new Support(sure, maybe, band.size());
    }

    /** Who actually comes, when it is asked of them. */
    private static List<BandMember> followers(ServerPlayer usurper, List<BandMember> band, boolean split) {
        float chance = joinChance(usurper, split);
        List<BandMember> come = new ArrayList<>();
        for (BandMember member : band) {
            if (member.bondWith(usurper) >= SURE_BOND || usurper.getRandom().nextFloat() < chance) {
                come.add(member);
            }
        }
        return come;
    }

    // ------------------------------------------------------------ splitting off

    private static void splitOff(ServerPlayer usurper) {
        ServerPlayer lead = Newcomers.leadOf(usurper);
        List<BandMember> band = bandOf(usurper);
        List<BandMember> come = followers(usurper, band, true);
        if (come.isEmpty()) {
            usurper.sendSystemMessage(Component.literal("You go round them one by one. Nobody will come with you.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        List<String> names = new ArrayList<>();
        for (BandMember member : come) {
            member.ensureName();
            names.add(member.getName().getString());
            member.changeLeader(usurper.getUUID());
            member.getNavigation().moveTo(usurper, 1.0D);
            // A child goes with whoever minds it.
            for (BandMember child : usurper.serverLevel().getEntities(ModEntities.BAND_MEMBER.get(),
                    m -> m.isAlive() && m.isBaby() && member.getUUID().equals(m.getCaretaker()))) {
                child.changeLeader(usurper.getUUID());
            }
        }
        Newcomers.split(usurper);
        dev.hominin.evolution.hunt.Predation.settle(usurper, usurper.blockPosition());
        Cohesion.startAt(usurper, 25);
        usurper.sendSystemMessage(Component.literal("You split off. " + String.join(", ", names)
                + (come.size() == 1 ? " comes" : " come") + " with you: a band of your own, and this is its ground.")
                .withStyle(ChatFormatting.GOLD));
        if (lead != usurper) {
            Cohesion.add(lead, -3, name(usurper) + " split off");
            lead.sendSystemMessage(Component.literal(name(usurper) + " has split off from your band, and "
                    + String.join(", ", names) + (come.size() == 1 ? " went" : " went") + " with them.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------ civil conflict

    private static void challenge(ServerPlayer usurper) {
        ServerPlayer lead = Newcomers.leadOf(usurper);
        if (lead == usurper) {
            usurper.displayClientMessage(Component.literal("Your leader has to be here for that."), true);
            return;
        }
        List<BandMember> band = bandOf(usurper);
        List<BandMember> behind = followers(usurper, band, false);
        int against = band.size() - behind.size();
        // It comes to shouting and shoving: everyone takes a side.
        for (BandMember member : band) {
            Band.memberDisplay(member, member.getRandom().nextInt(10));
        }
        if (behind.size() <= against) {
            Newcomers.setRole(usurper, Role.MEMBER);
            for (BandMember member : behind) {
                // Those who backed them are marked for it.
                member.addBond(-2);
            }
            for (BandMember member : band) {
                if (!behind.contains(member)) {
                    member.addBondFrom(usurper, -2);
                }
            }
            Cohesion.add(lead, -5, "a challenge for the band");
            usurper.sendSystemMessage(Component.literal("You challenge " + name(lead) + " - and the band stands by them ("
                    + behind.size() + " for you, " + against + " against). You are a member of it now, and nobody will "
                    + "forget it.").withStyle(ChatFormatting.RED));
            lead.sendSystemMessage(Component.literal(name(usurper) + " challenged you for the band - and it stood by you ("
                    + against + " to " + behind.size() + "). They are a member of it now.").withStyle(ChatFormatting.GOLD));
            return;
        }
        int cohesion = Cohesion.get(lead);
        UUID was = lead.getUUID();
        for (BandMember member : usurper.serverLevel().getEntities(ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && was.equals(m.getLeader()))) {
            member.changeLeader(usurper.getUUID());
        }
        Newcomers.split(usurper);
        dev.hominin.evolution.hunt.Predation.settle(usurper, dev.hominin.evolution.hunt.Predation.campOf(lead));
        // The old leader leads with them now - as a member - and so does everyone who led with the old leader.
        Newcomers.join(lead, usurper);
        Newcomers.setRole(lead, Role.MEMBER);
        Cohesion.startAt(usurper, Math.max(5, cohesion - 10));
        usurper.sendSystemMessage(Component.literal("You challenge " + name(lead) + " - and the band comes over to you ("
                + behind.size() + " to " + against + "). You lead it now; " + name(lead) + " is a member of it.")
                .withStyle(ChatFormatting.GOLD));
        lead.sendSystemMessage(Component.literal(name(usurper) + " challenged you for the band, and it went over to them ("
                + behind.size() + " to " + against + "). They lead it now; you are a member of it.")
                .withStyle(ChatFormatting.RED));
    }

    public static void forget(UUID player) {
        listed.remove(player);
        asked.remove(player);
    }

    private BandRoles() {
    }
}
