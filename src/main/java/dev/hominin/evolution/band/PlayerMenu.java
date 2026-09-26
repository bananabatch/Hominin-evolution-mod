package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.mind.Skills;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Another player, right-clicked: what you can do with them. Groom them, trade what you hold for what they hold, teach
 * them something you know, ask them to be your mate - and, mates of the other sex, have a child. Each of those is
 * asked, and they can say no. Info is just looked at: nobody is asked.
 */
public final class PlayerMenu {
    public static final int ACTION_MENU = 80;
    public static final int ACTION_TEACH = 81;
    public static final int ACTION_ANSWER = 82;
    public static final int ACTION_INFO = 83;

    private static final int GROOM = 0;
    private static final int TRADE = 1;
    private static final int TEACH = 2;
    private static final int MATE = 3;
    private static final int CHILD = 4;
    private static final int INFO = 5;

    private static final int ACCEPT = 1;
    private static final int DECLINE = 0;

    /** Close enough to do anything but look. */
    private static final double REACH = 6.0D;
    /** How long a question waits for an answer. */
    private static final long ASK_TICKS = 30 * 20L;
    /** A grooming session between two players: long enough to have to stand still for it. */
    private static final int GROOM_TICKS = 100;
    private static final double GROOM_RANGE = 4.0D;

    /** What one player has asked of another, waiting on the answer: by who is being asked. */
    private record Request(UUID from, int kind, int skill, ItemStack offered, ItemStack wanted, long until) {
    }

    private static final Map<UUID, Request> requests = new HashMap<>();

    /** Grooming under way, by the groomer: who, and how long so far. */
    private record Session(UUID groomed, int ticks) {
    }

    private static final Map<UUID, Session> sessions = new HashMap<>();

    // ------------------------------------------------------------ the click

