package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Grief. When one of the band dies, whoever was closest to them - their mate, their child, their mother, a friend
 * they groomed every day - may not take it well. A troubled member goes quiet, talks about who they lost, and wants
 * something to keep their hands busy: a good stone, most often, and they get good with it.
 *
 * <p>The band notices before you do: <i>"We need you to look after them."</i> That is a need, like any other - sit
 * with them a while, groom them, give them a good stone or something to eat. Leave them to it and they may go sour:
 * stop caring what anyone thinks. Then it is <i>"hang out with them"</i> - and a sour one wants a lot: to be groomed,
 * a proper meal. Give them that and they come back.
 */
public final class Troubles {
    public static final int NONE = 0;
    public static final int TROUBLED = 1;
    public static final int SOUR = 2;

    /** How likely someone this close is to take it badly. */
    private static final float KIN_CHANCE = 0.6F;
    private static final float FRIEND_CHANCE = 0.25F;
    /** How close a friend has to be: this much grooming between them. */
    private static final int FRIEND_AFFINITY = 4;
    /** However many die, no more than two of a band take it this hard at once. */
    public static final int MOST_AT_ONCE = 2;
    /** Between one member's remembering out loud and the next, per leader. */
    private static final long SAY_GAP = 3 * 60 * 20L;
    private static final Map<UUID, Long> lastSaid = new HashMap<>();

