package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Kneeling to drink. The crosshair passes straight through water - the game only picks
 * solid blocks with it - so an ordinary right-click at a river reports nothing to aim at.
 * This traces the look again with fluids included, the way a bucket finds water.
 */
public final class Drinking {
    /** About an arm and a neck: you have to be at the water, not near it. */
    private static final double REACH = 4.5D;
    private static final int COOLDOWN_TICKS = 20;

    private static final Map<UUID, Long> lastDrink = new HashMap<>();

    /** Right-clicked with an empty hand: drink, if there is water in front of them. */
    public static void tryDrink(ServerPlayer player) {
        long now = player.level().getGameTime();
        if (now - lastDrink.getOrDefault(player.getUUID(), -99999L) < COOLDOWN_TICKS) {
            return;
        }
        BlockPos water = waterInReach(player);
        if (water == null) {
            return;
        }
        lastDrink.put(player.getUUID(), now);
        // Sick from bad meat, you drink past thirst: it is going straight through you.
        if (Thirst.get(player) >= Thirst.MAX && !FoodIllness.has(player)) {
            player.displayClientMessage(Component.literal("You have drunk your fill."), true);
            return;
        }
        dev.hominin.evolution.event.EvolutionEventHandler.drinkWater(player, water);
    }

    /** The water the player is looking at, or standing in, within reach. */
    @Nullable
    private static BlockPos waterInReach(ServerPlayer player) {
        Level level = player.level();
        Vec3 eyes = player.getEyePosition();
        Vec3 look = eyes.add(player.getViewVector(1.0F).scale(REACH));
        BlockHitResult hit = level.clip(new ClipContext(eyes, look, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            return hit.getBlockPos();
        }
        // Standing in it: a hominin up to its knees in a river does not have to aim.
        if (player.isInWater()) {
            return player.blockPosition();
        }
        return null;
    }

    public static void forget(UUID player) {
        lastDrink.remove(player);
    }

    private Drinking() {
    }
}
