package dev.hominin.evolution.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.stage.BuiltinMilestones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
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

    private static final float FORAGE_SUCCESS_CHANCE = 0.2F;
    /** Poking through soil/leaf litter with a stick turns up far more than bare hands. */
    private static final float FORAGE_SUCCESS_CHANCE_WITH_STICK = 0.5F;
    /** Breathing room after a forage resolves, so you cannot strip a patch by holding right-click. */
    private static final long FORAGE_COOLDOWN_TICKS = 60L;
    private static final long KNAP_COOLDOWN_TICKS = 40L;

    /** A club to the head buys you five seconds to get away, or to hit it again. */
    private static final int STUN_TICKS = 100;

    private static final float LONG_BONE_DROP_CHANCE = 0.1F;
    /** Only animals with real limb bones in them; chickens and rabbits have nothing worth cracking. */
    private static final float LONG_BONE_MIN_HEIGHT = 1.0F;

    private static final Map<UUID, Progress> knapProgress = new HashMap<>();
    private static final Map<UUID, Progress> forageProgress = new HashMap<>();
    private static final Map<UUID, Progress> knockProgress = new HashMap<>();
    private static final Map<UUID, Progress> stickSharpenProgress = new HashMap<>();

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
        // block-specific interactions below.
        if (event.getItemStack().is(ModItems.LONG_BRANCH.get())) {
            knockForBand(player, event.getLevel(), event.getPos());
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.is(ModTags.Blocks.WORKABLE_STONE_DEPOSIT)) {
            if (player.isShiftKeyDown()) {
                EvolutionManager.incrementCriterion(player, "notice_stone_deposit", 1);
            } else if (EvolutionManager.isReadyForMilestone(player, BuiltinMilestones.STRIKE_FLAKE)) {
                EvolutionManager.attemptMilestone(player, BuiltinMilestones.STRIKE_FLAKE);
            } else {
                knapRock(player, event.getLevel(), event.getPos());
            }
        } else if (state.is(ModTags.Blocks.FORAGING_GROUND)) {
            // Sneaking is required so that ordinary right-clicks - placing a block,
            // eating, cracking a bone - never turn into an accidental forage.
            if (player.isShiftKeyDown()) {
                forage(player, event.getLevel(), event.getPos());
            }
        }
    }

    /**
     * Knocking loose stone off an outcrop. This is the mod's only source of workable
     * stone - there is no mining - so it has to stay repeatable, otherwise every
     * stone tool downstream of it becomes uncraftable.
     */
    private static void knapRock(ServerPlayer player, Level level, BlockPos pos) {
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

        ItemStack rock = new ItemStack(ModItems.ROCK.get());
        if (!player.getInventory().add(rock)) {
            player.drop(rock, false);
        }
        player.displayClientMessage(Component.literal("A fist-sized rock breaks free."), true);
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
        if (count < CLICKS_REQUIRED) {
            player.displayClientMessage(
                    Component.literal("Foraging " + count + "/" + CLICKS_REQUIRED + "..."), true);
            return;
        }

        boolean withStick = player.getMainHandItem().is(Items.STICK) || player.getOffhandItem().is(Items.STICK);
        float successChance = withStick ? FORAGE_SUCCESS_CHANCE_WITH_STICK : FORAGE_SUCCESS_CHANCE;
        if (level.getRandom().nextFloat() >= successChance) {
            player.displayClientMessage(Component.literal("You search the soil but find nothing."), true);
            return;
        }
        Item[] insects = {ModItems.GRUB.get(), ModItems.BEETLE.get(), ModItems.EARTHWORM.get()};
        Item insect = insects[level.getRandom().nextInt(insects.length)];
        ItemStack insectStack = new ItemStack(insect);
        Component insectName = insectStack.getHoverName();
        if (!player.getInventory().add(insectStack)) {
            player.drop(insectStack, false);
        }
        player.sendSystemMessage(Component.literal("You root through the soil and find a ")
                .append(insectName)
                .append(Component.literal(".")));
    }

    /**
     * Hammering a branch against something. Volume 4 puts the audible range at about
     * 64 blocks, which is the "call" part - anything nearby hears where you are.
     */
    private static void knockForBand(ServerPlayer player, Level level, BlockPos pos) {
        int count = advance(knockProgress, player.getUUID(), level.getGameTime(), null, NO_COOLDOWN);
        if (count <= TOO_SOON) {
            return;
        }
        level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 4.0F, 0.7F);
        if (count < CLICKS_REQUIRED) {
            player.displayClientMessage(
                    Component.literal("Knock " + count + "/" + CLICKS_REQUIRED + "..."), true);
            return;
        }
        player.sendSystemMessage(Component.literal("You hammer out a call. It carries across the landscape."));
    }

    /**
     * Right-clicking the air with a bone or a stick. Bones get cracked open for
     * marrow; a stick gets whittled to a point. A long branch is not handled here -
     * turning one into a spear needs a rock to grind it against, so it happens in
     * the crafting grid instead.
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.is(ModItems.LONG_BONE.get())) {
            crackBone(player, held, 2);
        } else if (held.is(Items.BONE)) {
            crackBone(player, held, 1);
        } else if (held.is(Items.STICK)) {
            sharpenStick(player, held);
        }
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
        ItemStack marrow = new ItemStack(ModItems.BONE_MARROW.get(), marrowYield);
        if (!player.getInventory().add(marrow) && !marrow.isEmpty()) {
            player.drop(marrow, false);
        }
        bone.shrink(1);

        EvolutionManager.incrementCriterion(player, "scavenge_bones", 1);
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).addMeatScavenged(1);
        player.sendSystemMessage(Component.literal("You crack the bone open and scrape out the marrow."));
    }

    /** Whittling a stick to a point: three passes with a flake in the pack. */
    private static void sharpenStick(ServerPlayer player, ItemStack stick) {
        if (!hasFlake(player)) {
            player.sendSystemMessage(Component.literal("You need a flake to work this to a point."));
            return;
        }
        int count = advance(stickSharpenProgress, player.getUUID(), player.level().getGameTime(), null, NO_COOLDOWN);
        if (count <= TOO_SOON) {
            return;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.5F, 1.4F);
        if (count < CLICKS_REQUIRED) {
            player.displayClientMessage(
                    Component.literal("Sharpening " + count + "/" + CLICKS_REQUIRED + "..."), true);
            return;
        }

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

    private static boolean hasFlake(ServerPlayer player) {
        return player.getInventory().countItem(ModItems.FLAKE.get()) > 0;
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
        knapProgress.remove(playerId);
        forageProgress.remove(playerId);
        knockProgress.remove(playerId);
        stickSharpenProgress.remove(playerId);
        BlockBreakHandler.forget(playerId);
    }

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity().getType().is(ModTags.EntityTypes.BLOCKED_SPAWNS)) {
            // setSpawnCancelled, NOT setCanceled: cancelling this event merely skips
            // Mob#finalizeSpawn and the mob still spawns. Only setSpawnCancelled
            // actually stops it.
            event.setSpawnCancelled(true);
        }
    }

    /** A branch barely hurts, but a solid swing to the head leaves an animal reeling. */
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !player.getMainHandItem().is(ModItems.LONG_BRANCH.get())) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        // Only a fully wound-up swing connects hard enough to daze anything.
        if (player.getAttackStrengthScale(0.5F) < 0.9F) {
            return;
        }
        // Slowness VII takes the target's movement speed to zero outright.
        // The 6-arg constructor is the one that takes `visible` separately from
        // `showIcon`; particle rendering keys off `visible` alone, so this is the
        // only way to stun something without wrapping it in swirling particles.
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 6, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 1, false, false, true));
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || entity instanceof Player) {
            return;
        }
        dropAt(entity, new ItemStack(Items.BONE));
        if (entity.getBbHeight() >= LONG_BONE_MIN_HEIGHT
                && entity.level().getRandom().nextFloat() < LONG_BONE_DROP_CHANCE) {
            dropAt(entity, new ItemStack(ModItems.LONG_BONE.get()));
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
        if (!stack.has(DataComponents.FOOD)) {
            return;
        }
        Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
        ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
        if (biomeKey == null) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        data.addForaged(1);
        if (data.getForagedBiomes().add(biomeKey.location())) {
            EvolutionManager.incrementCriterion(player, "forage_biomes", 1);
        }
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Item craftedItem = event.getCrafting().getItem();
        if (craftedItem == ModItems.CHOPPER.get() || craftedItem == ModItems.HAMMERSTONE.get()) {
            EvolutionManager.incrementCriterion(player, "craft_oldowan_tools", 1);
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        ServerPlayer player = event.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (player == null || player.tickCount % DAY_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        long currentDay = player.level().getDayTime() / TICKS_PER_DAY;
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.getLastCountedDay() < 0) {
            data.setLastCountedDay(currentDay);
            return;
        }
        if (currentDay > data.getLastCountedDay()) {
            data.setLastCountedDay(currentDay);
            EvolutionManager.incrementCriterion(player, "survive_days", 1);
        }
    }
}
