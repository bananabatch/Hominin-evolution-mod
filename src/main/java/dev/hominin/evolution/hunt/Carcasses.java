package dev.hominin.evolution.hunt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.block.CarcassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What is left behind, and what comes for it.
 *
 * <p>Every animal that dies leaves a carcass where it fell - killed, or simply worn out.
 * Animals do die on their own out here: exhaustion, disease, an old wound that never
 * closed. That is the scavenger's living, and it is how a hominin ate meat long before
 * it could catch anything.
 */
public final class Carcasses {
    /** How often a loaded animal is checked against its own mortality, and how likely it is to fail. */
    private static final int MORTALITY_CHECK_TICKS = 1200;
    private static final float MORTALITY_CHANCE = 0.02F;
    /** Never where the player can watch it happen for no reason. */
    private static final double OUT_OF_SIGHT = 24.0D;
    private static final double MORTALITY_RANGE = 96.0D;

    /** How often a fresh kill brings something bigger along. */
    private static final float HYENA_CHANCE = 0.25F;
    private static final double HYENA_CROWDING = 48.0D;

    /**
     * Whether this death was somebody's doing. A kill you made is a kill you were
     * standing at - there was nobody else there first, and treating it as an abandoned
     * carcass let a player farm band members by killing cows.
     */
    private static boolean killedByHand(LivingEntity dead) {
        Long when = handled.remove(dead.getUUID());
        if (when != null && dead.level().getGameTime() - when < HANDLED_MEMORY) {
            return true;
        }
        var source = dead.getLastDamageSource();
        return source != null
                && (source.getEntity() instanceof Player || source.getEntity() instanceof BandMember);
    }

