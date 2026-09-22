package dev.hominin.evolution.event;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.WildBands;
import dev.hominin.evolution.climb.Climbing;
import dev.hominin.evolution.climb.ClimbingServer;
import dev.hominin.evolution.combat.HeadTraumaHandler;
import dev.hominin.evolution.combat.ThreatDisplay;
import dev.hominin.evolution.combat.WoundHandler;
import dev.hominin.evolution.stage.ChecklistTracker;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.mind.Thinking;
import dev.hominin.evolution.tool.ToolUse;
import dev.hominin.evolution.stage.Arrival;
import dev.hominin.evolution.stage.BuiltinMilestones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class EvolutionEventHandler {
    private static final int DAY_CHECK_INTERVAL_TICKS = 200;
    private static final long TICKS_PER_DAY = 24000L;

    /** Every repeated-click action in the mod shares this rhythm so they all feel the same. */
    private static final int CLICKS_REQUIRED = 3;
    private static final long CLICK_COOLDOWN_TICKS = 8L;
    private static final long PROGRESS_TIMEOUT_TICKS = 100L;
    private static final long NO_COOLDOWN = 0L;
    /** {@link #advance} result: the player is still locked out from the last completed action. */
    private static final int ON_COOLDOWN = -1;
    /** {@link #advance} result: this click landed too soon after the previous one. */
    private static final int TOO_SOON = 0;

    private static final float FORAGE_SUCCESS_CHANCE = 0.35F;
    /**
     * Poking through soil and leaf litter with a point turns up far more than bare
     * hands. It has to be the sharpened stick rather than a plain one: an unworked
     * stick is the thing you whittle, and having both jobs on the same item meant
     * a forage was quietly sharpening the stick it was being done with.
     */
    private static final float FORAGE_SUCCESS_CHANCE_WITH_STICK = 0.6F;
    /** A digging stick is the real tool for this, and brings up more than one thing at a time. */
    private static final float FORAGE_SUCCESS_CHANCE_DIGGING = 0.75F;
    /** From habilis, meat and marrow are the diet; the ground is a poorer fallback. */
    private static final float HABILIS_FORAGE_MULTIPLIER = 0.6F;
    /** Breathing room after a forage resolves, so you cannot strip a patch by holding right-click. */
    private static final long FORAGE_COOLDOWN_TICKS = 60L;
    private static final long KNAP_COOLDOWN_TICKS = 40L;
    /** How often striking plain stone, rather than a deposit, turns up nothing usable. */
    private static final float PLAIN_STONE_EMPTY_CHANCE = 0.4F;

    /** A club to the head buys you five seconds to get away, or to hit it again. */

    /** Knock count at which the strike becomes a warning rather than just noise. */
    private static final int KNOCKS_TO_WARN = 2;
    /** Knocks needed before the warning turns into a call for the band. */
    private static final int KNOCKS_TO_CALL = 4;
    private static final double KNOCK_SCARE_RADIUS = 12.0D;
    private static final float KNOCK_SCARE_CHANCE = 0.6F;

    /** How long a frightened animal keeps running, at least. */
    private static final int STARTLE_STUN_TICKS = 120;

    private static final float LONG_BONE_DROP_CHANCE = 0.1F;
    /** Only animals with real limb bones in them; chickens and rabbits have nothing worth cracking. */
    private static final float LONG_BONE_MIN_HEIGHT = 1.0F;

    private static final Map<UUID, Progress> knapProgress = new HashMap<>();
    private static final Map<UUID, Progress> forageProgress = new HashMap<>();
    private static final Map<UUID, Progress> knockProgress = new HashMap<>();

    /**
     * A repeated-click action in flight. {@code pos} is null when the action is not
     * tied to a block; {@code blockedUntil} is the game time before which no new
     * click counts, used to hold the player off after an action resolves.
     */
    private record Progress(int count, long lastTick, BlockPos pos, long blockedUntil) {
    }

    private EvolutionEventHandler() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Banging a branch works against any block, so it is checked before the
        // block-specific interactions below. It has to be an upright face though -
        // you hammer a branch against a trunk, not down into the dirt.
        if (event.getItemStack().is(ModItems.LONG_BRANCH.get())) {
            if (event.getFace() != null && event.getFace().getAxis().isHorizontal()) {
                knockForBand(player, event.getLevel(), event.getPos());
            }
            return;
        }
        // The drill works anywhere there is dry ground to work against, so like the
        // branch it is checked before any block's own interaction.
        if (event.getItemStack().is(ModItems.FIRE_DRILL.get())) {
            if (dev.hominin.evolution.survival.Hearths.use(player, event.getPos(), event.getItemStack())) {
                event.setCanceled(true);
                return;
            }
            workTheDrill(player, event.getLevel(), event.getPos(), event.getFace());
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        // A hearth takes fuel: sticks, branches, grass, logs.
        if (state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                && dev.hominin.evolution.survival.Hearths.use(player, event.getPos(), event.getItemStack())) {
            event.setCanceled(true);
            return;
        }
        // Fishing comes before the block's own interaction: the stick is what makes
        // it termite fishing rather than whatever else the block would have done.
        if (state.is(ModTags.Blocks.TERMITE_SOURCE) && event.getItemStack().is(Items.STICK)) {
            fishForTermites(player, event.getLevel(), event.getPos(), event.getItemStack());
            return;
        }
        if (state.is(ModTags.Blocks.WORKABLE_STONE_DEPOSIT)) {
            // The deposit is only a quarry now. Striking a flake happens in the
            // knapping screen, on a rock you are holding, which is the one place
            // the player actually chooses what they are trying to make.
            if (player.isShiftKeyDown()) {
                EvolutionManager.incrementCriterion(player, "notice_stone_deposit", 1);
            } else {
                knapRock(player, event.getLevel(), event.getPos());
            }
        } else if (state.is(ModTags.Blocks.FORAGING_GROUND)) {
            // Sneaking is required so that ordinary right-clicks - placing a block,
            // eating, cracking a bone - never turn into an accidental forage.
            if (player.isShiftKeyDown()) {
                forage(player, event.getLevel(), event.getPos());
            }
        } else if (state.getFluidState().is(FluidTags.WATER)) {
            drinkWater(player, event.getPos());
        } else if (isFireSource(event.getLevel(), event.getPos())) {
            if (player.isShiftKeyDown()) {
                EvolutionManager.incrementCriterion(player, "notice_fire_source", 1);
                player.displayClientMessage(Component.literal("You take note of the flame, careful not to touch it."), true);
            } else if (EvolutionManager.isReadyForMilestone(player, BuiltinMilestones.FIRE_TRANSFER)) {
                EvolutionManager.attemptMilestone(player, BuiltinMilestones.FIRE_TRANSFER);
            }
        }
    }


    /**
     * Making fire rather than finding it.
     *
     * <p>A fire you lit yourself counts for the milestone exactly as a fire you carried
     * off a lightning strike does - it is the same flame, and the point was always that
     * you can keep it. The drill is spent doing it: it is a worn spindle and a charred
     * board afterwards, and the next one is another two sticks.
     */
    private static void workTheDrill(ServerPlayer player, Level level, BlockPos pos, @Nullable Direction face) {
        BlockPos above = face != null ? pos.relative(face) : pos.above();
        if (!level.getBlockState(above).canBeReplaced() || !level.getBlockState(pos).isSolid()) {
            player.displayClientMessage(Component.literal(
                    "You need dry ground under it and room above it."), true);
            return;
        }
        if (level.isRainingAt(above)) {
            player.displayClientMessage(Component.literal(
                    "The dust will not catch in this wet. Get under something."), true);
            return;
        }
        // From erectus, fire is kept: the drill lights a hearth that burns as long as it is fed.
        boolean hearth = dev.hominin.evolution.knapping.Acheulean.canUse(player) && level instanceof net.minecraft.server.level.ServerLevel;
        if (hearth) {
            dev.hominin.evolution.survival.Hearths.light((net.minecraft.server.level.ServerLevel) level, above);
        } else {
            level.setBlock(above, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 11);
        }
        level.playSound(null, above, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
        // Somebody who has done this before does not wreck the drill doing it.
        if (!dev.hominin.evolution.mind.Skills.knows(player, dev.hominin.evolution.mind.Skills.Skill.FIRE) || player.getRandom().nextBoolean()) {
            player.getMainHandItem().shrink(1);
        }
        dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.FIRE);
        player.sendSystemMessage(Component.literal(hearth
                ? "The smoke thickens and catches. A hearth - feed it sticks and branches and it will keep."
                : "The smoke thickens, catches, and goes up. You made that.")
                .withStyle(ChatFormatting.GOLD));
        if (EvolutionManager.isReadyForMilestone(player, BuiltinMilestones.FIRE_TRANSFER)) {
            EvolutionManager.attemptMilestone(player, BuiltinMilestones.FIRE_TRANSFER);
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Working a stick into a mound and drawing it back out loaded with soldiers.
     *
     * <p>Chimps do this with a stripped twig and it takes them years to learn, so it
     * gets the same three-strike rhythm as every other worked action here rather
     * than paying out on one click. The mound is not consumed - a colony outlasts
     * anything that eats from it, which is what makes it worth remembering where
     * one is.
     */
    private static void fishForTermites(ServerPlayer player, Level level, BlockPos pos, ItemStack stick) {
        int count = advance(forageProgress, player.getUUID(), level.getGameTime(), pos, FORAGE_COOLDOWN_TICKS);
        if (count == TOO_SOON) {
            return;
        }
        if (count == ON_COOLDOWN) {
            player.displayClientMessage(Component.literal("The soldiers have gone back down. Give it a moment."), true);
            return;
        }
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.5F, 1.3F);
        if (count < CLICKS_REQUIRED) {
            player.displayClientMessage(
                    Component.literal("Working the stick in " + count + "/" + CLICKS_REQUIRED + "..."), true);
            return;
        }
        // Give before taking, so a full inventory cannot eat the stick.
        ItemStack loaded = new ItemStack(ModItems.TERMITE_STICK.get());
        if (!player.getInventory().add(loaded)) {
            player.drop(loaded, false);
        }
        // The first time, the trick is the reward: you keep the bare stick as well as the
        // loaded one, so learning it never costs you the thing you learned it with.
        boolean first = !dev.hominin.evolution.mind.Skills.knows(player,
                dev.hominin.evolution.mind.Skills.Skill.TERMITE_FISHING);
        if (!first) {
            stick.shrink(1);
        }
        player.displayClientMessage(
                Component.literal("You draw the stick out covered in soldiers."), true);
        if (!dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.TERMITE_FISHING)
                && player.getRandom().nextInt(4) == 0) {
            giveOrDrop(player, new ItemStack(ModItems.GRUB.get()));
        }
    }

    /** The last day each player was told about the state of the land. */
    private static final Map<UUID, Long> droughtTold = new HashMap<>();

    /**
     * Every second day the land may be strained. Foraging pays less, and other bands have
     * nothing to spare for anyone.
     */
    private static void announceDrought(ServerPlayer player, long day) {
        if (droughtTold.getOrDefault(player.getUUID(), -1L) == day) {
            return;
        }
        droughtTold.put(player.getUUID(), day);
        if (dev.hominin.evolution.survival.Drought.isActive(player.level())) {
            player.sendSystemMessage(Component.literal(
                    "The ground is cracked and the roots are dry. There will be little to find today, "
                    + "and nobody will want to share.").withStyle(ChatFormatting.GOLD));
        }
    }

    /** A fire or lava block right here, or immediately adjacent - "wildfire, lava" without touching either. */
    private static boolean isFireSource(Level level, BlockPos pos) {
        if (isFireOrLava(level, pos)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (isFireOrLava(level, pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFireOrLava(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.getFluidState().is(FluidTags.LAVA);
    }

    /**
     * Drinking straight from a natural source. Any distinct biome the player has
     * drunk in counts toward the criterion, same "variety of place" logic as
     * foraging - exact coordinates aren't tracked, only where.
     */
    public static void drinkWater(ServerPlayer player, BlockPos where) {
        dev.hominin.evolution.survival.Thirst.drink(player, dev.hominin.evolution.survival.Thirst.DRINK_FROM_SOURCE);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS,
                0.6F, 1.0F + player.getRandom().nextFloat() * 0.2F);
        player.swing(InteractionHand.MAIN_HAND, true);
        dev.hominin.evolution.band.Territory.usedResource(player, where);
        ResourceKey<Biome> biomeKey = currentBiomeKey(player);
        if (biomeKey == null) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.getWaterSourceBiomes().add(biomeKey.location())) {
            EvolutionManager.incrementCriterion(player, "water_sources", 1);
            player.displayClientMessage(Component.literal("You drink from the water."), true);
        }
    }

    @Nullable
    private static ResourceKey<Biome> currentBiomeKey(ServerPlayer player) {
        Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
        return biomeHolder.unwrapKey().orElse(null);
    }

    /**
     * Knocking loose stone off an outcrop. This is the mod's only source of workable
     * stone - there is no mining - so it has to stay repeatable, otherwise every
     * stone tool downstream of it becomes uncraftable.
     */
    /**
     * What a worked deposit gives up. Quartzite is the common, coarse result and
     * comes in quantity; chert is the better stone and the rarer one, so the split
     * is between a lot of adequate material and a little good material.
     */
    private static final float QUARTZITE_SHARE = 0.6F;
    private static final int QUARTZITE_YIELD = 3;
    private static final int CHERT_YIELD = 2;

    /**
     * Chance a worked face turns up a cobble already the right shape and weight to
     * strike with. Loose surface quartzite carries the same chance in its loot
     * table, which is where a first hammerstone comes from.
     */
    private static final float HAMMERSTONE_FIND_CHANCE = 0.22F;
    private static final float CHERT_HAMMERSTONE_FIND_CHANCE = 0.18F;

    /**
     * Rarer than a plain hammerstone: a chert nodule round enough to strike with.
     * Rolled first, so a chert face gives up one or the other, never both.
     */
    // A chert seam is the only place a hammerstone of chert comes from, and chert seams
    // are scarce by design. At six percent you could work one out entirely and never see
    // a nodule, which put the multi tool out of reach for reasons nobody could see.
    private static final float CHERT_NODULE_FIND_CHANCE = 0.22F;

    /**
     * What a face comes off as. A named outcrop gives up its own stone, which is
     * what makes finding a chert seam worth the walk; plain country rock is a
     * mixed quarry and rolls for it.
     */
    private static ItemStack yieldOf(BlockState state, Level level) {
        if (state.is(ModBlocks.CHERT_DEPOSIT.get())) {
            return new ItemStack(ModItems.CHERT_ROCK.get(), CHERT_YIELD);
        }
        if (state.is(ModBlocks.QUARTZITE_DEPOSIT.get())) {
            return new ItemStack(ModItems.GRANITE_ROCK.get(), QUARTZITE_YIELD);
        }
        if (state.is(ModBlocks.LIMESTONE_DEPOSIT.get())) {
            return new ItemStack(ModItems.LIMESTONE_ROCK.get(), QUARTZITE_YIELD);
        }
        return level.getRandom().nextFloat() < QUARTZITE_SHARE
                ? new ItemStack(ModItems.GRANITE_ROCK.get(), QUARTZITE_YIELD)
                : new ItemStack(ModItems.CHERT_ROCK.get(), CHERT_YIELD);
    }

    private static void knapRock(ServerPlayer player, Level level, BlockPos pos) {
        // Bare hands do nothing to a rock face. Loose surface cobbles are the way
        // in - two of those make a hammerstone, and the hammerstone opens deposits.
        // Either hand will do - what matters is that something to strike with is to hand.
        InteractionHand hammer = ToolUse.handWith(player, ModTags.Items.HAMMERSTONES);
        if (hammer == null) {
            player.displayClientMessage(
                    Component.literal("Your hands are not enough. You need a hammerstone."), true);
            return;
        }
        int count = advance(knapProgress, player.getUUID(), level.getGameTime(), pos, KNAP_COOLDOWN_TICKS);
        if (count == TOO_SOON) {
            return;
        }
        if (count == ON_COOLDOWN) {
            player.displayClientMessage(Component.literal("The stone needs a moment to be worked again."), true);
            return;
        }
        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F, 0.9F);
        if (count < CLICKS_REQUIRED) {
            player.displayClientMessage(
                    Component.literal("Striking " + count + "/" + CLICKS_REQUIRED + "..."), true);
            return;
        }
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
        ToolUse.wear(player, hammer);
        dev.hominin.evolution.band.Territory.usedResource(player, pos);

        // Ordinary rock is mostly useless inside. A proper deposit is what always pays.
        boolean deposit = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock()).getNamespace().equals(HomininEvolutionMod.MODID);
        if (!deposit && level.getRandom().nextFloat() < PLAIN_STONE_EMPTY_CHANCE) {
            player.displayClientMessage(Component.literal(
                    "The stone breaks up into useless rubble. Nothing here worth knapping."), true);
            return;
        }

        ItemStack won = yieldOf(level.getBlockState(pos), level);
        boolean quartzite = won.is(ModItems.GRANITE_ROCK.get());
        boolean chert = won.is(ModItems.CHERT_ROCK.get());
        giveOrDrop(player, won);
        if (won.is(ModItems.LIMESTONE_ROCK.get())) {
            player.displayClientMessage(Component.literal(
                    "Chalky stuff comes away in slabs. It will not hold an edge."), true);
            return;
        }
        if (chert && level.getRandom().nextFloat() < CHERT_NODULE_FIND_CHANCE
                && dev.hominin.evolution.hunt.Seams.takeCobble(level, pos)) {
            giveOrDrop(player, new ItemStack(ModItems.CHERT_HAMMERSTONE.get()));
            ToolUse.creditOldowanTool(player, ModItems.CHERT_HAMMERSTONE.get());
            player.displayClientMessage(Component.literal(
                    "A whole nodule of chert drops free - round, dense, and just the size of a fist."), true);
            return;
        }
        float hammerChance = chert ? CHERT_HAMMERSTONE_FIND_CHANCE : HAMMERSTONE_FIND_CHANCE;
        if ((quartzite || chert) && level.getRandom().nextFloat() < hammerChance
                && dev.hominin.evolution.hunt.Seams.takeCobble(level, pos)) {
            giveOrDrop(player, new ItemStack(ModItems.HAMMERSTONE.get()));
            // Picking the one usable cobble out of a face of rubble is the whole skill.
            ToolUse.creditOldowanTool(player, ModItems.HAMMERSTONE.get());
            player.displayClientMessage(Component.literal(
                    "One piece comes away round and heavy. It sits in the hand like it was meant to."), true);
            return;
        }
        if (dev.hominin.evolution.hunt.Seams.isWorkedOut(level, pos)) {
            player.displayClientMessage(Component.literal(quartzite
                    ? "Nothing but flat rubble now. Whatever was round in this face is out of it."
                    : "The good stone in this seam is gone. There will be another somewhere."), true);
            return;
        }
        player.displayClientMessage(Component.literal(quartzite
                ? "The face shears away - coarse quartzite, and plenty of it."
                : "A seam of chert comes loose. Finer stone, and less of it."), true);
    }

    private static void forage(ServerPlayer player, Level level, BlockPos pos) {
        int count = advance(forageProgress, player.getUUID(), level.getGameTime(), pos, FORAGE_COOLDOWN_TICKS);
        if (count == TOO_SOON) {
            return;
        }
        if (count == ON_COOLDOWN) {
            player.displayClientMessage(Component.literal("You need a moment before searching again."), true);
            return;
        }
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.6F, 1.0F);
        if (count == 1) {
            Band.leaderForaging(player, pos);
        }
        if (count < CLICKS_REQUIRED) {
            player.displayClientMessage(
                    Component.literal("Foraging " + count + "/" + CLICKS_REQUIRED + "..."), true);
            return;
        }

        boolean digging = player.getMainHandItem().is(ModItems.DIGGING_STICK.get())
                || player.getOffhandItem().is(ModItems.DIGGING_STICK.get());
        boolean withStick = player.getMainHandItem().is(ModItems.SHARPENED_STICK.get())
                || player.getOffhandItem().is(ModItems.SHARPENED_STICK.get());
        float successChance = digging ? FORAGE_SUCCESS_CHANCE_DIGGING
                : withStick ? FORAGE_SUCCESS_CHANCE_WITH_STICK : FORAGE_SUCCESS_CHANCE;
        successChance *= dev.hominin.evolution.survival.Drought.forageMultiplier(level);
        // Paranthropus and the other primates were here first.
        successChance *= dev.hominin.evolution.band.Paranthropus.forageShare(player);
        if (dev.hominin.evolution.hunt.Predation.standing(player) >= 2) {
            // Habilis and after live off carcasses and marrow; rooting in the dirt pays less.
            successChance *= HABILIS_FORAGE_MULTIPLIER;
        }
        dev.hominin.evolution.band.Territory.usedResource(player, pos);
        if (level.getRandom().nextFloat() >= successChance) {
            String blame = dev.hominin.evolution.band.Paranthropus.whyEmpty(player);
            player.displayClientMessage(Component.literal(blame != null ? blame
                    : dev.hominin.evolution.survival.Drought.isActive(level)
                            ? "You search the dry soil but find nothing."
                            : "You search the soil but find nothing."), true);
            return;
        }
        Item[] insects = {ModItems.GRUB.get(), ModItems.BEETLE.get(), ModItems.EARTHWORM.get()};
        // A digging stick turns the ground over properly, and brings up a handful at a time.
        int found = digging ? 1 + level.getRandom().nextInt(3) : 1;
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < found; i++) {
            ItemStack insectStack = new ItemStack(insects[level.getRandom().nextInt(insects.length)]);
            names.append(i == 0 ? "" : ", ").append(insectStack.getHoverName().getString());
            if (!player.getInventory().add(insectStack)) {
                player.drop(insectStack, false);
            }
        }
        player.sendSystemMessage(Component.literal((digging
                ? "You turn the ground over with the digging stick and find "
                : "You root through the soil and find a ") + names + "."));
    }

    /**
     * Hammering a branch against something. Knocking is a warning that may drive
     * animals off; keep going and it becomes a call that carries far enough to
     * reach a band. Stopping early is a deliberate choice, not a failed attempt.
     */
    private static void knockForBand(ServerPlayer player, Level level, BlockPos pos) {
        int count = advance(knockProgress, player.getUUID(), level.getGameTime(), null, NO_COOLDOWN);
        if (count <= TOO_SOON) {
            return;
        }
        if (count < KNOCKS_TO_WARN) {
            level.playSound(null, pos, ModSounds.BRANCH_KNOCK.get(), SoundSource.PLAYERS, 1.6F, 1.0F);
            player.displayClientMessage(
                    Component.literal("Knock " + count + "/" + KNOCKS_TO_CALL + "..."), true);
            return;
        }
        if (count < KNOCKS_TO_CALL) {
            level.playSound(null, pos, ModSounds.BRANCH_KNOCK.get(), SoundSource.PLAYERS, 2.4F, 0.9F);
            int startled = startleNearby(player, KNOCK_SCARE_RADIUS, KNOCK_SCARE_CHANCE, false);
            player.displayClientMessage(startled > 0
                    ? Component.literal("The noise sends something crashing away.")
                    : Component.literal("Knock " + count + "/" + KNOCKS_TO_CALL + "..."), true);
            return;
        }
        level.playSound(null, pos, ModSounds.BAND_CALL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendSystemMessage(Component.literal("You hammer out a call. It carries across the landscape."));
    }

    /**
     * Startles nearby animals. Each candidate rolls separately, so a display that
     * works on one animal may not faze the one beside it. An animal that does react
     * either bolts or freezes - most things caught off guard lock up rather than
     * run, so freezing is the commoner outcome.
     *
     * @param predatorsOnly limit the effect to things tagged as predators
     * @return how many actually reacted
     */
    public static int startleNearby(LivingEntity player, double radius, float chance, boolean predatorsOnly) {
        Level level = player.level();
        AABB area = player.getBoundingBox().inflate(radius);
        int startled = 0;
        for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, area)) {
            // "Threats" are predators and anything hostile; a knock startles everything.
            boolean threat = mob.getType().is(ModTags.EntityTypes.PREDATORS)
                    || mob instanceof net.minecraft.world.entity.monster.Enemy;
            if ((predatorsOnly && !threat) || !dev.hominin.evolution.combat.Scare.canBeScared(mob)
                    || mob instanceof dev.hominin.evolution.band.BandMember) {
                continue;
            }
            if (level.getRandom().nextFloat() >= chance) {
                continue;
            }
            // A real fright: it runs, and cannot come straight back at anyone until it wears off.
            dev.hominin.evolution.combat.Scare.scare(mob, player.position(),
                    STARTLE_STUN_TICKS + level.getRandom().nextInt(STARTLE_STUN_TICKS / 2));
            startled++;
        }
        return startled;
    }

    /**
     * Right-clicking the air with a bone cracks it open for marrow. Sticks are not
     * sharpened here any more - that is held on the work key, so a right-click with
     * a stick stays free for fishing termites and everything else.
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getFace() != null) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                && ThreatDisplay.isThrowable(held) && ThreatDisplay.throwHeld(player, held)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (held.is(Items.EGG) && (player.getOffhandItem().is(ModItems.SHARPENED_STICK.get())
                || player.getOffhandItem().is(ModItems.POINTY_STICK.get()))) {
            drainEgg(player, held);
            return;
        }
        if (held.is(ModItems.LONG_BONE.get())) {
            crackBone(player, held, 2);
        } else if (held.is(Items.BONE)) {
            crackBone(player, held, 1);
        }
    }

    /**
     * Piercing an egg with a point and drinking it out, which leaves the shell whole. An
     * empty shell is the oldest water bottle there is.
     */
    private static void drainEgg(ServerPlayer player, ItemStack egg) {
        player.getFoodData().eat(2, 0.3F);
        dev.hominin.evolution.survival.Thirst.drink(player, 2);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.7F, 1.1F);
        ItemStack shell = new ItemStack(ModItems.EMPTY_EGGSHELL.get());
        if (!player.getInventory().add(shell)) {
            player.drop(shell, false);
        }
        egg.shrink(1);
        player.displayClientMessage(Component.literal(
                "You pierce the shell and drink it out, leaving it whole."), true);
    }

    /** Handles both our long bone and the vanilla bone, so bones from any mod's animals work. */
    private static void crackBone(ServerPlayer player, ItemStack bone, int marrowYield) {
        if (!hasFlake(player)) {
            player.sendSystemMessage(Component.literal("You need a flake to crack this bone open."));
            return;
        }

        // Hand the marrow over BEFORE shrinking the bone. While the bone still fills
        // the held slot, Inventory#getFreeSlot cannot hand that same slot back to us
        // - and vanilla clears the held slot outright once the stack empties, which
        // would otherwise delete the marrow the instant it was created.
        boolean skilled = dev.hominin.evolution.mind.Skills.knows(player, dev.hominin.evolution.mind.Skills.Skill.MARROW);
        ItemStack marrow = new ItemStack(ModItems.BONE_MARROW.get(),
                marrowYield + (skilled && player.getRandom().nextInt(3) == 0 ? 1 : 0));
        if (!player.getInventory().add(marrow) && !marrow.isEmpty()) {
            player.drop(marrow, false);
        }
        bone.shrink(1);
        dullFlake(player);

        EvolutionManager.incrementCriterion(player, "scavenge_bones", 1);
        dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.MARROW);
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).addMeatScavenged(1);
        player.sendSystemMessage(Component.literal("You crack the bone open and scrape out the marrow."));
    }

    /**
     * Gnawing a stick to a point. No tool needed - Fongoli chimps sharpen their
     * jabbing sticks with their teeth, and a hominin can do the same.
     *
     * <p>Called once the client has seen the work key held long enough. The hold is
     * the effort, so there is no click count here; the server still checks that a
     * stick is really in hand.
     */
    public static void sharpenHeldStick(ServerPlayer player) {
        ItemStack stick = player.getMainHandItem();
        if (!stick.is(Items.STICK)) {
            return;
        }
        // A flake in the other hand, and a hominin who knows how to use one, makes a real point instead.
        var recipe = dev.hominin.evolution.combat.ItemInteractions.match(stick, player.getOffhandItem());
        if (recipe != null && recipe.result() == ModItems.POINTY_STICK && dev.hominin.evolution.combat.ItemInteractions
                .isAvailable(player.getData(Attachments.PLAYER_EVOLUTION_DATA), recipe)) {
            dev.hominin.evolution.combat.ItemInteractions.interact(player);
            return;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.5F, 1.4F);

        // Same ordering trap as the marrow: give first, shrink second.
        ItemStack sharpened = new ItemStack(ModItems.SHARPENED_STICK.get());
        Component name = sharpened.getHoverName();
        if (!player.getInventory().add(sharpened) && !sharpened.isEmpty()) {
            player.drop(sharpened, false);
        }
        stick.shrink(1);
        player.sendSystemMessage(Component.literal("You work the wood down into a ")
                .append(name)
                .append(Component.literal(".")));
    }

    /** Wears the flake doing the work: the one in hand if there is one, otherwise the first carried. */
    private static void dullFlake(ServerPlayer player) {
        InteractionHand hand = ToolUse.handWith(player, ModTags.Items.FLAKES);
        if (hand != null) {
            ToolUse.wear(player, hand);
            return;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModTags.Items.FLAKES) && stack.isDamageableItem()) {
                stack.hurtAndBreak(1, player.serverLevel(), player, item -> {
                });
                return;
            }
        }
    }

    private static boolean hasFlake(ServerPlayer player) {
        return player.getInventory().contains(ModTags.Items.FLAKES);
    }

    /**
     * Advances a repeated-click counter.
     *
     * @param pos            the block being worked, or null if the action is not tied to a block
     * @param postCooldown   ticks to lock the player out for once the action resolves
     * @return {@link #ON_COOLDOWN} while locked out, {@link #TOO_SOON} if the click came
     *         too close on the heels of the last one, otherwise the new click count
     */
    private static int advance(Map<UUID, Progress> tracker, UUID playerId, long gameTime, BlockPos pos,
            long postCooldown) {
        Progress previous = tracker.get(playerId);
        if (previous != null && gameTime < previous.blockedUntil()) {
            return ON_COOLDOWN;
        }
        if (previous != null && gameTime - previous.lastTick() < CLICK_COOLDOWN_TICKS) {
            return TOO_SOON;
        }
        boolean continues = previous != null
                && gameTime - previous.lastTick() <= PROGRESS_TIMEOUT_TICKS
                && (pos == null || pos.equals(previous.pos()));
        int count = continues ? previous.count() + 1 : 1;
        if (count >= CLICKS_REQUIRED) {
            // Reset the counter but keep the entry alive as the cooldown marker.
            tracker.put(playerId, new Progress(0, gameTime, null, gameTime + postCooldown));
        } else {
            tracker.put(playerId, new Progress(count, gameTime, pos, 0L));
        }
        return count;
    }

    /** Wipes a departing player's half-finished actions so the trackers do not grow forever. */
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        if (event.getEntity() instanceof ServerPlayer leaving) {
            ChecklistTracker.forget(leaving);
            dev.hominin.evolution.band.Grooming.forget(playerId);
            dev.hominin.evolution.hunt.Quarry.forget(playerId);
            dev.hominin.evolution.survival.Drinking.forget(playerId);
            dev.hominin.evolution.hunt.Predation.forget(playerId);
            dev.hominin.evolution.band.WildBands.forget(playerId);
            dev.hominin.evolution.band.Panic.forget(playerId);
            dev.hominin.evolution.stage.CutsceneGuard.forget(playerId);
            dev.hominin.evolution.combat.Bleeding.forget(playerId);
            dev.hominin.evolution.survival.Infestation.forget(playerId);
            dev.hominin.evolution.entity.TroopRelations.forget(playerId);
            dev.hominin.evolution.mind.Teaching.forget(playerId);
            dev.hominin.evolution.survival.Afflictions.forget(playerId);
            Thinking.forget(leaving);
            Arrival.forget(leaving);
        }
        knapProgress.remove(playerId);
        forageProgress.remove(playerId);
        knockProgress.remove(playerId);
        ThreatDisplay.forget(playerId);
        ClimbingServer.forget(playerId);
        dev.hominin.evolution.band.Social.forget(playerId);
        touchedColdBiome.remove(playerId);
        wasNight.remove(playerId);
        nightTreeCoverSeen.remove(playerId);
        climbedTrees.remove(playerId);
        BlockBreakHandler.forget(playerId);
    }

    /** How far a lightning strike is noticeable from - it's the flash people react to, not proximity to the char mark. */
    private static final double LIGHTNING_NOTICE_RADIUS = 24.0D;

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity().getType().is(ModTags.EntityTypes.BLOCKED_SPAWNS)) {
            // setSpawnCancelled, NOT setCanceled: cancelling this event merely skips
            // Mob#finalizeSpawn and the mob still spawns. Only setSpawnCancelled
            // actually stops it.
            event.setSpawnCancelled(true);
        }
    }

    /**
     * Lightning bolts are plain entities, not Mobs, so FinalizeSpawnEvent (which
     * only fires from within Mob#finalizeSpawn) never sees them - this is the
     * general "any entity entered the level" hook instead.
     */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LightningBolt bolt) {
            notifyLightningWitnesses(bolt);
        }
    }

    private static void notifyLightningWitnesses(LightningBolt bolt) {
        Level level = bolt.level();
        if (level.isClientSide()) {
            return;
        }
        AABB area = bolt.getBoundingBox().inflate(LIGHTNING_NOTICE_RADIUS);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            EvolutionManager.incrementCriterion(player, "notice_fire_source", 1);
            player.displayClientMessage(Component.literal("Lightning strikes nearby - you take note of it."), true);
        }
    }

    /**
     * A wooden weapon barely breaks skin, so it works by concussion instead.
     * The escalation lives in {@link HeadTraumaHandler}.
     */
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        // Your own band and the bands walking with you only ever take pulled blows.
        if (target instanceof dev.hominin.evolution.band.BandMember member && member.isCompanionOf(player)) {
            return;
        }
        HeadTraumaHandler.strike(player, target);
        WoundHandler.strike(player, target);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer dead) {
            // However you died, the disease died with you.
            dev.hominin.evolution.survival.Kuru.clear(dead);
            dev.hominin.evolution.band.Mating.clearPregnancy(dead);
        }
        if (entity.level().isClientSide() || entity instanceof Player) {
            return;
        }
        creditHunt(event.getSource().getEntity(), entity);
        dev.hominin.evolution.hunt.Quarry.creditPersistence(entity);
        checkArmsRace(event, entity);
        dev.hominin.evolution.hunt.Carcasses.onDeath(entity);
        checkTaungChild(event, entity);
        if (entity instanceof dev.hominin.evolution.band.BandMember member
                && event.getSource().getEntity() instanceof LivingEntity killer) {
            dev.hominin.evolution.hunt.PredatorAppetite.onBandMemberKilled(member, killer);
        }
        if (dev.hominin.evolution.hunt.PredatorAppetite.isPredator(entity)) {
            dev.hominin.evolution.hunt.PredatorAppetite.forget(entity.getUUID());
        }
        dropAt(entity, new ItemStack(Items.BONE));
        if (entity.getBbHeight() >= LONG_BONE_MIN_HEIGHT
                && entity.level().getRandom().nextFloat() < LONG_BONE_DROP_CHANCE) {
            dropAt(entity, new ItemStack(ModItems.LONG_BONE.get()));
        }
    }

    /**
     * A child of the band, taken by a bird. The Taung Child - the first australopithecine
     * ever described - has talon punctures in its eye sockets; an eagle took it. This is
     * the same thing happening to yours, and it is as old a way to lose a child as exists.
     */
    private static void checkTaungChild(LivingDeathEvent event, LivingEntity entity) {
        if (!(entity instanceof dev.hominin.evolution.band.BandMember child) || !child.isBaby()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof dev.hominin.evolution.entity.CrownedEagle)) {
            return;
        }
        if (child.leaderPlayer() instanceof ServerPlayer leader) {
            leader.sendSystemMessage(Component.literal(child.getName().getString()
                    + " is carried off. There is nothing left of them but the marks of talons.")
                    .withStyle(ChatFormatting.DARK_RED));
            dev.hominin.evolution.advancement.HomininAdvancements.award(leader, "hominin/taung_child");
        }
    }

    /**
     * A stone, thrown, killing something outright. Every other animal alive has spent its
     * whole history settling fights at arm's length; erectus is the first thing that can
     * end one from thirty blocks away, and the shoulder that does it is why.
     */
    private static void checkArmsRace(LivingDeathEvent event, LivingEntity victim) {
        if (!(event.getSource().getDirectEntity() instanceof dev.hominin.evolution.entity.ThrownObject thrown)
                || !(thrown.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        if (!thrown.getItem().is(ModItems.ROCK.get()) && !thrown.getItem().is(ModTags.Items.KNAPPABLE_STONE)) {
            return;
        }
        if (!player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath().equals("homo_erectus")) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.hominin.evolution.network.ArmsRacePayload());
        dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/arms_race");
    }

    /**
     * A kill only counts as a hunt if the weapon is still in hand when the animal
     * goes down - the point of the criterion is using the tool, not owning it.
     */
    private static void creditHunt(@Nullable net.minecraft.world.entity.Entity killer, LivingEntity victim) {
        if (killer instanceof dev.hominin.evolution.band.BandMember member) {
            ItemStack weapon = member.getMainHandItem();
            if (weapon.is(ModItems.SHARPENED_STICK.get())) {
                Band.contribute(member, "hunt_with_stick");
            } else if (weapon.is(ModItems.SHARPENED_SPEAR.get()) || weapon.is(ModItems.FIRE_HARDENED_SPEAR.get())) {
                Band.contribute(member, "hunt_with_spear");
            }
            return;
        }
        if (!(killer instanceof ServerPlayer player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (weapon.is(ModItems.SHARPENED_STICK.get())) {
            EvolutionManager.incrementCriterion(player, "hunt_with_stick", 1);
        } else if (weapon.is(ModItems.SHARPENED_SPEAR.get()) || weapon.is(ModItems.FIRE_HARDENED_SPEAR.get())) {
            EvolutionManager.incrementCriterion(player, "hunt_with_spear", 1);
        }
    }

    private static void dropAt(LivingEntity entity, ItemStack stack) {
        entity.level().addFreshEntity(new ItemEntity(entity.level(),
                entity.getX(), entity.getY(), entity.getZ(), stack));
    }

    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        // Raw meat on a wound that has only just closed is how an infection starts.
        if (stack.is(ModItems.MEAT_CHUNK.get()) || stack.is(net.minecraft.world.item.Items.BEEF)
                || stack.is(net.minecraft.world.item.Items.PORKCHOP)
                || stack.is(net.minecraft.world.item.Items.MUTTON)
                || stack.is(net.minecraft.world.item.Items.CHICKEN)
                || stack.is(net.minecraft.world.item.Items.RABBIT)) {
            dev.hominin.evolution.combat.Bleeding.maybeInfect(player, "Raw, and you were already torn open.");
        }
        if (stack.is(ModItems.COOKED_MEAT_CHUNK.get()) || stack.is(net.minecraft.world.item.Items.COOKED_BEEF)
                || stack.is(net.minecraft.world.item.Items.COOKED_PORKCHOP)
                || stack.is(net.minecraft.world.item.Items.COOKED_MUTTON)
                || stack.is(net.minecraft.world.item.Items.COOKED_CHICKEN)
                || stack.is(net.minecraft.world.item.Items.COOKED_RABBIT)) {
            EvolutionManager.incrementCriterion(player, "eat_cooked_meat", 1);
        }
        if (dev.hominin.evolution.band.Mortuary.isHomininFlesh(stack)) {
            dev.hominin.evolution.band.Mortuary.ate(player, stack);
        }
        if (stack.is(ModItems.WATER_EGGSHELL.get())) {
            dev.hominin.evolution.survival.Thirst.drink(player,
                    dev.hominin.evolution.survival.Thirst.DRINK_FROM_SHELL);
            return;
        }
        if (!stack.has(DataComponents.FOOD)) {
            return;
        }
        ResourceKey<Biome> biomeKey = currentBiomeKey(player);
        if (biomeKey == null) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        data.addForaged(1);
        if (data.getForagedBiomes().add(biomeKey.location())) {
            EvolutionManager.incrementCriterion(player, "forage_biomes", 1);
        }
    }

    /** The hammerstone is still a grid recipe; every other tool credits itself where it is made. */
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ToolUse.creditOldowanTool(player, event.getCrafting().getItem());
        }
    }

    /**
     * Weather you cannot miss. You do not have to see a tree take a strike - being out
     * under a storm at all is how the idea arrives, and it gives a habilis a second
     * route to fire that does not depend on stumbling across lava.
     */
    private static void noticeStorm(ServerPlayer player) {
        if (player.tickCount % 100 != 0 || !player.serverLevel().isThundering()
                || !player.serverLevel().canSeeSky(player.blockPosition())) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.getCriterionCounters().getOrDefault("notice_fire_source", 0) > 0) {
            return;
        }
        EvolutionManager.incrementCriterion(player, "notice_fire_source", 1);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "The sky cracks open and something out there catches. So that is where it comes from.")
                .withStyle(net.minecraft.ChatFormatting.YELLOW));
    }

    /** How far out to look for tree cover when judging where a player slept. */
    private static final int TREE_COVER_RADIUS = 3;

    /**
     * Distance between payouts, in {@code walkDist} units. Vanilla accumulates
     * walkDist at 0.6x the blocks actually travelled, so this is about 1200 blocks
     * on foot - far enough to feel like a real search, and it excludes boats and
     * mounts because vanilla skips walkDist entirely while riding.
     */
    private static final float DISTANCE_CREDIT_UNITS = 1200.0F * 0.6F;

    /**
     * Criteria that depend on what generated near the player, and so need a
     * guaranteed route that pure effort can reach.
     */
    private static final String[] DISTANCE_BACKED_CRITERIA = {
            "forage_biomes", "water_sources", "cold_biome_edge"};

    private static final Map<UUID, Boolean> touchedColdBiome = new HashMap<>();
    private static final Map<UUID, Boolean> wasNight = new HashMap<>();
    private static final Map<UUID, Boolean> nightTreeCoverSeen = new HashMap<>();

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        ServerPlayer player = event.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (player == null) {
            return;
        }
        Arrival.tick(player);
        dev.hominin.evolution.band.Panic.tick(player);
        dev.hominin.evolution.survival.Thirst.tick(player);
        dev.hominin.evolution.band.Grooming.tick(player);
        dev.hominin.evolution.survival.Infestation.tick(player);
        dev.hominin.evolution.entity.TroopRelations.tick(player);
        dev.hominin.evolution.hunt.Carcasses.tickMortality(player);
        dev.hominin.evolution.hunt.Carcasses.tickLoners(player);
        dev.hominin.evolution.hunt.Predation.tick(player);
        dev.hominin.evolution.hunt.Quarry.tick(player.serverLevel());
        ThreatDisplay.tick(player);
        dev.hominin.evolution.combat.Bleeding.tick(player);
        dev.hominin.evolution.combat.Bleeding.tickInfection(player);
        noticeStorm(player);
        ClimbingServer.tick(player);
        WildBands.tick(player);
        dev.hominin.evolution.band.Paranthropus.tick(player);
        dev.hominin.evolution.survival.Kuru.tick(player);
        dev.hominin.evolution.band.Mating.tick(player);
        dev.hominin.evolution.survival.Hearths.tickPlayer(player);
        dev.hominin.evolution.stage.ErectusGoals.tick(player);
        dev.hominin.evolution.band.Mortuary.tick(player);
        Band.tickPlayer(player);
        dev.hominin.evolution.inventory.InventoryLimits.tick(player);
        dev.hominin.evolution.entity.WildAnimals.tick(player);
        // Climbing is checked far more often than the rest: a player is only up a
        // tree for a few seconds, so a once-a-second sweep would miss most climbs.
        if (player.tickCount % CLIMB_CHECK_INTERVAL_TICKS == 0) {
            checkTreeClimb(player);
        }
        if (player.tickCount % DAY_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        // Rides the existing slow tick; it only sends a packet when a line changes.
        ChecklistTracker.refresh(player);
        long currentDay = player.level().getDayTime() / TICKS_PER_DAY;
        announceDrought(player, currentDay);
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.getLastCountedDay() < 0) {
            data.setLastCountedDay(currentDay);
        } else if (currentDay > data.getLastCountedDay()) {
            data.setLastCountedDay(currentDay);
            EvolutionManager.incrementCriterion(player, "survive_days", 1);
        }

        checkColdBiomeEdge(player);
        checkNightSurvival(player);
        checkDistanceCredit(player, data);
    }

    /**
     * Ground covered is the fallback for criteria that depend on what happened to
     * generate nearby. Some worlds simply have no cold biome or third water source
     * within reach; walking far enough gets you there anyway, slowly, so no one is
     * ever hard-stuck behind terrain luck.
     */
    private static void checkDistanceCredit(ServerPlayer player, PlayerEvolutionData data) {
        float walked = player.walkDist - data.getStageStartWalkDistance();
        if (walked < 0.0F) {
            // Lifetime distance only ever climbs, so a negative gap means the
            // snapshot is stale (an older save, or a stage set before this existed).
            data.setStageStartWalkDistance(player.walkDist);
            return;
        }
        int earned = (int) (walked / DISTANCE_CREDIT_UNITS);
        if (earned <= data.getDistanceCredits()) {
            return;
        }
        data.setDistanceCredits(earned);
        for (String criterion : DISTANCE_BACKED_CRITERIA) {
            EvolutionManager.incrementCriterion(player, criterion, 1);
        }
        player.sendSystemMessage(Component.literal(
                "You have covered a lot of ground, and learned the country by crossing it."));
    }

    private static final int CLIMB_CHECK_INTERVAL_TICKS = 10;

    /** How far above the foot of the tree counts as being up it rather than beside it. */
    private static final int CLIMB_MIN_HEIGHT = 4;

    /** Tallest tree worth scanning down through - jungle giants top out well below this. */
    private static final int CLIMB_MAX_SCAN = 32;

    /** How far out to look for a trunk, so a leafy hillside is not mistaken for a tree. */
    private static final int CLIMB_TRUNK_RADIUS = 2;

    /**
     * Trees already credited, held in memory rather than on the player's save data.
     * The counter itself persists; this only stops one tree being climbed over and
     * over for credit in a single session, and re-earning it after a restart is a
     * fair trade for not spending one of the save's remaining codec fields.
     */
    private static final Map<UUID, Set<Long>> climbedTrees = new HashMap<>();

    /**
     * Credits a climb when the player is stood on a tree, high enough above what
     * the tree grows out of. Trees are keyed by a 4x4 patch of the ground beneath
     * them, so moving around one canopy cannot be farmed for repeat credit.
     */
    private static void checkTreeClimb(ServerPlayer player) {
        boolean climbing = Climbing.isClimbing(player);
        if (!player.onGround() && !climbing) {
            return;
        }
        Level level = player.level();
        BlockPos support = player.blockPosition().below();
        BlockState standing = level.getBlockState(support);
        // Mid-climb there may be nothing underfoot yet; the trunk check below still applies.
        if (!climbing && !standing.is(BlockTags.LOGS) && !standing.is(BlockTags.LEAVES)) {
            return;
        }
        if (!hasTrunkNearby(level, support)) {
            return;
        }
        // Drop through the canopy - and any gaps in it - to whatever the tree stands on.
        BlockPos ground = support;
        for (int i = 0; i < CLIMB_MAX_SCAN; i++) {
            BlockPos below = ground.below();
            BlockState state = level.getBlockState(below);
            if (!state.isAir() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                break;
            }
            ground = below;
        }
        if (support.getY() - ground.getY() < CLIMB_MIN_HEIGHT) {
            return;
        }
        long key = (((long) (ground.getX() >> 2)) << 32) | ((ground.getZ() >> 2) & 0xFFFFFFFFL);
        if (climbedTrees.computeIfAbsent(player.getUUID(), id -> new HashSet<>()).add(key)) {
            EvolutionManager.incrementCriterion(player, "climb_trees", 1);
        }
    }

    private static boolean hasTrunkNearby(Level level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-CLIMB_TRUNK_RADIUS, -1, -CLIMB_TRUNK_RADIUS),
                center.offset(CLIMB_TRUNK_RADIUS, 1, CLIMB_TRUNK_RADIUS))) {
            if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    /** "Reach a colder edge and turn back" - fires the moment a cold spell ends. */
    private static void checkColdBiomeEdge(ServerPlayer player) {
        UUID id = player.getUUID();
        BlockPos pos = player.blockPosition();
        boolean cold = player.level().getBiome(pos).value().coldEnoughToSnow(pos);
        if (cold) {
            touchedColdBiome.put(id, true);
        } else if (Boolean.TRUE.equals(touchedColdBiome.remove(id))) {
            EvolutionManager.incrementCriterion(player, "cold_biome_edge", 1);
        }
    }

    /**
     * Watches a full night for tree cover. Whether the criterion fires is decided
     * the moment night ends, based on whether cover was ever seen while it lasted.
     */
    private static void checkNightSurvival(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean isNight = player.level().isNight();
        boolean previouslyNight = wasNight.getOrDefault(id, false);
        if (isNight) {
            if (!previouslyNight) {
                nightTreeCoverSeen.put(id, false);
            }
            if (isNearTreeCover(player)) {
                nightTreeCoverSeen.put(id, true);
            }
        } else if (previouslyNight && !nightTreeCoverSeen.getOrDefault(id, true)) {
            EvolutionManager.incrementCriterion(player, "ground_night_survival", 1);
        }
        wasNight.put(id, isNight);
    }

    /** Scans a small box around the player for logs/leaves - shared with the future tree-sleeping penalty. */
    private static boolean isNearTreeCover(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        Level level = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-TREE_COVER_RADIUS, -1, -TREE_COVER_RADIUS),
                center.offset(TREE_COVER_RADIUS, TREE_COVER_RADIUS, TREE_COVER_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }
}
