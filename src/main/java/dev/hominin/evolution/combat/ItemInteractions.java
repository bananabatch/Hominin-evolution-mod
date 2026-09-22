package dev.hominin.evolution.combat;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.food.MeatSplitting;
import dev.hominin.evolution.knapping.Knapping;
import dev.hominin.evolution.tool.Grinding;
import dev.hominin.evolution.tool.ToolUse;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Two-handed work: hold the piece in one hand, the tool in the other, and press
 * the interact key.
 *
 * <p>Everything a hominin makes is made this way rather than on a crafting grid -
 * there is no workbench in the Lower Palaeolithic, and putting the recipes on the
 * hands keeps the stages honest, because each one needs a specific pairing the
 * player has to have found first.
 *
 * <p>Holding the right pairing is not enough on its own. Every recipe here has to
 * be worked out first, by {@link dev.hominin.evolution.mind.Thinking}; until then
 * the hands go through the motions and sometimes ruin the material. That is the
 * point of the mechanic - the insight is the invention, and the invention is the
 * part that actually took a hundred thousand years.
 */
public final class ItemInteractions {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, path);
    }

    private static final ResourceLocation ARDIPITHECUS = id("ardipithecus");
    private static final ResourceLocation AUSTRALOPITHECUS = id("australopithecus");
    private static final ResourceLocation HABILIS = id("homo_habilis");
    private static final ResourceLocation ERECTUS = id("homo_erectus");

    /** Chance a blind attempt ruins something, and which hand pays when it does. */
    private static final float FUMBLE_CHANCE = 0.2F;
    private static final float FUMBLE_HAND_SPLIT = 0.5F;

    /**
     * One recipe: what goes in each hand, what comes out, the earliest stage that
     * can do it, and whether it has to be done beside a fire. The id doubles as
     * the key for whether the player has had the idea yet.
     */
    public record HandRecipe(ResourceLocation id, Supplier<Item> mainHand, @Nullable Supplier<Item> offHand,
            Supplier<Item> result, List<ResourceLocation> stages, boolean needsFire, boolean consumesOffHand,
            boolean needsThought, String message, String insight) {
    }

    private static final int FIRE_RADIUS = 3;

    private static final List<HandRecipe> RECIPES = List.of(
            // Every stage: a nest is older than any of them. Great apes build one every night.
            new HandRecipe(id("nest"), ModItems.NESTING_MATERIAL, ModItems.NESTING_MATERIAL, ModItems.NEST,
                    List.of(ARDIPITHECUS, AUSTRALOPITHECUS, HABILIS, ERECTUS), false, true, false,
                    "You bend and weave the twigs into a rough nest.",
                    ""),
            // Habilis: finishing a gnawed point with a flake. No idea needed - it is
            // the same job the teeth were already doing, with a better tool.
            new HandRecipe(id("pointy_stick"), ModItems.SHARPENED_STICK, ModItems.FLAKE, ModItems.POINTY_STICK,
                    List.of(HABILIS, ERECTUS), false, false, false,
                    "You pare the point down with the flake until it is fine and even.",
                    ""),
            // A plain stick works just as well: the flake does both jobs at once.
            new HandRecipe(id("pointy_stick"), () -> net.minecraft.world.item.Items.STICK, ModItems.FLAKE,
                    ModItems.POINTY_STICK, List.of(HABILIS, ERECTUS), false, false, false,
                    "You whittle the stick to a fine, even point with the flake.",
                    ""),
            // Habilis: a flake is sharp enough to whittle a branch to a point.
            new HandRecipe(id("sharpened_spear"), ModItems.LONG_BRANCH, ModItems.FLAKE, ModItems.SHARPENED_SPEAR,
                    List.of(HABILIS, ERECTUS), false, false, true,
                    "You work the branch to a point with the flake.",
                    "A point. The branch wants to be a point, and the flake is what cuts it."),
            // Habilis: a flake against a cobble trims it down to a working edge. This
            // and the digging stick are what give habilis three separate things to
            // work out, which is what the stage asks for.
            new HandRecipe(id("chopper"), ModItems.ROCK, ModItems.FLAKE, ModItems.CHOPPER,
                    List.of(HABILIS, ERECTUS), false, false, true,
                    "You trim the cobble down until one side will cut.",
                    "The edge does not have to be thin. It has to be an edge."),
            // Habilis: a chopper is the first tool that can shape another tool.
            new HandRecipe(id("digging_stick"), ModItems.LONG_BRANCH, ModItems.CHOPPER, ModItems.DIGGING_STICK,
                    List.of(HABILIS, ERECTUS), false, false, true,
                    "You hack the branch down to a blunt, strong point.",
                    "Not everything worth eating is above the ground."),
            // Erectus: beating the end of a branch with a stone leaves the weight at one end.
            new HandRecipe(id("wooden_club"), ModItems.LONG_BRANCH, ModItems.ROCK, ModItems.WOODEN_CLUB,
                    List.of(ERECTUS), false, false, true,
                    "You batter the end of the branch until it carries its own weight.",
                    "Weight at the far end. It would land harder if it were heavier where it lands."),
            // Erectus: grass twisted against itself until it holds. Two hands and no
            // tool at all - the material is the idea.
            new HandRecipe(id("twine"), ModItems.THATCH, ModItems.THATCH, ModItems.TWINE,
                    List.of(ERECTUS), false, true, true,
                    "You twist the stems against each other until they bind into cord.",
                    "Twisted the other way, it holds itself together. That will tie anything."),
            // Habilis: the drill. Spinning one stick against another is the only way to
            // fire that does not need a fire to already exist, and working it out is
            // what lets a habilis stop waiting for lightning.
            new HandRecipe(id("fire_drill"), () -> net.minecraft.world.item.Items.STICK,
                    () -> net.minecraft.world.item.Items.STICK, ModItems.FIRE_DRILL,
                    List.of(HABILIS, ERECTUS), false, true, true,
                    "You spin one stick against the other until the dust smoulders.",
                    "Rubbing it makes it hot. Rub it hard enough, for long enough, and hot becomes fire."),
            // Erectus: turning the point in a fire case-hardens the wood. Needs a free
            // hand rather than a second ingredient - the fire is the other half.
            new HandRecipe(id("fire_hardened_spear"), ModItems.SHARPENED_SPEAR, null, ModItems.FIRE_HARDENED_SPEAR,
                    List.of(ERECTUS), true, false, true,
                    "You turn the point in the embers until the wood darkens and hardens.",
                    "Fire does something to wood short of burning it. The point could be harder."));

    /**
     * A null requirement means the hand must be empty. A recipe naming a rock, a
     * flake or a chopper means the role, not that exact item - any cobble batters a
     * branch, and a multi tool cuts as well as a flake does.
     */
    private static boolean matches(ItemStack stack, @Nullable Supplier<Item> wanted) {
        if (wanted == null) {
            return stack.isEmpty();
        }
        TagKey<Item> role = roleOf(wanted);
        return role != null ? stack.is(role) : stack.is(wanted.get());
    }

    @Nullable
    private static TagKey<Item> roleOf(Supplier<Item> wanted) {
        if (wanted == ModItems.ROCK) {
            return ModTags.Items.ROCKS;
        }
        if (wanted == ModItems.FLAKE) {
            return ModTags.Items.FLAKES;
        }
        return wanted == ModItems.CHOPPER ? ModTags.Items.CHOPPERS : null;
    }

    /** The recipe the player's hands currently describe, if any. */
    @Nullable
    public static HandRecipe match(ItemStack main, ItemStack off) {
        for (HandRecipe recipe : RECIPES) {
            if (matches(main, recipe.mainHand()) && matches(off, recipe.offHand())) {
                return recipe;
            }
        }
        return null;
    }

    public static boolean isAvailable(PlayerEvolutionData data, HandRecipe recipe) {
        return data.isDeveloperMode() || recipe.stages().contains(data.getStage());
    }

    public static boolean isKnown(PlayerEvolutionData data, HandRecipe recipe) {
        return data.isDeveloperMode() || data.getUnlockedRecipes().contains(recipe.id());
    }

    public static void interact(ServerPlayer player) {
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);

        // Knapping is checked first: a rock under a hammerstone is its own job and
        // never collides with a recipe, none of which take a bare stone in hand.
        if (Knapping.tryOpen(player, main, off)) {
            return;
        }
        if (MeatSplitting.trySplit(player, main, off)) {
            return;
        }
        if (Grinding.tryGrind(player, main, off)) {
            return;
        }

        if (tryStation(player, main, off) || tryWorkStation(player, main, off)) {
            return;
        }
        HandRecipe recipe = match(main, off);
        if (recipe == null) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (!isAvailable(data, recipe)) {
            player.displayClientMessage(
                    Component.literal("You turn it over in your hands, but nothing comes of it yet."), true);
            return;
        }
        if (recipe.needsThought() && !isKnown(data, recipe)) {
            fumble(player, main, off);
            return;
        }
        if (recipe.needsFire() && !nearFire(player)) {
            player.displayClientMessage(Component.literal("This needs a fire."), true);
            return;
        }
        main.shrink(1);
        if (recipe.consumesOffHand()) {
            off.shrink(1);
        } else {
            // The tool that did the work pays for it; a plain rock or flake does not wear.
            ToolUse.wear(player, InteractionHand.OFF_HAND);
        }
        ItemStack result = new ItemStack(recipe.result().get());
        ToolUse.creditOldowanTool(player, result.getItem());
        // Working out the drill is itself the discovery of fire. It is the one route
        // that needs no lightning and no lava - which is the point of knowing it.
        if (result.is(ModItems.FIRE_DRILL.get())) {
            dev.hominin.evolution.EvolutionManager.incrementCriterion(
                    player, "notice_fire_source", 1);
        }
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK,
                SoundSource.PLAYERS, 0.7F, 1.1F);
        player.displayClientMessage(Component.literal(recipe.message()), true);
    }

    /**
     * The knapping station: four sticks lashed into a frame, and a hide stretched over it for
     * a mat. Erectus and later - it is the first thing made to make other things with.
     */
    private static boolean tryStation(ServerPlayer player, ItemStack main, ItemStack off) {
        if (!main.is(net.minecraft.world.item.Items.STICK) || !off.is(ModItems.HIDE.get())) {
            return false;
        }
        if (!dev.hominin.evolution.knapping.Acheulean.canUse(player)) {
            player.displayClientMessage(Component.literal("You turn it over in your hands, but nothing comes of it yet."),
                    true);
            return true;
        }
        if (main.getCount() < 4) {
            player.displayClientMessage(Component.literal("A frame for the mat takes four sticks."), true);
            return true;
        }
        main.shrink(4);
        off.shrink(1);
        ItemStack station = new ItemStack(ModItems.KNAPPING_STATION.get());
        if (!player.getInventory().add(station)) {
            player.drop(station, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.9F, 0.8F);
        player.displayClientMessage(Component.literal("You lash the frame and stretch the hide across it. "
                + "Somewhere to sit and work stone properly."), true);
        return true;
    }

    /**
     * The primitive work station: a branch driven into a base of rocks, hide lashed round it.
     * Two hide in the main hand, four rocks of any kind in the off hand.
     */
    private static boolean tryWorkStation(ServerPlayer player, ItemStack main, ItemStack off) {
        if (!main.is(ModItems.HIDE.get()) || !(off.is(ModTags.Items.ROCKS) || off.is(ModTags.Items.KNAPPABLE_STONE))) {
            return false;
        }
        if (!dev.hominin.evolution.knapping.Acheulean.canUse(player)) {
            player.displayClientMessage(Component.literal("You turn it over in your hands, but nothing comes of it yet."),
                    true);
            return true;
        }
        if (main.getCount() < 2 || off.getCount() < 4) {
            player.displayClientMessage(Component.literal("A work station takes two hide and four rocks of any kind."),
                    true);
            return true;
        }
        main.shrink(2);
        off.shrink(4);
        ItemStack station = new ItemStack(ModItems.WORK_STATION.get());
        if (!player.getInventory().add(station)) {
            player.drop(station, false);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.9F, 0.8F);
        player.displayClientMessage(Component.literal("You drive a branch into a base of rocks and lash hide round "
                + "it. Somewhere to work more than stone now."), true);
        return true;
    }

    /**
     * Working material you have not worked out yet. Usually nothing happens; now
     * and then you destroy it, because that is what happens when the hands move
     * ahead of the idea behind them.
     */
    private static void fumble(ServerPlayer player, ItemStack main, ItemStack off) {
        if (player.getRandom().nextFloat() >= FUMBLE_CHANCE) {
            player.displayClientMessage(
                    Component.literal("You aren't sure what to do with what you're holding yet."), true);
            return;
        }
        boolean ruinMain = off.isEmpty() || player.getRandom().nextFloat() < FUMBLE_HAND_SPLIT;
        (ruinMain ? main : off).shrink(1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK,
                SoundSource.PLAYERS, 0.9F, 0.7F);
        player.displayClientMessage(Component.literal("You try to do something, but break it."), true);
    }

    private static boolean nearFire(ServerPlayer player) {
        Level level = player.level();
        BlockPos origin = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-FIRE_RADIUS, -2, -FIRE_RADIUS),
                origin.offset(FIRE_RADIUS, 2, FIRE_RADIUS))) {
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                    || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
                return true;
            }
        }
        return false;
    }

    private ItemInteractions() {
    }
}
