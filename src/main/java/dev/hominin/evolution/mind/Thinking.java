package dev.hominin.evolution.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.combat.ItemInteractions;
import dev.hominin.evolution.stage.BuiltinMilestones;
import dev.hominin.evolution.combat.ItemInteractions.HandRecipe;
import dev.hominin.evolution.data.PlayerEvolutionData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Working out what the things in your hands are for.
 *
 * <p>Nothing in this mod can be built until it has been thought of first, and
 * thinking is not free. A brain that size burns about a fifth of the body's energy
 * at rest, so this demands a body with nothing else to spend: full health, a full
 * stomach, and it takes most of the stomach with it. That is the honest version of
 * why encephalisation waited on diet, and it is also a decent game rule - it makes
 * inventing something a thing you prepare for rather than a thing you spam.
 */
public final class Thinking {
    /** Ticks the key has to be held. Long enough to be a decision, not a twitch. */
    public static final int HOLD_TICKS = 40;

    /** Three real-time minutes. Ideas do not come back quickly. */
    private static final int COOLDOWN_TICKS = 3 * 60 * 20;

    /** Vanilla's full hunger bar. Thinking demands the whole thing, not most of it. */
    /**
     * Thinking is expensive, not impossible. Demanding a full bar of each meant that on
     * a bad day - and most days out here are bad ones - you simply could not think, and
     * a stage that needs four thoughts became a wall. It still costs; it no longer
     * requires you to be perfectly fed and unhurt first.
     */
    private static final int FULL_FOOD = 14;
    private static final float THINKING_HEALTH_FRACTION = 0.6F;

    /** Hunger the effort costs - most of a meal, taken off a full bar. */
    private static final int FOOD_COST = 6;

    private static final Map<UUID, Long> lastThought = new HashMap<>();

    /**
     * Resolves one completed hold of the think key. Every failure path reports
     * something specific, because "nothing happened" is indistinguishable from a
     * broken keybind.
     */
    public static void think(ServerPlayer player) {
        // Stared down by a troop you just struck: this is not a moment for ideas. You drop
        // low, look away, and make yourself as small and as harmless as you can.
        if (dev.hominin.evolution.entity.TroopRelations.hasPendingMistake(player)) {
            player.setShiftKeyDown(true);
            dev.hominin.evolution.entity.TroopRelations.forgive(player,
                    "You drop low and look away. After a long moment, the troop goes back to foraging.");
            dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.DEESCALATION);
            return;
        }
        long now = player.level().getGameTime();
        // Thinking about the animal that just ran is not inventing anything, and costs nothing
        // but what the chase itself costs.
        if (dev.hominin.evolution.hunt.Quarry.trySeed(player)) {
            return;
        }
        // Something about this place you could not put your finger on: now you can.
        if (Insights.tryReveal(player)) {
            return;
        }
        Long last = lastThought.get(player.getUUID());
        int cooldown = COOLDOWN_TICKS - (dev.hominin.evolution.mind.Skills.knows(player, dev.hominin.evolution.mind.Skills.Skill.LONG_VIEW) ? 1200 : 0);
        if (last != null && now - last < cooldown) {
            int seconds = (int) ((cooldown - (now - last)) / 20L);
            player.displayClientMessage(Component.literal(
                    "Your head is still thick from the last of it. (" + seconds + "s)"), true);
            return;
        }
        if (player.getHealth() < player.getMaxHealth() * THINKING_HEALTH_FRACTION) {
            player.displayClientMessage(
                    Component.literal("You hurt too much to hold a thought."), true);
            return;
        }
        if (player.getFoodData().getFoodLevel() < FULL_FOOD) {
            player.displayClientMessage(
                    Component.literal("You are too hungry to think. Eat first."), true);
            return;
        }