    /** A right-click on another player opens the menu - on both sides, so nothing else happens with it. */
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Player)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof ServerPlayer other) {
            open(player, other);
        }
    }

    private static String name(Player player) {
        return player.getGameProfile().getName();
    }

    public static void open(ServerPlayer player, ServerPlayer other) {
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        labels.add("Groom them");
        values.add(GROOM);
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            ItemStack theirs = other.getMainHandItem();
            labels.add(theirs.isEmpty() ? "Give them your " + describe(held)
                    : "Trade: your " + describe(held) + " for their " + describe(theirs));
            values.add(TRADE);
        }
        if (!teachable(player, other).isEmpty()) {
            labels.add("Teach a skill...");
            values.add(TEACH);
        }
        boolean mates = PlayerTies.areMates(player.server, player.getUUID(), other.getUUID());
        if (!mates) {
            labels.add("Be my mate");
            values.add(MATE);
        } else if (Mating.isFemale(player) != Mating.isFemale(other)) {
            labels.add("Let's have a child");
            values.add(CHILD);
        }
        labels.add("Info");
        values.add(INFO);
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(other.getId(), ACTION_MENU, name(other)
                + (mates ? " - your mate" : ""), labels, values));
    }

    private static String describe(ItemStack stack) {
        return (stack.getCount() > 1 ? stack.getCount() + " " : "") + stack.getHoverName().getString().toLowerCase();
    }

    /** What this player knows that the other does not - and could learn, being what they are. */
    private static List<Skills.Skill> teachable(ServerPlayer teacher, ServerPlayer learner) {
        List<Skills.Skill> list = new ArrayList<>();
        var stage = learner.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        for (Skills.Skill skill : Skills.Skill.values()) {
            if (Skills.knows(teacher, skill) && !Skills.knows(learner, skill) && skill.learnableAs(stage)) {
                list.add(skill);
            }
        }
        return list;
    }

    @Nullable
    private static ServerPlayer other(ServerPlayer player, int entityId) {
        return player.level().getEntity(entityId) instanceof ServerPlayer other && other != player && other.isAlive()
                ? other : null;
    }

    private static boolean near(ServerPlayer player, ServerPlayer other) {
        if (player.level() != other.level() || player.distanceToSqr(other) > REACH * REACH) {
            player.displayClientMessage(Component.literal("You need to be closer to " + name(other) + "."), true);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ picked from the menu

    public static void choose(ServerPlayer player, int entityId, int value) {
        ServerPlayer other = other(player, entityId);
        if (other == null) {
            return;
        }
        if (value == INFO) {
            info(player, other);
            return;
        }
        if (!near(player, other)) {
            return;
        }
        switch (value) {
            case GROOM -> ask(player, other, GROOM, -1, name(player) + " wants to groom you.");
            case TRADE -> {
                ItemStack offered = player.getMainHandItem();
                if (offered.isEmpty()) {
                    return;
                }
                ItemStack wanted = other.getMainHandItem();
                String question = wanted.isEmpty()
                        ? name(player) + " wants to give you " + describe(offered) + "."
                        : name(player) + " offers you " + describe(offered) + " for the " + describe(wanted)
                                + " you are holding.";
                requests.put(other.getUUID(), new Request(player.getUUID(), TRADE, -1, offered.copy(), wanted.copy(),
                        player.level().getGameTime() + ASK_TICKS));
                send(player, other, question);
            }
            case TEACH -> {
                List<Skills.Skill> skills = teachable(player, other);
                List<String> labels = new ArrayList<>();
                List<Integer> values = new ArrayList<>();
                for (Skills.Skill skill : skills) {
                    labels.add(skill.title());
                    values.add(skill.ordinal());
                }
                PacketDistributor.sendToPlayer(player, new ChoicesPayload(other.getId(), ACTION_TEACH,
                        "Teach " + name(other) + "...", labels, values));
            }
            case MATE -> ask(player, other, MATE, -1, name(player) + " asks you to be their mate."
                    + (PlayerTies.mateOf(player.server, other.getUUID()) != null ? " (You would leave the mate you have.)"
                    : ""));
            case CHILD -> {
                ServerPlayer mother = Mating.isFemale(player) ? player : other;
                if (!childPossible(player, other, mother)) {
                    return;
                }
                ask(player, other, CHILD, -1, name(player) + " wants to have a child with you.");
            }
            default -> {
            }
        }
    }

    /** A skill picked to teach: they are asked whether they want to learn it. */
    public static void chooseSkill(ServerPlayer player, int entityId, int ordinal) {
        ServerPlayer other = other(player, entityId);
        Skills.Skill[] all = Skills.Skill.values();
        if (other == null || ordinal < 0 || ordinal >= all.length || !near(player, other)) {
            return;
        }
        ask(player, other, TEACH, ordinal, name(player) + " offers to show you " + all[ordinal].title().toLowerCase()
                + ".");
    }

    private static void ask(ServerPlayer player, ServerPlayer other, int kind, int skill, String question) {
        requests.put(other.getUUID(), new Request(player.getUUID(), kind, skill, ItemStack.EMPTY, ItemStack.EMPTY,
                player.level().getGameTime() + ASK_TICKS));
        send(player, other, question);
    }

    private static void send(ServerPlayer player, ServerPlayer other, String question) {
        PacketDistributor.sendToPlayer(other, new ChoicesPayload(player.getId(), ACTION_ANSWER, question,
                List.of("Accept", "Decline"), List.of(ACCEPT, DECLINE)));
        player.displayClientMessage(Component.literal("You ask " + name(other) + ". Waiting for their answer...")
                .withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------ the answer

    public static void answer(ServerPlayer asked, int askerEntityId, int value) {
        Request request = requests.remove(asked.getUUID());
        ServerPlayer asker = other(asked, askerEntityId);
        if (request == null || asker == null || !asker.getUUID().equals(request.from())) {
            return;
        }
        if (asked.level().getGameTime() > request.until()) {
            asked.displayClientMessage(Component.literal("Too late - " + name(asker) + " has stopped waiting."), true);
            return;
        }
        if (value != ACCEPT) {
            asker.sendSystemMessage(Component.literal(name(asked) + " says no.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!near(asked, asker)) {
            asker.sendSystemMessage(Component.literal(name(asked) + " would - but you are too far apart now.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        switch (request.kind()) {
            case GROOM -> {
                sessions.put(asker.getUUID(), new Session(asked.getUUID(), 0));
                asker.displayClientMessage(Component.literal("You start picking through " + name(asked)
                        + "'s hair. Stay close.").withStyle(ChatFormatting.GRAY), true);
                asked.displayClientMessage(Component.literal(name(asker) + " starts grooming you. Stay close.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            case TRADE -> trade(asker, asked, request);
            case TEACH -> teach(asker, asked, request.skill());
            case MATE -> {
                PlayerTies.pair(asker.server, asker.getUUID(), asked.getUUID());
                hearts(asked);
                asker.sendSystemMessage(Component.literal(name(asked) + " is your mate now.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                asked.sendSystemMessage(Component.literal(name(asker) + " is your mate now.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            case CHILD -> {
                ServerPlayer mother = Mating.isFemale(asker) ? asker : asked;
                ServerPlayer father = mother == asker ? asked : asker;
                if (!childPossible(asker, asked, mother)) {
                    return;
                }
                Mating.conceive(mother, name(father));
                hearts(mother);
                father.sendSystemMessage(Component.literal(name(mother) + " is carrying your child.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            default -> {
            }
        }
    }

    private static boolean childPossible(ServerPlayer asker, ServerPlayer other, ServerPlayer mother) {
        if (!PlayerTies.areMates(asker.server, asker.getUUID(), other.getUUID())
                || Mating.isFemale(asker) == Mating.isFemale(other)) {
            asker.displayClientMessage(Component.literal("That takes mates, one of each."), true);
            return false;
        }
        if (Mating.isPregnant(mother)) {
            asker.displayClientMessage(Component.literal(name(mother) + " is already carrying a child."), true);
            return false;
        }
        if (!Band.hasRoomFor(Newcomers.leadOf(mother))) {
            asker.displayClientMessage(Component.literal("The band is as big as the land can feed."), true);
            return false;
        }
        return true;
    }

    /** The trade goes through only if both still hold what was offered and asked for. */
    private static void trade(ServerPlayer giver, ServerPlayer taker, Request request) {
        ItemStack offered = giver.getMainHandItem();
        ItemStack wanted = taker.getMainHandItem();
        if (!ItemStack.matches(offered, request.offered()) || !ItemStack.matches(wanted, request.wanted())) {
            giver.sendSystemMessage(Component.literal("The trade falls through - one of you is not holding what was "
                    + "offered any more.").withStyle(ChatFormatting.GRAY));
            taker.sendSystemMessage(Component.literal("The trade falls through - one of you is not holding what was "
                    + "offered any more.").withStyle(ChatFormatting.GRAY));
            return;
        }
        giver.setItemInHand(InteractionHand.MAIN_HAND, wanted.copy());
        taker.setItemInHand(InteractionHand.MAIN_HAND, offered.copy());
        giver.level().playSound(null, giver.blockPosition(), SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS,
                0.6F, 1.0F);
        String got = wanted.isEmpty() ? "" : " and take " + describe(wanted);
        giver.sendSystemMessage(Component.literal("You hand " + name(taker) + " " + describe(offered) + got + ".")
                .withStyle(ChatFormatting.GREEN));
        taker.sendSystemMessage(Component.literal(name(giver) + " hands you " + describe(offered)
                + (wanted.isEmpty() ? "." : ", and takes your " + describe(wanted) + "."))
                .withStyle(ChatFormatting.GREEN));
    }

    private static void teach(ServerPlayer teacher, ServerPlayer learner, int ordinal) {
        Skills.Skill[] all = Skills.Skill.values();
        if (ordinal < 0 || ordinal >= all.length || !Skills.knows(teacher, all[ordinal])) {
            return;
        }
        Skills.Skill skill = all[ordinal];
        if (Skills.learn(learner, skill)) {
            teacher.sendSystemMessage(Component.literal("You show " + name(learner) + " " + skill.title().toLowerCase()
                    + ", and they have it.").withStyle(ChatFormatting.GREEN));
        } else {
            teacher.sendSystemMessage(Component.literal(name(learner) + " cannot take it in.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void hearts(ServerPlayer player) {
        player.serverLevel().sendParticles(ParticleTypes.HEART, player.getX(), player.getEyeY() + 0.4D, player.getZ(),
                10, 0.5D, 0.4D, 0.5D, 0.0D);
    }

    // ------------------------------------------------------------ grooming

    /** Every tick for the groomer: stay close and still, and when it is done, the ticks come off. */
    public static void tick(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (!(player.serverLevel().getPlayerByUUID(session.groomed()) instanceof ServerPlayer groomed)
                || !groomed.isAlive() || groomed.distanceToSqr(player) > GROOM_RANGE * GROOM_RANGE
                || player.isSprinting() || groomed.isSprinting()) {
            sessions.remove(player.getUUID());
            player.displayClientMessage(Component.literal("You break off."), true);
            return;
        }
        int ticks = session.ticks() + 1;
        sessions.put(player.getUUID(), new Session(session.groomed(), ticks));
        if (ticks % 20 == 0) {
            player.swing(InteractionHand.MAIN_HAND, true);
            player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, groomed.getX(), groomed.getEyeY(),
                    groomed.getZ(), 2, 0.3D, 0.3D, 0.3D, 0.0D);
        }
        if (ticks < GROOM_TICKS) {
            return;
        }
        sessions.remove(player.getUUID());
        boolean practised = Skills.knows(player, Skills.Skill.GROOMING);
        int before = dev.hominin.evolution.survival.Infestation.of(groomed);
        int removed = 2 + (practised ? 1 : 0);
        dev.hominin.evolution.survival.Infestation.groomed(groomed, removed);
        int found = Math.min(before, removed);
        if (found > 0) {
            ItemStack picked = dev.hominin.evolution.survival.Infestation.pickedOff(found);
            if (!player.getInventory().add(picked)) {
                player.drop(picked, false);
            }
        }
        Skills.learn(player, Skills.Skill.GROOMING);
        groomed.heal(1.0F);
        hearts(groomed);
        player.displayClientMessage(Component.literal(found > 0 ? "You pick " + found + " tick" + (found > 1 ? "s" : "")
                + " off " + name(groomed) + "." : name(groomed) + " is clean - but they liked it all the same.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        groomed.displayClientMessage(Component.literal(name(player) + " finishes grooming you. That feels better.")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ------------------------------------------------------------ info

    private static void info(ServerPlayer player, ServerPlayer other) {
        var data = other.getData(Attachments.PLAYER_EVOLUTION_DATA);
        var stage = dev.hominin.evolution.stage.StageRegistry.get(data.getStage());
        StringBuilder text = new StringBuilder(name(other));
        text.append(" - ").append(stage == null ? "hominin" : stage.displayName()).append(", ")
                .append(Mating.isFemale(other) ? "female" : "male").append(".");
        UUID host = Newcomers.hostOf(other);
        if (host != null && player.server.getPlayerList().getPlayer(host) instanceof ServerPlayer leader) {
            text.append(" Leads ").append(leader == player ? "your" : name(leader) + "'s").append(" band with them (")
                    .append(Newcomers.roleOf(other).label()).append(").");
        } else {
            int members = Band.all(other).size();
            text.append(" Leads a band of their own (").append(members).append(members == 1 ? " member)." : " members).");
        }
        UUID mate = PlayerTies.mateOf(player.server, other.getUUID());
        if (mate != null) {
            text.append(mate.equals(player.getUUID()) ? " Your mate." : " Has a mate.");
        }
        if (Mating.isPregnant(other)) {
            text.append(" Carrying a child.");
        }
        int known = 0;
        for (Skills.Skill skill : Skills.Skill.values()) {
            if (Skills.knows(other, skill)) {
                known++;
            }
        }
        text.append(" Knows ").append(known).append(known == 1 ? " skill." : " skills.");
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(other.getId(), ACTION_INFO, text.toString(),
                List.of("OK"), List.of(0)));
    }

    public static void forget(UUID player) {
        requests.remove(player);
        sessions.remove(player);
    }

    private PlayerMenu() {
    }
}
