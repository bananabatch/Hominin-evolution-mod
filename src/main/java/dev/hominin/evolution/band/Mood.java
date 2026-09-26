package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.survival.Seasons;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The band's temper: the members who do not care what anyone thinks, and the leader who has to
 * hold them to the same standards they hold the leader to.
 *
 * <p><b>Antisocial members.</b> From habilis on, some are born that way (one in ten), and more
 * become it when the band stops holding together: with no ways of its own to keep them in line,
 * the lower the band's cohesion, the more of them stop caring. They steal, they will not teach the
 * young, they are always asking for food and never give any, they hoard, and now and then they pick
 * a fight - never to the death, but enough to lay someone up. A band that holds that nobody lords it
 * over the rest checks them; from erectus a leader can have the band shun one, and they either bend
 * or go.
 *
 * <p><b>The leader.</b> You are held to it too. Walk about loaded with food while people go hungry
 * and they will come and stand in front of you - pass it around, or the band remembers. Keep asking
 * for things and never give anything back, and they will tell you so, once.
 */
public final class Mood {
    private static final String RECIPROCITY = "reciprocity_owed";
    private static final String RECIPROCITY_WARNED = "reciprocity_warned";
    private static final String HOARD_WARNED = "hoard_warned_minute";
    /** Taking this many more than you have given gets you told; this many more costs cohesion. */
    private static final int OWED_WARN = 5;
    private static final int OWED_COST = 8;
    /** Carrying this much food is more than your share: the band asks you to split it - and splits its own. */
    public static final int HOARD_FOOD = 20;
    private static final int HOARD_GRACE_MINUTES = 3;

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    private static int minute(ServerPlayer player) {
        return (int) (player.level().getGameTime() / 1200L);
    }

    // ------------------------------------------------------------ give and take

    /** The leader took something from the band: food handed over, an item asked for, an errand run. */
    public static void took(ServerPlayer player, int amount) {
        counters(player).merge(RECIPROCITY, amount, Integer::sum);
    }

    /** The leader gave something to the band. */
    public static void gave(ServerPlayer player, int amount) {
        Map<String, Integer> counters = counters(player);
        int owed = Math.max(-4, counters.getOrDefault(RECIPROCITY, 0) - amount);
        counters.put(RECIPROCITY, owed);
        if (owed < OWED_WARN) {
            counters.remove(RECIPROCITY_WARNED);
        }
        counters.remove(HOARD_WARNED);
    }

    // ------------------------------------------------------------ antisocial

    /** Rolled once for each member from erectus on: one in ten does not care what anyone thinks. */
    public static void rollTemper(BandMember member) {
        if (!Bands.erectusOn(member.getStage())) {
            // Before erectus a band is too small, and too close, for anyone to stop caring.
            if (member.isAntisocial()) {
                member.clearTemper();
            }
            return;
        }
        if (member.temperRolled()) {
            return;
        }
        member.setTemper(!member.isBaby() && member.getRandom().nextFloat() < 0.1F);
    }

    /** How much more theft there is: a band coming apart steals from itself. */
    public static float theftFactor(ServerPlayer player) {
        int cohesion = Cohesion.get(player);
        float factor = cohesion <= Cohesion.DIRE ? 2.5F : cohesion <= Cohesion.BORDERLINE ? 2.0F
                : cohesion < Cohesion.NEUTRAL ? 1.4F : 1.0F;
        return Seasons.isDry(player.level()) ? factor * 1.5F : factor;
    }

