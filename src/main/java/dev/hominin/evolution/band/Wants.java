package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.goal.CraftGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a habilis wants, and what it thinks. From habilis on, band members have tastes -
 * some favour quartzite over chert, a few cannot leave obsidian alone - and now and then
 * they want something and ask you for it, or offer you a trade for something you carry.
 *
 * <p>Give a member what it asked for, or its favourite food, and it warms to you. A
 * member that likes you looks out for you: food when you are going hungry, a better
 * weapon than the one you have, a hammerstone when you have none. Ignoring a want costs
 * nothing yet.
 */
public final class Wants {
    /** Between wants, per member. */
    private static final int WANT_MIN_TICKS = 4 * 60 * 20;
    private static final int WANT_SPREAD_TICKS = 4 * 60 * 20;
    /** How long a want lasts before it is forgotten. */
    private static final int WANT_LASTS_TICKS = 5 * 60 * 20;
    private static final int THOUGHT_MIN_TICKS = 3 * 60 * 20;
    private static final int THOUGHT_SPREAD_TICKS = 4 * 60 * 20;
    /** At most one thought in chat per leader this often. */
    private static final int THOUGHT_LEADER_GAP = 60 * 20;
    private static final int GIFT_COOLDOWN_TICKS = 5 * 60 * 20;
    /** Bond needed before a member starts looking out for you. */
    public static final int GIFT_BOND = 3;
    private static final double TALK_RANGE = 24.0D;

    private static final Map<UUID, Long> lastLeaderThought = new HashMap<>();
    private static final Map<UUID, Long> lastComplaint = new HashMap<>();

    /** Whether this member has wants and thoughts of its own: habilis and later, in the player's band. */
    public static boolean hasWants(BandMember member) {
        return CraftGoal.canCraft(member) && member.getLeader() != null;
    }

    public static boolean isGoodStone(ItemStack stack) {
        return (stack.is(ModItems.ROCK.get()) || stack.is(ModTags.Items.KNAPPABLE_STONE))
                && !stack.is(ModItems.LIMESTONE_ROCK.get());
    }

    /** Once a second, for members of the player's band. */
    public static void tick(BandMember member) {
        if (!hasWants(member) || !(member.leaderPlayer() instanceof Player leader) || member.isBaby()) {
            return;
        }
        long now = member.level().getGameTime();
        member.ensurePersonality();
        if (member.getWant() != null && now > member.getWantUntil()) {
            member.clearWant();
        }
        if (member.distanceToSqr(leader) > TALK_RANGE * TALK_RANGE || member.inDanger()) {
            return;
        }
        if (member.getWant() == null && now >= member.getNextWant()) {
            member.setNextWant(now + WANT_MIN_TICKS + member.getRandom().nextInt(WANT_SPREAD_TICKS));
            chooseWant(member, leader, now);
        }
        if (now >= member.getNextThought()) {
            member.setNextThought(now + THOUGHT_MIN_TICKS + member.getRandom().nextInt(THOUGHT_SPREAD_TICKS));
            think(member, leader, now);
        }
        if (member.getBond() >= GIFT_BOND && now >= member.getNextGift() && member.distanceToSqr(leader) < 12.0D * 12.0D
                && member.getRandom().nextFloat() < Math.min(0.3F, member.getBond() * 0.02F)) {
            if (tryGift(member, leader)) {
                member.setNextGift(now + GIFT_COOLDOWN_TICKS);
            }
        }
    }

    // ------------------------------------------------------------ wants

