package dev.hominin.evolution.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.mind.Skills;
import dev.hominin.evolution.stage.GateCriterion;
import dev.hominin.evolution.stage.Lineage;
import dev.hominin.evolution.stage.StageDefinition;
import dev.hominin.evolution.stage.StageRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The great weapons. Which one a people comes to depends on which way they went: north, towards the Neanderthals,
 * the Schoningen spear - a long shaft hardened and balanced end to end, a throwing spear that brought down horses;
 * staying in Africa, towards us, the stone-tipped spear - a Levallois blade hafted on the end of a shaft, the Kathu
 * Pan points.
 *
 * <p>Neither comes out of nowhere. It has to be seen before it can be made - hold K with a shaft (or, for the stone
 * tip, a blade) in hand - and it is only seen by a people far enough along: two hard requirements and three tasks
 * done, and two nights survived. Knowing how once ({@link Skills.Skill#SUPER_WEAPONS}) makes it come a hard
 * requirement and a task sooner, in whatever you become; and a band that has been shown makes them too.
 */
public final class SuperWeapons {
    public static final ResourceLocation SCHONINGEN = id("schoningen_spear");
    public static final ResourceLocation STONE_TIPPED = id("stone_tipped_spear");

    private static final int HARD = 2;
    private static final int TASKS = 3;
    private static final int NIGHTS = 2;
    private static final String MADE = "super_weapons_made";

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, path);
    }

    /** The great weapon of this path, or null before the line splits. */
    @Nullable
    public static ResourceLocation weaponFor(int lineage) {
        return lineage == Lineage.NEANDERTHAL ? SCHONINGEN : lineage == Lineage.SAPIENS ? STONE_TIPPED : null;
    }

    public static boolean knows(ServerPlayer player, ResourceLocation weapon) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getUnlockedRecipes().contains(weapon);
    }

    public static boolean isSuperWeapon(ItemStack stack) {
        return stack.is(ModItems.SCHONINGEN_SPEAR.get()) || stack.is(ModItems.STONE_TIPPED_SPEAR.get());
    }

    /** What still stands between this people and seeing it - empty when nothing does. */
    public static String missing(ServerPlayer player) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.current(data);
        if (stage == null) {
            return "your people are not ready";
        }
        boolean skilled = Skills.knows(player, Skills.Skill.SUPER_WEAPONS);
        int hardNeeded = HARD - (skilled ? 1 : 0);
        int tasksNeeded = TASKS - (skilled ? 1 : 0);
        int hard = 0;
        for (GateCriterion criterion : stage.gate().required()) {
            if (!criterion.id().equals("super_weapon") && EvolutionManager.isCriterionSatisfied(data, criterion)) {
                hard++;
            }
        }
        int tasks = EvolutionManager.optionalSatisfiedCount(data, stage);
        int nights = data.getCriterionCounters().getOrDefault("survive_days", 0);
        List<String> parts = new ArrayList<>();
        if (hard < hardNeeded) {
            parts.add((hardNeeded - hard) + " more hard requirement" + (hardNeeded - hard == 1 ? "" : "s"));
        }
        if (tasks < tasksNeeded) {
            parts.add((tasksNeeded - tasks) + " more task" + (tasksNeeded - tasks == 1 ? "" : "s"));
        }
        if (nights < NIGHTS) {
            parts.add((NIGHTS - nights) + " more night" + (NIGHTS - nights == 1 ? "" : "s") + " survived");
        }
        return String.join(", ", parts);
    }

    /**
     * Holding K over a shaft or a blade. Returns 0 if this has nothing to do with it, 1 if it only told you
     * something (the thought is not spent), and 2 if the weapon was seen - the thought is spent.
     */
    public static int think(ServerPlayer player) {
        ResourceLocation weapon = weaponFor(Lineage.of(player));
        if (weapon == null) {
            return 0;
        }
        boolean shaft = holds(player, ModItems.WORKABLE_SHAFT.get());
        boolean blade = holds(player, ModItems.LEVALLOIS_BLADE.get());
        if (weapon == SCHONINGEN ? !shaft : !shaft && !blade) {
            return 0;
        }
        if (knows(player, weapon)) {
            player.displayClientMessage(Component.literal(weapon == SCHONINGEN
                    ? "You already know what this shaft could be. Harden the whole of it in a fire."
                    : "You already know how the blade goes on the shaft. The work station, and five twine.")
                    .withStyle(ChatFormatting.GRAY), true);
            return 1;
        }
        String missing = missing(player);
        if (!missing.isEmpty()) {
            player.displayClientMessage(Component.literal("Something is there, but it will not come. Not yet: "
                    + missing + ".").withStyle(ChatFormatting.GRAY), true);
            return 1;
        }
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getUnlockedRecipes().add(weapon);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F,
                1.2F);
        if (weapon == SCHONINGEN) {
            player.sendSystemMessage(Component.literal("You feel like you may be able to make this better.")
                    .withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.literal("Not a point on the end of it - the whole shaft, hardened and "
                    + "balanced, heaviest a third of the way along, so it flies true. Turn the whole length of it in a "
                    + "fire: right-click a fire with it four times, or hold it there.").withStyle(ChatFormatting.GRAY));
        } else {
            player.sendSystemMessage(Component.literal("You see it: the blade on the end of the shaft, bound so tight "
                    + "it is one thing.").withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.literal("At the work station: the Levallois blade above the shaft in "
                    + "the middle, and five twine in the tool slot.").withStyle(ChatFormatting.GRAY));
        }
        return 2;
    }

    private static boolean holds(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).is(item) || player.getItemInHand(InteractionHand.OFF_HAND).is(item);
    }

    /** One made: the checklist, the skill - which the band sees - and, the first time, the feast it deserves. */
    public static void made(ServerPlayer player, ItemStack weapon) {
        EvolutionManager.incrementCriterion(player, "super_weapon", 1);
        // Every one made is one shown to whoever is watching.
        boolean first = !Skills.knows(player, Skills.Skill.SUPER_WEAPONS);
        Skills.learn(player, Skills.Skill.SUPER_WEAPONS);
        Map<String, Integer> counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int made = counters.merge(MADE, 1, Integer::sum);
        String name = weapon.getHoverName().getString();
        player.sendSystemMessage(Component.literal(made == 1
                ? "A " + name.toLowerCase() + ". Nothing your people have ever held hits like this."
                : "Another " + name.toLowerCase() + ".").withStyle(ChatFormatting.GOLD));
        if (made == 1) {
            dev.hominin.evolution.band.Feast.event(player, "the first " + name.toLowerCase());
            dev.hominin.evolution.band.Chatter.news(player, "news_super_weapon", name.toLowerCase());
        }
        if (first) {
            dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/super_weapon");
        }
    }

    private SuperWeapons() {
    }
}
