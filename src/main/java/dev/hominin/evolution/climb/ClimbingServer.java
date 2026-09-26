package dev.hominin.evolution.climb;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.advancement.HomininAdvancements;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.network.ClimbStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server's half of tree climbing: holding the flag, taking it away when rotten wood
 * breaks, and what being up a tree does for you.
 *
 * <p>What it does for you is the point. Most things that hunt on the ground cannot
 * follow you up a trunk, so a predator that sees you in a tree gives up on you - which
 * makes a tree the escape it was for every hominin before fire.
 */
public final class ClimbingServer {
    /** Rotten wood is checked once a second, so these are per-second chances. */
    private static final float DECAYING_SLIP_CHANCE = 0.06F;
    private static final float DECAYED_SLIP_CHANCE = 0.15F;
    /** How often a slip on a decayed log breaks it clean off the tree. */
    private static final float DECAYED_BREAK_CHANCE = 0.5F;

    /** After a slip the hands are not coming back to the trunk straight away. */
    private static final int SLIP_REGRIP_TICKS = 40;
    /** After a refused start, just long enough to stop the client asking every tick. */
    private static final int REFUSED_REGRIP_TICKS = 10;

    /**
     * A death by falling this long after a slip counts as the slip's fault. Long enough
     * to cover the fall from the tallest tree, short enough that it is still the tree.
     */
    private static final long LUCY_WINDOW_TICKS = 30 * 20;

    /** How far above a predator a player has to be before it stops trying. */
    private static final double SAFE_HEIGHT = 2.5D;
    private static final double PREDATOR_GIVE_UP_RADIUS = 24.0D;

    /** A little slack on the server's grip check, for the tick or two of lag behind the client. */
    private static final int SERVER_TRUNK_REACH = Climbing.CANOPY_REACH + 1;

    private static final double WALL_SLACK = 1.0D;

    private static final Map<UUID, Long> noGripUntil = new HashMap<>();
    private static final Map<UUID, Long> slippedAt = new HashMap<>();

    /** The client asks to start or stop. Stopping is always allowed. */
    public static void request(ServerPlayer player, boolean climbing) {
        if (climbing == Climbing.isClimbing(player)) {
            return;
        }
        if (climbing && !mayStart(player)) {
            PacketDistributor.sendToPlayer(player,
                    new ClimbStatePayload(player.getId(), false, REFUSED_REGRIP_TICKS));
            return;
        }
        set(player, climbing);
    }

    private static boolean mayStart(ServerPlayer player) {
        Long blocked = noGripUntil.get(player.getUUID());
        if (blocked != null && player.level().getGameTime() < blocked) {
            return false;
        }
        return !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isPassenger()
                && (Climbing.trunkNearby(player.level(), player.blockPosition(), SERVER_TRUNK_REACH)
                        && Climbing.climbsTrees(player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage())
                        || Climbing.grippedWall(player, WALL_SLACK) != null || Climbing.grippedLog(player) != null);
    }

    private static void set(ServerPlayer player, boolean climbing) {
        Climbing.setClimbing(player, climbing);
        player.resetFallDistance();
        PacketDistributor.sendToPlayersTrackingEntity(player, new ClimbStatePayload(player.getId(), climbing, 0));
    }