    private static boolean anyMorals(ServerPlayer player) {
        for (Morals.Moral moral : Morals.Moral.values()) {
            if (Morals.holds(player, moral)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the band keeps its bullies in line. */
    public static boolean checked(ServerPlayer player) {
        return Morals.applies(player, Morals.Moral.REVERSE_DOMINANCE);
    }

    // ------------------------------------------------------------ once a minute

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 1200 != 300) {
            return;
        }
        List<BandMember> band = Band.all(player);
        if (band.isEmpty()) {
            return;
        }
        band.forEach(Mood::rollTemper);
        if (band.stream().anyMatch(m -> m.isAntisocial() && !m.isBaby())) {
            dev.hominin.evolution.guide.Tips.antisocialAbout(player);
        }
        reciprocity(player, band);
        hoarding(player, band);
        membersShare(player, band);
        if (player.getRandom().nextInt(12) == 0) {
            // Rarely: a fight is an event, not the weather.
            fight(player, band);
        }
        if (player.getRandom().nextInt(5) == 0) {
            drift(player, band);
        }
        if (player.getRandom().nextInt(6) == 0) {
            talkOfWays(player, band);
        }
    }

    private static void reciprocity(ServerPlayer player, List<BandMember> band) {
        Map<String, Integer> counters = counters(player);
        int owed = counters.getOrDefault(RECIPROCITY, 0);
        // It fades: a debt from days ago is forgiven.
        if (owed > 0 && player.getRandom().nextInt(10) == 0) {
            counters.put(RECIPROCITY, --owed);
        }
        BandMember speaker = someone(player, band);
        if (owed >= OWED_COST && counters.getOrDefault(RECIPROCITY_WARNED, 0) > 0) {
            counters.put(RECIPROCITY, OWED_WARN - 1);
            counters.remove(RECIPROCITY_WARNED);
            Cohesion.add(player, -3, "given something back for everything you took");
            if (speaker != null) {
                say(player, speaker, "We told you. You take, and take, and never give. We are done pretending not to notice.",
                        ChatFormatting.RED);
            }
        } else if (owed >= OWED_WARN && counters.getOrDefault(RECIPROCITY_WARNED, 0) == 0 && speaker != null) {
            counters.put(RECIPROCITY_WARNED, 1);
            say(player, speaker, "You keep asking us for things and you never give anything back. Even a bite of food "
                    + "would do. Keep this up and the whole band will hold it against you.", ChatFormatting.YELLOW);
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.RECIPROCITY);
        }
    }