    private static void chooseWant(BandMember member, Player leader, long now) {
        List<Item> options = new ArrayList<>();
        if (member.isObsessedWithObsidian() && member.count(ModItems.OBSIDIAN_ROCK.get()) == 0) {
            options.add(ModItems.OBSIDIAN_ROCK.get());
            options.add(ModItems.OBSIDIAN_ROCK.get());
        }
        Item preferred = member.preferredStone();
        if (preferred != null && member.count(preferred) < 2) {
            options.add(preferred);
        }
        if (!member.carriesWeapon()) {
            options.add(ModItems.LONG_BRANCH.get());
        }
        if (member.getHunger() < BandMember.HUNGRY || options.isEmpty()) {
            options.add(member.favouriteFood());
        }
        Item want = options.get(member.getRandom().nextInt(options.size()));
        member.setWant(want, now + WANT_LASTS_TICKS);
        String name = new ItemStack(want).getHoverName().getString();

        // They can see what you carry. If you have it, they may offer something like it back.
        if (leader.getInventory().hasAnyOf(java.util.Set.of(want))) {
            int slot = tradeGood(member, new ItemStack(want));
            if (slot >= 0) {
                ItemStack offer = member.getInventory().getItem(slot);
                member.setTradeOffer(offer.getItem());
                say(member, leader, "You've got " + name + "... I'd give you my "
                        + offer.getHoverName().getString() + " for it.",
                        "(Hand " + member.getName().getString() + " the " + name + " to trade.)");
                return;
            }
        }
        String line = want == ModItems.OBSIDIAN_ROCK.get() ? "Obsidian... I keep thinking about obsidian. If you ever find some..."
                : want == preferred ? "I'd really like some " + name.toLowerCase() + ", if you come across it."
                : want == ModItems.LONG_BRANCH.get() ? "I've got nothing to hold on to. A good branch would do."
                : "I could really go for " + name.toLowerCase() + " right now.";
        say(member, leader, line, "(" + member.getName().getString() + " wants " + name + ".)");
    }