    /**
     * Everything a player or the band has drawn blood from, and when. The last hit is
     * not enough to go on: an animal you speared bleeds out a minute later from magic
     * damage with no attacker on it at all, and that is exactly the persistence hunt -
     * your kill, arriving late. Five minutes covers any wound this mod can open.
     */
    private static final Map<UUID, Long> handled = new HashMap<>();
    private static final long HANDLED_MEMORY = 6000L;

    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        if (hurt.level().isClientSide() || hurt instanceof Player || hurt instanceof BandMember) {
            return;
        }
        var attacker = event.getSource().getEntity();
        if (attacker instanceof Player || attacker instanceof BandMember) {
            if (handled.size() > 4096) {
                handled.clear();
            }
            handled.put(hurt.getUUID(), hurt.level().getGameTime());
        }
    }

    /** Whether this player's band already has everybody it can hold. */
    private static boolean bandIsFull(ServerPlayer player) {
        var stage = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage();
        return dev.hominin.evolution.band.Band.all(player).size()
                >= dev.hominin.evolution.band.BandSizes.of(stage).maxMembers();
    }

    /** Leaves a carcass where something died, and sometimes tells a hyena about it. */
    public static void onDeath(LivingEntity dead) {
        if (dead instanceof Player || dead instanceof BandMember || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        if (dead.getBbHeight() < 0.5F && dead.getMaxHealth() < 6.0F) {
            // A chicken leaves feathers and not much else.
            return;
        }
        BlockPos pos = placeCarcass(level, dead.blockPosition(), false);
        if (pos == null) {
            return;
        }
        // Somebody else got here first, and they are not an animal. A hominin at a kill
        // keeps the scavengers off it, which is most of the reason to be at one.
        if (!killedByHand(dead) && level.random.nextFloat() < LONER_AT_KILL_CHANCE
                && leaveLoner(level, pos)) {
            return;
        }
        if (level.random.nextFloat() < HYENA_CHANCE) {
            callScavenger(level, pos);
        }
    }

    /** Puts a carcass down at or beside a spot, if there is anywhere for it to lie. */
    public static BlockPos placeCarcass(ServerLevel level, BlockPos around, boolean large) {
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-1, -1, -1), around.offset(1, 1, 1))) {
            if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isSolid()) {
                continue;
            }
            Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.random);
            BlockState state = ModBlocks.CARCASS.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, facing)
                    .setValue(CarcassBlock.LARGE, large);
            if (state.canSurvive(level, pos)) {
                level.setBlock(pos, state, 3);
                return pos.immutable();
            }
        }
        return null;
    }

    /** How often a fresh kill already has somebody at it, and an old bone bed does. */
    private static final float LONER_AT_KILL_CHANCE = 0.12F;
    private static final float LONER_AT_BONE_BED_CHANCE = 0.3F;
    /** How often a player's surroundings are searched for an unvisited bone bed. */
    private static final int LONER_CHECK_TICKS = 600;
    private static final int LONER_SEARCH_RADIUS = 20;

    /** Bone beds already looked over, so each one is only ever worth one meeting. */
    private static final java.util.Set<BlockPos> visitedBeds = new java.util.HashSet<>();

    /**
     * Somebody of your own kind, alone at a carcass. A hominin whose band is gone is not
     * dead - it is sitting somewhere with nothing left, living off whatever it finds, and
     * it will come with anyone who comes for it.
     */
    private static boolean leaveLoner(ServerLevel level, BlockPos carcass) {
        if (!(level.getNearestPlayer(carcass.getX(), carcass.getY(), carcass.getZ(), 160.0D, false)
                instanceof ServerPlayer nearest)) {
            return false;
        }
        // Nobody joins a band that is already as big as it gets. A stranger at a kill
        // is a chance at a recruit, and a chance you cannot take is not worth spawning.
        if (bandIsFull(nearest)) {
            return false;
        }
        BandMember loner = ModEntities.BAND_MEMBER.get().create(level);
        if (loner == null) {
            return false;
        }
        BlockPos spot = dev.hominin.evolution.band.Band.standingSpotNear(level, carcass, 2,
                level.random.nextFloat() * Mth.TWO_PI);
        loner.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        loner.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        loner.setStage(nearest.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage());
        loner.ensureName();
        loner.setGrieving(true);
        level.addFreshEntity(loner);
        return true;
    }

    /**
     * Old bones out in the country often have somebody sitting by them. Checked around each
     * player rather than at worldgen, so the meeting happens where somebody is there to have it.
     */
    public static void tickLoners(ServerPlayer player) {
        if (player.tickCount % LONER_CHECK_TICKS != 200 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-LONER_SEARCH_RADIUS, -6, -LONER_SEARCH_RADIUS),
                origin.offset(LONER_SEARCH_RADIUS, 6, LONER_SEARCH_RADIUS))) {
            var state = level.getBlockState(pos);
            if (!state.is(ModBlocks.CARCASS.get())
                    || !state.getValue(dev.hominin.evolution.block.CarcassBlock.LARGE)) {
                continue;
            }
            BlockPos bed = pos.immutable();
            if (!visitedBeds.add(bed)) {
                continue;
            }
            if (level.random.nextFloat() < LONER_AT_BONE_BED_CHANCE) {
                leaveLoner(level, bed);
            }
            return;
        }
    }

    /** Meat on the ground carries. Something with a better nose is already on its way. */
    private static void callScavenger(ServerLevel level, BlockPos carcass) {
        if (!level.getEntitiesOfClass(dev.hominin.evolution.entity.Pachycrocuta.class,
                new net.minecraft.world.phys.AABB(carcass).inflate(HYENA_CROWDING)).isEmpty()) {
            return;
        }
        for (int attempt = 0; attempt < 8; attempt++) {
            float angle = level.random.nextFloat() * Mth.TWO_PI;
            int distance = 20 + level.random.nextInt(16);
            int x = carcass.getX() + Math.round(Mth.cos(angle) * distance);
            int z = carcass.getZ() + Math.round(Mth.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getFluidState(pos.below()).isEmpty() || !level.getBlockState(pos.below()).isSolid()) {
                continue;
            }
            var hyena = ModEntities.PACHYCROCUTA.get().create(level);
            if (hyena == null) {
                return;
            }
            hyena.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            hyena.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            level.addFreshEntity(hyena);
            hyena.getNavigation().moveTo(carcass.getX() + 0.5D, carcass.getY(), carcass.getZ() + 0.5D, 1.1D);
            Player nearest = level.getNearestPlayer(carcass.getX(), carcass.getY(), carcass.getZ(), 48.0D, false);
            if (nearest != null) {
                nearest.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Something else has smelled the kill."), true);
            }
            return;
        }
    }

    /**
     * Animals wear out, sicken, and die of old wounds without anyone's help. Checked around
     * each player so the country they walk through has bones in it.
     */
    public static void tickMortality(ServerPlayer player) {
        if (player.tickCount % MORTALITY_CHECK_TICKS != 400 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        var animals = level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(MORTALITY_RANGE),
                animal -> animal.isAlive() && !animal.isBaby()
                        && animal.distanceToSqr(player) > OUT_OF_SIGHT * OUT_OF_SIGHT);
        if (animals.isEmpty() || level.random.nextFloat() >= MORTALITY_CHANCE * animals.size()) {
            return;
        }
        Animal doomed = animals.get(level.random.nextInt(animals.size()));
        // Whatever it was - a wound that never closed, thirst, a bad season - it stops here.
        doomed.hurt(level.damageSources().starve(), doomed.getMaxHealth() * 2.0F);
    }

    private Carcasses() {
    }
}
