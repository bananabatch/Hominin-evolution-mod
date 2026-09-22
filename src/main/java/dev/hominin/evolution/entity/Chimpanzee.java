package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.combat.Bleeding;
import dev.hominin.evolution.survival.Infestation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Pan troglodytes: the relatives who stayed in the trees.
 *
 * <p>A community holds a range and defends it, and an alpha keeps order inside it by
 * reminding everyone - you included - who is in charge. That reminder is a status check:
 * the alpha walks up, stands too close, and waits. The warning signs are small (the hair
 * lifts, a low grunt) and there is no second prompt. Answer it the way you answer a troop
 * you have wronged, or the alpha answers for you.
 *
 * <p>Get past that, feed them, and a community will come to know you: they groom you,
 * and a hominin who runs into their range with a cat behind it has a lot of angry
 * relatives between it and the cat.
 */
public class Chimpanzee extends PathfinderMob implements TroopAnimal, TreeClimber {
    private static final double RANGE = 24.0D;
    private static final int ANGER_TICKS = 300;
    /** How often the alpha feels the need to make a point. */
    private static final int CHECK_MIN = 1800;
    private static final int CHECK_SPREAD = 1800;

    @Nullable
    private UUID communityId;
    /** Null for the alpha, who answers to nobody. */
    @Nullable
    private UUID alphaId;
    @Nullable
    private BlockPos home;
    private int angerTicks;
    private int nextCheck;
    private int nextGroom;
    @Nullable
    private UUID checking;

    public Chimpanzee(EntityType<? extends Chimpanzee> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    public void joinCommunity(UUID community, @Nullable UUID alpha) {
        communityId = community;
        alphaId = alpha;
        if (home == null) {
            home = blockPosition();
        }
    }

    @Override
    @Nullable
    public UUID getTroop() {
        return communityId;
    }

    public boolean isAlpha() {
        return communityId != null && alphaId == null;
    }

    @Override
    public boolean isAngry() {
        return angerTicks > 0;
    }

    @Override
    public void turnOn(Player player) {
        enrage(player, ANGER_TICKS);
    }

    private void enrage(LivingEntity target, int ticks) {
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        angerTicks = ticks;
        setTarget(target);
        playSound(ModSounds.BABOON_ANGRY.get(), 1.6F, 0.7F);
    }

    /** A failed status check: the alpha makes its point, once, and hard. */
    public void putInPlace(Player player) {
        enrage(player, 60);
        player.displayClientMessage(Component.literal("The alpha lunges at you. You took too long to back down.")
                .withStyle(ChatFormatting.RED), true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.3D, true) {
            @Override
            public boolean canUse() {
                return angerTicks > 0 && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return angerTicks > 0 && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new StatusCheckGoal());
        goalSelector.addGoal(3, new GroomFriendGoal());
        goalSelector.addGoal(4, new ClimbTreeGoal<>(this, () -> isAngry() || isBaby() || isInWater() || checking != null,
                this::threatened));
        goalSelector.addGoal(4, new StayInRangeGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------ trees

    /** Up a trunk right now: they nest in the canopy at night and go up when a cat is about. */
    private boolean climbing;

    @Override
    public void setClimbing(boolean climbing) {
        this.climbing = climbing;
    }

    @Override
    public boolean onClimbable() {
        return (climbing && horizontalCollision) || super.onClimbable();
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !climbing && super.causeFallDamage(distance, multiplier, source);
    }

    /** A big cat close by, and nobody angry enough to fight it: time to be somewhere higher. */
    private boolean threatened() {
        return getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < 100
                || !level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(10.0D),
                        m -> m.isAlive() && m.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS)).isEmpty();
    }

    private List<Chimpanzee> communityNearby() {
        return level().getEntitiesOfClass(Chimpanzee.class, getBoundingBox().inflate(RANGE),
                c -> c != this && c.isAlive() && communityId != null && communityId.equals(c.communityId));
    }

    // ------------------------------------------------------------ being hit

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (!hurt || level().isClientSide() || !(source.getEntity() instanceof LivingEntity attacker)
                || attacker instanceof Chimpanzee) {
            return hurt;
        }
        List<Chimpanzee> community = communityNearby();
        if (attacker instanceof ServerPlayer player && !player.isSpectator() && community.size() >= 2
                && TroopRelations.mistake(player, this)) {
            getNavigation().stop();
            return hurt;
        }
        enrage(attacker, ANGER_TICKS);
        for (Chimpanzee mate : community) {
            mate.enrage(attacker, ANGER_TICKS);
        }
        return hurt;
    }

    /**
     * A display at chimpanzees is a challenge, and they take it as one - but they are not
     * Dinopithecus. The community goes still and gives you the same five seconds a troop
     * would to take it back.
     */
    public static boolean answerDisplay(ServerPlayer player, double radius) {
        for (Chimpanzee chimp : player.level().getEntitiesOfClass(Chimpanzee.class,
                player.getBoundingBox().inflate(radius), c -> c.communityId != null)) {
            return TroopRelations.mistake(player, chimp);
        }
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity bitten && getRandom().nextInt(3) == 0) {
            Bleeding.inflict(bitten, Bleeding.Tier.EXTERNAL);
        }
        return hit;
    }