        if (EvolutionManager.isReadyForMilestone(player, BuiltinMilestones.WALK_UPRIGHT)) {
            lastThought.put(player.getUUID(), now);
            EvolutionManager.attemptMilestone(player, BuiltinMilestones.WALK_UPRIGHT);
            return;
        }

        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        HandRecipe recipe = ItemInteractions.match(main, off);
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);

        if (recipe == null) {
            if (main.isEmpty() && off.isEmpty()) {
                reflect(player, now);
                return;
            }
            // Holding something that suggests nothing is still a mind at work: it wanders.
            reflect(player, now);
            return;
        }
        dev.hominin.evolution.knapping.Acheulean.pondered(player, player.getMainHandItem());
        if (!recipe.needsThought()) {
            player.displayClientMessage(
                    Component.literal("There is nothing to work out here. Your hands already know."), true);
            return;
        }
        if (!ItemInteractions.isAvailable(data, recipe)) {
            player.displayClientMessage(
                    Component.literal("Something is there, but it will not come. Not yet."), true);
            return;
        }
        if (data.getUnlockedRecipes().contains(recipe.id())) {
            player.displayClientMessage(
                    Component.literal("You already know what these are for."), true);
            return;
        }

        data.getUnlockedRecipes().add(recipe.id());
        spend(player, now);
        // Counted whether or not the stage needs it, so the criterion is just a
        // tally of times you have actually worked something out.
        EvolutionManager.incrementCriterion(player, "think_times", 1);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.4F, 1.6F);
        player.sendSystemMessage(Component.literal(recipe.insight()).withStyle(ChatFormatting.YELLOW));
        player.displayClientMessage(Component.literal("You work it out.").withStyle(ChatFormatting.YELLOW), true);
    }

    /**
     * Thinking with nothing in the hands: not about a tool, but about time itself. The
     * day, the days before it - a past and a future, which is a strange new thing to
     * hold in a head. It invents nothing, but it counts, and it is the one kind of
     * thought that can be had again and again.
     */
    private static void reflect(ServerPlayer player, long now) {
        spend(player, now);
        // A second time sitting with nothing but the day in your head, and it takes.
        int reflections = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA)
                .getCriterionCounters().merge("reflections", 1, Integer::sum);
        if (reflections >= 2) {
            dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.LONG_VIEW);
        }
        EvolutionManager.incrementCriterion(player, "think_times", 1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        player.sendSystemMessage(Component.literal(reflection(player)).withStyle(ChatFormatting.YELLOW));
    }

    /** Where a mind goes when there is nothing in the hands to think about. */
    private static String reflection(ServerPlayer player) {
        java.util.List<String> thoughts = new java.util.ArrayList<>();
        thoughts.add("You think about the day, and the days before it. The past is a foreign concept, "
                + "and a new one - but you feel more capable than before.");
        thoughts.add("You remember a place - and then realise you could walk back to it.");
        thoughts.add("You turn a stone over in your mind the way you would turn one in your hand.");
        thoughts.add("Two things, struck the right way, make a third. What else is two things waiting to be one?");
        thoughts.add("Somebody who was here is not here any more. You notice the space where they were.");
        if (player.level().isNight()) {
            thoughts.add("You look up. The lights up there do not move the way anything down here does.");
        }
        if (player.getFoodData().getFoodLevel() < 16) {
            thoughts.add("Your stomach talks over everything. Tomorrow's food is a thought you have never had before.");
        }
        if (player.isInWater() || dev.hominin.evolution.survival.Thirst.get(player) < 12) {
            thoughts.add("You watch water move and wonder where it goes, and whether it ever comes back.");
        }
        if (dev.hominin.evolution.band.Band.ownNear(player, 16.0D).size() >= 3) {
            thoughts.add("You count the others without any numbers to count with: enough, or not enough.");
        }
        if (player.getInventory().hasAnyOf(java.util.Set.of(dev.hominin.evolution.ModItems.LONG_BONE.get(),
                dev.hominin.evolution.ModItems.BONE_MARROW.get()))) {
            thoughts.add("There was more inside the bone than anyone had thought to look for.");
        }
        return thoughts.get(player.getRandom().nextInt(thoughts.size()));
    }

    private static void spend(ServerPlayer player, long now) {
        lastThought.put(player.getUUID(), now);
        player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - FOOD_COST);
        player.getFoodData().setSaturation(0.0F);
    }

    public static void forget(ServerPlayer player) {
        lastThought.remove(player.getUUID());
    }

    private Thinking() {
    }
}
