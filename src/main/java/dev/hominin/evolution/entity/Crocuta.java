package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Crocuta: the clan hyena, the spotted hyena's own early line - out on the same East African
 * grass as the first Homo, and the thing early hominins actually fought over carcasses with.
 *
 * <p>A clan does not hunt you. It does not care about you at all, until you come near its food.
 * Then it tells you: the whole clan turns, bristles and barks. Back off and that is the end of
 * it. Stay, and a few seconds later they come for you together.
 *
 * <p>A threat display is a bluff, and a clan knows what a bluff is. Sometimes it breaks and
 * scatters off the kill; sometimes it calls you on it and attacks at once. A clan also goes
 * looking for kills it did not make - including yours.
 */
public class Crocuta extends PathfinderMob {
    /** How close to their food counts as coming for it. */
    private static final double WARN_RANGE = 9.0D;
    /** Still this close once the warning is over, and they attack. */
    private static final double ATTACK_RANGE = 7.0D;
    /** Past this, whoever it was has backed off and the clan settles. */
    private static final double RELAX_RANGE = 13.0D;
    /** How far from the food they will chase somebody before turning back to it. */
    private static final double LEASH = 22.0D;
    private static final int WARN_TICKS = 80;
    private static final double CLAN_RANGE = 24.0D;
    private static final double CARCASS_SEARCH = 40.0D;
    /** After being run off a kill, a clan leaves food alone for a while. */
    private static final int SCATTER_TICKS = 900;

    /** One warning message per clan, however many of them bark. */
    private static final Map<UUID, Long> clanWarnedAt = new HashMap<>();

    @Nullable
    private UUID clanId;
    @Nullable
    private BlockPos claim;
    private int warnTicks;
    private int fleeTicks;
    @Nullable
    private Vec3 fleeFrom;

