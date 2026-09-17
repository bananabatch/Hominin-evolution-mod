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
    private static final int FULL_FOOD = 20;

    /** Hunger the effort costs - most of a meal, taken off a full bar. */
    private static final int FOOD_COST = 6;

    private static final Map<UUID, Long> lastThought = new HashMap<>();

    /**
     * Resolves one completed hold of the think key. Every failure path reports
     * something specific, because "nothing happened" is indistinguishable from a
     * broken keybind.
     */
    public static void think(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long last = lastThought.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            int seconds = (int) ((COOLDOWN_TICKS - (now - last)) / 20L);
            player.displayClientMessage(Component.literal(
                    "Your head is still thick from the last of it. (" + seconds + "s)"), true);
            return;
        }
        if (player.getHealth() < player.getMaxHealth()) {
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
            player.displayClientMessage(
                    Component.literal("Nothing you are holding suggests anything."), true);
            return;
        }
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
        EvolutionManager.incrementCriterion(player, "think_times", 1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        player.sendSystemMessage(Component.literal(
                "You think about the day, and the days before it. The past is a foreign concept, "
                        + "and a new one - but you feel more capable than before.")
                .withStyle(ChatFormatting.YELLOW));
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