    private static int foodCarried(ServerPlayer player) {
        int food = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.has(DataComponents.FOOD)) {
                food += stack.getCount();
            }
        }
        return food;
    }

    private static void hoarding(ServerPlayer player, List<BandMember> band) {
        Map<String, Integer> counters = counters(player);
        // Not only the starving: anyone who could eat something asks, when you are carrying that much.
        List<BandMember> hungry = new ArrayList<>(band.stream()
                .filter(m -> !m.isBaby() && m.getHunger() < BandMember.MAX_HUNGER - 2
                        && m.distanceToSqr(player) < 32.0D * 32.0D).toList());
        askLater(player);
        if (foodCarried(player) < HOARD_FOOD || hungry.isEmpty() || foodPiledNear(player) >= HOARD_FOOD) {
            // Nobody wants it - or there is plenty on the band's food piles to eat from.
            counters.remove(HOARD_WARNED);
            return;
        }
        if (hungry.stream().noneMatch(BandMember::isHungry)) {
            // Peckish, not hungry: nobody can eat that much now. Put it by - or remember them later.
            if (counters.getOrDefault(HOARD_WARNED, 0) == 0) {
                counters.put(HOARD_WARNED, minute(player));
                BandMember asker = hungry.get(0);
                say(player, asker, player.getRandom().nextBoolean()
                        ? "That's a lot of food to carry about. Lay some of it on a food pile - we'll eat from it."
                        : "I couldn't eat another bite. If I'm hungry later, give me some of that, please.",
                        ChatFormatting.YELLOW);
                for (BandMember member : hungry) {
                    later.put(member.getUUID(), player.getUUID());
                }
            }
            return;
        }
        int warned = counters.getOrDefault(HOARD_WARNED, 0);
        if (warned == 0) {
            counters.put(HOARD_WARNED, minute(player));
            hungry.sort(Comparator.comparingInt(BandMember::getHunger));
            // The hungriest come to you first: you have the most, so you are the one to ask.
            for (BandMember beggar : hungry.subList(0, Math.min(2, hungry.size()))) {
                beggar.getNavigation().moveTo(player, 1.0D);
                beggar.attendTo(player, BandMember.ATTEND_TICKS);
            }
            say(player, hungry.get(0), "That is more food than anyone can eat - " + foodCarried(player)
                    + " of it. Split it with us.", ChatFormatting.YELLOW);
            player.sendSystemMessage(Component.literal("(Hold the food and choose \"Split what I'm holding between you\" "
                    + "under H, Food.)").withStyle(ChatFormatting.DARK_GRAY));
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.HOARDING);
            return;
        }
        if (minute(player) - warned >= HOARD_GRACE_MINUTES) {
            counters.put(HOARD_WARNED, minute(player));
            Cohesion.add(player, -2, "passed around the food you were carrying");
            say(player, hungry.get(0), "Still nothing? You eat well enough.", ChatFormatting.RED);
        }
    }

    /** Who asked to be given some later, and of whom. */
    private static final Map<java.util.UUID, java.util.UUID> later = new java.util.HashMap<>();

    /** Somebody who asked to be fed later is hungry now: they come and ask. */
    private static void askLater(ServerPlayer player) {
        if (later.isEmpty() || foodCarried(player) == 0) {
            return;
        }
        for (BandMember member : Band.ownNear(player, 48.0D)) {
            if (member.isHungry() && player.getUUID().equals(later.get(member.getUUID()))) {
                later.remove(member.getUUID());
                member.getNavigation().moveTo(player, 1.0D);
                member.attendTo(player, BandMember.ATTEND_TICKS);
                say(player, member, "I'm hungry now. You said you'd have some for me.", ChatFormatting.YELLOW);
                return;
            }
        }
    }

    /** Food on the band's own piles near the player - food anyone may eat from. */
    static int foodPiledNear(ServerPlayer player) {
        int food = 0;
        for (net.minecraft.core.BlockPos pos : ToolPiles.piles(player.serverLevel(), player.getUUID())) {
            if (pos.distSqr(player.blockPosition()) < 32.0D * 32.0D && player.serverLevel().isLoaded(pos)
                    && player.serverLevel().getBlockEntity(pos) instanceof dev.hominin.evolution.block.ToolPileBlockEntity pile) {
                food += pile.total(s -> s.has(DataComponents.FOOD));
            }
        }
        return food;
    }

    private static int foodCarried(BandMember member) {
        int food = 0;
        var pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (pack.getItem(slot).has(DataComponents.FOOD)) {
                food += pack.getItem(slot).getCount();
            }
        }
        if (member.getOffhandItem().has(DataComponents.FOOD)) {
            food += member.getOffhandItem().getCount();
        }
        return food;
    }

    /**
     * Held to the same rule: one of the band carrying twenty or more splits it with everyone near - the rest of
     * the band first, the least fed first, and you too if you could eat.
     */
    private static void membersShare(ServerPlayer player, List<BandMember> band) {
        for (BandMember sharer : band) {
            if (sharer.isBaby() || foodCarried(sharer) < HOARD_FOOD) {
                continue;
            }
            List<BandMember> others = new ArrayList<>(band.stream().filter(m -> m != sharer && !m.isBaby()
                    && m.distanceToSqr(sharer) < 16.0D * 16.0D).toList());
            others.sort(Comparator.comparingInt(BandMember::getHunger));
            int toSpare = foodCarried(sharer) - HOARD_FOOD / 2;
            int given = 0;
            int people = 0;
            for (BandMember other : others) {
                int share = 0;
                while (share < 3 && given < toSpare) {
                    ItemStack piece = sharer.takeFood();
                    if (piece.isEmpty()) {
                        break;
                    }
                    other.addToInventory(piece);
                    share++;
                    given++;
                }
                if (share > 0) {
                    people++;
                }
            }
            boolean toYou = false;
            if (given < toSpare && sharer.distanceToSqr(player) < 16.0D * 16.0D && player.getFoodData().needsFood()) {
                for (int i = 0; i < 2; i++) {
                    ItemStack piece = sharer.takeFood();
                    if (piece.isEmpty()) {
                        break;
                    }
                    if (!player.getInventory().add(piece)) {
                        player.drop(piece, false);
                    }
                    given++;
                    toYou = true;
                }
            }
            if (given == 0) {
                continue;
            }
            sharer.ensureName();
            sharer.swing(InteractionHand.MAIN_HAND);
            player.sendSystemMessage(Component.literal(sharer.getName().getString() + " has more food than one person "
                    + "needs, and shares it out: " + given + " pieces between " + (people + (toYou ? 1 : 0))
                    + (toYou ? ", you among them." : ".")).withStyle(ChatFormatting.LIGHT_PURPLE));
            Cohesion.addLimited(player, "member_share", 1, 5 * 60 * 20L);
            return;
        }
    }

    /** "Pass around what I'm holding": the stack in hand, split between the band nearby, hungriest first. */
    public static void passAround(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !held.has(DataComponents.FOOD)) {
            player.displayClientMessage(Component.literal("Hold the food you want to pass around."), true);
            return;
        }
        List<BandMember> band = new ArrayList<>(Band.ownNear(player, 16.0D));
        band.removeIf(m -> m.getHunger() >= BandMember.MAX_HUNGER);
        if (band.isEmpty()) {
            player.displayClientMessage(Component.literal("Nobody near is hungry."), true);
            return;
        }
        band.sort(Comparator.comparingInt(BandMember::getHunger));
        int given = 0;
        int round = 0;
        while (!held.isEmpty() && round < 4) {
            for (BandMember member : band) {
                if (held.isEmpty()) {
                    break;
                }
                ItemStack piece = held.split(1);
                member.feed(piece);
                if (round == 0 && member.getRandom().nextBoolean()) {
                    member.addBond(1);
                }
                given++;
            }
            round++;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, player.getSoundSource(), 0.6F, 1.0F);
        player.sendSystemMessage(Component.literal("You pass it around: " + given + " pieces between " + band.size()
                + ". Everyone saw you do it.").withStyle(ChatFormatting.LIGHT_PURPLE));
        gave(player, 3);
        Cohesion.addLimited(player, "pass_around", given >= band.size() ? 2 : 1, 5 * 60 * 20L);
    }

    /** Now and then someone who does not care picks a fight - never to the death. */
    private static void fight(ServerPlayer player, List<BandMember> band) {
        List<BandMember> bullies = band.stream().filter(m -> m.isAntisocial() && !m.isBaby() && !m.isInjured()).toList();
        if (bullies.isEmpty()) {
            return;
        }
        BandMember bully = bullies.get(player.getRandom().nextInt(bullies.size()));
        List<BandMember> near = Band.near(bully, 8.0D).stream()
                .filter(m -> m != bully && !m.isBaby() && m.isLedBy(player)).toList();
        if (near.isEmpty()) {
            return;
        }
        BandMember victim = near.get(player.getRandom().nextInt(near.size()));
        // Close to the band, or to the other one, and it rarely comes to blows.
        if (player.getRandom().nextFloat() < Morals.closeness(bully, victim)) {
            return;
        }
        bully.ensureName();
        victim.ensureName();
        if (checked(player) && player.getRandom().nextFloat() < 0.7F) {
            player.sendSystemMessage(Component.literal(bully.getName().getString() + " squares up to "
                    + victim.getName().getString() + " - and the whole band closes round them before it comes to blows. "
                    + "Nobody lords it over the rest here.").withStyle(ChatFormatting.GRAY));
            return;
        }
        bully.swing(InteractionHand.MAIN_HAND);
        float hurt = Math.min(victim.getHealth() - 2.0F, 3.0F + player.getRandom().nextInt(4));
        if (hurt > 0.0F) {
            victim.setHealth(victim.getHealth() - hurt);
            victim.playSound(SoundEvents.PLAYER_HURT, 0.8F, 1.1F);
        }
        boolean laidUp = player.getRandom().nextFloat() < 0.4F;
        if (laidUp) {
            victim.injure();
        }
        player.sendSystemMessage(Component.literal(bully.getName().getString() + " picks a fight with "
                + victim.getName().getString() + (laidUp ? " and hurts them badly." : " and draws blood.")
                + " Nobody stops it.").withStyle(ChatFormatting.RED));
        Cohesion.add(player, -1, null);
    }

    /**
     * Without ways of its own a band that is coming apart breeds people who stop caring; with them,
     * and holding together, the antisocial ones come round in time.
     */
    private static void drift(ServerPlayer player, List<BandMember> band) {
        int cohesion = Cohesion.get(player);
        boolean morals = anyMorals(player);
        for (BandMember member : band) {
            if (member.isBaby()) {
                continue;
            }
            member.ensureName();
            if (!member.isAntisocial() && !morals && cohesion < Cohesion.NEUTRAL && Bands.erectusOn(member.getStage())
                    && player.getRandom().nextFloat() < (Cohesion.NEUTRAL - cohesion) * 0.003F) {
                member.setTemper(true);
                player.sendSystemMessage(Component.literal(member.getName().getString()
                        + " has stopped caring what the band thinks of them. With no ways of its own to hold it together, "
                        + "the band is coming apart at the edges.").withStyle(ChatFormatting.DARK_RED));
                return;
            }
            if (member.isAntisocial() && morals && cohesion >= Cohesion.POSITIVE
                    && player.getRandom().nextFloat() < (checked(player) ? 0.2F : 0.08F)) {
                member.setTemper(false);
                player.sendSystemMessage(Component.literal(member.getName().getString()
                        + " has come round. The band's ways hold them now, too.").withStyle(ChatFormatting.GREEN));
                return;
            }
        }
    }

    /** People talk about the rules they have, and the ones they do not. */
    private static void talkOfWays(ServerPlayer player, List<BandMember> band) {
        BandMember speaker = someone(player, band);
        if (speaker == null) {
            return;
        }
        String line = null;
        if (Seasons.isProsperous(player.level()) && !Morals.holds(player, Morals.Moral.ALWAYS_SHARE)) {
            line = "A season like this and nobody here has to share. So I keep what I find. Some bands share everything - "
                    + "we never decided that.";
        } else if (Seasons.isDry(player.level()) && !Morals.holds(player, Morals.Moral.NO_THEFT_HARD)) {
            line = "When food is short, people take it. Nobody here ever said it was wrong.";
        } else if (Seasons.isDry(player.level()) && !Morals.holds(player, Morals.Moral.TIGHT_TIMES)
                && !Morals.holds(player, Morals.Moral.ALWAYS_SHARE)) {
            line = "Do we share in times like these, or look after our own? Nobody has ever said.";
        } else if (band.stream().anyMatch(BandMember::isAntisocial) && !Morals.holds(player, Morals.Moral.REVERSE_DOMINANCE)) {
            line = "Some of us do what we like and nobody says a thing. Other bands do not stand for that.";
        }
        if (line != null && Mortuary.canAdopt(player)) {
            say(player, speaker, line, ChatFormatting.WHITE);
            player.sendSystemMessage(Component.literal("(A band's ways are set under H: Our ways.)").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    // ------------------------------------------------------------ shunning

    /** "Shun them": the whole band turns its back. The defiant bend or go; anyone else is wronged. */
    public static void shun(ServerPlayer player, BandMember member) {
        member.ensureName();
        String name = member.getName().getString();
        if (!Mortuary.canAdopt(player)) {
            player.displayClientMessage(Component.literal("Your band cannot turn its back on one of its own like that yet. "
                    + "(Erectus and later.)"), true);
            return;
        }
        if (member.isBaby()) {
            player.displayClientMessage(Component.literal("Not a child."), true);
            return;
        }
        if (!member.isAntisocial()) {
            member.addBond(-4);
            Cohesion.add(player, -4, "left " + name + " alone - they did nothing to be shunned for");
            player.sendSystemMessage(Component.literal("The band will not do it. " + name
                    + " has done nothing to deserve it, and everyone knows you tried. (Bond -4)").withStyle(ChatFormatting.RED));
            return;
        }
        float bends = checked(player) ? 0.7F : 0.4F;
        if (player.getRandom().nextFloat() < bends) {
            member.setTemper(false);
            member.addBond(-1);
            Cohesion.add(player, 2);
            player.sendSystemMessage(Component.literal("Nobody looks at " + name + ", nobody shares with them, nobody "
                    + "grooms them. After a while they come back to the fire and sit where they are told.")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.literal(name + " will not bend. They take what they carry and walk away "
                    + "from the band for good.").withStyle(ChatFormatting.GOLD));
            member.discard();
            Cohesion.add(player, 1);
        }
    }

    // ------------------------------------------------------------ helpers

    private static BandMember someone(ServerPlayer player, List<BandMember> band) {
        List<BandMember> near = band.stream().filter(m -> !m.isBaby() && m.distanceToSqr(player) < 24.0D * 24.0D).toList();
        return near.isEmpty() ? null : near.get(player.getRandom().nextInt(near.size()));
    }

    private static void say(ServerPlayer player, BandMember speaker, String line, ChatFormatting style) {
        speaker.ensureName();
        player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(style)));
    }

    private Mood() {
    }
}
