package dev.hominin.evolution.knapping;

import java.util.List;
import java.util.Map;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.item.AcheuleanToolItem;
import dev.hominin.evolution.mind.Skills;
import dev.hominin.evolution.stage.BuiltinMilestones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The Acheulean industry: hand axes and cleavers, made at a knapping station from erectus on.
 * A flawless hand axe is the industry's multitool - see {@link AcheuleanToolItem}.
 *
 * <p>Two things decide how a tool comes out. The stone sets a ceiling - limestone never gets
 * past crude, quartzite only reaches strong in the best hands, chert can be flawless, and
 * obsidian is never worse than strong. The knapper's skill, level 4 (a beginner) down to
 * level 1, sets what they usually manage, with a chance of doing better. Skill comes from
 * making things: one tool leaves level 4, two more leave level 3, three more leave level 2.
 */
public final class Acheulean {
    /** Carries over when you evolve: hands remember. */
    public static final String LEVEL = EvolutionManager.SKILL_PREFIX + "knapping_level";
    private static final String PROGRESS = EvolutionManager.SKILL_PREFIX + "knapping_progress";
    /** Tools to make at each level before reaching the next: level 4 needs 1, 3 needs 2, 2 needs 3. */
    private static final int[] TO_ADVANCE = {0, 0, 3, 2, 1};
    public static final int STONE_COST = 2;

    public static final List<KnappingChoice> CHOICES =
            List.of(KnappingChoice.HAND_AXE, KnappingChoice.CLEAVER);

    public static boolean isAcheulean(KnappingChoice choice) {
        return CHOICES.contains(choice);
    }

    // ------------------------------------------------------------ skill

    /** 4 (beginner) to 1 (master). A Lomekwian knapper starts at 3. */
    public static int level(ServerPlayer player) {
        Map<String, Integer> counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        Integer level = counters.get(LEVEL);
        if (level == null) {
            level = Skills.knows(player, Skills.Skill.LOMEKWIAN) ? 3 : 4;
            counters.put(LEVEL, level);
        }
        return level;
    }

    /** One more thing made, or thought through. Returns true if it took you up a level. */
    public static boolean practise(ServerPlayer player, int amount) {
        int level = level(player);
        if (level <= 1) {
            return false;
        }
        Map<String, Integer> counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int progress = counters.getOrDefault(PROGRESS, 0) + amount;
        if (progress < TO_ADVANCE[level]) {
            counters.put(PROGRESS, progress);
            return false;
        }
        counters.put(PROGRESS, 0);
        counters.put(LEVEL, level - 1);
        if (level - 1 <= 2) {
            EvolutionManager.forceSatisfyCriterion(player, "knapping_level_2");
        }
        player.sendSystemMessage(Component.literal("Your hands are getting surer. Knapping skill: level " + (level - 1)
                + (level - 1 == 1 ? " - as good as anyone alive." : ".")).withStyle(ChatFormatting.GOLD));
        return true;
    }

    public static String describe(ServerPlayer player) {
        int level = level(player);
        if (level <= 1) {
            return "level 1 (master)";
        }
        int progress = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().getOrDefault(PROGRESS, 0);
        return "level " + level + " (" + progress + "/" + TO_ADVANCE[level] + " tools to the next)";
    }

    // ------------------------------------------------------------ the roll

    /** How a tool comes out, for a knapper of this level working this stone. Lower is better. */
    public static int rollQuality(int level, ItemStack stone, RandomSource random) {
        boolean obsidian = stone.is(ModItems.OBSIDIAN_ROCK.get());
        boolean chert = stone.is(ModItems.CHERT_ROCK.get()) || stone.is(ModItems.CHERT_HAMMERSTONE.get());
        int tier = switch (level) {
            case 3 -> random.nextFloat() < 0.2F ? 2 : 3;
            case 2 -> random.nextFloat() < (obsidian ? 0.4F : 0.25F) ? 1 : 2;
            case 1 -> random.nextFloat() < (obsidian ? 0.45F : chert ? 0.15F : 0.0F) ? 0 : 1;
            default -> 4;
        };
        // The stone's own ceiling.
        int ceiling;
        if (stone.is(ModItems.LIMESTONE_ROCK.get())) {
            ceiling = 4;
        } else if (stone.is(ModItems.GRANITE_ROCK.get())) {
            ceiling = level <= 1 ? 2 : 3;
        } else if (chert || obsidian) {
            ceiling = 0;
        } else {
            ceiling = 3;
        }
        tier = Math.max(tier, ceiling);
        // Obsidian flakes so cleanly that even a beginner gets a strong edge out of it.
        return obsidian ? Math.min(tier, 2) : tier;
    }

