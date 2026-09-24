package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Fire as something kept, not found. A hearth is a fire pit (see
 * {@link dev.hominin.evolution.block.FirePitBlockEntity}) - or, in a world from before there were fire pits,
 * a campfire - and it burns for as long as somebody feeds it.
 *
 * <p>It cooks, and at night nothing that hunts will come into its light.
 */
public final class Hearths extends SavedData {
    private static final String NAME = "hominin_hearths";
    /** A freshly lit hearth: a couple of minutes on the tinder alone. */
    public static final int LIGHT_FUEL = 2400;
    private static final int MAX_FUEL = 24000;
    /** How far its light keeps things off, and how close counts as sitting at it. */
    public static final double LIGHT_RADIUS = 16.0D;
    private static final double WARD_RADIUS = 24.0D;

    /** Where each hearth is, and the game time it burns out. */
    private final Map<BlockPos, Long> burnsOutAt = new HashMap<>();

    private static Hearths of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Hearths::new, Hearths::load), NAME);
    }

    private static Hearths load(CompoundTag tag, HolderLookup.Provider registries) {
        Hearths hearths = new Hearths();
        for (Tag entry : tag.getList("Hearths", Tag.TAG_COMPOUND)) {
            CompoundTag hearth = (CompoundTag) entry;
            hearths.burnsOutAt.put(BlockPos.of(hearth.getLong("Pos")), hearth.getLong("Until"));
        }
        return hearths;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Long> entry : burnsOutAt.entrySet()) {
            CompoundTag hearth = new CompoundTag();
            hearth.putLong("Pos", entry.getKey().asLong());
            hearth.putLong("Until", entry.getValue());
            list.add(hearth);
        }
        tag.put("Hearths", list);
        return tag;
    }

    // ------------------------------------------------------------ lighting and feeding

    /** A drill worked on the ground by erectus or later: a hearth, lit. */
    public static void light(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 11);
        Hearths hearths = of(level);
        hearths.burnsOutAt.put(pos.immutable(), level.getGameTime() + LIGHT_FUEL);
        hearths.setDirty();
    }

    /** A fire pit has caught: it is a hearth for as long as it burns. The pit keeps its own time. */
    public static void pitLit(ServerLevel level, BlockPos pos) {
        Hearths hearths = of(level);
        hearths.burnsOutAt.put(pos.immutable(), Long.MAX_VALUE);
        hearths.setDirty();
    }

    public static void pitOut(ServerLevel level, BlockPos pos) {
        Hearths hearths = of(level);
        if (hearths.burnsOutAt.remove(pos) != null) {
            hearths.setDirty();
        }
    }

    /** A campfire or fire pit, burning. */
    public static boolean isLitHearth(BlockState state) {
        if (state.is(Blocks.CAMPFIRE)) {
            return state.getValue(CampfireBlock.LIT);
        }
        return state.is(dev.hominin.evolution.ModBlocks.FIRE_PIT.get())
                && state.getValue(dev.hominin.evolution.block.FirePitBlock.LIT);
    }

    /** How long this feeds a fire for, in ticks; zero if it does not burn. */
    private static int fuelValue(ItemStack stack) {
        if (stack.is(Items.STICK) || stack.is(ModItems.SHARPENED_STICK.get())) {
            return 1200;
        }
        if (ModItems.isLongBranch(stack)) {
            return 3000;
        }
        if (stack.is(net.minecraft.tags.ItemTags.LOGS)) {
            return 6000;
        }
        if (stack.is(ModItems.THATCH.get()) || stack.is(ModItems.NESTING_MATERIAL.get())) {
            return 600;
        }
        return 0;
    }

    /**
     * Right-clicking a hearth. Fuel feeds it; a drill relights a dead one. Returns true when the
     * click was used.
     */
    public static boolean use(ServerPlayer player, BlockPos pos, ItemStack held) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.CAMPFIRE)) {
            return false;
        }
        Hearths hearths = of(level);
        long now = level.getGameTime();
        if (held.is(ModItems.FIRE_DRILL.get()) && !state.getValue(CampfireBlock.LIT)) {
            level.setBlock(pos, state.setValue(CampfireBlock.LIT, true), 11);
            hearths.burnsOutAt.put(pos.immutable(), now + LIGHT_FUEL);
            hearths.setDirty();
            held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
            player.displayClientMessage(Component.literal("The embers catch again."), true);
            return true;
        }
        int fuel = fuelValue(held);
        if (fuel <= 0 || !state.getValue(CampfireBlock.LIT)) {
            return false;
        }
        long until = Math.max(now, hearths.burnsOutAt.getOrDefault(pos, now)) + fuel;
        until = Math.min(until, now + MAX_FUEL);
        hearths.burnsOutAt.put(pos.immutable(), until);
        hearths.setDirty();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
        player.displayClientMessage(Component.literal("The fire takes it. About " + Math.max(1, (until - now) / 1200)
                + " minutes of fire left."), true);
        return true;
    }

    // ------------------------------------------------------------ burning down

    /** Every five seconds, per level: fires with nothing left to burn go out. */
    public static void tickLevel(ServerLevel level) {
        if (level.getGameTime() % 100L != 0L) {
            return;
        }
        Hearths hearths = of(level);
        long now = level.getGameTime();
        boolean changed = false;
        for (Iterator<Map.Entry<BlockPos, Long>> it = hearths.burnsOutAt.entrySet().iterator(); it.hasNext();) {
            Map.Entry<BlockPos, Long> entry = it.next();
            BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(dev.hominin.evolution.ModBlocks.FIRE_PIT.get())) {
                // A pit keeps its own time; it only has to still be burning.
                if (!isLitHearth(state)) {
                    it.remove();
                    changed = true;
                }
                continue;
            }
            if (!state.is(Blocks.CAMPFIRE)) {
                it.remove();
                changed = true;
                continue;
            }
            if (now >= entry.getValue() && state.getValue(CampfireBlock.LIT)) {
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), 11);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
                it.remove();
                changed = true;
                for (ServerPlayer player : level.players()) {
                    if (player.blockPosition().closerThan(pos, 24.0D)) {
                        player.displayClientMessage(Component.literal("The hearth burns down to ash.")
                                .withStyle(ChatFormatting.GRAY), true);
                    }
                }
            }
        }
        if (changed) {
            hearths.setDirty();
        }
    }

    /** The nearest lit hearth this player is sitting by, if any. */
    @Nullable
    public static BlockPos litNear(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        Hearths hearths = of(level);
        BlockPos best = null;
        for (BlockPos pos : hearths.burnsOutAt.keySet()) {
            if (pos.closerToCenterThan(player.position(), radius) && level.isLoaded(pos)
                    && isLitHearth(level.getBlockState(pos))) {
                if (best == null || pos.distToCenterSqr(player.position()) < best.distToCenterSqr(player.position())) {
                    best = pos;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------ the night

    private static final Map<UUID, int[]> nights = new HashMap<>();
    private static final int NIGHT_SECONDS_NEEDED = 300;

    /**
     * Once a second per player. At night a lit hearth keeps everything that hunts out of its
     * light; and a night spent at one - awake beside it, or asleep beside it - counts for the
     * erectus checklist.
     */
    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || player.isSpectator()) {
            return;
        }
        long time = player.level().getDayTime() % 24000L;
        boolean night = time >= 13000L && time < 23000L;
        int[] record = nights.computeIfAbsent(player.getUUID(), id -> new int[2]);
        BlockPos hearth = litNear(player, LIGHT_RADIUS);
        if (night) {
            if (hearth != null) {
                record[0]++;
                if (player.isSleeping()) {
                    record[1] = 1;
                }
                ward(player, hearth);
            }
            return;
        }
        // Morning: was the night spent by the fire?
        if (record[0] > 0 || record[1] > 0) {
            if (record[0] >= NIGHT_SECONDS_NEEDED || record[1] > 0) {
                EvolutionManager.incrementCriterion(player, "hearth_night", 1);
                player.displayClientMessage(Component.literal("A whole night by the fire, and nothing came near it.")
                        .withStyle(ChatFormatting.GOLD), true);
            }
            record[0] = 0;
            record[1] = 0;
        }
    }

    /** Anything that hunts, near the fire at night: it will not come into the light. */
    private static void ward(ServerPlayer player, BlockPos hearth) {
        for (PathfinderMob mob : player.level().getEntitiesOfClass(PathfinderMob.class,
                player.getBoundingBox().inflate(WARD_RADIUS),
                m -> m.isAlive() && m.getType().is(ModTags.EntityTypes.PREDATORS)
                        && !dev.hominin.evolution.combat.Scare.isScared(m))) {
            if (mob.blockPosition().closerThan(hearth, WARD_RADIUS)) {
                dev.hominin.evolution.combat.Scare.scare(mob, net.minecraft.world.phys.Vec3.atCenterOf(hearth), 200);
            }
        }
    }

    public static void forget(UUID player) {
        nights.remove(player);
    }

    // ------------------------------------------------------------ what dies burning

    /**
     * Anything a thrown torch set burning that dies of it comes apart cooked - mostly. Four pieces in ten
     * were in the flames too long.
     */
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        net.minecraft.world.entity.LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        long torched = dead.getPersistentData().getLong(dev.hominin.evolution.entity.ThrownTorch.TORCHED);
        if (torched <= 0L || dead.level().getGameTime() - torched > 600L) {
            return;
        }
        java.util.List<net.minecraft.world.entity.item.ItemEntity> charred = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.item.ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!isRawMeat(stack)) {
                continue;
            }
            int burnt = 0;
            for (int i = 0; i < stack.getCount(); i++) {
                if (dead.getRandom().nextFloat() < 0.4F) {
                    burnt++;
                }
            }
            int cooked = stack.getCount() - burnt;
            if (cooked > 0) {
                drop.setItem(new ItemStack(ModItems.COOKED_MEAT_CHUNK.get(), cooked));
                if (burnt > 0) {
                    charred.add(new net.minecraft.world.entity.item.ItemEntity(dead.level(), drop.getX(), drop.getY(),
                            drop.getZ(), new ItemStack(ModItems.CHARRED_MEAT.get(), burnt)));
                }
            } else {
                drop.setItem(new ItemStack(ModItems.CHARRED_MEAT.get(), burnt));
            }
        }
        event.getDrops().addAll(charred);
    }

    private static boolean isRawMeat(ItemStack stack) {
        return stack.is(ModItems.MEAT_CHUNK.get()) || stack.is(net.neoforged.neoforge.common.Tags.Items.FOODS_RAW_MEAT)
                || stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON) || stack.is(Items.CHICKEN)
                || stack.is(Items.RABBIT);
    }
}
