package dev.hominin.evolution.hunt;

import java.util.Map;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.knapping.Acheulean;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Persistence hunting as a skill: level 3 (anyone) down to level 1 (a hunter people follow).
 *
 * <p>The better the hunter, the more often the animal stays in sight when it breaks and runs -
 * 20%, 40%, 60% - so the chase does not have to be thought back together. And experience lands
 * harder: half a heart more at level 2, a whole heart at level 1, on anything hunted.
 *
 * <p>It is also where a new body's starting skills are rolled. From erectus on, every species you
 * become starts with its own hands: mostly ordinary, now and then gifted - which is what makes
 * the gifted ones in a band worth keeping close.
 */
public final class Persistence {
    public static final String LEVEL = EvolutionManager.SKILL_PREFIX + "hunting_level";
    private static final String PROGRESS = EvolutionManager.SKILL_PREFIX + "hunting_progress";
    /** Starting skills rolled for this body and not yet told: said once the new band is round you. */
    private static final String UNTOLD = EvolutionManager.SKILL_PREFIX + "skills_untold";
    /** Persistence kills to make at each level before the next: level 3 needs 2, level 2 needs 3. */
    private static final int[] TO_ADVANCE = {0, 0, 3, 2};

    // ------------------------------------------------------------ the rolls

    /** Knapping, 4 (beginner) to 1 (master): mostly 4 or 3, sometimes 2, rarely 1. */
    public static int rollKnapping(RandomSource random) {
        float roll = random.nextFloat();
        return roll < 0.06F ? 1 : roll < 0.22F ? 2 : roll < 0.60F ? 3 : 4;
    }

    /** Hunting, 3 to 1: mostly 3, sometimes 2, rarely 1. */
    public static int rollHunting(RandomSource random) {
        float roll = random.nextFloat();
        return roll < 0.08F ? 1 : roll < 0.33F ? 2 : 3;
    }

    /** Erectus and every species after it: where bodies start to differ in what their hands can do. */
    public static boolean rollsSkills(ResourceLocation stage) {
        String path = stage.getPath();
        return !path.equals("ardipithecus") && !path.startsWith("australopithecus") && !path.equals("homo_habilis")
                && !path.equals("homo_rudolfensis") && !path.equals("paranthropus_boisei");
    }

    /**
     * A new body: roll its skills. Knowing Lomekwian knapping still counts for something - whatever
     * you become is never a complete beginner at it.
     */
    public static void rollFor(ServerPlayer player) {
        Map<String, Integer> counters = counters(player);
        RandomSource random = player.getRandom();
        int knapping = rollKnapping(random);
        if (knapping == 4 && dev.hominin.evolution.mind.Skills.knows(player, dev.hominin.evolution.mind.Skills.Skill.LOMEKWIAN)) {
            knapping = 3;
        }
        counters.put(Acheulean.LEVEL, knapping);
        counters.remove(EvolutionManager.SKILL_PREFIX + "knapping_progress");
        int hunting = rollHunting(random);
        if (dev.hominin.evolution.mind.Skills.knows(player, dev.hominin.evolution.mind.Skills.Skill.EARLY_TRACKING)) {
            // Habilis learned to hold a chase in its head; its descendants are born better at it.
            hunting = Math.max(1, hunting - 1);
        }
        counters.put(LEVEL, hunting);
        counters.remove(PROGRESS);
        counters.put(UNTOLD, 1);
        if (knapping <= 2) {
            EvolutionManager.forceSatisfyCriterion(player, "knapping_level_2");
        }
    }

    /** Tells the player what this body can do, once, when the new band is round them. */
    public static void tellStartingSkills(ServerPlayer player) {
        Map<String, Integer> counters = counters(player);
        if (counters.remove(UNTOLD) == null) {
            return;
        }
        int knapping = Acheulean.level(player);
        int hunting = level(player);
        player.sendSystemMessage(Component.literal("What these hands can do:").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("  Knapping - level " + knapping + " " + knappingWord(knapping))
                .withStyle(knapping <= 2 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("  Persistence hunting - level " + hunting + " " + huntingWord(hunting))
                .withStyle(hunting <= 2 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(knapping >= 3 && hunting >= 3
                ? "Ordinary hands. Anyone in the band who is better is worth keeping close - Tribe stats shows who."
                : "Better than most. The band will notice.").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static String knappingWord(int level) {
        return switch (level) {
            case 1 -> "(master - rare)";
            case 2 -> "(skilled - uncommon)";
            case 3 -> "(able)";
            default -> "(a beginner)";
        };
    }

    public static String huntingWord(int level) {
        return switch (level) {
            case 1 -> "(a hunter people follow - rare)";
            case 2 -> "(a good tracker - uncommon)";
            default -> "(ordinary)";
        };
    }

    // ------------------------------------------------------------ the player's level

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    public static int level(ServerPlayer player) {
        return counters(player).computeIfAbsent(LEVEL, key -> 3);
    }

    /** One more animal run down. Returns true if it took you up a level. */
    public static boolean practise(ServerPlayer player) {
        int level = level(player);
        if (level <= 1) {
            return false;
        }
        Map<String, Integer> counters = counters(player);
        int progress = counters.getOrDefault(PROGRESS, 0) + 1;
        if (progress < TO_ADVANCE[level]) {
            counters.put(PROGRESS, progress);
            return false;
        }
        counters.put(PROGRESS, 0);
        counters.put(LEVEL, level - 1);
        player.sendSystemMessage(Component.literal("You read the ground better now. Persistence hunting: level "
                + (level - 1) + (level - 1 == 1 ? " - the band will follow you anywhere." : "."))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    public static String describe(ServerPlayer player) {
        int level = level(player);
        if (level <= 1) {
            return "level 1 " + huntingWord(1) + " - 60% keep it in sight, +1 heart on the hunt";
        }
        int progress = counters(player).getOrDefault(PROGRESS, 0);
        return "level " + level + " - " + Math.round(highlightChance(level) * 100) + "% keep it in sight"
                + (level == 2 ? ", +half a heart on the hunt" : "") + " (" + progress + "/" + TO_ADVANCE[level]
                + " runs-down to the next)";
    }

    // ------------------------------------------------------------ what the level does

    /** The chance an animal that breaks and runs stays marked out, without having to think it back. */
    public static float highlightChance(int level) {
        return level <= 1 ? 0.6F : level == 2 ? 0.4F : 0.2F;
    }

    /** Extra damage on anything hunted: experience knows where to put the blow. */
    public static float damageBonus(int level) {
        return level <= 1 ? 2.0F : level == 2 ? 1.0F : 0.0F;
    }

    /** Game animals: anything that is not a predator and is not people. */
    public static boolean isHunted(LivingEntity target) {
        return target instanceof Animal && !(target instanceof BandMember)
                && !target.getType().is(ModTags.EntityTypes.PREDATORS);
    }

    /** A hunter's blow on game lands harder with experience - the player's, or a band member's own. */
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !isHunted(victim)) {
            return;
        }
        int level;
        if (event.getSource().getEntity() instanceof ServerPlayer hunter) {
            level = level(hunter);
        } else if (event.getSource().getEntity() instanceof BandMember member && !member.isBaby()) {
            level = member.getHuntLevel();
        } else {
            return;
        }
        float bonus = damageBonus(level);
        if (bonus > 0.0F) {
            event.setAmount(event.getAmount() + bonus);
        }
    }

    private Persistence() {
    }
}