    public static void tick(ServerPlayer player) {
        boolean climbing = Climbing.isClimbing(player);
        if (climbing) {
            // Hand over hand is not falling, however far down it goes.
            player.resetFallDistance();
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (climbing && !Climbing.trunkNearby(player.level(), player.blockPosition(), SERVER_TRUNK_REACH)
                && Climbing.grippedWall(player, WALL_SLACK) == null) {
            // Nothing to hold on to, whatever the client thinks.
            set(player, false);
            PacketDistributor.sendToPlayer(player, new ClimbStatePayload(player.getId(), false, 0));
        } else if (climbing) {
            checkRottenWood(player);
        }
        shakeOffPursuers(player);
    }

    /**
     * Decaying wood takes a climber's weight most of the time. Decayed wood, riddled
     * through, often does not - and when it goes, it can go with the branch.
     */
    private static void checkRottenWood(ServerPlayer player) {
        Level level = player.level();
        AABB reach = player.getBoundingBox().inflate(0.6D, 0.5D, 0.6D);
        BlockPos decayed = null;
        boolean decaying = false;
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(reach.minX), Mth.floor(reach.minY), Mth.floor(reach.minZ),
                Mth.floor(reach.maxX), Mth.floor(reach.maxY), Mth.floor(reach.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.DECAYED_LOG.get())) {
                decayed = pos.immutable();
            } else if (state.is(ModBlocks.DECAYING_LOG.get())) {
                decaying = true;
            }
        }
        float chance = decayed != null ? DECAYED_SLIP_CHANCE : decaying ? DECAYING_SLIP_CHANCE : 0.0F;
        if (chance > 0.0F && player.getRandom().nextFloat() < chance) {
            slip(player, decayed);
        }
    }

    private static void slip(ServerPlayer player, @Nullable BlockPos decayed) {
        long now = player.level().getGameTime();
        Climbing.setClimbing(player, false);
        noGripUntil.put(player.getUUID(), now + SLIP_REGRIP_TICKS);
        slippedAt.put(player.getUUID(), now);

        PacketDistributor.sendToPlayersTrackingEntity(player, new ClimbStatePayload(player.getId(), false, 0));
        PacketDistributor.sendToPlayer(player, new ClimbStatePayload(player.getId(), false, SLIP_REGRIP_TICKS));

        Level level = player.level();
        BlockPos soundAt = decayed != null ? decayed : player.blockPosition();
        level.playSound(null, soundAt, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
        if (decayed != null && player.getRandom().nextFloat() < DECAYED_BREAK_CHANCE) {
            level.destroyBlock(decayed, true, player);
        }

        // Pushed off the trunk, so the fall is a fall and not a slide down the bark.
        Vec3 away = decayed != null
                ? player.position().subtract(Vec3.atCenterOf(decayed)).multiply(1.0D, 0.0D, 1.0D)
                : Vec3.ZERO;
        if (away.lengthSqr() > 1.0E-4D) {
            away = away.normalize().scale(0.25D);
        }
        player.setDeltaMovement(away.x, -0.1D, away.z);
        player.hurtMarked = true;
        player.displayClientMessage(Component.literal("The rotten wood gives way under your grip!"), true);
    }

    /**
     * Up a tree, or resting on top of one, and clear of the ground by more than a
     * predator can reach.
     */
    private static boolean isOutOfReach(Player player, LivingEntity hunter) {
        if (player.getY() - hunter.getY() < SAFE_HEIGHT) {
            return false;
        }
        if (Climbing.isClimbing(player)) {
            return true;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1D, player.getZ());
        BlockState standing = player.level().getBlockState(below);
        return player.onGround() && (standing.is(BlockTags.LEAVES) || standing.is(BlockTags.LOGS));
    }

    /** A predator never picks, or keeps, a target it cannot reach up a tree. */
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity hunter = event.getEntity();
        if (hunter.level().isClientSide() || !hunter.getType().is(ModTags.EntityTypes.PREDATORS)) {
            return;
        }
        LivingEntity target = event.getNewAboutToBeSetTarget();
        boolean outOfReach = target instanceof ServerPlayer player
                ? isOutOfReach(player, hunter)
                : target instanceof BandMember member && member.isUpATree()
                        && member.getY() - hunter.getY() >= SAFE_HEIGHT;
        if (outOfReach) {
            event.setCanceled(true);
        } else if (target instanceof ServerPlayer player) {
            dev.hominin.evolution.band.Band.defend(player, hunter);
            dev.hominin.evolution.guide.Tips.huntedBy(player, hunter);
            dev.hominin.evolution.band.Relations.alliesJoin(player, hunter, false);
        }
    }

    /**
     * Anything already on the player's trail when they got up the tree loses interest.
     * Target changes only catch new targets, so this sweep covers the ones already set.
     */
    public static void shakeOffPursuers(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(PREDATOR_GIVE_UP_RADIUS);
        boolean lostOne = false;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area,
                mob -> mob.getTarget() == player && mob.getType().is(ModTags.EntityTypes.PREDATORS))) {
            if (isOutOfReach(player, mob)) {
                mob.setTarget(null);
                mob.getNavigation().stop();
                lostOne = true;
            }
        }
        if (lostOne) {
            player.displayClientMessage(Component.literal("Below you, it circles, then gives up."), true);
        }
    }

    /** Lucy probably died falling from a tree. Any fatal fall earns her name. */
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().is(DamageTypeTags.IS_FALL)) {
            HomininAdvancements.award(player, HomininAdvancements.LUCY);
        }
    }

    /** Anyone who starts watching a climber needs to see them climbing. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && Climbing.isClimbing(target)
                && event.getEntity() instanceof ServerPlayer watcher) {
            PacketDistributor.sendToPlayer(watcher, new ClimbStatePayload(target.getId(), true, 0));
        }
    }

    public static void forget(UUID player) {
        noGripUntil.remove(player);
        slippedAt.remove(player);
    }

    private ClimbingServer() {
    }
}
