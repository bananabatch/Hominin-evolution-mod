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
        ITEM("I need an item..."),
        HURT("I'm hurt, look after me"),
        TRAVEL("Let's stick together today"),
        HUNT("Let's hunt together"),
        NO_HUNT("Don't hunt with me"),
        CLIMB("Let's climb a tree / All clear");

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
            case ITEM -> askForItem(player, listeners, who, individual);
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
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.hominin.evolution.network.MemberInventoryPayload(member.getId(), member.getName().getString(),
                        slots, stacks));
    }

    /** The player picked something from a member's list. */
    public static void takeItem(ServerPlayer player, int entityId, int slot) {
        if (!(player.level().getEntity(entityId) instanceof BandMember member) || !member.isAlive()
                || !member.isLedBy(player) || member.distanceToSqr(player) > INDIVIDUAL_RADIUS * INDIVIDUAL_RADIUS) {
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
