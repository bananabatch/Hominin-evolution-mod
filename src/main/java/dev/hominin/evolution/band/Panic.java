package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.network.CutsceneStartPayload;
import dev.hominin.evolution.stage.CutsceneGuard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What happens to a social animal left on its own.
 *
 * <p>Losing the whole band is not an inventory problem. A primate alone on the savannah
 * is a dead primate, and it knows it: the breath goes, the eyes shut, and when they open
 * again you are somewhere else with people around you and no clear memory of the walk.
 *
 * <p>What you were before stays where it happened, sitting with its head in its hands.
 * Go back for it and it will get up and come with you.
 */
public final class Panic {
    /** Long enough for the screen to go black before anything moves. */
    private static final int FADE_TICKS = 20;
    private static final int PROTECTED_TICKS = 200;
    /** How far the blind walk carries you. */
    private static final int WALK_DISTANCE = 90;

    private record Pending(long moveAt) {
    }

    private static final Map<UUID, Pending> pending = new HashMap<>();

    /**
     * The band is gone. Start the attack; the move happens once the screen is dark.
     *
     * <p>If the player is dead, or already watching something else, there is no attack and
     * no walk - they simply wake with a new band wherever they are. Stacking a cutscene on
     * top of another one, or holding it until later, is worse than not having it.
     */
    public static void begin(ServerPlayer player) {
        if (!CutsceneGuard.tryStart(player, PROTECTED_TICKS)) {
            Band.formNewBand(player);
            player.sendSystemMessage(Component.literal(
                    "Your band is gone. Others find you before you have to face a night alone.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PROTECTED_TICKS, 6, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS,
                1.0F, 0.7F);
        PacketDistributor.sendToPlayer(player, new CutsceneStartPayload(
                "There is nobody left.",
                "Your chest closes. You shut your eyes and walk."));
        // The one you were stays behind, where it happened.
        leaveOldSelf(player);
        pending.put(player.getUUID(), new Pending(player.level().getGameTime() + FADE_TICKS));
    }

    public static boolean isPanicking(ServerPlayer player) {
        return pending.containsKey(player.getUUID());
    }

    /** Called every player tick; does nothing unless this player is mid-attack. */
    public static void tick(ServerPlayer player) {
        Pending due = pending.get(player.getUUID());
        if (due == null || player.level().getGameTime() < due.moveAt()) {
            return;
        }
        pending.remove(player.getUUID());
        walkBlindly(player);
    }

    private static void leaveOldSelf(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BandMember old = ModEntities.BAND_MEMBER.get().create(level);
        if (old == null) {
            return;
        }
        BlockPos pos = player.blockPosition();
        old.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        old.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        old.setStage(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        old.setCustomName(player.getName().copy());
        old.setGrieving(true);
        level.addFreshEntity(old);
    }

    /** Somewhere else, with no memory of getting there, and strangers who are not strangers. */
    private static void walkBlindly(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos destination = somewhereElse(level, player.blockPosition(), player.getRandom().nextFloat());
        if (destination != null) {
            player.teleportTo(level, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.resetFallDistance();
        }
        Band.formNewBand(player);
        player.sendSystemMessage(Component.literal(
                "You come back to yourself a long way off, with people around you again. "
                        + "The one you were is still back there.").withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    private static BlockPos somewhereElse(ServerLevel level, BlockPos from, float seed) {
        for (int attempt = 0; attempt < 16; attempt++) {
            float angle = (seed + attempt * 0.17F) * Mth.TWO_PI;
            int distance = WALK_DISTANCE - 10 + level.random.nextInt(21);
            int x = from.getX() + Math.round(Mth.cos(angle) * distance);
            int z = from.getZ() + Math.round(Mth.sin(angle) * distance);
            level.getChunk(x >> 4, z >> 4);
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getFluidState(pos.below()).isEmpty() && level.getBlockState(pos.below()).isSolid()) {
                return pos;
            }
        }
        return null;
    }

    public static void forget(UUID player) {
        pending.remove(player);
    }

    private Panic() {
    }
}
