package dev.hominin.evolution.knapping;

import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.network.OpenKnappingPayload;
import dev.hominin.evolution.stage.BuiltinMilestones;
import dev.hominin.evolution.tool.ToolUse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Striking one stone with another, on purpose.
 *
 * <p>The player picks what they are aiming at and the stone decides whether they
 * got it. Two things send an attempt sideways: soft stone that shatters instead of
 * flaking, and reaching for a shaped tool before anyone has worked out what a
 * sharp edge is for. Either way the result is a Lomekwian core - a real class of
 * artefact, made 700,000 years before the Oldowan, by hands that were clearly
 * hitting rocks together without a plan for what came off.
 *
 * <p>So a botched knap is not a wasted rock. It is the oldest stone tool there is,
 * and having made one is what lets a hand attempt the multi tool with confidence.
 */
public final class Knapping {
    /** Progress toward knapping skill. Survives evolving; see {@link EvolutionManager#SKILL_PREFIX}. */
    public static final String SKILL_KNAPPING = EvolutionManager.SKILL_PREFIX + "knapping";

    /** Marker for having made a Lomekwian core, which the multi tool builds on. */
    private static final ResourceLocation LOMEKWIAN_INSIGHT =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "lomekwian_knowledge");

    private static final ResourceLocation AUSTRALOPITHECUS =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "australopithecus");

    /** Stone a multi tool takes. Chert flakes less cleanly than obsidian, so it needs more. */
    private static final int MULTITOOL_CHERT_COST = 3;
    private static final int MULTITOOL_OBSIDIAN_COST = 2;

    /** A chert hammerstone is one big nodule - it breaks down into this much stone. */
    private static final int SPLIT_CORE_YIELD = 4;

    /** Chance a multi tool shatters in the hands of someone who never made the cruder one first. */
    private static final float UNPRACTISED_MULTITOOL_FAIL = 0.4F;

    private static final List<KnappingChoice> STONE_CHOICES =
            List.of(KnappingChoice.FLAKE, KnappingChoice.CHOPPER, KnappingChoice.MULTITOOL,
                    KnappingChoice.GRINDING_STONE);
    private static final List<KnappingChoice> CORE_CHOICES =
            List.of(KnappingChoice.SPLIT_CORE, KnappingChoice.MULTITOOL);

    /**
     * What this piece of stone can be made into. A chert hammerstone is a finished
     * hammer as well as a nodule, so it offers the two things worth giving that up for.
     */
    public static List<KnappingChoice> choicesFor(ItemStack stone) {
        if (stone.is(ModItems.CHERT_HAMMERSTONE.get())) {
            return CORE_CHOICES;
        }
        return stone.is(ModTags.Items.KNAPPABLE_STONE) ? STONE_CHOICES : List.of();
    }

    /**
     * Opens the knapping screen if the hands are set up for it. Returns true when
     * the press was about knapping at all, so the caller stops looking for a
     * two-handed recipe - including when the attempt is refused, because "you are
     * holding a rock wrong" is more use than silence.
     */
    public static boolean tryOpen(ServerPlayer player, ItemStack main, ItemStack off) {
        List<KnappingChoice> choices = choicesFor(main);
        if (choices.isEmpty()) {
            return false;
        }
        if (!off.is(ModTags.Items.HAMMERSTONES)) {
            // A cobble is both knapping stock and a chopper blank, so anything else
            // in the off hand means this press was meant for a recipe instead.
            if (!off.isEmpty()) {
                return false;
            }
            player.displayClientMessage(Component.literal(
                    "You need something to strike with in your off hand."), true);
            return true;
        }
        PacketDistributor.sendToPlayer(player, new OpenKnappingPayload(
                main.getHoverName().getString(), isGoodStone(main),
                choices.stream().map(KnappingChoice::ordinal).toList()));
        return true;
    }

    /** Chert, quartzite and obsidian fracture predictably. Limestone does not. */
    private static boolean isGoodStone(ItemStack stack) {
        return !stack.is(ModItems.LIMESTONE_ROCK.get());
    }

    /**
     * Resolves one strike. The hands are re-checked here rather than trusted from
     * the screen, because the client could have sent anything - including a choice
     * the stone in hand was never offered.
     */
    public static void resolve(ServerPlayer player, @Nullable KnappingChoice choice) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (choice == null || !off.is(ModTags.Items.HAMMERSTONES) || !choicesFor(main).contains(choice)) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);

        if (choice == KnappingChoice.SPLIT_CORE) {
            splitCore(player, main);
            return;
        }
        // Before the flake exists as an idea, nothing shaped comes out of a rock.
        // Aiming past it is the mistake that produces the Lomekwian core.
        boolean overreaching = choice != KnappingChoice.FLAKE
                && AUSTRALOPITHECUS.equals(data.getStage())
                && !data.isDeveloperMode();

        if (choice == KnappingChoice.MULTITOOL && !overreaching) {
            makeMultitool(player, data, main);
            return;
        }

        // Soft stone ruins an edge but makes a fine grinding face - limestone and
        // sandstone were the abrasives of choice for exactly that reason.
        boolean good = isGoodStone(main) || choice == KnappingChoice.GRINDING_STONE;
        strike(player, main);
        if (!good || overreaching) {
            produceLomekwian(player, data, !good);
            return;
        }
        if (choice == KnappingChoice.FLAKE
                && EvolutionManager.isReadyForMilestone(player, BuiltinMilestones.STRIKE_FLAKE)) {
            // The milestone handler hands over the flake itself.
            EvolutionManager.attemptMilestone(player, BuiltinMilestones.STRIKE_FLAKE);
            ToolUse.creditOldowanTool(player, ModItems.FLAKE.get());
            return;
        }
        give(player, new ItemStack(choice.result()));
        ToolUse.creditOldowanTool(player, choice.result());
        player.displayClientMessage(Component.literal("The stone comes away clean."), true);
    }

    /** Uses one piece of stone and one use of the hammer. */
    private static void strike(ServerPlayer player, ItemStack main) {
        strike(player, main, 1);
    }

    private static void strike(ServerPlayer player, ItemStack main, int stoneUsed) {
        main.shrink(stoneUsed);
        ToolUse.wear(player, InteractionHand.OFF_HAND);
        player.level().playSound(null, player.blockPosition(), SoundEvents.STONE_BREAK,
                SoundSource.PLAYERS, 0.9F, 1.0F);
    }

    /**
     * The advanced piece. It needs good stone and plenty of it, and a hand that has
     * never made the crude version first misjudges it often enough to matter - the
     * Lomekwian core is how you learn where not to strike.
     */
    private static void makeMultitool(ServerPlayer player, PlayerEvolutionData data, ItemStack main) {
        int cost = multitoolCost(main);
        if (cost == 0) {
            player.displayClientMessage(Component.literal(
                    "This stone will not take an edge like that. It needs chert or obsidian."), true);
            return;
        }
        if (main.getCount() < cost) {
            player.displayClientMessage(Component.literal(
                    "Not enough stone to work - you need " + cost + " in hand."), true);
            return;
        }
        strike(player, main, cost);

        boolean practised = data.isDeveloperMode() || data.getUnlockedRecipes().contains(LOMEKWIAN_INSIGHT);
        if (!practised && player.getRandom().nextFloat() < UNPRACTISED_MULTITOOL_FAIL) {
            player.displayClientMessage(Component.literal(
                    "It shatters under the blow. You struck it like you knew how, and you did not."), true);
            return;
        }
        give(player, new ItemStack(ModItems.OLDOWAN_MULTITOOL.get()));
        ToolUse.creditOldowanTool(player, ModItems.OLDOWAN_MULTITOOL.get());
        player.displayClientMessage(Component.literal(
                "Flake by flake it comes: an edge to cut, a weight to strike, a spine to chop."), true);
    }

    /** Zero means this stone cannot become a multi tool at all. */
    private static int multitoolCost(ItemStack stone) {
        if (stone.is(ModItems.CHERT_HAMMERSTONE.get())) {
            return 1;
        }
        if (stone.is(ModItems.OBSIDIAN_ROCK.get())) {
            return MULTITOOL_OBSIDIAN_COST;
        }
        return stone.is(ModItems.CHERT_ROCK.get()) ? MULTITOOL_CHERT_COST : 0;
    }

    private static void splitCore(ServerPlayer player, ItemStack main) {
        strike(player, main);
        give(player, new ItemStack(ModItems.CHERT_ROCK.get(), SPLIT_CORE_YIELD));
        player.displayClientMessage(Component.literal(
                "The nodule breaks along its bedding into clean pieces of chert."), true);
    }

    private static void produceLomekwian(ServerPlayer player, PlayerEvolutionData data, boolean softStone) {
        give(player, new ItemStack(ModItems.LOMEKWIAN_TOOL.get()));
        EvolutionManager.incrementCriterion(player, SKILL_KNAPPING, 1);

        player.displayClientMessage(Component.literal(softStone
                ? "The stone crumbles instead of splitting - but the piece in your hand has an edge on it."
                : "It does not come away as you meant it to - but the piece in your hand has an edge on it."),
                true);
        if (data.getUnlockedRecipes().add(LOMEKWIAN_INSIGHT)) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.4F, 1.3F);
            player.sendSystemMessage(Component.literal(
                    "A mistake, and still a tool. Your hands have learned something from it.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private Knapping() {
    }
}
