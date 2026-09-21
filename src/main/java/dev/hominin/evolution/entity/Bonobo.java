package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.band.BandMember;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pan paniscus: the other chimpanzee, the one that settles things with sex and sharing
 * instead of a fight.
 *
 * <p>A troop gives food away to keep the peace, grooms whoever sits with it, and drifts
 * along beside anybody foraging nearby. Where a troop lives nothing hunts - no predator
 * will come within {@link #SANCTUARY} blocks of one - which makes bonobo country the only
 * safe ground there is.
 *
 * <p>That holds for exactly as long as you leave them alone. Hurt one, and the troop
 * remembers you as the thing that hunts them; so does every troop you meet after, for
 * the rest of your line. There is no apology for it.
 */
public class Bonobo extends PathfinderMob implements TreeClimber {
    /** How far from a peaceful troop the predators stay away. */
    public static final double SANCTUARY = 64.0D;
    /** Survives evolving: it is not your body they remember, it is your people. */
    public static final String BETRAYED = EvolutionManager.SKILL_PREFIX + "bonobo_betrayed";
    private static final double TROOP_RADIUS = 24.0D;
    private static final double RANGE = 40.0D;

    @Nullable
    private UUID troopId;
    @Nullable
    private BlockPos home;
    /** This troop has been hunted. It is no refuge any more, for anyone. */
    private boolean betrayed;
    private int nextShare;
    private int nextGroom;

    public Bonobo(EntityType<? extends Bonobo> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    public void joinTroop(UUID troop, boolean hunted) {
        troopId = troop;
        betrayed = hunted;
        if (home == null) {
            home = blockPosition();
        }
    }

    public boolean isBetrayed() {
        return betrayed;
    }

    /** Whether this player's people have ever hunted bonobos. */
    public static boolean isBetrayer(Player player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().getOrDefault(BETRAYED, 0) > 0;
    }

    /** Whether a peaceful bonobo troop lives near here - in which case nothing hunts here. */
    public static boolean sanctuary(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(Bonobo.class, new net.minecraft.world.phys.AABB(pos).inflate(SANCTUARY),
                b -> b.isAlive() && !b.betrayed).isEmpty();
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.5D));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 16.0F, 1.1D, 1.5D,
                p -> betrayed && p instanceof Player player && isBetrayer(player)));
        goalSelector.addGoal(3, new ShareFoodGoal());
        goalSelector.addGoal(3, new GroomGoal());
        goalSelector.addGoal(4, new ClimbTreeGoal<>(this, () -> isBaby() || isInWater(),
                () -> getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < 100));
        goalSelector.addGoal(5, new ForageAlongsideGoal());
        goalSelector.addGoal(5, new StayInRangeGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    private List<Bonobo> troopNearby() {
        return level().getEntitiesOfClass(Bonobo.class, getBoundingBox().inflate(TROOP_RADIUS),
                b -> b != this && b.isAlive() && troopId != null && troopId.equals(b.troopId));
    }

    /** Somebody this troop has reason to be generous with. */
    private boolean friendly(Player player) {
        return !betrayed && !player.isSpectator() && !isBetrayer(player);
    }

    // ------------------------------------------------------------ trees

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

    // ------------------------------------------------------------ being hunted

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (!hurt || level().isClientSide()) {
            return hurt;
        }
        ServerPlayer hunter = null;
        if (source.getEntity() instanceof ServerPlayer player && !player.isCreative()) {
            hunter = player;
        } else if (source.getEntity() instanceof BandMember member
                && member.leaderPlayer() instanceof ServerPlayer leader) {
            hunter = leader;
        }
        if (hunter != null) {
            betray(hunter);
        }
        return hurt;
    }

    /** The end of it. Every troop from now on knows what your people are. */
    private void betray(ServerPlayer hunter) {
        boolean first = !isBetrayer(hunter);
        hunter.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(BETRAYED, 1);
        betrayed = true;
        for (Bonobo mate : troopNearby()) {
            mate.betrayed = true;
            mate.setLastHurtByMob(hunter);
        }
        playSound(ModSounds.BABOON_ANGRY.get(), 2.0F, 1.5F);
        if (first) {
            hunter.sendSystemMessage(Component.literal(
                    "The bonobos scatter, screaming. They will not forget this, and neither will any of their kind. "
                            + "Nowhere is safe now.").withStyle(ChatFormatting.DARK_RED));
        }
    }

    // ------------------------------------------------------------ every tick

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (troopId == null && tickCount % 40 == 0) {
            for (Bonobo other : level().getEntitiesOfClass(Bonobo.class, getBoundingBox().inflate(TROOP_RADIUS),
                    b -> b != this && b.troopId != null)) {
                joinTroop(other.troopId, other.betrayed);
                break;
            }
            if (troopId == null) {
                joinTroop(UUID.randomUUID(), false);
            }
        }
        if (home == null) {
            home = blockPosition();
        }
    }

    // ------------------------------------------------------------ being handed things

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide()) {
                player.displayClientMessage(betrayed || isBetrayer(player)
                        ? Component.literal("They keep their distance. They remember what your people are.")
                                .withStyle(ChatFormatting.RED)
                        : Component.literal("This troop is at peace with you. Nothing hunts within "
                                + (int) SANCTUARY + " blocks of them.").withStyle(ChatFormatting.GREEN), false);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }
        if (!held.has(DataComponents.FOOD)) {
            return super.mobInteract(player, hand);
        }
        if (level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!friendly(player)) {
            player.displayClientMessage(Component.literal("It will not come near enough to take it."), true);
            return InteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        playSound(SoundEvents.GENERIC_EAT, 0.7F, 1.2F);
        ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.3D, getZ(),
                3, 0.3D, 0.2D, 0.3D, 0.0D);
        player.displayClientMessage(Component.literal("It takes it, and shares it round the others."), true);
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------ goals

    /** Food, given for nothing, to whoever near them looks like they need it. */
    private class ShareFoodGoal extends Goal {
        @Nullable
        private Player friend;
        private int ticks;

        ShareFoodGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (betrayed || isBaby() || tickCount < nextShare || getRandom().nextInt(300) != 0) {
                return false;
            }
            for (Player player : level().players()) {
                if (friendly(player) && distanceTo(player) < 14.0F && player.getFoodData().getFoodLevel() < 16) {
                    friend = player;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return friend != null && friend.isAlive() && ticks < 300 && distanceTo(friend) < 20.0F;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void stop() {
            nextShare = tickCount + 20 * 60 * 5 + getRandom().nextInt(20 * 60 * 3);
            friend = null;
        }

        @Override
        public void tick() {
            if (friend == null) {
                return;
            }
            ticks++;
            getLookControl().setLookAt(friend, 30.0F, 30.0F);
            if (distanceTo(friend) > 2.0F) {
                if (ticks % 10 == 1) {
                    getNavigation().moveTo(friend, 1.0D);
                }
                return;
            }
            getNavigation().stop();
            ItemStack gift = switch (getRandom().nextInt(3)) {
                case 0 -> new ItemStack(Items.APPLE);
                case 1 -> new ItemStack(Items.SWEET_BERRIES, 2 + getRandom().nextInt(3));
                default -> new ItemStack(Items.MELON_SLICE, 2);
            };
            if (!friend.getInventory().add(gift)) {
                friend.drop(gift, false);
            }
            playSound(SoundEvents.ITEM_PICKUP, 0.7F, 1.3F);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.3D, getZ(), 3, 0.3D, 0.2D, 0.3D, 0.0D);
            }
            friend.displayClientMessage(Component.literal("A bonobo presses ").append(gift.getHoverName())
                    .append(" into your hands and wanders off.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            friend = null;
            ticks = 1000;
        }
    }

    /** They groom anybody who sits among them. No trust needed - that is rather the point. */
    private class GroomGoal extends Goal {
        @Nullable
        private Player friend;
        private int working;

        GroomGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (betrayed || tickCount < nextGroom || getRandom().nextInt(200) != 0) {
                return false;
            }
            for (Player player : level().players()) {
                if (player instanceof ServerPlayer server && friendly(player) && distanceTo(player) < 10.0F
                        && Infestation.of(server) > 0) {
                    friend = player;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return friend != null && friend.isAlive() && working < 100 && distanceTo(friend) < 16.0F;
        }

        @Override
        public void start() {
            working = 0;
        }

        @Override
        public void stop() {
            nextGroom = tickCount + 1800;
            friend = null;
        }

        @Override
        public void tick() {
            if (friend == null) {
                return;
            }
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
                playSound(SoundEvents.WOOL_HIT, 0.5F, 1.3F);
                server.displayClientMessage(Component.literal(
                        "A bonobo settles against you and starts working through your hair.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    /** Somebody foraging nearby is company: they drift along with you while you work. */
    private class ForageAlongsideGoal extends Goal {
        ForageAlongsideGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (betrayed || getRandom().nextInt(160) != 0) {
                return false;
            }
            Player near = level().getNearestPlayer(Bonobo.this, 20.0D);
            if (near == null || !friendly(near) || distanceTo(near) < 5.0F || home == null
                    || !home.closerToCenterThan(near.position(), RANGE)) {
                return false;
            }
            Vec3 spot = near.position().add(getRandom().nextInt(9) - 4, 0, getRandom().nextInt(9) - 4);
            return getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9D);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone();
        }
    }

    /** A troop has a home range and keeps to it. */
    private class StayInRangeGoal extends Goal {
        StayInRangeGoal() {
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return home != null && distanceToSqr(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D) > RANGE * RANGE;
        }

        @Override
        public boolean canContinueToUse() {
            return home != null && !getNavigation().isDone();
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
        // Bonobos are higher-voiced than chimpanzees - more squeak than hoot.
        return 1.25F + getRandom().nextFloat() * 0.15F;
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
        if (home != null) {
            tag.putLong("Home", home.asLong());
        }
        tag.putBoolean("Betrayed", betrayed);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        troopId = tag.hasUUID("Troop") ? tag.getUUID("Troop") : null;
        home = tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null;
        betrayed = tag.getBoolean("Betrayed");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