    /** Something this member would part with for the want: close to it in worth, and not the same thing. */
    private static int tradeGood(BandMember member, ItemStack want) {
        ResourceLocation stage = member.getStage();
        int wantTier = Trading.tierOf(want, stage);
        SimpleContainer pack = member.getInventory();
        int best = -1;
        int bestValue = -1;
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            int tier = Trading.tierOf(stack, stage);
            if (stack.isEmpty() || stack.is(want.getItem()) || tier == 0 || Math.abs(tier - wantTier) > 1
                    || member.refusesToPartWith(stack)) {
                continue;
            }
            int value = Trading.valueOf(stack, stage);
            if (value > bestValue) {
                bestValue = value;
                best = slot;
            }
        }
        return best;
    }

    /** The player handed this member something. Returns true if it was what they wanted. */
    public static boolean receive(BandMember member, Player player, ItemStack held) {
        Item want = member.getWant();
        if (want == null || !held.is(want)) {
            return false;
        }
        String name = member.getName().getString();
        ItemStack taken = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        Item offer = member.getTradeOffer();
        ItemStack given = offer == null ? ItemStack.EMPTY : member.takeOneOf(offer);
        member.addToInventory(taken);
        member.clearWant();
        member.addBond(2);
        member.playSound(SoundEvents.ITEM_PICKUP, 0.7F, 1.1F);
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HEART, member.getX(), member.getEyeY() + 0.3D,
                member.getZ(), 4, 0.3D, 0.2D, 0.3D, 0.0D);
        if (!given.isEmpty()) {
            player.displayClientMessage(Component.literal(name + " takes it, and hands you ")
                    .append(given.getHoverName()).append(" as promised."), true);
            if (!player.getInventory().add(given)) {
                player.drop(given, false);
            }
        } else {
            player.displayClientMessage(Component.literal(name + " takes it gratefully. They won't forget this.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        if (want == ModItems.OBSIDIAN_ROCK.get()) {
            Band.announceDiscovery(member, " can't stop turning the obsidian over in their hands.");
        }
        return true;
    }

    // ------------------------------------------------------------ looking out for you

    /** A member who likes you gives you what you are short of - unasked. */
    private static boolean tryGift(BandMember member, Player leader) {
        String name = member.getName().getString();
        if (leader.getFoodData().getFoodLevel() <= 14 && member.hasFood()) {
            ItemStack food = member.takeFood();
            if (!food.isEmpty()) {
                give(leader, food, name + " sees you're hungry and hands you " + food.getHoverName().getString()
                        + " without being asked.");
                return true;
            }
        }
        int yourBest = -1;
        ItemStack yourWeapon = ItemStack.EMPTY;
        var inventory = leader.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            int rank = BandMember.weaponRank(stack);
            if (rank > yourBest) {
                yourBest = rank;
                yourWeapon = stack;
            }
        }
        if (member.bestWeaponRank() > yourBest && member.bestWeaponRank() >= 2) {
            ItemStack weapon = member.takeBestWeapon();
            if (!weapon.isEmpty()) {
                String than = yourWeapon.isEmpty() ? "" : " - better than that " + yourWeapon.getHoverName().getString();
                give(leader, weapon, name + " hands you their " + weapon.getHoverName().getString() + than + ".");
                member.equipBestWeapon();
                return true;
            }
        }
        if (!inventory.contains(ModTags.Items.HAMMERSTONES)) {
            ItemStack hammer = member.takeFirst(s -> s.is(ModTags.Items.HAMMERSTONES));
            if (!hammer.isEmpty()) {
                give(leader, hammer, name + " notices you have nothing to strike with, and gives you their "
                        + hammer.getHoverName().getString() + ".");
                return true;
            }
        }
        if (!inventory.contains(ModTags.Items.FLAKES) && !inventory.contains(ModTags.Items.CHOPPERS)) {
            ItemStack edge = member.takeFirst(s -> s.is(ModTags.Items.FLAKES) || s.is(ModTags.Items.CHOPPERS));
            if (!edge.isEmpty()) {
                give(leader, edge, name + " presses a " + edge.getHoverName().getString()
                        + " into your hand. You had nothing to cut with.");
                return true;
            }
        }
        return false;
    }

    private static void give(Player player, ItemStack stack, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------ thoughts

    private static void think(BandMember member, Player leader, long now) {
        if (now - lastLeaderThought.getOrDefault(leader.getUUID(), -99999L) < THOUGHT_LEADER_GAP) {
            return;
        }
        List<String> thoughts = new ArrayList<>();
        Item preferred = member.preferredStone();
        if (member.getHunger() < BandMember.HUNGRY) {
            thoughts.add("My stomach won't stop. " + new ItemStack(member.favouriteFood()).getHoverName().getString()
                    + " would be good.");
        }
        if (member.isObsessedWithObsidian()) {
            thoughts.add(member.count(ModItems.OBSIDIAN_ROCK.get()) > 0
                    ? "The black glass is sharper than anything. Nobody touches it but me."
                    : "There's black glass somewhere. There has to be.");
        }
        if (preferred == ModItems.GRANITE_ROCK.get()) {
            thoughts.add("Chert is pretty, but quartzite doesn't snap on you.");
        } else if (preferred == ModItems.CHERT_ROCK.get()) {
            thoughts.add("Good chert breaks just how you want it to.");
        }
        if (member.count(ModItems.LIMESTONE_ROCK.get()) > 0) {
            thoughts.add("This limestone is useless. Crumbles the moment you hit it.");
        }
        if (member.getWant() != null) {
            thoughts.add("Still thinking about " + new ItemStack(member.getWant()).getHoverName().getString().toLowerCase() + ".");
        }
        if (member.getBond() >= GIFT_BOND) {
            thoughts.add("You look after us. I'll look after you.");
        } else if (member.getBond() == 0) {
            thoughts.add("I wonder if you even know my name.");
        }
        if (!member.carriesWeapon()) {
            thoughts.add("Something out there is watching. I've nothing to hit it with.");
        }
        if (member.level().isNight()) {
            thoughts.add("The dark is full of eyes.");
        }
        thoughts.add("The flakes are sharper when you strike at the edge, not the middle.");
        thoughts.add("The ground here has good roots under it.");
        thoughts.add("I saw where the termites are. I'll remember.");
        lastLeaderThought.put(leader.getUUID(), now);
        String thought = thoughts.get(member.getRandom().nextInt(thoughts.size()));
        leader.sendSystemMessage(Component.literal(member.getName().getString() + " thinks: ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(thought).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
    }

    private static void say(BandMember member, Player leader, String line, @Nullable String hint) {
        member.ensureName();
        leader.sendSystemMessage(Component.literal("<" + member.getName().getString() + "> ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
        if (hint != null) {
            leader.sendSystemMessage(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static void complainAboutLimestone(BandMember member) {
        long now = member.level().getGameTime();
        if (member.leaderPlayer() instanceof Player leader && member.distanceToSqr(leader) < TALK_RANGE * TALK_RANGE
                && now - lastComplaint.getOrDefault(member.getUUID(), -99999L) > 2 * 60 * 20) {
            lastComplaint.put(member.getUUID(), now);
            say(member, leader, "Limestone again. Too soft - it won't hold an edge. I'm not using this.", null);
        }
    }

    public static String describeItem(@Nullable Item item) {
        return item == null ? "nothing" : new ItemStack(item).getHoverName().getString();
    }

    public static ResourceLocation idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    static boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD);
    }

    private Wants() {
    }
}
