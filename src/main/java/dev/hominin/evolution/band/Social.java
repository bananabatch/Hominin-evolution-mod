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
        TOGETHER("Social"),
        CULTURE("Culture"),
        OTHERS("The others"),
        DEVELOPER("Developer");

        private final String label;

        Topic(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** The Developer tab is too long for one list: it is split up by what the commands are about. */
    public enum DevSection {
        BAND("Your band"),
        YOU("You"),
        WORLD("The world"),
        OTHERS("Other bands"),
        PLACES("Places and tools"),
        BUILDING("Building");

        private final String label;

        DevSection(String label) {
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
        KNAP("Knap me a tool...", Topic.THINGS),
        PASS_AROUND("Split what I'm holding between you", Topic.FOOD),
        GIVE("Here, take this (what I'm holding)", Topic.THINGS),
        LEAD_STONE("Show me good stone", Topic.THINGS),
        LEAD_OBSIDIAN("Show me obsidian", Topic.THINGS),
        HURT("I'm hurt, look after me", Topic.DANGER),
        TRAVEL("Let's stick together today", Topic.TOGETHER),
        HUNT("Let's hunt together", Topic.DANGER),
        NO_HUNT("Don't hunt with me", Topic.DANGER),
        CLIMB("Let's climb a tree / All clear", Topic.DANGER),
        WATCH("Keep watch with me tonight", Topic.DANGER),
        GROOM("Groom them", Topic.TOGETHER),
        GROOM_ME("Get these off me", Topic.TOGETHER),
        PLAY("Let's play", Topic.TOGETHER),
        TEACH("Teach...", Topic.TOGETHER),
        SHARE("Let's share food", Topic.FOOD),
        INFO("Info", Topic.TOGETHER),
        TRIBE("Tribe stats", Topic.TOGETHER),
        PROMISE("I'll do better", Topic.TOGETHER),
        ASK_MEMORIES("What do you remember?", Topic.TOGETHER),
        SHUN("Shun them", Topic.TOGETHER),
        MAKE_MATE("Be my mate", Topic.TOGETHER),
        SWAP("Let me live as you for a while", Topic.TOGETHER),
        HAVE_CHILD("Let's have a child", Topic.TOGETHER),
        CULTURE("Our ways: morals and norms", Topic.CULTURE),
        // ------------------------------------------------ developer: your band
        DEV_BOND_UP("Bond +5", DevSection.BAND),
        DEV_BOND_DOWN("Bond -5", DevSection.BAND),
        DEV_COHESION("Cohesion +10", DevSection.BAND),
        DEV_COHESION_DOWN("Cohesion -10", DevSection.BAND),
        DEV_SPAWN_MEMBER("Add a member", DevSection.BAND),
        DEV_KILL_MEMBER("Kill one member", DevSection.BAND),
        DEV_MAKE_MATE("Make them my mate", DevSection.BAND),
        DEV_MEMBER_FOOD("Give a member 24 food", DevSection.BAND),
        DEV_TRAINING("Max play training", DevSection.BAND),
        DEV_TEACH_BAND("Teach band every skill", DevSection.BAND),
        DEV_TROUBLED("Make one troubled", DevSection.BAND),
        DEV_SOUR("Make one gone sour", DevSection.BAND),
        DEV_PSYCHOPATH("Make one a psychopath", DevSection.BAND),
        DEV_WHO_PSYCHOPATH("Who is the psychopath?", DevSection.BAND),
        DEV_PSYCHOPATH_LEAVES("Psychopath leaves now", DevSection.BAND),
        // ------------------------------------------------ developer: you
        DEV_TICKS_UP("Ticks +3", DevSection.YOU),
        DEV_HEAL_CLEAR("Clear ticks, afflictions", DevSection.YOU),
        DEV_SPOIL_HELD("Spoil the meat I hold", DevSection.YOU),
        DEV_FOOD_ILL("Make me sick (bad meat)", DevSection.YOU),
        DEV_WATER("Fill water", DevSection.YOU),
        DEV_FEED("Fill food", DevSection.YOU),
        DEV_FOOD_PILE("Give me 24 food", DevSection.YOU),
        DEV_PRESENCE_UP("Presence +10", DevSection.YOU),
        DEV_PRESENCE_DOWN("Presence -10", DevSection.YOU),
        DEV_NAME_UP("Your name +2", DevSection.YOU),
        DEV_SKILLS_ALL("Learn every skill", DevSection.YOU),
        DEV_SKILLS_NONE("Forget every skill", DevSection.YOU),
        DEV_RARE("Give me the rare things", DevSection.YOU),
        DEV_TOOLS("Give me stone tools", DevSection.YOU),
        // ------------------------------------------------ developer: the world
        DEV_DUSK("Time: dusk", DevSection.WORLD),
        DEV_MORNING("Time: morning", DevSection.WORLD),
        DEV_SEASON_NEXT("Other season", DevSection.WORLD),
        DEV_SUPER_DRY("Super dry season", DevSection.WORLD),
        DEV_VERY_PROSPEROUS("Very prosperous season", DevSection.WORLD),
        DEV_DESPERATE("Desperate times on/off", DevSection.WORLD),
        DEV_TROOP_TRUST("Nearest troop: trust", DevSection.WORLD),
        DEV_TROOP_GRUDGE("Nearest troop: grudge", DevSection.WORLD),
        DEV_SPAWN_TROOP("Baboon troop nearby", DevSection.WORLD),
        DEV_SPAWN_HERD("Megafauna herd nearby", DevSection.WORLD),
        // ------------------------------------------------ developer: other bands
        DEV_SPAWN_BAND("A new band nearby", DevSection.OTHERS),
        DEV_ALLY("Nearest band: allies", DevSection.OTHERS),
        DEV_HOSTILE("Nearest band: hostile", DevSection.OTHERS),
        DEV_DESPERATION("Nearest: desperation +1", DevSection.OTHERS),
        DEV_NIGHT_RAID("Night raid now", DevSection.OTHERS),
        DEV_FOOD_RAID("Food raid now", DevSection.OTHERS),
        DEV_TRADE_VISIT("Ally trade visit now", DevSection.OTHERS),
        DEV_KILL_BAND("A band dies", DevSection.OTHERS),
        DEV_PLIGHT("An ally is attacked", DevSection.OTHERS),
        DEV_RESCUE("Allies come to you", DevSection.OTHERS),
        // ------------------------------------------------ developer: places and tools
        DEV_REVEAL_PLACES("Know places in 400", DevSection.PLACES),
        DEV_FORGET_PLACES("Forget all places", DevSection.PLACES),
        DEV_LOSE_PLACES("Lose places as band", DevSection.PLACES),
        DEV_NEXT_PLACE("Lead: nearest unknown", DevSection.PLACES),
        DEV_PLACE_SPRING("A spring here", DevSection.PLACES),
        DEV_PLACE_LICK("A salt lick here", DevSection.PLACES),
        DEV_PLACE_DEPOSIT("A tool deposit here", DevSection.PLACES),
        DEV_TOOL_PILE("A full tool pile here", DevSection.PLACES),
        // ------------------------------------------------ developer: building
        DEV_BUILD_FINISH("Finish nearest build", DevSection.BUILDING),
        DEV_BUILD_MATERIALS("Thatch, posts and hide", DevSection.BUILDING),
        DEV_BUILD_UNLOCK("Unlock every blueprint", DevSection.BUILDING),
        DEV_BUILD_ASK("Ask what it's for again", DevSection.BUILDING),
        DEV_BUILD_CLEAR("Forget my builds", DevSection.BUILDING),
        DEV_BUILD_SUGGEST("Someone suggests a build", DevSection.BUILDING);

        private final String label;
        private final Topic topic;
        @Nullable
        private final DevSection section;

        Command(String label, Topic topic) {
            this.label = label;
            this.topic = topic;
            this.section = null;
        }

        Command(String label, DevSection section) {
            this.label = label;
            this.topic = Topic.DEVELOPER;
            this.section = section;
        }

        public String label() {
            return label;
        }

        public Topic topic() {
            return topic;
        }

        @Nullable
        public DevSection section() {
            return section;
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
        if (command == Command.CULTURE) {
            if (!Mortuary.canAdopt(player)) {
                say(player, "Your band has no ways of its own yet. (Erectus and later.)");
                return;
            }
            Morals.send(player);
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
            case CULTURE -> Morals.send(player);
            case TRIBE -> sendTribe(player);
            case PROMISE -> Cohesion.promise(player);
            case ASK_MEMORIES -> dev.hominin.evolution.mind.MentalMap.askAround(player);
            case PASS_AROUND -> Mood.passAround(player);
            case SHUN -> {
                if (individual) {
                    Mood.shun(player, first);
                }
            }
            case MAKE_MATE -> {
                if (individual) {
                    Mating.makeMate(player, first);
                }
            }
            case SWAP -> {
                if (individual) {
                    Band.swapInto(player, first);
                }
            }
            case WATCH -> {
                if (individual) {
                    Band.keepWatch(player, first);
                }
            }
            case HAVE_CHILD -> {
                if (individual) {
                    Mating.haveChild(player, first);
                }
            }
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
            case KNAP -> {
                if (individual) {
                    Commissions.open(player, first);
                }
            }
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

    /** Said to the other band as a whole, even with your own band standing round you. */
    public static final int OTHER_BAND = -2;

    private static List<BandMember> listeners(ServerPlayer player, int entityId) {
        if (entityId == OTHER_BAND) {
            return nearestOtherBand(player);
        }
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
        boolean tight = Morals.applies(player, Morals.Moral.TIGHT_TIMES);
        for (BandMember member : listeners) {
            if (member.isBaby()) {
                continue;
            }
            // Times are tight, and the band holds that nobody has to share: only friends do.
            if (tight && member.getBond() < Wants.GIFT_BOND) {
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
        boolean sharing = Morals.applies(player, Morals.Moral.ALWAYS_SHARE);
        long cooldown = sharing ? SHARE_COOLDOWN / 2 : SHARE_COOLDOWN;
        if (last != null && now - last < cooldown) {
            say(player, "You only just shared a meal. (" + (cooldown - (now - last)) / 20 + "s)");
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
                Lines.say(best, "share_thanks");
            }
        }
        for (BandMember member : diners) {
            // Where sharing is the rule, a shared meal means more.
            member.addBond(sharing ? 2 : 1);
        }
        Cohesion.addLimited(player, "share", Math.min(3, Math.max(1, diners.size() / 2)), 10 * 60 * 20L);
        Mood.gave(player, 2);
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
        if (member.isAntisocial()) {
            lines.add("Antisocial: steals, hoards, begs, will not teach, picks fights (erectus: Shun them)");
        }
        if (member.getTrouble() == Troubles.TROUBLED) {
            lines.add("Grieving " + (member.getGrievingFor().isEmpty() ? "someone" : member.getGrievingFor())
                    + " - they have not been the same since. Look after them.");
        } else if (member.getTrouble() == Troubles.SOUR) {
            lines.add("Gone sour since " + (member.getGrievingFor().isEmpty() ? "they lost someone"
                    : member.getGrievingFor() + " died") + ". Hang out with them.");
        }
        String sign = Psychopaths.signOf(member);
        if (sign != null) {
            lines.add(sign);
        }
        lines.add(Relations.speciesName(member.getStage()) + (Species.abilities(member.getStage()).isEmpty() ? ""
                : " - " + Species.abilities(member.getStage())));
        lines.add("Knapping: level " + member.getKnapLevel() + " "
                + dev.hominin.evolution.hunt.Persistence.knappingWord(member.getKnapLevel()));
        lines.add("Persistence hunting: level " + member.getHuntLevel() + " "
                + dev.hominin.evolution.hunt.Persistence.huntingWord(member.getHuntLevel()));
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

    /** A line in the band list that finds this member when clicked. See MemberInfoScreen. */
    private static String link(BandMember member, String text) {
        return "@" + member.getId() + "|" + text;
    }

    private static String header(String text) {
        return "#" + text;
    }

    /**
     * The whole band at a glance: who matters most to it, who is asking you for what, who is
     * expecting and who the children are - and then everyone. Every name finds its owner.
     */
    private static void sendTribe(ServerPlayer player) {
        List<BandMember> band = Band.all(player);
        band.forEach(BandMember::ensureName);
        var counters = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        List<String> lines = new ArrayList<>();
        long children = band.stream().filter(BandMember::isBaby).count();
        int cohesion = Cohesion.get(player);
        lines.add(BandNames.capital(Relations.ownName(player)));
        lines.add("Cohesion: " + cohesion + "/" + Cohesion.MAX + " - " + Cohesion.label(cohesion)
                + (Cohesion.promised(player) ? " (you promised to do better)" : ""));
        String need = Needs.describe(player);
        if (need != null) {
            BandMember needy = Needs.needy(player);
            lines.add(needy != null ? link(needy, "NEED: " + need) : "NEED: " + need);
        }
        lines.add("Members: " + band.size()
                + " (" + (band.size() - children) + " grown, " + children + " children)");
        BandMember mine = Mating.mateOf(player);
        if (mine != null) {
            lines.add(link(mine, "Your mate: " + mine.getName().getString()));
        }
        for (Morals.Moral moral : Morals.Moral.values()) {
            int state = Morals.state(player, moral);
            if (state == Morals.HELD || state == Morals.FADING) {
                lines.add("Way: " + moral.title() + (state == Morals.FADING ? " (fading)" : "")
                        + (Morals.applies(player, moral) ? "" : " - not binding now"));
            }
        }
        lines.add("Season: " + dev.hominin.evolution.survival.Seasons.of(player.level()).label() + ", "
                + dev.hominin.evolution.survival.Seasons.daysLeft(player.level()) + " days left"
                + (dev.hominin.evolution.survival.Drought.isActive(player.level()) ? " (a dry day)" : ""));
        float days = dev.hominin.evolution.hunt.Predation.daysOnGround(player);
        lines.add(String.format(java.util.Locale.ROOT, "On this ground: %.1f days", days)
                + (days >= 4.5F ? " - everything here knows you; hold it, or move on 200 blocks"
                        : days >= 4.0F ? " - they will start testing you at 4.5" : " (at 4.5 they start testing you)"));
        int presence = Presence.get(player);
        lines.add("Presence: " + presence + "/" + Presence.MAX + " - " + Presence.label(presence));
        if (dev.hominin.evolution.hunt.Predation.settled(player)) {
            var land = dev.hominin.evolution.world.Land.ofPlayer(player);
            lines.add("Your ground's pressure: " + land.total() + "/10 - " + String.join(", ", land.describe(player)));
        }
        int desperate = Claims.ownDesperation(player);
        lines.add("Desperation: " + desperate + "/5 - " + Claims.desperationLabel(desperate));
        long known = Bands.all(player.serverLevel()).stream().filter(b -> b.knownTo(player.getUUID())).count();
        lines.add("Other bands you know of: " + known + " (H: The others)");
        lines.add("Your hands: knapping " + dev.hominin.evolution.knapping.Acheulean.level(player)
                + ", persistence hunting " + dev.hominin.evolution.hunt.Persistence.level(player));

        List<BandMember> gifted = new ArrayList<>(band.stream().filter(m -> !m.isBaby() && m.isGifted()).toList());
        gifted.sort(java.util.Comparator.comparingInt(BandMember::talent).thenComparing(m -> -m.getBond()));
        lines.add("");
        lines.add(header("Most valuable"));
        if (gifted.isEmpty()) {
            lines.add("Nobody stands out yet - ordinary hands, all of them.");
        }
        for (BandMember member : gifted.subList(0, Math.min(5, gifted.size()))) {
            StringBuilder line = new StringBuilder(member.getName().getString()).append(" - ");
            if (member.getKnapLevel() <= 2) {
                line.append("knapping ").append(member.getKnapLevel())
                        .append(member.getKnapLevel() == 1 ? " (master)" : " (skilled)");
            }
            if (member.getHuntLevel() <= 2) {
                line.append(member.getKnapLevel() <= 2 ? ", " : "").append("hunting ").append(member.getHuntLevel())
                        .append(member.getHuntLevel() == 1 ? " (great tracker)" : " (good tracker)");
            }
            line.append(" - bond ").append(member.getBond());
            if (member.getBond() >= Wants.HUNTS_FOR_YOU_BOND) {
                line.append(" (looks after you)");
            }
            lines.add(link(member, line.toString()));
        }

        lines.add("");
        lines.add(header("Asking you for"));
        boolean anyAsk = false;
        for (BandMember member : band) {
            if (member.isWantVoiced()) {
                anyAsk = true;
                lines.add(link(member, member.getName().getString() + " wants " + Wants.describeItem(member.getWant())
                        + (member.getTradeOffer() != null ? " (offering " + Wants.describeItem(member.getTradeOffer()) + ")" : "")));
            }
            String job = Commissions.describe(member, player.getUUID());
            if (job != null) {
                anyAsk = true;
                lines.add(link(member, member.getName().getString() + " " + job));
            }
        }
        if (!anyAsk) {
            lines.add("Nobody is asking for anything right now.");
        }

        List<BandMember> expecting = band.stream().filter(BandMember::isPregnant).toList();
        if (!expecting.isEmpty() || Mating.isPregnant(player)) {
            lines.add("");
            lines.add(header("Expecting"));
            if (Mating.isPregnant(player)) {
                lines.add("You - " + Mating.describePregnancy(player));
            }
            for (BandMember member : expecting) {
                String father = Mating.mateName(member, player.serverLevel());
                lines.add(link(member, member.getName().getString() + (member.isInLabour() ? " - GIVING BIRTH, alone"
                        : " - pregnant") + (father != null ? " (father: " + father + ")" : "")));
            }
        }

        List<BandMember> young = band.stream().filter(BandMember::isBaby).toList();
        if (!young.isEmpty()) {
            lines.add("");
            lines.add(header("Children"));
            for (BandMember child : young) {
                String minder = null;
                if (child.getCaretaker() != null
                        && player.serverLevel().getEntity(child.getCaretaker()) instanceof BandMember carer) {
                    carer.ensureName();
                    minder = carer.getName().getString();
                }
                lines.add(link(child, child.getName().getString() + (child.isFemale() ? " (girl)" : " (boy)")
                        + (minder != null ? " - minded by " + minder : "")));
            }
        }

        lines.add("");
        lines.add(header("Everyone"));
        band.sort((x, y) -> Integer.compare(y.getBond(), x.getBond()));
        for (BandMember member : band) {
            if (member.isBaby()) {
                continue;
            }
            StringBuilder line = new StringBuilder(member.getName().getString())
                    .append(member.isFemale() ? " (F)" : " (M)")
                    .append(member.isAntisocial() ? " ANTISOCIAL" : "")
                    .append(member.getTrouble() == Troubles.TROUBLED ? " - grieving" : "")
                    .append(member.isPsychopathKnown() ? " - you know what they are" : "")
                    .append(" - bond ").append(member.getBond())
                    .append(" - knap ").append(member.getKnapLevel()).append(", hunt ").append(member.getHuntLevel());
            String mate = Mating.mateName(member, player.serverLevel());
            if (mate != null) {
                line.append(" - mate: ").append(mate);
            }
            var friend = member.closestFriend();
            if (friend != null && player.serverLevel().getEntity(friend.getKey()) instanceof BandMember close) {
                close.ensureName();
                line.append(" - closest to ").append(close.getName().getString());
            }
            lines.add(link(member, line.toString()));
        }
        lines.add("");
        lines.add("(Click a name to find them. Hover one and press P if you think they are using everyone.)");
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.hominin.evolution.network.MemberInfoPayload("Your band", lines));
    }

    /** A name clicked in the band list: that member glows for a while, and you are told which way. */
    public static void find(ServerPlayer player, int entityId) {
        if (!(player.level().getEntity(entityId) instanceof BandMember member) || !member.isLedBy(player)) {
            player.displayClientMessage(Component.literal("They are too far off to find from here."), true);
            return;
        }
        member.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.GLOWING, FIND_GLOW_TICKS, 0, false, false));
        member.ensureName();
        double dx = member.getX() - player.getX();
        double dz = member.getZ() - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String[] compass = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        int sector = Math.floorMod(Math.round((float) (Math.toDegrees(Math.atan2(-dx, dz)) / 45.0D)), 8);
        player.displayClientMessage(Component.literal(member.getName().getString() + " - " + distance + " blocks "
                + (distance < 3 ? "away, right here" : compass[sector])).withStyle(ChatFormatting.AQUA), true);
    }

    private static final int FIND_GLOW_TICKS = 15 * 20;

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
        if (member.isAntisocial() && member.getBond() < 6) {
            say(player, member.getName().getString() + " pulls it back. \"Mine.\"");
            return;
        }
        ItemStack item = member.takeFromSlot(slot);
        if (item.isEmpty()) {
            say(player, member.getName().getString() + " doesn't have that any more.");
            return;
        }
        say(player, member.getName().getString() + " hands you " + item.getHoverName().getString() + ".");
        Morals.tookFrom(player, member, item);
        Mood.took(player, 1);
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

    static void askToTravel(ServerPlayer player, BandMember member) {
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
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(Relations.travelRefusal(player, member))
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        Territory.grantAccess(member, player);
        Relations.travelled(player, member);
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