    /**
     * The chance of each tier, 0 (flawless) to 4 (crude), for this level and stone - the same
     * sums {@link #rollQuality} makes, laid out for the station's odds panel.
     */
    public static double[] odds(int level, ItemStack stone) {
        boolean obsidian = stone.is(ModItems.OBSIDIAN_ROCK.get());
        boolean chert = stone.is(ModItems.CHERT_ROCK.get()) || stone.is(ModItems.CHERT_HAMMERSTONE.get());
        double[] base = new double[5];
        switch (level) {
            case 3 -> {
                base[3] = 0.8D;
                base[2] = 0.2D;
            }
            case 2 -> {
                double better = obsidian ? 0.4D : 0.25D;
                base[1] = better;
                base[2] = 1.0D - better;
            }
            case 1 -> {
                double flawless = obsidian ? 0.45D : chert ? 0.15D : 0.0D;
                base[0] = flawless;
                base[1] = 1.0D - flawless;
            }
            default -> base[4] = 1.0D;
        }
        int ceiling = stone.is(ModItems.LIMESTONE_ROCK.get()) ? 4
                : stone.is(ModItems.GRANITE_ROCK.get()) ? (level <= 1 ? 2 : 3)
                : chert || obsidian ? 0 : 3;
        double[] out = new double[5];
        for (int tier = 0; tier <= 4; tier++) {
            int result = Math.max(tier, ceiling);
            if (obsidian) {
                result = Math.min(result, 2);
            }
            out[result] += base[tier];
        }
        return out;
    }

    /**
     * One Acheulean tool made from this kind of stone, the stone already paid for: rolled,
     * handed over, and credited - checklist, skill, the flawless achievement, the milestone.
     */
    public static ItemStack make(ServerPlayer player, KnappingChoice choice, ItemStack stoneKind, BlockPos where) {
        int quality = rollQuality(level(player), stoneKind, player.getRandom());
        boolean obsidian = stoneKind.is(ModItems.OBSIDIAN_ROCK.get());
        AcheuleanToolItem tool = (AcheuleanToolItem) choice.result();
        ItemStack made = dev.hominin.evolution.item.StoneMaterial.stampFrom(tool.make(quality), stoneKind);
        String name = made.getHoverName().getString().toLowerCase();
        if (!player.getInventory().add(made.copy())) {
            player.drop(made.copy(), false);
        }
        player.level().playSound(null, where, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(Component.literal("It comes off the stone as a "
                + AcheuleanToolItem.TIER_NAMES[quality].toLowerCase() + " " + name + " (tier " + quality + ").")
                .withStyle(ChatFormatting.GOLD), true);
        EvolutionManager.incrementCriterion(player, "make_acheulean_tool", 1);
        if (quality == 0) {
            dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/last_tool");
        }
        practise(player, 1);
        if (quality <= 2 && !obsidian
                && EvolutionManager.isReadyForMilestone(player, BuiltinMilestones.FINE_ACHEULEAN)) {
            EvolutionManager.attemptMilestone(player, BuiltinMilestones.FINE_ACHEULEAN);
        }
        return made;
    }

    // ------------------------------------------------------------ who can

    public static boolean canUse(ServerPlayer player) {
        var data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.isDeveloperMode()) {
            return true;
        }
        String era = data.getStage().getPath();
        return !era.startsWith("australopithecus") && !era.equals("ardipithecus") && !era.equals("homo_habilis")
                && !era.equals("homo_rudolfensis");
    }

    /** Thinking hard over a good stone or a fine tool sometimes teaches the hands something. */
    public static void pondered(ServerPlayer player, ItemStack held) {
        if (!canUse(player) || level(player) <= 1) {
            return;
        }
        boolean worthIt = held.is(ModItems.OBSIDIAN_ROCK.get()) || held.is(ModItems.CHERT_ROCK.get())
                || (held.getItem() instanceof AcheuleanToolItem && AcheuleanToolItem.qualityOf(held) <= 2);
        if (!worthIt && !(held.getItem() instanceof AcheuleanToolItem) && !held.is(ModTags.Items.KNAPPABLE_STONE)) {
            return;
        }
        if (player.getRandom().nextFloat() < (worthIt ? 0.35F : 0.08F)) {
            player.displayClientMessage(Component.literal("You turn it over and see how the flakes came away. "
                    + "Your hands will remember."), true);
            practise(player, 1);
        }
    }

    private Acheulean() {
    }
}
