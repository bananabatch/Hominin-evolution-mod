package dev.hominin.evolution.knapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.item.AcheuleanToolItem;
import dev.hominin.evolution.mind.Skills;
import dev.hominin.evolution.network.OpenKnappingPayload;
import dev.hominin.evolution.stage.BuiltinMilestones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Acheulean industry: hand axes, cleavers and the Acheulean multitool, made at a
 * knapping station from erectus on.
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
    /** How close the station must be for a strike chosen on the screen to still count. */
    private static final double STATION_REACH = 6.0D;
    public static final int STONE_COST = 2;
    public static final int MULTITOOL_STONE_COST = 4;

    private static final Map<UUID, BlockPos> sessions = new HashMap<>();

    public static final List<KnappingChoice> CHOICES =
            List.of(KnappingChoice.HAND_AXE, KnappingChoice.CLEAVER, KnappingChoice.ACHEULEAN_MULTITOOL);

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

    // ------------------------------------------------------------ the station

    private static boolean hasSoftHammer(Player player) {
        return player.getInventory().contains(s -> s.is(ModItems.LONG_BONE.get()) || s.is(Items.BONE));
    }

    private static boolean hasHammerstone(Player player) {
        return player.getInventory().contains(s -> s.is(ModTags.Items.HAMMERSTONES));
    }

    public static boolean canUse(ServerPlayer player) {
        var data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.isDeveloperMode()) {
            return true;
        }
        String era = data.getStage().getPath();
        return !era.startsWith("australopithecus") && !era.equals("ardipithecus") && !era.equals("homo_habilis")
                && !era.equals("homo_rudolfensis");
    }

    /** Sitting down at the station with a stone in hand. */
    public static void open(ServerPlayer player, BlockPos station) {
        if (!canUse(player)) {
            player.displayClientMessage(Component.literal("You do not know what this is for yet. (Homo erectus.)"), true);
            return;
        }
        ItemStack stone = player.getMainHandItem();
        if (!stone.is(ModTags.Items.KNAPPABLE_STONE)) {
            player.displayClientMessage(Component.literal("Hold the stone you mean to work - two of them, at least."),
                    true);
            return;
        }
        if (!hasHammerstone(player) || !hasSoftHammer(player)) {
            player.displayClientMessage(Component.literal("You need a hammerstone for the rough shaping and a bone "
                    + "to take the fine flakes off. Carry both."), true);
            return;
        }
        sessions.put(player.getUUID(), station.immutable());
        PacketDistributor.sendToPlayer(player, new OpenKnappingPayload(stone.getHoverName().getString(),
                !stone.is(ModItems.LIMESTONE_ROCK.get()), CHOICES.stream().map(KnappingChoice::ordinal).toList()));
    }

    /** The strike chosen on the screen. Everything is checked again: the client could have sent anything. */
    public static void resolve(ServerPlayer player, KnappingChoice choice) {
        BlockPos station = sessions.get(player.getUUID());
        if (station == null || !isAcheulean(choice) || !canUse(player)
                || !player.level().getBlockState(station).is(dev.hominin.evolution.ModBlocks.KNAPPING_STATION.get())
                || player.distanceToSqr(station.getX() + 0.5D, station.getY() + 0.5D, station.getZ() + 0.5D)
                        > STATION_REACH * STATION_REACH) {
            return;
        }
        ItemStack stone = player.getItemInHand(InteractionHand.MAIN_HAND);
        int cost = choice == KnappingChoice.ACHEULEAN_MULTITOOL ? MULTITOOL_STONE_COST : STONE_COST;
        if (!stone.is(ModTags.Items.KNAPPABLE_STONE) || !hasHammerstone(player) || !hasSoftHammer(player)) {
            return;
        }
        if (stone.getCount() < cost) {
            player.displayClientMessage(Component.literal("That takes " + cost + " of this stone, in hand."), true);
            return;
        }
        int quality = rollQuality(level(player), stone, player.getRandom());
        boolean obsidian = stone.is(ModItems.OBSIDIAN_ROCK.get());
        if (!player.getAbilities().instabuild) {
            stone.shrink(cost);
        }
        AcheuleanToolItem tool = (AcheuleanToolItem) choice.result();
        ItemStack made = tool.make(quality);
        String name = made.getHoverName().getString().toLowerCase();
        if (!player.getInventory().add(made)) {
            player.drop(made, false);
        }
        player.level().playSound(null, station, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.displayClientMessage(Component.literal("It comes off the stone as a "
                + AcheuleanToolItem.TIER_NAMES[quality].toLowerCase() + " " + name + " (tier " + quality + ").")
                .withStyle(ChatFormatting.GOLD), true);
        EvolutionManager.incrementCriterion(player, "make_acheulean_tool", 1);
        if (quality == 0) {
            dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/last_tool");
        }
        practise(player, 1);
        // Heidelbergensis: the hands are good enough when the stone is ordinary and the tool is not.
        if (quality <= 2 && !obsidian
                && EvolutionManager.isReadyForMilestone(player, BuiltinMilestones.FINE_ACHEULEAN)) {
            EvolutionManager.attemptMilestone(player, BuiltinMilestones.FINE_ACHEULEAN);
        }
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

    public static void forget(UUID player) {
        sessions.remove(player);
    }

    private Acheulean() {
    }
}
