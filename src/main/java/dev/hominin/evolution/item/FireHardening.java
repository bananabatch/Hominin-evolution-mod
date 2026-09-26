package dev.hominin.evolution.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Turning wood in a fire until it case-hardens. A sharpened spear held to a fire - a lit fire pit, a campfire, open
 * flame - and turned four times (right-click it four times, or just hold it there) comes out fire-hardened. A
 * workable shaft does the same, once you have seen what it could be (see {@link SuperWeapons}): the whole length
 * hardened and balanced, it is a Schoningen spear.
 */
public final class FireHardening {
    public static final int TURNS = 4;
    /** Take it out of the fire this long and you start again. */
    private static final long COOLS_TICKS = 60L;

    private record Turning(BlockPos fire, int slot, int turns, long lastAt) {
    }

    private static final Map<UUID, Turning> turning = new HashMap<>();

    /** Something burning to hold wood in. */
    public static boolean isFire(BlockState state) {
        return dev.hominin.evolution.survival.Hearths.isLitHearth(state) || state.is(BlockTags.FIRE);
    }

    /** Whether this is wood a fire could do something for - so the client knows not to use it any other way. */
    private static boolean hardenable(ItemStack held) {
        return held.is(ModItems.SHARPENED_SPEAR.get()) || held.is(ModItems.WORKABLE_SHAFT.get());
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        // Open flame has nothing to click: the ground it burns on does as well.
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !hardenable(held)
                || !isFire(event.getLevel().getBlockState(event.getPos()))
                        && !event.getLevel().getBlockState(event.getPos().above()).is(BlockTags.FIRE)) {
            return;
        }
        // Sneaking with a shaft still feeds the fire with it, as any branch would.
        if (event.getEntity().isShiftKeyDown() && held.is(ModItems.WORKABLE_SHAFT.get())) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            // The client does not know what the server will make of it: it only stops the spear being drawn back.
            if (held.is(ModItems.SHARPENED_SPEAR.get())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        ItemStack result = result(player, held);
        if (result.isEmpty()) {
            // A plain shaft is fuel, as far as a fire is concerned - unless you have thought about what it could be.
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        turn(player, event.getPos(), held, result);
    }

    /** What this piece of wood becomes in a fire, in these hands - or nothing. */
    private static ItemStack result(ServerPlayer player, ItemStack held) {
        if (held.is(ModItems.SHARPENED_SPEAR.get())) {
            return new ItemStack(ModItems.FIRE_HARDENED_SPEAR.get());
        }
        if (held.is(ModItems.WORKABLE_SHAFT.get()) && SuperWeapons.knows(player, SuperWeapons.SCHONINGEN)) {
            return new ItemStack(ModItems.SCHONINGEN_SPEAR.get());
        }
        return ItemStack.EMPTY;
    }

    private static void turn(ServerPlayer player, BlockPos fire, ItemStack held, ItemStack result) {
        long now = player.level().getGameTime();
        int slot = player.getInventory().selected;
        Turning was = turning.get(player.getUUID());
        int turns = was != null && was.fire().equals(fire) && was.slot() == slot && now - was.lastAt() < COOLS_TICKS
                ? was.turns() + 1 : 1;
        if (was != null && now == was.lastAt()) {
            // One turn a tick, however the click arrived.
            return;
        }
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        ServerLevel level = player.serverLevel();
        level.playSound(null, fire, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 1.0F, 0.9F + turns * 0.05F);
        level.sendParticles(ParticleTypes.SMOKE, fire.getX() + 0.5D, fire.getY() + 0.9D, fire.getZ() + 0.5D, 6, 0.2D,
                0.1D, 0.2D, 0.01D);
        level.sendParticles(ParticleTypes.FLAME, fire.getX() + 0.5D, fire.getY() + 0.8D, fire.getZ() + 0.5D, 2, 0.15D,
                0.05D, 0.15D, 0.005D);
        if (turns < TURNS) {
            turning.put(player.getUUID(), new Turning(fire.immutable(), slot, turns, now));
            player.displayClientMessage(Component.literal((held.is(ModItems.WORKABLE_SHAFT.get())
                    ? "You turn the whole shaft through the flames" : "You turn the point in the flames")
                    + "... (" + turns + "/" + TURNS + ")").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        turning.remove(player.getUUID());
        // Wear carries over: a battered spear hardened is still a battered spear.
        if (held.isDamageableItem() && result.isDamageableItem() && held.getMaxDamage() > 0) {
            float worn = held.getDamageValue() / (float) held.getMaxDamage();
            result.setDamageValue(Math.min(result.getMaxDamage() - 1, Math.round(worn * result.getMaxDamage())));
        }
        if (held.has(DataComponents.CUSTOM_NAME)) {
            result.set(DataComponents.CUSTOM_NAME, held.get(DataComponents.CUSTOM_NAME));
        }
        held.shrink(1);
        if (held.isEmpty()) {
            player.getInventory().setItem(slot, result);
        } else if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        level.playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
        EvolutionManager.incrementCriterion(player, "fire_harden_spear", 1);
        if (result.is(ModItems.SCHONINGEN_SPEAR.get())) {
            SuperWeapons.made(player, result);
        } else {
            player.displayClientMessage(Component.literal("The point comes out black and hard as bone: a fire-hardened spear.")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    public static void forget(UUID player) {
        turning.remove(player);
    }

    private FireHardening() {
    }
}
