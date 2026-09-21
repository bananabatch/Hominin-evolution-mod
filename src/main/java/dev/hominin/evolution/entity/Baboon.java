package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Papio angusticeps, an early baboon. Troops of a dozen or twenty forage across the
 * same country as the hominins, and will swap food with them.
 *
 * <p>One on its own is fair game, and runs when attacked. Attack one near its troop
 * and the whole troop turns on the attacker - hominin, band member or predator - and
 * mauls it - screaming, surrounding, biting, and the bites bleed. Hunt the stragglers,
 * or better, trade. A troop will even mob a predator that wanders too close.
 */
public class Baboon extends PathfinderMob {
    /** A baboon with at least this many troop-mates nearby fights instead of running. */
    private static final int SWARM_TROOP_SIZE = 4;
    private static final double TROOP_RADIUS = 28.0D;
    /** How close a predator can come before the troop mobs it. */
    private static final double MOB_PREDATOR_RADIUS = 12.0D;
    /** Baboon canines tear: a bite opens a wound often enough to matter. */
    private static final float BLEED_CHANCE = 0.5F;
    private static final int BLEED_TICKS = 6 * 20;
    private static final int ANGER_TICKS = 400;
    private static final int PANIC_TICKS = 200;
    private static final int POUCH_SIZE = 3;

    @Nullable
    private UUID troopId;
    @Nullable
    private UUID troopLeader;
    private int angerTicks;
    private int panicTicks;
    /** Food it has foraged, carried in its cheek pouches - which is what it trades. */
    private final SimpleContainer pouch = new SimpleContainer(POUCH_SIZE);

