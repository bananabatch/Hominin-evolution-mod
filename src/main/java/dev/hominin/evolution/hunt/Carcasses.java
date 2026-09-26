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

    /** Leaves a carcass where something died, and sometimes tells a hyena about it. */
    public static void onDeath(LivingEntity dead) {
        if (dead instanceof Player || !(dead.level() instanceof ServerLevel level)) {
            return;
        }
        // One of us, or a cousin close enough: what is left is a hominin carcass.
        if (dead instanceof BandMember || dead instanceof dev.hominin.evolution.entity.Chimpanzee
                || dead instanceof dev.hominin.evolution.entity.Bonobo) {
            placeCarcass(level, dead.blockPosition(), ModBlocks.HOMININ_CARCASS.get().defaultBlockState());
            return;
        }
        if (dead.getBbHeight() < 0.5F && dead.getMaxHealth() < 6.0F) {
            // A chicken leaves feathers and not much else.
            return;
        }
        // Megafauna leave a giant carcass: more meat than a band can carry, and a smell that
        // brings in everything with a nose for miles.
        boolean mega = dead.getType().is(dev.hominin.evolution.ModTags.EntityTypes.MEGAFAUNA);
        BlockPos pos = mega
                ? placeCarcass(level, dead.blockPosition(), ModBlocks.GIANT_CARCASS.get().defaultBlockState())
                : placeCarcass(level, dead.blockPosition(), false);
        if (pos == null) {
            return;
        }
        boolean yours = killedByHand(dead);
        boolean hard = dev.hominin.evolution.survival.Seasons.strained(level);
        if (mega) {
            if (level.random.nextFloat() < (hard ? 0.6F : 0.45F)) {
                callGiantHyena(level, pos);
            } else if (level.random.nextFloat() < 0.4F) {
                callClan(level, pos, yours);
            }
            return;
        }
        // A clan goes after other animals' kills as much as it makes its own - yours included.
        float clanChance = yours ? (hard ? 0.4F : 0.25F) : HYENA_CHANCE;
        if (level.random.nextFloat() < clanChance) {
            callClan(level, pos, yours);
        } else if (level.random.nextFloat() < 0.06F) {
            callGiantHyena(level, pos);
        }
    }

    /** Puts a carcass down at or beside a spot, if there is anywhere for it to lie. */
    public static BlockPos placeCarcass(ServerLevel level, BlockPos around, boolean large) {
        return placeCarcass(level, around, ModBlocks.CARCASS.get().defaultBlockState().setValue(CarcassBlock.LARGE, large));
    }

    /** Puts this carcass down at or beside a spot, facing any way. */
    public static BlockPos placeCarcass(ServerLevel level, BlockPos around, BlockState carcass) {
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-1, -1, -1), around.offset(1, 1, 1))) {
            if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isSolid()) {
                continue;
            }
            Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(level.random);
            BlockState state = carcass.setValue(HorizontalDirectionalBlock.FACING, facing);
            if (state.canSurvive(level, pos)) {
                level.setBlock(pos, state, 3);
                if (state.is(ModBlocks.CARCASS.get()) || state.is(ModBlocks.GIANT_CARCASS.get())) {
                    recordKill(level, pos, state.is(ModBlocks.GIANT_CARCASS.get()));
                }
                return pos.immutable();
            }
        }
        return null;
    }

    // ------------------------------------------------------------ who comes for it

    /**
     * How big a clan. Crocuta lived in clans, and a clan took kills off everything else on the
     * plain by turning up in numbers. Hard times bring bigger, hungrier clans.
     */
    private static int clanSize(ServerLevel level) {
        if (dev.hominin.evolution.survival.Seasons.strained(level)) {
            return 4 + level.random.nextInt(3);
        }
        if (dev.hominin.evolution.survival.Seasons.plentiful(level)) {
            return 2 + level.random.nextInt(3);
        }
        return 3 + level.random.nextInt(3);
    }

    /** Somewhere on open ground, this far off the carcass, for something to walk in from. */
    @javax.annotation.Nullable
    private static BlockPos approachFrom(ServerLevel level, BlockPos carcass, int min, int span) {
        for (int attempt = 0; attempt < 8; attempt++) {
            float angle = level.random.nextFloat() * Mth.TWO_PI;
            int distance = min + level.random.nextInt(span);
            int x = carcass.getX() + Math.round(Mth.cos(angle) * distance);
            int z = carcass.getZ() + Math.round(Mth.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (level.getFluidState(pos.below()).isEmpty() && level.getBlockState(pos.below()).isSolid()) {
                return pos;
            }
        }
        return null;
    }

    /**
     * A clan, coming for the carcass. If you made the kill, they are coming to take it off you -
     * and they will tell you so before they do.
     */
    public static int callClan(ServerLevel level, BlockPos carcass, boolean yours) {
        if (!level.getEntitiesOfClass(dev.hominin.evolution.entity.Crocuta.class,
                new net.minecraft.world.phys.AABB(carcass).inflate(HYENA_CROWDING)).isEmpty()) {
            return 0;
        }
        BlockPos from = approachFrom(level, carcass, 20, 14);
        if (from == null) {
            return 0;
        }
        UUID clanId = UUID.randomUUID();
        int size = clanSize(level);
        int came = 0;
        for (int i = 0; i < size; i++) {
            var hyena = ModEntities.CROCUTA.get().create(level);
            if (hyena == null) {
                break;
            }
            BlockPos spot = dev.hominin.evolution.band.Band.standingSpotNear(level, from, 1 + i / 2,
                    level.random.nextFloat() * Mth.TWO_PI);
            hyena.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            hyena.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
            hyena.joinClan(clanId);
            hyena.claim(carcass);
            level.addFreshEntity(hyena);
            came++;
        }
        Player nearest = level.getNearestPlayer(carcass.getX(), carcass.getY(), carcass.getZ(), 48.0D, false);
        if (came > 0 && nearest != null) {
            nearest.sendSystemMessage(net.minecraft.network.chat.Component.literal(yours
                    ? "A clan of hyenas is coming for your kill - " + came + " of them, whooping to each other."
                    : "A clan of hyenas has smelled the kill. There are " + came + " of them.")
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        return came;
    }

    /** The giant hyena, alone, coming in on the smell of something big. */
    public static boolean callGiantHyena(ServerLevel level, BlockPos carcass) {
        if (!level.getEntitiesOfClass(dev.hominin.evolution.entity.Pachycrocuta.class,
                new net.minecraft.world.phys.AABB(carcass).inflate(96.0D)).isEmpty()) {
            return false;
        }
        BlockPos from = approachFrom(level, carcass, 30, 16);
        if (from == null) {
            return false;
        }
        var hyena = ModEntities.PACHYCROCUTA.get().create(level);
        if (hyena == null) {
            return false;
        }
        hyena.moveTo(from.getX() + 0.5D, from.getY(), from.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        hyena.finalizeSpawn(level, level.getCurrentDifficultyAt(from), MobSpawnType.EVENT, null);
        level.addFreshEntity(hyena);
        Player nearest = level.getNearestPlayer(carcass.getX(), carcass.getY(), carcass.getZ(), 64.0D, false);
        if (nearest != null) {
            nearest.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Something big has smelled the kill. A giant hyena is coming, and it does not share.")
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        return true;
    }

    // ------------------------------------------------------------ the clan at the carcass

    /**
     * A carcass with a clan on it and nobody standing over it: they strip it. A giant carcass
     * takes them four sittings. With anyone in the way, they are busy fighting for it instead.
     */
    public static void clanFeeds(ServerLevel level, BlockPos pos) {
        var at = level.getEntitiesOfClass(dev.hominin.evolution.entity.Crocuta.class,
                new net.minecraft.world.phys.AABB(pos).inflate(3.0D), c -> c.isAlive() && !c.isScattering());
        if (at.isEmpty()) {
            return;
        }
        boolean contested = !level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(pos)
                .inflate(6.0D), e -> e.isAlive() && (e instanceof BandMember
                        || (e instanceof Player p && !p.isSpectator() && !p.isCreative()))).isEmpty();
        if (contested) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GENERIC_EAT, net.minecraft.sounds.SoundSource.HOSTILE,
                1.0F, 0.9F);
        if (state.is(ModBlocks.GIANT_CARCASS.get())
                && state.getValue(dev.hominin.evolution.block.GiantCarcassBlock.BITES) < 3) {
            level.setBlock(pos, state.setValue(dev.hominin.evolution.block.GiantCarcassBlock.BITES,
                    state.getValue(dev.hominin.evolution.block.GiantCarcassBlock.BITES) + 1), 3);
            return;
        }
        level.removeBlock(pos, false);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ITEM_SLIME, pos.getX() + 0.5D,
                pos.getY() + 0.4D, pos.getZ() + 0.5D, 12, 0.3D, 0.2D, 0.3D, 0.0D);
        Player nearest = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 32.0D, false);
        if (nearest != null) {
            nearest.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "The clan strips the carcass between them. There is nothing left of it."), true);
        }
    }

    // ------------------------------------------------------------ where the kills are

    private record Kill(BlockPos pos, long at, boolean giant) {
    }

    /** Fresh kills, per dimension. What the scavengers go looking for, without scanning the ground. */
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, java.util.List<Kill>>
            kills = new HashMap<>();
    /** Three days: after that a carcass is bones, and nothing comes for it on the smell alone. */
    private static final long KILL_MEMORY = 72000L;

    private static void recordKill(ServerLevel level, BlockPos pos, boolean giant) {
        java.util.List<Kill> list = kills.computeIfAbsent(level.dimension(), k -> new java.util.ArrayList<>());
        list.add(new Kill(pos.immutable(), level.getGameTime(), giant));
        if (list.size() > 256) {
            list.remove(0);
        }
    }

    /**
     * The nearest kill still lying there, within reach of a nose. With {@code giantFirst}, any
     * megafauna carcass in range beats a nearer ordinary one - that is what a giant hyena wants.
     */
    @javax.annotation.Nullable
    public static BlockPos nearestKill(ServerLevel level, BlockPos around, double radius, boolean giantFirst) {
        java.util.List<Kill> list = kills.get(level.dimension());
        if (list == null) {
            return null;
        }
        long now = level.getGameTime();
        list.removeIf(kill -> now - kill.at() > KILL_MEMORY || (level.isLoaded(kill.pos())
                && !dev.hominin.evolution.entity.Crocuta.isCarcass(level.getBlockState(kill.pos()))));
        Kill best = null;
        double bestScore = radius * radius;
        for (Kill kill : list) {
            if (!level.isLoaded(kill.pos())) {
                continue;
            }
            double distance = kill.pos().distSqr(around);
            if (distance > radius * radius) {
                continue;
            }
            double score = giantFirst && kill.giant() ? distance * 0.1D : distance;
            if (score < bestScore) {
                bestScore = score;
                best = kill;
            }
        }
        return best == null ? null : best.pos();
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
