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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Talking to hominins. There are no words yet - a call, a gesture, food held out - but
 * a band understands a few things well enough: come and forage, I am hungry, I need
 * something, I am hurt, and, to another band, walk with us today.
 *
 * <p>Said to everyone nearby, or to one member picked out first.
 */
public final class Social {
    /** What a thing said is about, so the talk menu is a few short lists rather than one long one. */
    public enum Topic {
        FOOD("Food"),
        THINGS("Tools and things"),
        DANGER("Danger"),
        TOGETHER("Each other"),
        DEVELOPER("Developer");

        private final String label;

        Topic(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Command {
        FORAGE("Let's forage", Topic.FOOD),
        FOOD("I'm hungry, can you get me food?", Topic.FOOD),
        ITEM("I need an item...", Topic.THINGS),
        TRADE("Trade...", Topic.THINGS),
        GIVE("Here, take this (what I'm holding)", Topic.THINGS),
        LEAD_STONE("Show me good stone", Topic.THINGS),
        LEAD_OBSIDIAN("Show me obsidian", Topic.THINGS),
        HURT("I'm hurt, look after me", Topic.DANGER),
        TRAVEL("Let's stick together today", Topic.TOGETHER),
        HUNT("Let's hunt together", Topic.DANGER),
        NO_HUNT("Don't hunt with me", Topic.DANGER),
        CLIMB("Let's climb a tree / All clear", Topic.DANGER),
        GROOM("Groom them", Topic.TOGETHER),
        GROOM_ME("Get these off me", Topic.TOGETHER),
        PLAY("Let's play", Topic.TOGETHER),
        TEACH("Teach...", Topic.TOGETHER),
        SHARE("Let's share food", Topic.TOGETHER),
        INFO("Info", Topic.TOGETHER),
        DEV_BOND_UP("Bond +5 (whoever's listening)", Topic.DEVELOPER),
        DEV_BOND_DOWN("Bond -5 (whoever's listening)", Topic.DEVELOPER),
        DEV_COHESION("Band cohesion +10", Topic.DEVELOPER),
        DEV_TROOP_TRUST("Nearest troop: trusts you", Topic.DEVELOPER),
        DEV_TROOP_GRUDGE("Nearest troop: grudge", Topic.DEVELOPER),
        DEV_TICKS_UP("Ticks +3", Topic.DEVELOPER),
        DEV_HEAL_CLEAR("Clear ticks and afflictions", Topic.DEVELOPER),
        DEV_WATER("Fill water", Topic.DEVELOPER),
        DEV_SKILLS_ALL("Learn every skill", Topic.DEVELOPER),
        DEV_SKILLS_NONE("Forget every skill", Topic.DEVELOPER),
        DEV_TRAINING("Max play training", Topic.DEVELOPER),
        DEV_TEACH_BAND("Teach the band every skill", Topic.DEVELOPER);

        private final String label;
        private final Topic topic;

        Command(String label, Topic topic) {
            this.label = label;
            this.topic = topic;
        }

        public String label() {
            return label;
        }

        public Topic topic() {
            return topic;
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

    private static final int HUNT_TICKS = 60 * 20;
    /** Up the tree until called down - with a long ceiling, in case the player forgets. */
    private static final int CLIMB_ORDER_TICKS = 10 * 60 * 20;

    private static final Map<UUID, Long> lastHurtCall = new HashMap<>();

    /** Only the tree-climbing stages will go up a tree when asked. */
    public static boolean canClimbOrder(ServerPlayer player) {
        String stage = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath();
        return stage.equals("ardipithecus") || stage.equals("australopithecus") || stage.equals("homo_habilis");
    }
    private static final Map<UUID, Long> guardUntil = new HashMap<>();

    /** Runs a command, said to one member (by entity id) or, with -1, to whoever is nearby. */
    public static void perform(ServerPlayer player, int entityId, Command command) {
        if (command.topic() == Topic.DEVELOPER) {
            Developer.run(player, entityId, command);
            return;
        }
        if (command == Command.TRAVEL && entityId < 0) {
            List<BandMember> other = nearestOtherBand(player);
            if (other.isEmpty()) {
                player.displayClientMessage(Component.literal("There is no other band close enough."), true);
            } else {
                askToTravel(player, other.get(0));
            }
            return;
        }
        List<BandMember> listeners = listeners(player, entityId);
        if (listeners.isEmpty()) {
            player.displayClientMessage(Component.literal("Nobody is close enough to hear you."), true);
            return;
        }
        boolean individual = entityId >= 0;
        // Addressed directly, they stop and listen rather than wandering off mid-sentence.
        if (individual) {
            listeners.forEach(member -> member.attendTo(player, BandMember.ATTEND_TICKS));
        }
        BandMember first = listeners.get(0);
        String who = individual ? first.getName().getString() : first.isWild() ? "The other band" : "Your band";
        // Paranthropus: no shared language worth the name. Trade, and being shown the way.
        if (Paranthropus.is(first) && command != Command.TRADE && command != Command.LEAD_STONE
                && command != Command.LEAD_OBSIDIAN) {
            say(player, "The Paranthropus stare at you. Whatever you meant, it did not get across.");
            return;
        }
        switch (command) {
            case GIVE -> nearestOf(player, listeners).receiveFromHand(player);
            case LEAD_STONE, LEAD_OBSIDIAN -> {
                if (!Paranthropus.is(first)) {
                    say(player, who + (individual ? " has" : " have") + " no better idea where to find it than you do.");
                    return;
                }
                Paranthropus.guide(player, nearestOf(player, listeners), command == Command.LEAD_OBSIDIAN);
            }
            case FORAGE -> {
                for (BandMember member : listeners) {
                    member.forageAlongside(player.blockPosition());
                }
                say(player, who + (individual ? " comes" : " come") + " to forage beside you.");
            }
            case FOOD -> askForFood(player, listeners, individual, who);
            case ITEM -> askForItem(player, listeners, who, individual);
            case TRADE -> Trading.openTrade(player, nearestOf(player, listeners));
            case HURT -> askForCare(player, listeners, who);
            case TRAVEL -> askToTravel(player, first);
            case HUNT -> {
                if (first.isWild() && !first.isGuestOf(player)) {
                    say(player, "They will not hunt for a stranger.");
                    return;
                }
                for (BandMember member : listeners) {
                    member.startHunt(HUNT_TICKS);
                }
                say(player, who + (individual ? " is" : " are") + " with you - watching for anything small enough to catch.");
            }
            case NO_HUNT -> {
                for (BandMember member : listeners) {
                    member.setHuntWithLeader(false);
                }
                say(player, who + " will leave your prey alone. They will still defend you.");
            }
            case GROOM -> {
                if (first.isWild() && !first.isGuestOf(player)) {
                    say(player, "They will not let a stranger that close.");
                    return;
                }
                Grooming.begin(player, first);
            }
            case GROOM_ME -> askToBeGroomed(player, listeners, who);
            case PLAY -> play(player, listeners);
            case TEACH -> dev.hominin.evolution.mind.Teaching.open(player, individual ? first.getId() : -1);
            case SHARE -> share(player, listeners);
            case INFO -> {
                if (individual) {
                    sendInfo(player, first);
                }
            }
            case CLIMB -> {
                if (!canClimbOrder(player)) {
                    say(player, "Your kind have mostly given up the trees.");
                    return;
                }
                if (listeners.stream().anyMatch(BandMember::hasClimbOrder)) {
                    // Asked again: come down.
                    for (BandMember member : listeners) {
                        member.orderClimb(0);
                    }
                    say(player, "All clear!");
                    return;
                }
                for (BandMember member : listeners) {
                    member.orderClimb(CLIMB_ORDER_TICKS);
                }
                say(player, who + (individual ? " heads" : " head") + " for the nearest tree. Ask again to call them down.");
            }
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
        return nearestOtherBand(player);
    }

    /** The closest band that is not travelling with you, nearest member first. */
    private static List<BandMember> nearestOtherBand(ServerPlayer player) {
        BandMember nearest = null;
        for (BandMember member : Band.near(player, GROUP_RADIUS)) {
            if (member.isWild() && !member.isGuestOf(player)
                    && (nearest == null || member.distanceToSqr(player) < nearest.distanceToSqr(player))) {
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

    private static void askForItem(ServerPlayer player, List<BandMember> listeners, String who, boolean individual) {
        if (listeners.get(0).isWild()) {
            say(player, "They keep hold of their things. Trade for them instead.");
            return;
        }
        if (individual) {
            showInventory(player, listeners.get(0));
            return;
        }
        // Asking the whole band: start with whoever is nearest, and let the player walk
        // through the rest one at a time to see who is carrying what.
        BandMember nearest = null;
        for (BandMember member : listeners) {
            if (member.isLedBy(player) && (nearest == null
                    || member.distanceToSqr(player) < nearest.distanceToSqr(player))) {
                nearest = member;
            }
        }
        if (nearest != null) {
            showInventory(player, nearest);
            return;
        }
        if (giveWhatIsNeeded(player, listeners)) {
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

    /**
     * The band looks at the player and works out what they are short of, most urgent first:
     * food when hungry or hurt (the worse off, the more filling), stones for someone with a
     * hammer and nothing to strike, and something to fight with for someone with nothing.
     * Returns false if the player seems to need nothing in particular.
     */
    private static boolean giveWhatIsNeeded(ServerPlayer player, List<BandMember> band) {
        int hungerGap = 20 - player.getFoodData().getFoodLevel();
        float healthGap = player.getMaxHealth() - player.getHealth();
        if (hungerGap >= 4 || healthGap >= player.getMaxHealth() * 0.4F) {
            return giveFood(player, band, hungerGap + Math.round(healthGap / 2.0F));
        }
        var inventory = player.getInventory();
        boolean hasHammer = inventory.contains(dev.hominin.evolution.ModTags.Items.HAMMERSTONES);
        int stones = 0;
        boolean hasWeapon = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE)) {
                stones += stack.getCount();
            }
            hasWeapon |= BandMember.isWeapon(stack);
        }
        if (hasHammer && stones < 2) {
            return giveStones(player, band, 2 - stones);
        }
        if (!hasWeapon) {
            return giveWeapon(player, band);
        }
        return false;
    }

    /** Food, most filling first when the need is great, lightest first when it is small. */
    private static boolean giveFood(ServerPlayer player, List<BandMember> band, int need) {
        int given = 0;
        int nutrition = 0;
        List<String> givers = new ArrayList<>();
        while (nutrition < need && given < 4) {
            BandMember bestGiver = null;
            int bestSlot = -1;
            int bestScore = Integer.MIN_VALUE;
            for (BandMember member : band) {
                var pack = member.getInventory();
                for (int slot = 0; slot < pack.getContainerSize(); slot++) {
                    var food = pack.getItem(slot).get(net.minecraft.core.component.DataComponents.FOOD);
                    if (food == null) {
                        continue;
                    }
                    int score = need >= 8 ? food.nutrition() : -Math.abs(food.nutrition() - need);
                    if (score > bestScore) {
                        bestScore = score;
                        bestGiver = member;
                        bestSlot = slot;
                    }
                }
            }
            if (bestGiver == null) {
                break;
            }
            ItemStack food = bestGiver.getInventory().removeItem(bestSlot, 1);
            nutrition += food.get(net.minecraft.core.component.DataComponents.FOOD).nutrition();
            given++;
            bestGiver.ensureName();
            if (!givers.contains(bestGiver.getName().getString())) {
                givers.add(bestGiver.getName().getString());
            }
            if (!player.getInventory().add(food)) {
                player.drop(food, false);
            }
        }
        if (given == 0) {
            BandMember forager = nearestAdult(player, band);
            if (forager == null) {
                return false;
            }
            forager.fetchFoodFor(player);
            say(player, "Nobody has any food on them. " + forager.getName().getString() + " goes looking for some.");
            return true;
        }
        say(player, String.join(" and ", givers) + (givers.size() > 1 ? " share" : " shares")
                + " food with you (" + given + ").");
        return true;
    }

    private static boolean giveStones(ServerPlayer player, List<BandMember> band, int wanted) {
        int given = 0;
        for (BandMember member : band) {
            var pack = member.getInventory();
            for (int slot = 0; slot < pack.getContainerSize() && given < wanted; slot++) {
                ItemStack stack = pack.getItem(slot);
                // Good knapping stone before soft limestone.
                if (stack.is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE)
                        && !stack.is(dev.hominin.evolution.ModItems.LIMESTONE_ROCK.get())) {
                    ItemStack stone = pack.removeItem(slot, 1);
                    if (!player.getInventory().add(stone)) {
                        player.drop(stone, false);
                    }
                    given++;
                }
            }
        }
        if (given > 0) {
            say(player, "You have a hammer and nothing to strike. The band hands you " + given
                    + (given == 1 ? " stone." : " stones."));
            return true;
        }
        BandMember runner = nearestAdult(player, band);
        if (runner == null) {
            return false;
        }
        runner.requestFetch(player, FetchKind.ANY_ROCK);
        say(player, "Nobody is carrying good stone. " + runner.getName().getString() + " goes to find you some.");
        return true;
    }

    private static boolean giveWeapon(ServerPlayer player, List<BandMember> band) {
        BandMember giver = null;
        for (BandMember member : band) {
            if (member.isBaby()) {
                continue;
            }
            if (giver == null || member.bestWeaponRank() > giver.bestWeaponRank()) {
                giver = member;
            }
        }
        ItemStack weapon = giver == null ? ItemStack.EMPTY : giver.takeBestWeapon();
        if (weapon.isEmpty()) {
            return false;
        }
        giver.ensureName();
        say(player, "You have nothing to defend yourself with. " + giver.getName().getString() + " hands you "
                + weapon.getHoverName().getString() + ".");
        if (!player.getInventory().add(weapon)) {
            player.drop(weapon, false);
        }
        giver.equipBestWeapon();
        return true;
    }

    @Nullable
    private static BandMember nearestAdult(ServerPlayer player, List<BandMember> band) {
        BandMember best = null;
        for (BandMember member : band) {
            if (!member.isBaby() && !member.isOnExcursion()
                    && (best == null || member.distanceToSqr(player) < best.distanceToSqr(player))) {
                best = member;
            }
        }
        if (best != null) {
            best.ensureName();
        }
        return best;
    }

    /** Everything worth knowing about one member, for the info screen. */
    // ------------------------------------------------------------ play and share

    private static final int PLAY_TICKS = 200;
    private static final int PLAY_COOLDOWN = 1200;
    /** Somebody hurt this recently means it is not a time for games. */
    private static final int PLAY_SAFE_AFTER = 1200;
    private static final int SHARE_COOLDOWN = 8 * 60 * 20;
    private static final Map<UUID, Long> lastPlay = new HashMap<>();
    private static final Map<UUID, Long> lastShare = new HashMap<>();

    /**
     * A game, and only when it is safe. Play is what animals do with the hours nothing is
     * trying to kill them, and a band that plays in the open with a cat about is not
     * playing, it is being eaten.
     */
    private static void play(ServerPlayer player, List<BandMember> listeners) {
        long now = player.level().getGameTime();
        Long last = lastPlay.get(player.getUUID());
        if (last != null && now - last < PLAY_COOLDOWN) {
            say(player, "They are still catching their breath from the last one.");
            return;
        }
        boolean shaken = player.tickCount - player.getLastHurtByMobTimestamp() < PLAY_SAFE_AFTER
                && player.getLastHurtByMobTimestamp() > 0;
        List<BandMember> players = new ArrayList<>();
        for (BandMember member : listeners) {
            if (member.inDanger()) {
                shaken = true;
            }
            if (!member.isWild() && !member.isPlaying() && !member.isUpATree()) {
                players.add(member);
            }
        }
        if (shaken) {
            say(player, "Nobody feels like playing with danger this close.");
            return;
        }
        if (players.size() < 2) {
            say(player, "There is nobody here to play with.");
            return;
        }
        lastPlay.put(player.getUUID(), now);
        boolean tag = player.getRandom().nextBoolean();
        int kind = tag ? BandMember.PLAY_TAG : BandMember.PLAY_WRESTLE;
        for (int i = 0; i + 1 < players.size(); i += 2) {
            players.get(i).startPlay(kind, players.get(i + 1), PLAY_TICKS);
            players.get(i + 1).startPlay(kind, players.get(i), PLAY_TICKS);
        }
        // You are in it too, and you learn from it the same way they do - three rounds'
        // worth per species, because the body you evolve into has to learn it again.
        String key = tag ? "play_tag" : "play_wrestle";
        int learned = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA)
                .getCriterionCounters().getOrDefault(key, 0);
        if (learned < BandMember.MAX_TRAINING) {
            dev.hominin.evolution.EvolutionManager.incrementCriterion(player, key, 1);
        }
        player.sendSystemMessage(Component.literal(tag
                ? "The band breaks into a game of chase. Next time you have to run, you will run a little longer."
                : "The band piles into a wrestling match. Next time you have to fight, you will last a little longer.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /**
     * Passing food round. Everyone who has something puts it in, and it goes to whoever
     * likes it best. Nobody ends up much fuller - the point is who noticed what you like.
     */
    private static void share(ServerPlayer player, List<BandMember> listeners) {
        long now = player.level().getGameTime();
        Long last = lastShare.get(player.getUUID());
        if (last != null && now - last < SHARE_COOLDOWN) {
            say(player, "You only just shared a meal. (" + (SHARE_COOLDOWN - (now - last)) / 20 + "s)");
            return;
        }
        List<BandMember> diners = new ArrayList<>();
        for (BandMember member : listeners) {
            if (!member.isWild() && !member.inDanger()) {
                diners.add(member);
            }
        }
        if (diners.isEmpty()) {
            say(player, "There is nobody here to share with.");
            return;
        }
        List<ItemStack> pot = new ArrayList<>();
        ItemStack held = player.getMainHandItem();
        if (held.has(net.minecraft.core.component.DataComponents.FOOD)) {
            pot.add(held.split(1));
        }
        for (BandMember member : diners) {
            ItemStack food = member.takeFood();
            if (!food.isEmpty()) {
                pot.add(food);
            }
        }
        if (pot.isEmpty()) {
            say(player, "Nobody has anything to share. Hold some food and ask again.");
            return;
        }
        lastShare.put(player.getUUID(), now);
        int favourites = 0;
        for (ItemStack food : pot) {
            BandMember best = diners.get(player.getRandom().nextInt(diners.size()));
            for (BandMember member : diners) {
                if (member.isFavourite(food)) {
                    best = member;
                    break;
                }
            }
            if (best.eatShared(food)) {
                favourites++;
                best.addBond(1);
            }
        }
        for (BandMember member : diners) {
            member.addBond(1);
        }
        dev.hominin.evolution.EvolutionManager.incrementCriterion(player, Band.COHESION, diners.size());
        player.sendSystemMessage(Component.literal("The food goes round. " + pot.size()
                + (pot.size() == 1 ? " thing" : " things") + " shared"
                + (favourites > 0 ? ", and " + favourites + " went to someone who loves it." : "."))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /**
     * You cannot reach your own back. That is the entire reason primates groom each
     * other at all, so asking is not a weakness in the design - it is the design.
     */
    private static void askToBeGroomed(ServerPlayer player, List<BandMember> listeners, String who) {
        if (dev.hominin.evolution.survival.Infestation.of(player) == 0) {
            say(player, "There is nothing on you worth picking off.");
            return;
        }
        BandMember willing = null;
        for (BandMember member : listeners) {
            // Only actually fighting rules somebody out. "In danger" also covered a raised
            // alarm or being a bit hurt, which is most of a band most of the time.
            if (member.isBaby() || member.getTarget() != null || member.isWild()) {
                continue;
            }
            // Somebody who owes you comes first, then whoever likes you most.
            if (member.owesGroomingTo(player)) {
                willing = member;
                break;
            }
            if (willing == null || member.getBond() > willing.getBond()) {
                willing = member;
            }
        }
        if (willing == null) {
            say(player, "Nobody here is free to do it.");
            return;
        }
        // Your own band will always do it. Going through each other's hair is what a band
        // is; nobody in it has to earn that first.
        willing.oweGrooming(player);
        say(player, willing.getName().getString() + " comes over to see to you.");
    }

    /** Whoever of these is closest - the one you are actually facing, most likely. */
    private static BandMember nearestOf(ServerPlayer player, List<BandMember> listeners) {
        BandMember nearest = listeners.get(0);
        for (BandMember member : listeners) {
            if (member.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = member;
            }
        }
        return nearest;
    }

    private static void sendInfo(ServerPlayer player, BandMember member) {
        member.ensureName();
        List<String> lines = new ArrayList<>();
        lines.add((member.isBaby() ? "Young " : "") + (member.isFemale() ? "Female" : "Male"));
        lines.add("Health: " + Math.round(member.getHealth()) + " / " + Math.round(member.getMaxHealth()));
        lines.add("Hunger: " + member.getHunger() + " / " + BandMember.MAX_HUNGER);
        lines.add("Favourite foods: " + String.join(", ", member.favouriteFoodNames()));
        lines.add("Bond with you: " + member.getBond() + (member.getBond() >= Wants.GIFT_BOND ? " (looks out for you)" : ""));
        List<String> knows = member.knownSkillTitles();
        lines.add("Knows: " + (knows.isEmpty() ? "nothing they were taught" : String.join(", ", knows)));
        lines.add("Ticks on them: " + (member.getTicksOnMe() == 0 ? "none" : String.valueOf(member.getTicksOnMe()))
                + (member.owesGroomingTo(player) ? " - owes you a turn" : ""));
        if (Wants.hasWants(member)) {
            Item preferred = member.preferredStone();
            lines.add("Prefers: " + (preferred == null ? "any good stone" : Wants.describeItem(preferred)));
            if (member.isObsessedWithObsidian()) {
                lines.add("Obsessed with obsidian");
            }
            lines.add("Wants: " + Wants.describeItem(member.getWant())
                    + (member.getTradeOffer() != null ? " (offering " + Wants.describeItem(member.getTradeOffer()) + ")" : ""));
        }
        if (member.getParty() > 0) {
            lines.add("Off with party " + member.getParty() + " today");
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.hominin.evolution.network.MemberInfoPayload(member.getName().getString(), lines));
    }

    /** Sends the player the list of what one member carries, to pick from. */
    private static void showInventory(ServerPlayer player, BandMember member) {
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        if (!member.getMainHandItem().isEmpty()) {
            slots.add(-1);
            stacks.add(member.getMainHandItem().copy());
        }
        var pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (!pack.getItem(slot).isEmpty()) {
                slots.add(slot);
                stacks.add(pack.getItem(slot).copy());
            }
        }
        member.ensureName();
        // Everyone in the band close enough to ask, nearest first, so the screen can page.
        List<BandMember> band = new ArrayList<>(Band.ownNear(player, GROUP_RADIUS));
        band.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player)));
        List<Integer> ids = new ArrayList<>();
        for (BandMember other : band) {
            if (!other.isBaby()) {
                ids.add(other.getId());
            }
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.hominin.evolution.network.MemberInventoryPayload(member.getId(), member.getName().getString(),
                        slots, stacks, ids));
    }

    /** The screen asked to see a different member of the band. */
    public static void viewInventory(ServerPlayer player, int entityId) {
        if (player.level().getEntity(entityId) instanceof BandMember member && member.isAlive()
                && member.isLedBy(player) && member.distanceToSqr(player) <= GROUP_RADIUS * GROUP_RADIUS) {
            showInventory(player, member);
        }
    }

    /** The player picked something from a member's list. */
    public static void takeItem(ServerPlayer player, int entityId, int slot) {
        if (!(player.level().getEntity(entityId) instanceof BandMember member) || !member.isAlive()
                || !member.isLedBy(player) || member.distanceToSqr(player) > INDIVIDUAL_RADIUS * INDIVIDUAL_RADIUS) {
            return;
        }
        ItemStack peek = slot == -1 ? member.getMainHandItem()
                : slot >= 0 && slot < member.getInventory().getContainerSize() ? member.getInventory().getItem(slot) : ItemStack.EMPTY;
        if (member.refusesToPartWith(peek)) {
            say(player, member.getName().getString() + " clutches the obsidian and won't let go of it.");
            return;
        }
        ItemStack item = member.takeFromSlot(slot);
        if (item.isEmpty()) {
            say(player, member.getName().getString() + " doesn't have that any more.");
            return;
        }
        say(player, member.getName().getString() + " hands you " + item.getHoverName().getString() + ".");
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
    }

    /**
     * "Get me ..." - said to one member, or to the band, in which case whoever is closest
     * and not busy goes. Food means foraging; everything else is a real errand.
     */
    public static void requestFetch(ServerPlayer player, int entityId, FetchKind kind) {
        BandMember runner = null;
        if (entityId >= 0) {
            if (player.level().getEntity(entityId) instanceof BandMember member && member.isAlive()
                    && member.distanceToSqr(player) <= INDIVIDUAL_RADIUS * INDIVIDUAL_RADIUS) {
                runner = member;
            }
        } else {
            for (BandMember member : Band.ownNear(player, GROUP_RADIUS)) {
                if (member.isBaby() || member.isOnExcursion() || member.getFetchKind() != null) {
                    continue;
                }
                if (runner == null || member.distanceToSqr(player) < runner.distanceToSqr(player)) {
                    runner = member;
                }
            }
        }
        if (runner == null) {
            say(player, "Nobody is free to go and get it.");
            return;
        }
        if (!runner.isLedBy(player)) {
            say(player, "They won't run errands for someone outside their band.");
            return;
        }
        if (runner.isBaby()) {
            say(player, runner.getName().getString() + " is too young to fetch things.");
            return;
        }
        runner.ensureName();
        if (runner.getBond() < kind.minBond()) {
            say(player, runner.getName().getString() + " doesn't think that much of you yet.");
            return;
        }
        if (kind == FetchKind.FOOD) {
            if (!runner.giveFoodTo(player)) {
                runner.fetchFoodFor(player);
                say(player, runner.getName().getString() + " goes to find you something to eat.");
            }
            return;
        }
        runner.requestFetch(player, kind);
        say(player, runner.getName().getString() + " goes to get you " + kind.label().toLowerCase() + ".");
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
        if (Paranthropus.is(member)) {
            say(player, "Paranthropus go their own way. They will show you the way somewhere, but not walk with you.");
            return;
        }
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
        if (!Territory.willTravelWith(member, player)) {
            say(player, member.getName().getString() + "'s band keep their distance. You have been taking from their ground.");
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "(Trade them something worth having, and they will share it with you.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Territory.grantAccess(member, player);
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