    // ------------------------------------------------------------ every tick

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (angerTicks > 0 && --angerTicks == 0) {
            setTarget(null);
        }
        if (communityId == null) {
            findCommunity();
            return;
        }
        if (home == null) {
            home = blockPosition();
        }
        // Frozen, staring, while you decide what to do about what you did.
        for (Player player : level().players()) {
            if (communityId.equals(TroopRelations.pendingTroop(player)) && distanceTo(player) < 28.0F
                    && !player.getUUID().equals(checking)) {
                getNavigation().stop();
                getLookControl().setLookAt(player, 30.0F, 30.0F);
                return;
            }
        }
        if (tickCount % 20 != 0 || angerTicks > 0) {
            return;
        }
        for (Player player : level().players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            // A grudge on their own ground: they come for you.
            if (TroopRelations.holdsGrudge(player, communityId) && distanceTo(player) < 12.0F
                    && home.closerToCenterThan(player.position(), RANGE)) {
                enrage(player, ANGER_TICKS);
                return;
            }
            // A friend with a cat behind it, inside the range: the whole community turns out.
            if (TroopRelations.isTrusted(player, communityId) && home.closerToCenterThan(player.position(), RANGE)) {
                List<Mob> threats = level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12.0D),
                        m -> m.isAlive() && m.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS)
                                && m.getTarget() == player);
                if (!threats.isEmpty()) {
                    enrage(threats.get(0), ANGER_TICKS);
                    for (Chimpanzee mate : communityNearby()) {
                        mate.enrage(threats.get(0), ANGER_TICKS);
                    }
                    player.displayClientMessage(Component.literal("The chimpanzees come screaming out of the trees at it.")
                            .withStyle(ChatFormatting.GOLD), true);
                    return;
                }
            }
        }
    }

    private void findCommunity() {
        if (tickCount % 40 != 0) {
            return;
        }
        for (Chimpanzee other : level().getEntitiesOfClass(Chimpanzee.class, getBoundingBox().inflate(RANGE),
                c -> c != this && c.isAlive() && c.communityId != null)) {
            joinCommunity(other.communityId, other.alphaId != null ? other.alphaId : other.getUUID());
            return;
        }
        if (!level().getEntitiesOfClass(Chimpanzee.class, getBoundingBox().inflate(RANGE),
                c -> c != this && c.isAlive()).isEmpty()) {
            joinCommunity(UUID.randomUUID(), null);
        }
    }

    // ------------------------------------------------------------ gifts

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide()) {
                player.displayClientMessage(TroopRelations.standing(player, communityId), false);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        // Anything held out while they are waiting on you is the answer they wanted.
        if (!held.isEmpty() && communityId != null && communityId.equals(TroopRelations.pendingTroop(player))) {
            if (player instanceof ServerPlayer server) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                playSound(SoundEvents.ITEM_PICKUP, 0.7F, 1.0F);
                TroopRelations.forgive(server, "It takes what you hold out, and the community settles.");
                checking = null;
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!held.has(DataComponents.FOOD) || communityId == null) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (isAngry()) {
            player.displayClientMessage(Component.literal("It is in no mood for you."), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        playSound(SoundEvents.GENERIC_EAT, 0.7F, 1.0F);
        ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getEyeY(), getZ(),
                4, 0.3D, 0.3D, 0.3D, 0.0D);
        player.displayClientMessage(Component.literal("It takes the food and watches you while it eats."), true);
        TroopRelations.goodwill(player, communityId, TroopRelations.GIFT_WORTH);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------ goals

    /**
     * The alpha's reminder. It picks somebody near its range, walks up to them, and stands
     * there bristling. Nothing announces it - you notice or you do not.
     */
    private class StatusCheckGoal extends Goal {
        @Nullable
        private Player target;
        private int ticks;

        StatusCheckGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!isAlpha() || isAngry() || home == null || tickCount < nextCheck) {
                return false;
            }
            nextCheck = tickCount + CHECK_MIN + getRandom().nextInt(CHECK_SPREAD);
            for (Player player : level().players()) {
                if (!player.isCreative() && !player.isSpectator()
                        && home.closerToCenterThan(player.position(), RANGE)
                        && !TroopRelations.hasPendingMistake(player)) {
                    target = player;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && !isAngry() && ticks < 20 * 30
                    && (checking == null || TroopRelations.hasPendingMistake(target));
        }

        @Override
        public void start() {
            ticks = 0;
            checking = null;
        }

        @Override
        public void stop() {
            checking = null;
            target = null;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            ticks++;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (checking == null) {
                if (distanceTo(target) > 2.5F) {
                    if (ticks % 10 == 1) {
                        getNavigation().moveTo(target, 1.0D);
                    }
                    return;
                }
                getNavigation().stop();
                checking = target.getUUID();
                if (target instanceof ServerPlayer server) {
                    TroopRelations.statusCheck(server, communityId, getUUID());
                }
            }
            getNavigation().stop();
            // The only warning you get: hair up, and a low sound in the chest.
            if (ticks % 15 == 0 && level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SMOKE, getX(), getEyeY() + 0.3D, getZ(), 2, 0.2D, 0.1D, 0.2D, 0.0D);
            }
            if (ticks % 30 == 0) {
                playSound(ModSounds.BABOON_AMBIENT.get(), 0.5F, 0.45F);
            }
        }
    }

    /** A community that knows you will see to your ticks. */
    private class GroomFriendGoal extends Goal {
        @Nullable
        private Player friend;
        private int working;

        GroomFriendGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (isAngry() || communityId == null || tickCount < nextGroom || getRandom().nextInt(200) != 0) {
                return false;
            }
            for (Player player : level().players()) {
                if (player instanceof ServerPlayer server && distanceTo(player) < 10.0F
                        && TroopRelations.isTrusted(player, communityId) && Infestation.of(server) > 0) {
                    friend = player;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return friend != null && friend.isAlive() && !isAngry() && working < 100 && distanceTo(friend) < 16.0F;
        }

        @Override
        public void start() {
            working = 0;
        }

        @Override
        public void stop() {
            nextGroom = tickCount + 2400;
            friend = null;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(friend, 30.0F, 30.0F);
            if (distanceTo(friend) > 2.0F) {
                if (tickCount % 10 == 0) {
                    getNavigation().moveTo(friend, 1.0D);
                }
                return;
            }
            getNavigation().stop();
            if (++working >= 100 && friend instanceof ServerPlayer server) {
                Infestation.groomed(server, 1);
                playSound(SoundEvents.WOOL_HIT, 0.5F, 1.2F);
                server.displayClientMessage(Component.literal(
                        "A chimpanzee sits down beside you and picks through your hair.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    /** They hold a range and keep to it. */
    private class StayInRangeGoal extends Goal {
        StayInRangeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return home != null && !isAngry()
                    && distanceToSqr(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D) > RANGE * RANGE;
        }

        @Override
        public boolean canContinueToUse() {
            return home != null && !isAngry() && !getNavigation().isDone();
        }

        @Override
        public void start() {
            getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.0D);
        }
    }

    // ------------------------------------------------------------ sounds, saving

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BABOON_AMBIENT.get();
    }

    @Override
    public float getVoicePitch() {
        return 0.75F + getRandom().nextFloat() * 0.1F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BABOON_HURT.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (communityId != null) {
            tag.putUUID("Community", communityId);
        }
        if (alphaId != null) {
            tag.putUUID("Alpha", alphaId);
        }
        if (home != null) {
            tag.putLong("Home", home.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        communityId = tag.hasUUID("Community") ? tag.getUUID("Community") : null;
        alphaId = tag.hasUUID("Alpha") ? tag.getUUID("Alpha") : null;
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