    public Crocuta(EntityType<? extends Crocuta> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ScatterGoal());
        // In and out, in and out - several of them at once is the danger, not any one bite.
        goalSelector.addGoal(2, new PredatorAttackGoal(this, 1.4D, 30, 10));
        goalSelector.addGoal(3, new HoldTheKillGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Hit one, and the whole clan answers.
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(Crocuta.class));
    }

    /** From an egg, a clan: this one and four more around it, all of a clan. */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType spawnType,
            @Nullable net.minecraft.world.entity.SpawnGroupData groupData) {
        net.minecraft.world.entity.SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        if (spawnType == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG) {
            clanId = UUID.randomUUID();
            for (int i = 0; i < 4; i++) {
                Crocuta mate = dev.hominin.evolution.ModEntities.CROCUTA.get().create(level.getLevel());
                if (mate == null) {
                    break;
                }
                mate.moveTo(getX() + random.nextInt(5) - 2, getY(), getZ() + random.nextInt(5) - 2,
                        random.nextFloat() * 360.0F, 0.0F);
                mate.finalizeSpawn(level, difficulty, net.minecraft.world.entity.MobSpawnType.EVENT, null);
                mate.joinClan(clanId);
                level.addFreshEntity(mate);
            }
        }
        return data;
    }

    /** Joins a clan, when a clan is put down together. */
    public void joinClan(UUID clan) {
        this.clanId = clan;
    }

    @Nullable
    public UUID clan() {
        return clanId;
    }

    /** Where the clan is eating, or means to. */
    public void claim(@Nullable BlockPos carcass) {
        this.claim = carcass == null ? null : carcass.immutable();
        warnTicks = 0;
    }

    @Nullable
    public BlockPos claimed() {
        return claim;
    }

    public boolean isScattering() {
        return fleeTicks > 0;
    }

    private List<Crocuta> clanNear() {
        return level().getEntitiesOfClass(Crocuta.class, getBoundingBox().inflate(CLAN_RANGE),
                other -> other.isAlive() && (clanId == null ? other == this : clanId.equals(other.clanId)));
    }

    public static boolean isCarcass(BlockState state) {
        return state.is(ModBlocks.CARCASS.get()) || state.is(ModBlocks.GIANT_CARCASS.get());
    }

    // ------------------------------------------------------------ the kill

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (fleeTicks > 0) {
            fleeTicks--;
            return;
        }
        if (tickCount % 10 != 0) {
            return;
        }
        if (claim != null && !isCarcass(level().getBlockState(claim))) {
            claim = null;
        }
        if (claim == null) {
            findFood();
            return;
        }
        if (getTarget() != null) {
            // They run you off the food; they do not chase you across the plain for it.
            if (getTarget().distanceToSqr(Vec3.atCenterOf(claim)) > LEASH * LEASH) {
                setTarget(null);
                warnTicks = 0;
            }
            return;
        }
        LivingEntity intruder = intruderAt(claim);
        if (intruder == null) {
            warnTicks = 0;
            return;
        }
        double distance = Math.sqrt(intruder.distanceToSqr(Vec3.atCenterOf(claim)));
        if (warnTicks == 0) {
            bark(intruder);
        }
        warnTicks += 10;
        getLookControl().setLookAt(intruder, 30.0F, 30.0F);
        if (warnTicks >= WARN_TICKS && distance <= ATTACK_RANGE) {
            for (Crocuta member : clanNear()) {
                if (!member.isScattering()) {
                    member.setTarget(intruder);
                }
            }
            if (intruder instanceof Player player) {
                player.displayClientMessage(Component.literal("You did not back off. The clan comes for you!")
                        .withStyle(ChatFormatting.RED), true);
            }
        } else if (distance > RELAX_RANGE) {
            warnTicks = 0;
        }
    }

    /** Anybody - you or one of yours - standing where the clan means to eat. */
    @Nullable
    private LivingEntity intruderAt(BlockPos carcass) {
        LivingEntity nearest = null;
        double best = WARN_RANGE * WARN_RANGE;
        for (LivingEntity entity : level().getEntitiesOfClass(LivingEntity.class, new AABB(carcass).inflate(WARN_RANGE),
                e -> e.isAlive() && ((e instanceof Player p && !p.isCreative() && !p.isSpectator())
                        || e instanceof BandMember))) {
            double distance = entity.distanceToSqr(Vec3.atCenterOf(carcass));
            if (distance < best) {
                best = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    /** Bristling, heads low, the whooping bark that means "this is ours". */
    private void bark(LivingEntity intruder) {
        playSound(ModSounds.CROCUTA_BARK.get(), 1.5F, 0.9F + random.nextFloat() * 0.3F);
        UUID clan = clanId != null ? clanId : getUUID();
        long now = level().getGameTime();
        if (now - clanWarnedAt.getOrDefault(clan, -99999L) < 200) {
            return;
        }
        if (clanWarnedAt.size() > 512) {
            clanWarnedAt.clear();
        }
        clanWarnedAt.put(clan, now);
        Player told = intruder instanceof Player player ? player
                : intruder instanceof BandMember member ? member.companionPlayer() : null;
        if (told != null) {
            told.displayClientMessage(Component.literal("The hyenas bristle and bark at you over the kill. Back off, "
                    + "or they will come for you. (A threat display might break them - or might not.)")
                    .withStyle(ChatFormatting.GOLD), false);
            if (told instanceof net.minecraft.server.level.ServerPlayer server) {
                dev.hominin.evolution.guide.Tips.offer(server, dev.hominin.evolution.guide.Tips.Tip.HYENA_CLAN);
            }
        }
    }

    /** Somewhere nearby, something dead. The whole clan takes the same one. */
    private void findFood() {
        if (tickCount % 60 != 0) {
            return;
        }
        // One nose per clan does the searching; the rest follow it.
        List<Crocuta> clan = clanNear();
        for (Crocuta member : clan) {
            if (member.getId() < getId()) {
                return;
            }
        }
        BlockPos found = level() instanceof net.minecraft.server.level.ServerLevel server
                ? dev.hominin.evolution.hunt.Carcasses.nearestKill(server, blockPosition(), CARCASS_SEARCH, false)
                : null;
        if (found != null) {
            for (Crocuta member : clan) {
                if (member.claim == null && !member.isScattering()) {
                    member.claim(found);
                }
            }
        }
    }

    // ------------------------------------------------------------ bluffs

    /** Run off: the clan leaves the kill and the country around it. */
    public void scatter(Vec3 from) {
        fleeTicks = SCATTER_TICKS;
        fleeFrom = from;
        claim = null;
        warnTicks = 0;
        setTarget(null);
        getNavigation().stop();
    }

    /**
     * A threat display at a clan. It is a bluff and they know it: with a band shouting behind
     * you they break more often, and hungry hyenas in a hard season less. If it fails they do
     * not wait for the warning to run out - they call it, and come.
     */
    public static int displayAt(ServerPlayer player, double radius, int bandSize) {
        Map<UUID, List<Crocuta>> clans = new HashMap<>();
        for (Crocuta hyena : player.level().getEntitiesOfClass(Crocuta.class, player.getBoundingBox().inflate(radius),
                h -> h.isAlive() && !h.isScattering())) {
            boolean involved = hyena.getTarget() != null || (hyena.claim != null
                    && hyena.claim.distSqr(player.blockPosition()) < (radius + 6.0D) * (radius + 6.0D));
            if (involved) {
                clans.computeIfAbsent(hyena.clanId != null ? hyena.clanId : hyena.getUUID(),
                        k -> new java.util.ArrayList<>()).add(hyena);
            }
        }
        int scattered = 0;
        for (List<Crocuta> clan : clans.values()) {
            float chance = Math.min(0.8F, 0.3F + 0.1F * bandSize
                    - (dev.hominin.evolution.survival.Seasons.strained(player.level()) ? 0.1F : 0.0F));
            if (player.getRandom().nextFloat() < chance) {
                boolean hadKill = false;
                for (Crocuta hyena : clan) {
                    hadKill |= hyena.claim != null;
                    hyena.scatter(player.position());
                    scattered++;
                }
                clan.get(0).playSound(ModSounds.CROCUTA_GIGGLE.get(), 1.4F, 1.1F);
                player.displayClientMessage(Component.literal("The clan breaks and scatters, giggling, into the grass.")
                        .withStyle(ChatFormatting.GREEN), true);
                if (hadKill) {
                    dev.hominin.evolution.EvolutionManager.incrementCriterion(player, "take_kill", 1);
                }
            } else {
                for (Crocuta hyena : clan) {
                    hyena.setTarget(player);
                }
                clan.get(0).playSound(ModSounds.CROCUTA_BARK.get(), 1.6F, 0.8F);
                player.displayClientMessage(Component.literal("They call your bluff - the clan comes at you!")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        return scattered;
    }

    // ------------------------------------------------------------ taking hurt

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && getHealth() < getMaxHealth() * 0.35F) {
            scatter(source.getEntity() != null ? source.getEntity().position() : position());
        }
        return hurt;
    }

    /** One of them down, and the rest of the clan may lose its nerve. */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide()) {
            return;
        }
        if (claim != null && source.getEntity() instanceof ServerPlayer player) {
            dev.hominin.evolution.EvolutionManager.incrementCriterion(player, "take_kill", 1);
        }
        Vec3 from = source.getEntity() != null ? source.getEntity().position() : position();
        int broke = 0;
        for (Crocuta member : clanNear()) {
            if (member != this && member.random.nextFloat() < 0.4F) {
                member.scatter(from);
                broke++;
            }
        }
        if (broke > 0 && source.getEntity() instanceof Player player) {
            player.displayClientMessage(Component.literal("Some of the clan lose their nerve and run."), true);
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity bitten && random.nextFloat() < 0.35F) {
            dev.hominin.evolution.combat.Bleeding.inflict(bitten, dev.hominin.evolution.combat.Bleeding.Tier.EXTERNAL);
        }
        return hit;
    }

    // ------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (clanId != null) {
            tag.putUUID("Clan", clanId);
        }
        if (claim != null) {
            tag.put("Claim", NbtUtils.writeBlockPos(claim));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        clanId = tag.hasUUID("Clan") ? tag.getUUID("Clan") : null;
        claim = NbtUtils.readBlockPos(tag, "Claim").orElse(null);
    }

    // ------------------------------------------------------------ sounds

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return ModSounds.CROCUTA_WHOOP.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.CROCUTA_HURT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return claim == null;
    }

    // ------------------------------------------------------------ goals

    /** Go to the kill, stay on it, and face whoever comes near. */
    private class HoldTheKillGoal extends Goal {
        HoldTheKillGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return claim != null && getTarget() == null && fleeTicks == 0;
        }

        @Override
        public void tick() {
            if (claim == null || tickCount % 20 != 0) {
                return;
            }
            double distance = distanceToSqr(Vec3.atCenterOf(claim));
            if (distance > 2.5D * 2.5D) {
                // Around it, not all on the same spot.
                double angle = (getUUID().getLeastSignificantBits() & 0xFF) / 255.0D * Math.PI * 2.0D;
                getNavigation().moveTo(claim.getX() + 0.5D + Math.cos(angle) * 1.6D, claim.getY(),
                        claim.getZ() + 0.5D + Math.sin(angle) * 1.6D, 1.0D);
            } else {
                getNavigation().stop();
                getLookControl().setLookAt(Vec3.atCenterOf(claim));
            }
        }
    }

    private class ScatterGoal extends Goal {
        ScatterGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return fleeTicks > 0;
        }

        @Override
        public void tick() {
            if (getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(Crocuta.this, 28, 7, fleeFrom != null ? fleeFrom : position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.35D);
                }
            }
        }
    }
}