    public Baboon(EntityType<? extends Baboon> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    public void joinTroop(UUID troop, @Nullable UUID leader) {
        troopId = troop;
        troopLeader = leader;
        if (home == null) {
            home = blockPosition();
        }
    }

    /** Where this troop sleeps. A baboon's friends are its friends by day only. */
    @Nullable
    private net.minecraft.core.BlockPos home;

    @Nullable
    public UUID getTroop() {
        return troopId;
    }

    /** The player this one is travelling with today, if any. */
    @Nullable
    private UUID escortOf;

    public void escort(Player player) {
        escortOf = player.getUUID();
        // A burst of hearts as it falls in, so you can see which of the troop chose you.
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.4D, getZ(),
                    6, 0.4D, 0.3D, 0.4D, 0.0D);
        }
        player.displayClientMessage(Component.literal("A baboon falls in beside you."), true);
    }

    public boolean isEscorting(Player player) {
        return player.getUUID().equals(escortOf);
    }

    public void stopEscorting() {
        escortOf = null;
    }

    /** The troop has decided about you, and it was not in your favour. */
    public void turnOn(Player player) {
        escortOf = null;
        enrage(player);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicAloneGoal());
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35D, true) {
            @Override
            public boolean canUse() {
                return angerTicks > 0 && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return angerTicks > 0 && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new EscortGoal());
        goalSelector.addGoal(2, new GoHomeGoal());
        goalSelector.addGoal(3, new ForageGoal());
        goalSelector.addGoal(4, new KeepWithTroopGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------ troop

    private List<Baboon> troopNearby() {
        return level().getEntitiesOfClass(Baboon.class, getBoundingBox().inflate(TROOP_RADIUS),
                other -> other != this && other.isAlive() && troopId != null && troopId.equals(other.troopId));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && source.getEntity() instanceof LivingEntity attacker
                && !(attacker instanceof Baboon)) {
            List<Baboon> troop = troopNearby();
            if (troop.size() + 1 >= SWARM_TROOP_SIZE) {
                // A player gets one chance to show it was not meant. Anything else - and a
                // player who has already been given that chance - gets the troop.
                if (attacker instanceof net.minecraft.server.level.ServerPlayer player && !player.isSpectator()
                        && TroopRelations.mistake(player, this)) {
                    getNavigation().stop();
                    return hurt;
                }
                // Caught near the troop: every one of them comes screaming.
                mob(attacker);
            } else {
                panicTicks = PANIC_TICKS;
            }
        }
        return hurt;
    }

    private void enrage(LivingEntity attacker) {
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        angerTicks = ANGER_TICKS;
        panicTicks = 0;
        setTarget(attacker);
        // Mobbing: the whole troop surges in faster than it would ever move otherwise.
        addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, ANGER_TICKS, 0, false, false));
    }

    /** Sets the whole troop on something. */
    private void mob(LivingEntity threat) {
        playSound(ModSounds.BABOON_ANGRY.get(), 1.8F, 0.9F + getRandom().nextFloat() * 0.2F);
        enrage(threat);
        for (Baboon mate : troopNearby()) {
            mate.enrage(threat);
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity victim && getRandom().nextFloat() < BLEED_CHANCE) {
            var existing = victim.getEffect(dev.hominin.evolution.ModEffects.BLEEDING);
            int severity = existing == null ? 0 : Math.min(1, existing.getAmplifier() + 1);
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(dev.hominin.evolution.ModEffects.BLEEDING,
                    BLEED_TICKS, severity, false, true, true));
        }
        return hit;
    }

    public boolean isAngry() {
        return angerTicks > 0;
    }

    /** True if this baboon would swarm rather than flee - wounds should not send it running. */
    public boolean hasTroopBehindIt() {
        return troopNearby().size() + 1 >= SWARM_TROOP_SIZE;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (angerTicks > 0 && --angerTicks == 0) {
            setTarget(null);
        }
        if (panicTicks > 0) {
            panicTicks--;
        }
        if (angerTicks > 0 && getRandom().nextInt(50) == 0) {
            playSound(ModSounds.BABOON_ANGRY.get(), 1.4F, 0.9F + getRandom().nextFloat() * 0.3F);
        }
        tickRelations();
        // The troop leader keeps watch: a predator that comes close is mobbed and driven off.
        if (tickCount % 40 == 0 && troopId != null && troopLeader == null && angerTicks == 0) {
            List<net.minecraft.world.entity.Mob> predators = level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    getBoundingBox().inflate(MOB_PREDATOR_RADIUS),
                    m -> m.isAlive() && m.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS));
            if (!predators.isEmpty() && troopNearby().size() + 1 >= SWARM_TROOP_SIZE) {
                mob(predators.get(0));
            }
        }
    }

    /**
     * Everything the troop does because of who you are to it: freezing to stare while you
     * decide how to put a mistake right, keeping a grudge within limits, and watching the
     * back of a friend it is travelling with.
     */
    private void tickRelations() {
        if (troopId == null) {
            findTroop();
            return;
        }
        if (home == null) {
            // A troop from an older save: wherever it is now is where it lives.
            home = blockPosition();
        }
        if (escortOf != null && !level().isDay()) {
            escortOf = null;
        }
        // And a heart now and then while it travels with you, to tell it from the rest.
        if (escortOf != null && tickCount % 60 == 0 && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.5D, getZ(),
                    1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
        // Waiting to see what you do. Not moving, not looking away.
        Player watched = null;
        for (Player player : level().players()) {
            if (troopId.equals(TroopRelations.pendingTroop(player)) && distanceTo(player) < 28.0F) {
                watched = player;
            }
        }
        if (watched != null) {
            getNavigation().stop();
            getLookControl().setLookAt(watched, 30.0F, 30.0F);
            return;
        }
        LivingEntity target = getTarget();
        // A grudge is fierce but local: run far enough and they let you go.
        if (target instanceof Player player && TroopRelations.holdsGrudge(player, troopId)
                && distanceTo(player) > GRUDGE_CHASE) {
            setTarget(null);
            angerTicks = 0;
        }
        if (tickCount % 20 != 0 || angerTicks > 0) {
            return;
        }
        // ...but come back near them and they remember.
        for (Player player : level().players()) {
            if (!player.isCreative() && !player.isSpectator() && distanceTo(player) < GRUDGE_SIGHT
                    && TroopRelations.holdsGrudge(player, troopId)) {
                mob(player);
                return;
            }
        }
        // A friend in trouble. Anything with teeth that comes near somebody the troop
        // travels with gets what any predator near the troop gets.
        if (escortOf != null && level().getPlayerByUUID(escortOf) instanceof Player friend) {
            List<net.minecraft.world.entity.Mob> threats = level().getEntitiesOfClass(
                    net.minecraft.world.entity.Mob.class, friend.getBoundingBox().inflate(MOB_PREDATOR_RADIUS),
                    m -> m.isAlive() && m.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS));
            if (!threats.isEmpty()) {
                playSound(ModSounds.BABOON_ANGRY.get(), 1.8F, 1.0F);
                enrage(threats.get(0));
            }
        }
    }

    /**
     * A baboon on its own - one hatched from an egg, or the last of a troop - goes
     * looking for others. It joins whatever troop is nearby, or, if the others around it
     * have none either, starts one they can all join.
     */
    private void findTroop() {
        if (tickCount % 40 != 0) {
            return;
        }
        for (Baboon other : level().getEntitiesOfClass(Baboon.class, getBoundingBox().inflate(TROOP_RADIUS),
                b -> b != this && b.isAlive() && b.troopId != null)) {
            joinTroop(other.troopId, other.troopLeader != null ? other.troopLeader : other.getUUID());
            return;
        }
        if (!level().getEntitiesOfClass(Baboon.class, getBoundingBox().inflate(TROOP_RADIUS),
                b -> b != this && b.isAlive()).isEmpty()) {
            joinTroop(UUID.randomUUID(), null);
        }
    }

    /** How far a troop with a grudge will chase you, and how close you can come before it notices. */
    private static final float GRUDGE_CHASE = 24.0F;
    private static final float GRUDGE_SIGHT = 9.0F;

    /**
     * Back to the troop's sleeping ground once it gets dark - whoever it spent the day
     * with. This is also what makes an escort an escort and not a pet: it leaves.
     */
    private class GoHomeGoal extends Goal {
        GoHomeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return home != null && !level().isDay() && !isAngry()
                    && distanceToSqr(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D) > 10.0D * 10.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return home != null && !level().isDay() && !isAngry()
                    && distanceToSqr(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D) > 4.0D * 4.0D;
        }

        @Override
        public void start() {
            escortOf = null;
            getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.1D);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (getNavigation().isDone()) {
                getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 1.1D);
            }
        }
    }

    /** Keeping company with a friend: close, but not underfoot. */
    private class EscortGoal extends Goal {
        EscortGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Nullable
        private Player friend() {
            return escortOf == null ? null : level().getPlayerByUUID(escortOf);
        }

        @Override
        public boolean canUse() {
            Player friend = friend();
            return friend != null && !isAngry() && distanceTo(friend) > 5.0F;
        }

        @Override
        public boolean canContinueToUse() {
            Player friend = friend();
            return friend != null && !isAngry() && distanceTo(friend) > 3.0F;
        }

        /**
         * Every tick, and moving from the start. It used to re-path only on ticks divisible
         * by ten - but goals only tick every other tick, offset by entity id, so half of
         * all baboons never saw such a tick and never took a step.
         */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private int repath;

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            Player friend = friend();
            if (friend != null && --repath <= 0) {
                repath = 10;
                getNavigation().moveTo(friend, 1.15D);
            }
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    // ------------------------------------------------------------ trading

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // Sneak-use: where you stand with this troop, and what it will take.
        if (player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
            if (!level().isClientSide()) {
                player.displayClientMessage(TroopRelations.standing(player, troopId), false);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        // Anything at all, offered while they stare, is an apology.
        if (hand == InteractionHand.MAIN_HAND && !held.isEmpty() && troopId != null
                && troopId.equals(TroopRelations.pendingTroop(player))) {
            if (!level().isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer server) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                playSound(SoundEvents.ITEM_PICKUP, 0.7F, 1.2F);
                TroopRelations.forgive(server, "It snatches what you hold out, and the troop settles.");
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (hand != InteractionHand.MAIN_HAND || !held.has(DataComponents.FOOD)) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (isAngry()) {
            player.displayClientMessage(Component.literal("It is in no mood to trade."), true);
            return InteractionResult.CONSUME;
        }
        for (int slot = 0; slot < pouch.getContainerSize(); slot++) {
            ItemStack stored = pouch.getItem(slot);
            if (!stored.isEmpty() && !stored.is(held.getItem())) {
                ItemStack given = pouch.removeItem(slot, 1);
                pouch.setItem(slot, held.copyWithCount(1));
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                if (!player.getInventory().add(given)) {
                    player.drop(given, false);
                }
                playSound(SoundEvents.ITEM_PICKUP, 0.7F, 1.2F);
                ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getEyeY(), getZ(),
                        4, 0.3D, 0.3D, 0.3D, 0.0D);
                player.displayClientMessage(Component.literal("The baboon snatches it and pushes ")
                        .append(given.getHoverName()).append(" into your hand."), true);
                if (troopId != null) {
                    TroopRelations.goodwill(player, troopId, 1);
                }
                return InteractionResult.CONSUME;
            }
        }
        // Nothing to swap - so it is a gift, which buys more than a trade does.
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        playSound(SoundEvents.GENERIC_EAT, 0.7F, 1.1F);
        ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getEyeY(), getZ(),
                4, 0.3D, 0.3D, 0.3D, 0.0D);
        player.displayClientMessage(Component.literal("It takes it, and eats it in front of you. A gift."), true);
        if (troopId != null) {
            TroopRelations.goodwill(player, troopId, 2);
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------ sounds, saving

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BABOON_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BABOON_HURT.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (troopId != null) {
            tag.putUUID("Troop", troopId);
        }
        if (troopLeader != null) {
            tag.putUUID("TroopLeader", troopLeader);
        }
        tag.put("Pouch", pouch.createTag(registryAccess()));
        if (home != null) {
            tag.putLong("Home", home.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        troopId = tag.hasUUID("Troop") ? tag.getUUID("Troop") : null;
        troopLeader = tag.hasUUID("TroopLeader") ? tag.getUUID("TroopLeader") : null;
        pouch.fromTag(tag.getList("Pouch", 10), registryAccess());
        home = tag.contains("Home") ? net.minecraft.core.BlockPos.of(tag.getLong("Home")) : null;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    /**
     * A troop leaves only when nobody is anywhere near it. Vanilla's random despawn beyond
     * 32 blocks would thin a troop out before anyone ever got close enough to see it.
     */
    @Override
    public void checkDespawn() {
        net.minecraft.world.entity.player.Player nearest = level().getNearestPlayer(this, -1.0D);
        if (nearest == null || nearest.distanceToSqr(this) > 160.0D * 160.0D) {
            discard();
        } else {
            setNoActionTime(0);
        }
    }

    // ------------------------------------------------------------ goals

    /** On its own, a hurt baboon runs. */
    private class PanicAloneGoal extends Goal {
        PanicAloneGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return panicTicks > 0 && angerTicks == 0;
        }

        @Override
        public void tick() {
            if (getNavigation().isDone()) {
                LivingEntity threat = getLastHurtByMob();
                Vec3 away = DefaultRandomPos.getPosAway(Baboon.this, 16, 7,
                        threat != null ? threat.position() : position());
                if (away != null) {
                    getNavigation().moveTo(away.x, away.y, away.z, 1.5D);
                }
            }
        }
    }

    /** Head down, picking through the grass - and now and then something goes in the pouch. */
    private class ForageGoal extends Goal {
        private int ticks;

        ForageGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return angerTicks == 0 && panicTicks == 0 && getRandom().nextInt(300) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return ticks < 80 && angerTicks == 0;
        }

        @Override
        public void start() {
            ticks = 0;
            getNavigation().stop();
        }

        @Override
        public void tick() {
            ticks++;
            getLookControl().setLookAt(getX() + getLookAngle().x, getY() - 1.0D, getZ() + getLookAngle().z);
            if (ticks % 20 == 0) {
                swing(InteractionHand.MAIN_HAND);
            }
            if (ticks == 79 && getRandom().nextFloat() < 0.5F) {
                float roll = getRandom().nextFloat();
                ItemStack food = roll < 0.4F ? new ItemStack(Items.SWEET_BERRIES)
                        : roll < 0.7F ? new ItemStack(ModItems.GRUB.get())
                        : roll < 0.85F ? new ItemStack(Items.APPLE) : new ItemStack(ModItems.BEETLE.get());
                pouch.addItem(food);
            }
        }
    }

    /** Stays within reach of the troop leader, so the troop moves as one. */
    private class KeepWithTroopGoal extends Goal {
        private Baboon leader;

        KeepWithTroopGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (troopLeader == null || !(level() instanceof ServerLevel server)) {
                return false;
            }
            if (!(server.getEntity(troopLeader) instanceof Baboon found) || !found.isAlive()) {
                return false;
            }
            leader = found;
            return distanceToSqr(leader) > 12.0D * 12.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return leader != null && leader.isAlive() && distanceToSqr(leader) > 6.0D * 6.0D
                    && !getNavigation().isDone();
        }

        @Override
        public void start() {
            getNavigation().moveTo(leader, 1.0D);
        }
    }
}