    /** A member has died: whoever was close to them may take it hard. */
    public static void memberDied(BandMember dead) {
        if (!(dead.level() instanceof ServerLevel level) || dead.getLeader() == null) {
            return;
        }
        dead.ensureName();
        String name = dead.getName().getString();
        UUID leader = dead.getLeader();
        List<BandMember> band = level.getEntitiesOfClass(BandMember.class, dead.getBoundingBox().inflate(128.0D),
                m -> m != dead && m.isAlive() && leader.equals(m.getLeader()) && !m.isBaby());
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(leader);
        int troubled = troubledIn(player != null ? Band.all(player) : band);
        for (BandMember other : band) {
            String kind = other.isMateOf(dead.getUUID()) ? "mate"
                    : dead.getUUID().equals(other.getMother()) ? "parent"
                    : other.getUUID().equals(dead.getMother()) ? "child"
                    : other.affinityWith(dead.getUUID()) >= FRIEND_AFFINITY ? "friend" : null;
            if (kind == null) {
                continue;
            }
            other.ensureName();
            if (other.isPsychopath()) {
                // One of the signs: it does not seem to touch them at all.
                if (player != null && other.getRandom().nextBoolean()) {
                    player.sendSystemMessage(Component.literal(other.getName().getString() + " does not seem much "
                            + "troubled that " + name + " is gone.").withStyle(ChatFormatting.DARK_GRAY));
                }
                continue;
            }
            float chance = kind.equals("friend") ? FRIEND_CHANCE : KIN_CHANCE;
            if (other.getTrouble() == NONE && troubled < MOST_AT_ONCE && other.getRandom().nextFloat() < chance) {
                troubled++;
                other.setTrouble(TROUBLED, name, kind);
                if (player != null) {
                    dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.TROUBLED);
                    player.sendSystemMessage(Component.literal(other.getName().getString() + " has gone quiet since "
                            + name + " died - " + (kind.equals("mate") ? "their mate"
                            : kind.equals("child") ? "their child" : kind.equals("parent") ? "their mother" : "a close friend")
                            + ".").withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    /** How many of these are grieving or gone sour. */
    public static int troubledIn(List<BandMember> band) {
        int count = 0;
        for (BandMember member : band) {
            if (member.isAlive() && member.getTrouble() != NONE) {
                count++;
            }
        }
        return count;
    }

    /** Looked after: they will carry it, but not alone. */
    static void eased(ServerPlayer player, BandMember member) {
        member.ensureName();
        String lost = member.getGrievingFor();
        member.setTrouble(NONE, "", "");
        player.sendSystemMessage(Component.literal(member.getName().getString() + " is doing a little better. They will "
                + "carry " + (lost.isEmpty() ? "it" : lost) + " with them, but not alone.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Left alone with it too long: they stop caring what anyone thinks. */
    static void goneSour(ServerPlayer player, BandMember member) {
        member.ensureName();
        member.setTrouble(SOUR, member.getGrievingFor(), member.getGriefKind());
        member.setTemper(true);
        player.sendSystemMessage(Component.literal(member.getName().getString() + " has stopped caring what anyone "
                + "thinks. Nobody sat with them when it mattered.").withStyle(ChatFormatting.DARK_RED));
    }

    /** Hung out with, groomed, fed: they are back. */
    static void backRound(ServerPlayer player, BandMember member) {
        member.ensureName();
        member.setTrouble(NONE, "", "");
        member.setTemper(false);
        member.addBond(4);
        Cohesion.add(player, 3);
        player.sendSystemMessage(Component.literal(member.getName().getString() + " sits by the fire with everyone "
                + "again, and leans against you. They are back.").withStyle(ChatFormatting.GREEN));
    }

    /** A stone given to someone troubled: it gives their hands something to do, and their hands learn. */
    public static void gotStone(BandMember member) {
        if (member.getTrouble() == TROUBLED) {
            member.practiseKnapping();
            member.practiseKnapping();
        }
    }

    /** Every tick for each player: now and then, grief out loud and hands at work. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 == 3) {
            Needs.company(player);
        }
        if (player.tickCount % 600 != 211) {
            return;
        }
        long now = player.level().getGameTime();
        for (BandMember member : Band.all(player)) {
            if (member.getTrouble() == NONE || member.isBaby()) {
                continue;
            }
            member.ensureName();
            if (member.getTrouble() == TROUBLED && hasStone(member) && member.getRandom().nextFloat() < 0.5F) {
                // Something to keep their mind off it: they knock at a stone, and get good.
                member.practiseKnapping();
                if (member.getRandom().nextInt(4) == 0 && member.distanceToSqr(player) < 32.0D * 32.0D) {
                    player.sendSystemMessage(Component.literal(member.getName().getString() + " sits apart, knocking "
                            + "flake after flake off a stone. Their hands are getting good at it.")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            if (member.distanceToSqr(player) < 24.0D * 24.0D && now - lastSaid.getOrDefault(player.getUUID(), -SAY_GAP) >= SAY_GAP
                    && member.getRandom().nextInt(3) == 0) {
                lastSaid.put(player.getUUID(), now);
                Band.announceDiscovery(member, ": \"" + line(member) + "\"");
            }
        }
    }

    private static boolean hasStone(BandMember member) {
        for (int slot = 0; slot < member.getInventory().getContainerSize(); slot++) {
            if (Wants.isGoodStone(member.getInventory().getItem(slot))) {
                return true;
            }
        }
        return Wants.isGoodStone(member.getMainHandItem());
    }

    /** What they say about it: who they lost, and how. */
    static String line(BandMember member) {
        String who = member.getGrievingFor().isEmpty() ? "them" : member.getGrievingFor();
        String[] lines = member.getTrouble() == SOUR ? new String[] {"What does it matter.", "Leave me be.",
                "Nobody came. So why should I?", "Don't. Just don't."}
                : switch (member.getGriefKind()) {
                    case "mate" -> new String[] {"I keep reaching for " + who + " in the night.",
                            who + " would have known what to do.", "The nest is too big now."};
                    case "child" -> new String[] {who + " should be here. They were so small.",
                            "I keep hearing " + who + ".", "I couldn't keep " + who + " safe."};
                    case "parent" -> new String[] {who + " always knew where the water was.",
                            "Who's going to show me now? " + who + " is gone.", "I still look for " + who + " at the fire."};
                    default -> new String[] {who + " and I used to sit right there.", "It's quiet without " + who + ".",
                            "Nobody laughed like " + who + " did."};
                };
        return lines[member.getRandom().nextInt(lines.length)];
    }

    /** What a troubled member wants: a good stone to work. */
    public static ItemStack comfort(BandMember member) {
        var preferred = member.preferredStone();
        return new ItemStack(preferred != null ? preferred : ModItems.CHERT_ROCK.get());
    }

    public static void forget(UUID player) {
        lastSaid.remove(player);
    }

    private Troubles() {
    }
}
