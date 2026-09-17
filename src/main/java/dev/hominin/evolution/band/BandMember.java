package dev.hominin.evolution.band;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.goal.ArmSelfGoal;
import dev.hominin.evolution.band.goal.ArmedMeleeGoal;
import dev.hominin.evolution.band.goal.FleeToTreeGoal;
import dev.hominin.evolution.band.goal.FollowLeaderGoal;
import dev.hominin.evolution.band.goal.ForageGoal;
import dev.hominin.evolution.band.goal.GatherItemsGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A hominin in a band: another member of the player's species, who wanders, keeps up
 * with its band, feeds itself and looks after itself.
 *
 * <p>It has the same needs a player has and meets them the same way. It gets hungry,
 * and forages or eats what it carries - or what it is handed, and says so when it is
 * going short. It picks up food and weapons. With nothing to defend itself with it
 * pulls a branch out of a tree, and when something attacks it, it fights with what it
 * holds or runs for a trunk and climbs out of reach. When something attacks its
 * leader, it fights regardless.
 *
 * <p>Two kinds exist. Members of the player's band have a leader (the player). Members
 * of a wild band have a band id and follow that band's alpha instead; they cannot be
 * recruited, only traded with.
 */
public class BandMember extends PathfinderMob implements InventoryCarrier {
    private static final EntityDataAccessor<String> STAGE =
            SynchedEntityData.defineId(BandMember.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> BABY =
            SynchedEntityData.defineId(BandMember.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMBING =
            SynchedEntityData.defineId(BandMember.class, EntityDataSerializers.BOOLEAN);
    /** Synced so the talk menu knows it is facing another band. */
    private static final EntityDataAccessor<Boolean> WILD =
            SynchedEntityData.defineId(BandMember.class, EntityDataSerializers.BOOLEAN);
    /** Synced for the climbing animation: hauling itself up a wall or cliff. */
    private static final EntityDataAccessor<Boolean> WALL_CLIMBING =
            SynchedEntityData.defineId(BandMember.class, EntityDataSerializers.BOOLEAN);

    /** Entity event: play the threat display animation on clients. */
    public static final byte DISPLAY_EVENT = 64;

    public static final int MAX_HUNGER = 20;
    /** Below this it starts looking for food. */
    public static final int HUNGRY = 16;
    /** Below this it eats what it is carrying. */
    private static final int EATS_BELOW = 14;
    /** Below this it tells its leader. */
    private static final int COMPLAINS_BELOW = 10;
    /** One point of hunger a minute: a full stomach lasts most of a day. */
    private static final int HUNGER_TICKS = 1200;
    private static final int HEAL_TICKS = 200;
    private static final int TALK_COOLDOWN = 3600;

    private static final float ARM_INSTEAD_OF_FLEE = 0.35F;
    private static final float DISPLAY_WHEN_HURT = 0.3F;

    /** One in-game day from conception to birth; two more to grow up. */
    private static final int PREGNANCY_TICKS = 24000;
    private static final int GROW_UP_TICKS = 48000;
    /** How long after being fed a member is ready to pair with another who was fed. */
    private static final int READY_TICKS = 200;

    /** Every bout of wrestling makes a better fighter, up to +2 damage. */
    public static final int MAX_FIGHT_SKILL = 20;
    private static final ResourceLocation FIGHT_SKILL_ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "fight_skill");

    /** How long defending a leader overrides the urge to run. */
    private static final int DEFEND_TICKS = 300;

    /** Weapons in order of preference, worst first. */
    private static final List<Supplier<Item>> WEAPONS = List.of(
            ModItems.LONG_BRANCH, ModItems.SHARPENED_STICK, ModItems.POINTY_STICK,
            ModItems.WOODEN_CLUB, ModItems.SHARPENED_SPEAR, ModItems.FIRE_HARDENED_SPEAR);

    private static final String[] SYLLABLES = {"ka", "nu", "ba", "mo", "ti", "ra", "ku", "sha", "do", "le",
            "ma", "gu", "ri", "ya", "zo", "en", "ok", "wa", "hu", "ji"};

    /** Nine things in all, like a hotbar: two hands and seven carried. */
    public static final int PACK_SLOTS = 7;
    private final SimpleContainer inventory = new SimpleContainer(PACK_SLOTS);

    /** Tools that help dig for insects, best last. */
    private static final List<Supplier<Item>> FORAGING_TOOLS = List.of(
            () -> net.minecraft.world.item.Items.STICK, ModItems.SHARPENED_STICK, ModItems.DIGGING_STICK);

    /** What the hands are wanted for, set by whatever the member is busy with. */
    public enum HandTask {
        NONE, FORAGE, FISH
    }

    private HandTask handTask = HandTask.NONE;
    /** Heard the band's alarm or display: arm up for a while. */
    private int alarmTicks;
    /** Something hunted this member. Once it is safe, a stick gets a point on it. */
    private boolean sharpenUrge;
    private boolean predatorNearby;

    // Adrenaline: fight, flight, or freeze.
    private static final int ADRENALINE_COOLDOWN = 5 * 60 * 20;
    private static final int ADRENALINE_TICKS = 20 * 20;
    private static final float FREEZE_CHANCE = 0.12F;
    private static final int FREEZE_TICKS = 100;
    /** How long a freeze goes on before the band notices. */
    private static final int FREEZE_NOTICED_AFTER = 60;
    private long adrenalineReadyAt;
    private long fightingUntil;
    private boolean panicking;
    private int freezeTicks;
    @Nullable
    private LivingEntity freezeThreat;

    // Fission-fusion: which party of the band this member is in today. 0 stays with the leader.
    private int party;
    @Nullable
    private UUID partyHead;

    // Wall climbing when the way is blocked.
    private boolean wallClimbing;
    private double wallClimbStartY;
    private int stuckTicks;
    private int wallClimbCooldown;
    @Nullable
    private BlockPos unreachableTarget;
    @Nullable
    private UUID leader;
    @Nullable
    private UUID bandId;
    @Nullable
    private UUID alpha;
    private boolean female;
    private int hunger = MAX_HUNGER;
    private int hungerClock;
    private int eatCooldown;
    private int fightSkill;

    private int fleeTicks;
    private int armUrgencyTicks;
    private int defendTicks;
    private int safeLandingTicks;
    private int displayDelay = -1;
    private int readyTicks;
    private int pregnancyTicks;
    private int growUpTicks;
    private long nextTalk;
    private int forageTogetherTicks;
    @Nullable
    private BlockPos forageAnchor;
    private int wrestleCooldown;

    /** What the player sent this member to get, for whom, and how long it will keep trying. */
    @Nullable
    private FetchKind fetchKind;
    @Nullable
    private UUID fetchFor;
    private int fetchTicks;
    /** Alloparenting: the child this adult watches over, or the adult watching over this child. */
    @Nullable
    private UUID ward;
    @Nullable
    private UUID caretaker;

    /** Three foods this member likes best, as item ids. Fed one, it eats more and warms to you. */
    private final java.util.List<ResourceLocation> favouriteFoods = new java.util.ArrayList<>();
    /** How attached this member is to the player. Unused until erectus. */
    private int bond;
    /** Whether this member has made a chopper, which it must before trying a multi tool. */
    private boolean madeChopper;

    private static final String[] FOOD_CHOICES = {
            "hominin_evolution:grub", "hominin_evolution:beetle", "hominin_evolution:earthworm",
            "hominin_evolution:termite_stick", "hominin_evolution:bone_marrow", "hominin_evolution:meat_chunk",
            "minecraft:sweet_berries", "minecraft:apple", "minecraft:carrot", "minecraft:melon_slice",
            "minecraft:glow_berries", "minecraft:beef", "minecraft:porkchop", "minecraft:chicken",
            "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:potato", "minecraft:beetroot"};

    /** Joins in with whatever the leader attacks. Turned off with "Don't hunt with me". */
    private boolean huntWithLeader = true;
    /** Ticks left of an active hunting party. */
    private int huntTicks;
    /** Ticks left of being told to get up a tree. */
    private int climbOrderTicks;
    /** Off exploring on its own; highlighted while away. */
    private int excursionTicks;
    @Nullable
    private BlockPos excursionTarget;

    /** A wild band travelling with a player for the day. */
    @Nullable
    private UUID guestOf;
    /** Where a wild band is heading when it leaves for the night. */
    @Nullable
    private BlockPos leavePos;
    private int leaveTicks;
    @Nullable
    private UUID wrestlePartner;
    private int wrestleTicks;
    /** Asked to find food and bring it to this player. */
    @Nullable
    private UUID deliverFoodTo;
    private int deliverTicks;

    /** Client only: tick the last display started, for the animation. */
    public int clientDisplayStart = -1000;

    public BandMember(EntityType<? extends BandMember> type, Level level) {
        super(type, level);
        setCanPickUpLoot(true);
        setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        setDropChance(EquipmentSlot.OFFHAND, 1.0F);
        female = random.nextBoolean();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STAGE, HomininEvolutionMod.MODID + ":australopithecus");
        builder.define(BABY, false);
        builder.define(CLIMBING, false);
        builder.define(WILD, false);
        builder.define(WALL_CLIMBING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new dev.hominin.evolution.band.goal.FreezeGoal(this));
        goalSelector.addGoal(1, new FleeToTreeGoal(this));
        goalSelector.addGoal(2, new ArmedMeleeGoal(this, 1.25D));
        goalSelector.addGoal(2, new dev.hominin.evolution.band.goal.WrestleGoal(this));
        goalSelector.addGoal(3, new dev.hominin.evolution.band.goal.FetchGoal(this));
        goalSelector.addGoal(3, new ArmSelfGoal(this));
        goalSelector.addGoal(4, new GatherItemsGoal(this));
        goalSelector.addGoal(4, new dev.hominin.evolution.band.goal.SharpenStickGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.TermiteFishGoal(this));
        goalSelector.addGoal(5, new ForageGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.NestBuildGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.TinkerGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.CraftGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.ExcursionGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.RoamGoal(this));
        goalSelector.addGoal(7, new FollowLeaderGoal(this, 1.1D, 10.0F, 4.0F));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        // Players are ignored: a stray swing from your own leader is not a reason to fight them.
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Player.class).setAlertOthers());
        // A hunting party goes after small, weak animals of its own accord.
        targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
                this, net.minecraft.world.entity.animal.Animal.class, 10, true, false,
                target -> isHunting() && isSmallPrey(target)));
    }

    // ------------------------------------------------------------ identity

    public ResourceLocation getStage() {
        ResourceLocation stage = ResourceLocation.tryParse(entityData.get(STAGE));
        return stage != null ? stage : ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "australopithecus");
    }

    public void setStage(ResourceLocation stage) {
        entityData.set(STAGE, stage.toString());
    }

    @Nullable
    public UUID getLeader() {
        return leader;
    }

    public void setLeader(@Nullable UUID leader) {
        this.leader = leader;
    }

    @Nullable
    public Player leaderPlayer() {
        return leader == null ? null : level().getPlayerByUUID(leader);
    }

    public boolean isLedBy(Player player) {
        return player.getUUID().equals(leader);
    }

    @Nullable
    public UUID getBandId() {
        return bandId;
    }

    public boolean isWild() {
        return bandId != null && leader == null;
    }

    /** Joins a wild band, following its alpha - or leading it, if the alpha is null. */
    public void joinWildBand(UUID band, @Nullable UUID alphaId) {
        this.bandId = band;
        this.alpha = alphaId;
        this.leader = null;
        entityData.set(WILD, true);
    }

    /** Client-safe: whether this is a member of some other band. */
    public boolean isOtherBand() {
        return entityData.get(WILD);
    }

    public boolean isAlpha() {
        return isWild() && alpha == null;
    }

    @Nullable
    public UUID getAlpha() {
        return alpha;
    }

    public boolean isGuestOf(Player player) {
        return player.getUUID().equals(guestOf);
    }

    public boolean isGuest() {
        return guestOf != null;
    }

    /** Fission-fusion: this wild band joins the player's for the rest of the day. */
    public void travelWith(Player player) {
        guestOf = player.getUUID();
        leavePos = null;
        leaveTicks = 0;
    }

    /** Leaves for the night, heading away from the player. */
    public void leaveTowards(BlockPos where) {
        guestOf = null;
        leavePos = where;
        leaveTicks = 1200;
    }

    @Nullable
    public BlockPos getLeavePos() {
        return leavePos;
    }

    /** Whoever this member currently keeps company with: its leader, or the player its band travels with. */
    @Nullable
    public Player companionPlayer() {
        Player player = leaderPlayer();
        if (player == null && guestOf != null) {
            player = level().getPlayerByUUID(guestOf);
        }
        return player;
    }

    public boolean isCompanionOf(Player player) {
        return isLedBy(player) || isGuestOf(player);
    }

    /** Who this member keeps up with: its leader, or its wild band's alpha. */
    @Nullable
    public LivingEntity followTarget() {
        if (leavePos != null) {
            return null;
        }
        if (level() instanceof ServerLevel server) {
            // A child keeps to whoever is minding it; a minder stays near the child.
            UUID minded = isBaby() ? caretaker : ward;
            if (minded != null && server.getEntity(minded) instanceof BandMember other && other.isAlive()) {
                if (isBaby() || other.distanceToSqr(this) > 8.0D * 8.0D) {
                    return other;
                }
            }
        }
        Player player = companionPlayer();
        if (player != null && party > 0 && partyHead != null && level() instanceof ServerLevel server) {
            // Off with a party for the day: keep with its head, who keeps within reach of the leader.
            if (!partyHead.equals(getUUID())) {
                if (server.getEntity(partyHead) instanceof BandMember head && head.isAlive() && head.party == party) {
                    return head;
                }
            } else if (distanceToSqr(player) < PARTY_LEASH * PARTY_LEASH) {
                return null;
            }
        }
        if (player != null) {
            return player;
        }
        if (alpha != null && level() instanceof ServerLevel server
                && server.getEntity(alpha) instanceof BandMember alphaMember && alphaMember.isAlive()) {
            return alphaMember;
        }
        return null;
    }

    /** How far a party's head lets the leader get before walking back towards them. */
    public static final double PARTY_LEASH = 30.0D;

    public int getParty() {
        return party;
    }

    public void joinParty(int party, @Nullable UUID head) {
        this.party = party;
        this.partyHead = party == 0 ? null : head;
    }

    /** A party's head stops well short of the leader: it only means to stay in reach. */
    public float followStopDistance(LivingEntity target, float normal) {
        return party > 0 && target instanceof Player && getUUID().equals(partyHead) ? 18.0F : normal;
    }

    public boolean isFemale() {
        return female;
    }

    /** A name, given the first time one is needed. */
    public boolean hasMadeChopper() {
        return madeChopper;
    }

    public void markMadeChopper() {
        madeChopper = true;
    }

    public int getBond() {
        return bond;
    }

    /** Picks three favourite foods, the first time they are needed. */
    private void ensureFavourites() {
        while (favouriteFoods.size() < 3) {
            ResourceLocation food = ResourceLocation.parse(FOOD_CHOICES[random.nextInt(FOOD_CHOICES.length)]);
            if (!favouriteFoods.contains(food)) {
                favouriteFoods.add(food);
            }
        }
    }

    public boolean isFavourite(ItemStack stack) {
        ensureFavourites();
        return favouriteFoods.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public void ensureName() {
        if (!hasCustomName()) {
            String name = SYLLABLES[random.nextInt(SYLLABLES.length)] + SYLLABLES[random.nextInt(SYLLABLES.length)];
            setCustomName(Component.literal(Character.toUpperCase(name.charAt(0)) + name.substring(1)));
        }
    }

    // ------------------------------------------------------------ age

    @Override
    public boolean isBaby() {
        return entityData.get(BABY);
    }

    public void makeBaby() {
        entityData.set(BABY, true);
        growUpTicks = GROW_UP_TICKS;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (BABY.equals(key)) {
            refreshDimensions();
        }
    }

    public boolean isPregnant() {
        return pregnancyTicks > 0;
    }

    // ------------------------------------------------------------ hunger

    public int getHunger() {
        return hunger;
    }

    public boolean isHungry() {
        return hunger < HUNGRY;
    }

    public boolean hasFood() {
        if (getOffhandItem().has(DataComponents.FOOD)) {
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).has(DataComponents.FOOD)) {
                return true;
            }
        }
        return false;
    }

    private boolean eatFromInventory() {
        ItemStack inHand = getOffhandItem();
        FoodProperties handFood = inHand.get(DataComponents.FOOD);
        if (handFood != null) {
            consume(handFood);
            inHand.shrink(1);
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                consume(food);
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static final int EATING_TICKS = 32;
    private int eatingTicks;

    /** Food to the off hand, and start chewing - the way a player eats, not in one gulp. */
    private void startEating() {
        if (isBaby()) {
            eatFromInventory();
            return;
        }
        if (!getOffhandItem().has(DataComponents.FOOD)) {
            int foodSlot = -1;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).has(DataComponents.FOOD)) {
                    foodSlot = slot;
                    break;
                }
            }
            if (foodSlot < 0) {
                return;
            }
            ItemStack previous = getOffhandItem();
            setItemSlot(EquipmentSlot.OFFHAND, inventory.removeItemNoUpdate(foodSlot));
            if (!previous.isEmpty()) {
                ItemStack left = inventory.addItem(previous);
                if (!left.isEmpty()) {
                    spawnAtLocation(left);
                }
            }
        }
        eatingTicks = EATING_TICKS;
    }

    private void tickEating() {
        ItemStack food = getOffhandItem();
        FoodProperties properties = food.get(DataComponents.FOOD);
        if (properties == null) {
            eatingTicks = 0;
            return;
        }
        if (eatingTicks % 4 == 0) {
            playSound(SoundEvents.GENERIC_EAT, 0.5F + 0.5F * random.nextInt(2),
                    (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
            if (level() instanceof ServerLevel server) {
                net.minecraft.world.phys.Vec3 mouth = getEyePosition().add(getLookAngle().scale(0.35D)).subtract(0.0D, 0.15D, 0.0D);
                server.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, food),
                        mouth.x, mouth.y, mouth.z, 3, 0.08D, 0.05D, 0.08D, 0.03D);
            }
        }
        if (eatingTicks % 8 == 0) {
            swing(InteractionHand.OFF_HAND);
        }
        if (--eatingTicks == 0) {
            consume(properties);
            food.shrink(1);
        }
    }

    private void consume(FoodProperties food) {
        hunger = Math.min(MAX_HUNGER, hunger + food.nutrition());
        eatCooldown = 40;
        swing(InteractionHand.MAIN_HAND);
        playSound(SoundEvents.GENERIC_EAT, 0.8F, 0.9F + random.nextFloat() * 0.2F);
        // A stick of termites leaves the stick behind, for a hominin as for a player.
        food.usingConvertsTo().ifPresent(leftover -> addToInventory(leftover.copy()));
    }

    /** The leader started foraging while this member was hungry: go and forage beside them. */
    public void forageAlongside(BlockPos where) {
        forageTogetherTicks = 600;
        forageAnchor = where;
    }

    public boolean isForagingTogether() {
        return forageTogetherTicks > 0 && forageAnchor != null;
    }

    @Nullable
    public BlockPos getForageAnchor() {
        return forageAnchor;
    }

    // ------------------------------------------------------------ items

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    public static boolean isWeapon(ItemStack stack) {
        return weaponRank(stack) >= 0;
    }

    private static int weaponRank(ItemStack stack) {
        for (int i = 0; i < WEAPONS.size(); i++) {
            if (stack.is(WEAPONS.get(i).get())) {
                return i;
            }
        }
        return -1;
    }

    /** Holding a weapon, ready to use. */
    public boolean hasWeapon() {
        return isWeapon(getMainHandItem());
    }

    /** Has a weapon somewhere - in hand or carried. */
    public boolean carriesWeapon() {
        return bestWeaponRank() >= 0;
    }

    /** Food, and things to hit with, and a stick or two for termites. The rest is left for the player. */
    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        boolean useful = stack.has(DataComponents.FOOD) || isWeapon(stack)
                || (stack.is(net.minecraft.world.item.Items.STICK) && countCarried(s -> s.is(stack.getItem())) < 2)
                || (dev.hominin.evolution.band.goal.CraftGoal.canCraft(this) && wantsMaterial(stack));
        return useful && hasRoomFor(stack);
    }

    /** What a thing is worth keeping, to this member: its trade worth to its own kind. */
    private int keepValue(ItemStack stack) {
        int value = Trading.valueOf(stack, getStage());
        if (stack.is(net.minecraft.world.item.Items.STICK)) {
            // A stick is for termites and sharpening: worth more than it trades for.
            value += 5;
        }
        if (stack.has(DataComponents.FOOD) && isHungry()) {
            value += 10;
        }
        return value;
    }

    /** The carried slot that would be given up first for something better, or -1 if none. */
    private int leastValuableSlot() {
        int worst = -1;
        int worstValue = Integer.MAX_VALUE;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && keepValue(stack) < worstValue) {
                worstValue = keepValue(stack);
                worst = slot;
            }
        }
        return worst;
    }

    /** Room for it now, or something worth less that could be dropped to make room. */
    public boolean hasRoomFor(ItemStack stack) {
        if (inventory.canAddItem(stack)) {
            return true;
        }
        int worst = leastValuableSlot();
        return worst >= 0 && keepValue(inventory.getItem(worst)) < keepValue(stack);
    }

    /** Hands full: drops the least valuable thing carried, if the new one is worth more. */
    private void makeRoomFor(ItemStack stack) {
        if (inventory.canAddItem(stack)) {
            return;
        }
        int worst = leastValuableSlot();
        if (worst >= 0 && keepValue(inventory.getItem(worst)) < keepValue(stack)) {
            spawnAtLocation(inventory.removeItemNoUpdate(worst));
        }
    }

    /** Makers keep a little raw material: a couple of sticks, a few stones, a flake. */
    private boolean wantsMaterial(ItemStack stack) {
        if (stack.is(net.minecraft.world.item.Items.STICK)) {
            return countCarried(s -> s.is(net.minecraft.world.item.Items.STICK)) < 2;
        }
        if (stack.is(ModItems.ROCK.get()) || stack.is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE)) {
            return countCarried(s -> s.is(ModItems.ROCK.get()) || s.is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE)) < 4;
        }
        if (stack.is(dev.hominin.evolution.ModTags.Items.FLAKES) || stack.is(ModItems.GRINDING_ROCK.get())) {
            return countCarried(s -> s.is(stack.getItem())) < 1;
        }
        return false;
    }

    private int countCarried(java.util.function.Predicate<ItemStack> test) {
        int count = test.test(getMainHandItem()) ? getMainHandItem().getCount() : 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (test.test(inventory.getItem(slot))) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    /** The best weapon carried, handed over - from the pack if there is one there, the hand otherwise. */
    public ItemStack takeBestWeapon() {
        int bestSlot = -1;
        int bestRank = -1;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            int rank = weaponRank(inventory.getItem(slot));
            if (rank > bestRank) {
                bestRank = rank;
                bestSlot = slot;
            }
        }
        if (bestSlot >= 0) {
            return inventory.removeItem(bestSlot, 1);
        }
        return isWeapon(getMainHandItem()) ? getMainHandItem().split(1) : ItemStack.EMPTY;
    }

    public int bestWeaponRank() {
        int best = weaponRank(getMainHandItem());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            best = Math.max(best, weaponRank(inventory.getItem(slot)));
        }
        return best;
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        makeRoomFor(itemEntity.getItem());
        InventoryCarrier.pickUpItem(this, this, itemEntity);
        equipBestWeapon();
    }

    public void addToInventory(ItemStack stack) {
        makeRoomFor(stack);
        ItemStack left = inventory.addItem(stack);
        if (!left.isEmpty()) {
            spawnAtLocation(left);
        }
        equipBestWeapon();
    }

    /** Puts the right thing in each hand for what is going on. */
    public void equipBestWeapon() {
        updateHands();
    }

    public void setHandTask(HandTask task) {
        if (handTask != task) {
            handTask = task;
            updateHands();
        }
    }

    /** The band is alarmed - a display, or a call for help: have something to fight with in hand. */
    public void raiseAlarm(int ticks) {
        alarmTicks = Math.max(alarmTicks, ticks);
        updateHands();
    }

    /** Hurt, under attack, alarmed, or a predator close by. */
    public boolean inDanger() {
        return fleeTicks > 0 || defendTicks > 0 || alarmTicks > 0 || getTarget() != null
                || getHealth() < getMaxHealth() * 0.5F || predatorNearby;
    }

    private void lookForPredators() {
        predatorNearby = !level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(10.0D),
                mob -> mob.isAlive() && (mob.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS)
                        || (mob instanceof net.minecraft.world.entity.monster.Enemy && mob.getTarget() != null))).isEmpty();
    }

    /**
     * The hands follow the situation. In danger, the best weapon. Foraging, something to dig
     * with; fishing, the stick. Otherwise a weapon if there is one, or the best tool. And a
     * hungry member keeps food ready in the other hand.
     */
    public void updateHands() {
        if (isBaby() || level().isClientSide()) {
            return;
        }
        if (inDanger()) {
            wieldBest(BandMember::weaponRank);
        } else if (handTask == HandTask.FORAGE) {
            wieldBest(BandMember::foragingRank);
        } else if (handTask == HandTask.FISH) {
            wieldBest(s -> s.is(net.minecraft.world.item.Items.STICK) ? 0 : -1);
        } else {
            // Nothing to do: carry the most valuable thing, where it can be seen.
            ResourceLocation stage = getStage();
            wieldBest(s -> s.isEmpty() || s.has(DataComponents.FOOD) ? -1 : Trading.valueOf(s, stage));
        }
        ItemStack off = getOffhandItem();
        if (off.isEmpty() && isHungry()) {
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).has(DataComponents.FOOD)) {
                    setItemSlot(EquipmentSlot.OFFHAND, inventory.removeItemNoUpdate(slot));
                    break;
                }
            }
        } else if (!off.isEmpty() && (!isHungry() || !off.has(DataComponents.FOOD)) && inventory.canAddItem(off)) {
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            inventory.addItem(off);
        }
    }

    private static int foragingRank(ItemStack stack) {
        for (int i = 0; i < FORAGING_TOOLS.size(); i++) {
            if (stack.is(FORAGING_TOOLS.get(i).get())) {
                return i;
            }
        }
        return -1;
    }

    /** How much the held tool helps turn up insects: nothing for bare hands. */
    public float foragingBonus() {
        return switch (foragingRank(getMainHandItem())) {
            case 0, 1 -> 0.15F;
            case 2 -> 0.25F;
            default -> 0.0F;
        };
    }

    /** Swaps the best-ranked carried thing into the main hand, if it beats what is there. */
    private void wieldBest(java.util.function.ToIntFunction<ItemStack> rank) {
        int bestSlot = -1;
        int bestRank = rank.applyAsInt(getMainHandItem());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            int r = rank.applyAsInt(inventory.getItem(slot));
            if (r > bestRank) {
                bestRank = r;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) {
            return;
        }
        ItemStack stored = inventory.getItem(bestSlot);
        ItemStack chosen = stored.getCount() > 1 ? stored.split(1) : inventory.removeItemNoUpdate(bestSlot);
        ItemStack previous = getMainHandItem();
        setItemSlot(EquipmentSlot.MAINHAND, chosen);
        if (!previous.isEmpty()) {
            ItemStack left = inventory.addItem(previous);
            if (!left.isEmpty()) {
                spawnAtLocation(left);
            }
        }
    }

    /** Empty-handed and unarmed: carry the most useful tool where it can be seen. */
    private void holdSomethingUseful() {
        if (!getMainHandItem().isEmpty()) {
            return;
        }
        int bestSlot = -1;
        int bestValue = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            int value = Trading.valueOf(stack);
            if (!stack.has(DataComponents.FOOD) && value > bestValue) {
                bestValue = value;
                bestSlot = slot;
            }
        }
        if (bestSlot >= 0) {
            setItemSlot(EquipmentSlot.MAINHAND, inventory.removeItem(bestSlot, 1));
        }
    }

    /** Hands over one carried thing: something from the pack first, the held weapon last. */
    private ItemStack handOver() {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (!inventory.getItem(slot).isEmpty()) {
                return inventory.removeItem(slot, 1);
            }
        }
        ItemStack held = getMainHandItem();
        if (!held.isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        return held;
    }

    // ------------------------------------------------------------ interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide()) {
            if (player.isShiftKeyDown()) {
                SocialSelection.entityId = getId();
                SocialSelection.selectedAtMillis = net.minecraft.Util.getMillis();
            }
            return InteractionResult.SUCCESS;
        }
        ensureName();
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            describeTo(player);
            player.sendSystemMessage(Component.literal("Press H within 5 seconds to talk to "
                    + getName().getString() + ".").withStyle(ChatFormatting.GRAY));
            return InteractionResult.CONSUME;
        }
        if (isWild()) {
            FoodProperties guestFood = held.get(DataComponents.FOOD);
            if (guestFood != null && isGuestOf(player)) {
                feedFromHand(player, held, guestFood);
            } else if (!held.isEmpty()) {
                Trading.offer(this, player, held);
            } else {
                player.displayClientMessage(Component.literal(getName().getString()
                        + " watches you warily. Offer something."), true);
            }
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            if (isLedBy(player)) {
                ItemStack given = handOver();
                if (given.isEmpty()) {
                    player.displayClientMessage(Component.literal(getName().getString() + " has nothing to give."), true);
                } else {
                    player.displayClientMessage(Component.literal(getName().getString() + " hands you ")
                            .append(given.getHoverName()).append("."), true);
                    if (!player.getInventory().add(given)) {
                        player.drop(given, false);
                    }
                }
            }
            return InteractionResult.CONSUME;
        }
        FoodProperties food = held.get(DataComponents.FOOD);
        if (food != null) {
            feedFromHand(player, held, food);
        } else if (isLedBy(player)) {
            addToInventory(held.copyWithCount(1));
            playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        }
        return InteractionResult.CONSUME;
    }

    private void feedFromHand(Player player, ItemStack held, FoodProperties food) {
        if (hunger >= MAX_HUNGER) {
            player.displayClientMessage(Component.literal(getName().getString() + " is not hungry."), true);
            return;
        }
        boolean recruiting = leader == null && bandId == null;
        if (recruiting && !Band.hasRoomFor(player)) {
            player.displayClientMessage(Component.literal("Your band is as big as the land can feed."), true);
            return;
        }
        boolean favourite = isFavourite(held);
        consume(food);
        heal(2.0F);
        if (favourite) {
            // A favourite goes further, and is remembered.
            hunger = Math.min(MAX_HUNGER, hunger + Math.max(2, food.nutrition() / 2));
            bond++;
            player.displayClientMessage(Component.literal(getName().getString() + " loves that!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.3D, getZ(),
                3, 0.3D, 0.2D, 0.3D, 0.0D);
        if (recruiting) {
            leader = player.getUUID();
            player.displayClientMessage(Component.literal(getName().getString()
                    + " eats from your hand, and stays close."), true);
            return;
        }
        if (!isBaby() && !isPregnant()) {
            readyTicks = READY_TICKS;
            Band.tryPair(this);
        }
    }

    private void describeTo(Player player) {
        ensureFavourites();
        StringBuilder likes = new StringBuilder("Favourite foods: ");
        for (int i = 0; i < favouriteFoods.size(); i++) {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(favouriteFoods.get(i));
            likes.append(i == 0 ? "" : ", ").append(new ItemStack(item).getHoverName().getString());
        }
        player.sendSystemMessage(Component.literal(likes.toString()).withStyle(ChatFormatting.GRAY));
        StringBuilder text = new StringBuilder(getName().getString())
                .append(isBaby() ? " (young " : " (").append(female ? "female)" : "male)")
                .append(" - hunger ").append(hunger).append("/").append(MAX_HUNGER);
        if (isPregnant()) {
            text.append(" - pregnant, due in about ").append(Math.max(1, pregnancyTicks / 1000)).append(" hours");
        }
        if (fightSkill > 0) {
            text.append(" - fighting ").append(fightSkill).append("/").append(MAX_FIGHT_SKILL);
        }
        if (bond > 0) {
            text.append(" - bond ").append(bond);
        }
        player.displayClientMessage(Component.literal(text.toString()), true);
    }

    /** Ready to pair: fed recently, grown, and not already expecting. */
    public boolean isReadyToPair() {
        return readyTicks > 0 && !isBaby() && !isPregnant();
    }

    public void conceive() {
        readyTicks = 0;
        if (female) {
            pregnancyTicks = PREGNANCY_TICKS;
        }
    }

    public void clearReady() {
        readyTicks = 0;
    }

    public boolean wantsToSharpen() {
        return sharpenUrge;
    }

    public void sharpened() {
        sharpenUrge = false;
    }

    // ------------------------------------------------------------ wrestling

    /**
     * Play-fighting with the leader. No damage either way - but it is how young primates
     * learn to fight, and every bout leaves this one a little better at it.
     */
    public void wrestle(Player player) {
        if (wrestleCooldown > 0) {
            return;
        }
        wrestleCooldown = 10;
        ensureName();
        double dx = getX() - player.getX();
        double dz = getZ() - player.getZ();
        knockback(0.25D, -dx, -dz);
        playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.2F);
        // Now it is a game, and it wrestles back until one of you stops.
        wrestlePartner = player.getUUID();
        wrestleTicks = WRESTLE_TICKS;
        if (fightSkill < MAX_FIGHT_SKILL && random.nextInt(3) == 0) {
            fightSkill++;
            applyFightSkill();
        }
        player.displayClientMessage(Component.literal("You wrestle with " + getName().getString() + "."), true);
    }

    // ------------------------------------------------------------ hunting, climbing, wandering

    public boolean huntsWithLeader() {
        return huntWithLeader;
    }

    public void setHuntWithLeader(boolean hunt) {
        huntWithLeader = hunt;
        if (!hunt) {
            huntTicks = 0;
        }
    }

    public void startHunt(int ticks) {
        huntWithLeader = true;
        huntTicks = ticks;
    }

    public boolean isHunting() {
        return huntTicks > 0 && !isBaby();
    }

    private static boolean isSmallPrey(LivingEntity target) {
        return !(target instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame())
                && target.getMaxHealth() <= 10.0F && target.getBbWidth() <= 1.0F;
    }

    // ------------------------------------------------------------ errands

    public void requestFetch(Player player, FetchKind kind) {
        fetchKind = kind;
        fetchFor = player.getUUID();
        fetchTicks = FETCH_TICKS;
    }

    public void clearFetch() {
        fetchKind = null;
        fetchFor = null;
        fetchTicks = 0;
    }

    @Nullable
    public FetchKind getFetchKind() {
        return fetchKind;
    }

    @Nullable
    public Player fetchPlayer() {
        return fetchFor == null ? null : level().getPlayerByUUID(fetchFor);
    }

    private static final int FETCH_TICKS = 2400;

    public boolean carries(FetchKind kind) {
        if (kind.matches(getMainHandItem())) {
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (kind.matches(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    /** Takes one of what was asked for out of the pack, or out of its hand. */
    public ItemStack takeOne(FetchKind kind) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (kind.matches(inventory.getItem(slot))) {
                return inventory.removeItem(slot, 1);
            }
        }
        if (kind.matches(getMainHandItem())) {
            return getMainHandItem().split(1);
        }
        return ItemStack.EMPTY;
    }

    /** One item from a given pack slot, or -1 for the hand. */
    public ItemStack takeFromSlot(int slot) {
        if (slot == -1) {
            return getMainHandItem().isEmpty() ? ItemStack.EMPTY : getMainHandItem().split(1);
        }
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(slot).isEmpty() ? ItemStack.EMPTY : inventory.removeItem(slot, 1);
    }

    // ------------------------------------------------------------ alloparenting

    @Nullable
    public UUID getWard() {
        return ward;
    }

    @Nullable
    public UUID getCaretaker() {
        return caretaker;
    }

    public void mind(BandMember child) {
        ward = child.getUUID();
        child.caretaker = getUUID();
    }

    public void stopMinding() {
        ward = null;
    }

    public void orderClimb(int ticks) {
        climbOrderTicks = ticks;
    }

    public boolean hasClimbOrder() {
        return climbOrderTicks > 0 && !isBaby();
    }

    public boolean isOnExcursion() {
        return excursionTicks > 0;
    }

    @Nullable
    public BlockPos getExcursionTarget() {
        return excursionTarget;
    }

    public void setExcursionTarget(BlockPos target) {
        excursionTarget = target;
    }

    /** Heads off alone for a while, glowing so the player can see where it went. */
    public void startExcursion(BlockPos target, int ticks) {
        excursionTarget = target;
        excursionTicks = ticks;
        addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.GLOWING, ticks, 0, false, false));
    }

    /** Back from wandering: returns to the leader, sometimes with something it found. */
    public void endExcursion() {
        excursionTicks = 0;
        excursionTarget = null;
        removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        Player player = leaderPlayer();
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            Band.returnFromExcursion(this, serverPlayer);
        }
    }

    /** How long a bout lasts after the last move in it. */
    public static final int WRESTLE_TICKS = 300;

    @Nullable
    public Player wrestlePartner() {
        return wrestleTicks > 0 && wrestlePartner != null ? level().getPlayerByUUID(wrestlePartner) : null;
    }

    public void stopWrestling() {
        wrestleTicks = 0;
        wrestlePartner = null;
    }

    /** Asked for food: bring some over, finding it first if need be. */
    public void fetchFoodFor(Player player) {
        deliverFoodTo = player.getUUID();
        deliverTicks = 1200;
        if (!hasFood()) {
            forageAlongside(player.blockPosition());
        }
    }

    /** Hands one piece of carried food straight to the player. Returns false if it had none. */
    public boolean giveFoodTo(Player player) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.has(DataComponents.FOOD)) {
                ItemStack given = stack.split(1);
                player.displayClientMessage(Component.literal(getName().getString() + " hands you ")
                        .append(given.getHoverName()).append("."), true);
                if (!player.getInventory().add(given)) {
                    player.drop(given, false);
                }
                swing(InteractionHand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    /** Hands over the most valuable thing it carries that is not food. Returns it, or empty. */
    public ItemStack mostValuableTool() {
        int bestSlot = -1;
        int bestValue = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            int value = Trading.valueOf(stack);
            if (!stack.has(DataComponents.FOOD) && value > bestValue) {
                bestValue = value;
                bestSlot = slot;
            }
        }
        ItemStack held = getMainHandItem();
        if (Trading.valueOf(held) > bestValue) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return held;
        }
        return bestSlot < 0 ? ItemStack.EMPTY : inventory.removeItem(bestSlot, 1);
    }

    public int valueOfBestTool() {
        int best = Trading.valueOf(getMainHandItem());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.has(DataComponents.FOOD)) {
                best = Math.max(best, Trading.valueOf(stack));
            }
        }
        return best;
    }

    private void applyFightSkill() {
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.addOrUpdateTransientModifier(new AttributeModifier(FIGHT_SKILL_ID, fightSkill * 0.1D,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    // ------------------------------------------------------------ danger

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && isBaby() && source.getEntity() instanceof net.minecraft.world.entity.Mob attacker
                && !(attacker instanceof BandMember)) {
            Band.childInDanger(this, attacker);
        }
        if (hurt && !level().isClientSide() && isOnExcursion() && leaderPlayer() != null) {
            Player player = leaderPlayer();
            // Somewhere out of sight, one of yours is in trouble - and you feel it.
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 600, 0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 600, 0));
            player.sendSystemMessage(Component.literal("You hear " + getName().getString()
                    + " cry out somewhere out there!").withStyle(ChatFormatting.RED));
            excursionTicks = 1;
        }
        if (hurt && !level().isClientSide() && source.getEntity() instanceof LivingEntity attacker
                && !(attacker instanceof Player)) {
            if (!(attacker instanceof BandMember)) {
                sharpenUrge = true;
            }
            adrenaline(attacker);
            raiseAlarm(100);
            if (freezeTicks > 0 || isFighting() || panicking) {
                return hurt;
            }
            if (!carriesWeapon() || isBaby()) {
                if (!isBaby() && random.nextFloat() < ARM_INSTEAD_OF_FLEE) {
                    armUrgencyTicks = 200;
                } else {
                    fleeTicks = 300;
                }
            }
            if (!isBaby() && random.nextFloat() < DISPLAY_WHEN_HURT) {
                Band.memberDisplay(this, 0);
            }
        }
        return hurt;
    }

    /** Fight this, with or without a weapon: it went for the leader. */
    public void defendAgainst(LivingEntity attacker) {
        if (isBaby() || attacker == this || !attacker.isAlive()) {
            return;
        }
        defendTicks = DEFEND_TICKS;
        fleeTicks = 0;
        setTarget(attacker);
    }

    public boolean isDefending() {
        return defendTicks > 0 && getTarget() != null;
    }

    public boolean shouldFlee() {
        return fleeTicks > 0 && freezeTicks <= 0 && (isBaby() || panicking || (!carriesWeapon() && !isDefending()));
    }

    // ------------------------------------------------------------ adrenaline

    /** Flight fired: run for a tree - or, at erectus and later, for the safety of the band. */
    public boolean fleesToSafety() {
        String stage = getStage().getPath();
        return !stage.equals("ardipithecus") && !stage.equals("australopithecus") && !stage.equals("homo_habilis");
    }

    /** Fight fired: stronger, tougher, and more likely to do real harm with each blow. */
    public boolean isFighting() {
        return level().getGameTime() < fightingUntil;
    }

    public boolean isFrozen() {
        return freezeTicks > 0;
    }

    /**
     * Something has come for this member. Once in five minutes the body answers for it:
     * fight - strength and resistance, and blows that tear and crack - or flight, a burst
     * of speed away. Now and then neither: it freezes, and the band has to come for it.
     */
    public void adrenaline(LivingEntity threat) {
        long now = level().getGameTime();
        if (isBaby() || now < adrenalineReadyAt || !threat.isAlive() || threat instanceof Player) {
            return;
        }
        adrenalineReadyAt = now + ADRENALINE_COOLDOWN;
        if (random.nextFloat() < FREEZE_CHANCE) {
            freezeTicks = FREEZE_TICKS;
            freezeThreat = threat;
            getNavigation().stop();
            Band.announceDiscovery(this, " freezes in terror!");
            return;
        }
        float fightChance = carriesWeapon() ? 0.65F : 0.35F;
        if (random.nextFloat() < fightChance) {
            fightingUntil = now + ADRENALINE_TICKS;
            addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, ADRENALINE_TICKS, 1));
            addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, ADRENALINE_TICKS, 0));
            panicking = false;
            fleeTicks = 0;
            defendTicks = DEFEND_TICKS;
            setTarget(threat);
            updateHands();
            Band.announceDiscovery(this, "'s blood is up - they turn and fight!");
        } else {
            addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, ADRENALINE_TICKS, 1));
            panicking = true;
            fleeTicks = ADRENALINE_TICKS;
            defendTicks = 0;
            setTarget(null);
            Band.announceDiscovery(this, " bolts in a panic!");
        }
    }

    private void tickFreeze() {
        if (freezeTicks <= 0) {
            return;
        }
        freezeTicks--;
        if (FREEZE_TICKS - freezeTicks == FREEZE_NOTICED_AFTER && freezeThreat != null && freezeThreat.isAlive()) {
            Band.rushToDefend(this, freezeThreat);
        }
        if (freezeTicks == 0) {
            freezeThreat = null;
            fleeTicks = Math.max(fleeTicks, 200);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living && !(target instanceof Player)) {
            float bonus = isFighting() ? 0.25F : 0.0F;
            dev.hominin.evolution.combat.WoundHandler.cutBy(this, getMainHandItem(), living, bonus);
            dev.hominin.evolution.combat.HeadTraumaHandler.bludgeonBy(this, getMainHandItem(), living, bonus);
        }
        return hit;
    }

    // ------------------------------------------------------------ getting over walls

    public boolean isWallClimbing() {
        return entityData.get(WALL_CLIMBING);
    }

    /** Leaves give way to a hominin up a tree or a wall, and to one just dropping out of the canopy. */
    public boolean phasesThroughLeaves() {
        return isClimbingTree() || wallClimbing || safeLandingTicks > 0;
    }

    /**
     * Blocked, and still trying to get somewhere: climb. Pushing into a wall for a moment,
     * or standing below a place the path could not reach, starts it; like the player, a
     * member can haul itself up four blocks of anything before it has to let go.
     */
    private void tickWallClimb() {
        if (isClimbingTree() || isInWater() || isPassenger() || freezeTicks > 0) {
            stopWallClimb(false);
            return;
        }
        net.minecraft.world.level.pathfinder.Path path = getNavigation().getPath();
        if (path != null && !path.canReach() && path.getTarget().getY() > getBlockY()) {
            unreachableTarget = path.getTarget();
        }
        net.minecraft.world.phys.Vec3 ahead = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, getYRot());
        if (wallClimbing) {
            resetFallDistance();
            if (unreachableTarget != null) {
                getMoveControl().setWantedPosition(unreachableTarget.getX() + 0.5D, unreachableTarget.getY(),
                        unreachableTarget.getZ() + 0.5D, 1.0D);
            } else {
                getMoveControl().setWantedPosition(getX() + ahead.x * 2.0D, getY() + 1.0D, getZ() + ahead.z * 2.0D, 1.0D);
            }
            if (!horizontalCollision) {
                // Over the top: a last shove onto the ledge.
                setDeltaMovement(ahead.x * 0.25D, Math.max(getDeltaMovement().y, 0.1D), ahead.z * 0.25D);
                stopWallClimb(false);
            } else if (getY() - wallClimbStartY >= dev.hominin.evolution.climb.Climbing.WALL_CLIMB_LIMIT) {
                stopWallClimb(true);
            }
            return;
        }
        if (wallClimbCooldown > 0) {
            wallClimbCooldown--;
            stuckTicks = 0;
            return;
        }
        boolean trying = !getNavigation().isDone() || getMoveControl().hasWanted();
        if (getNavigation().isDone() && unreachableTarget != null) {
            double dx = unreachableTarget.getX() + 0.5D - getX();
            double dz = unreachableTarget.getZ() + 0.5D - getZ();
            double flat = dx * dx + dz * dz;
            if (unreachableTarget.getY() > getY() + 0.5D && flat < 12.0D * 12.0D && flat > 0.5D) {
                getMoveControl().setWantedPosition(unreachableTarget.getX() + 0.5D, unreachableTarget.getY(),
                        unreachableTarget.getZ() + 0.5D, 1.0D);
                trying = true;
            } else {
                unreachableTarget = null;
            }
        }
        if (trying && horizontalCollision && onGround()) {
            if (++stuckTicks >= 12) {
                wallClimbing = true;
                wallClimbStartY = getY();
                entityData.set(WALL_CLIMBING, true);
                stuckTicks = 0;
            }
        } else if (stuckTicks > 0) {
            stuckTicks--;
        }
    }

    private void stopWallClimb(boolean gaveUp) {
        if (!wallClimbing) {
            return;
        }
        wallClimbing = false;
        entityData.set(WALL_CLIMBING, false);
        safeLandingTicks = Math.max(safeLandingTicks, 40);
        if (gaveUp) {
            wallClimbCooldown = 200;
            unreachableTarget = null;
        }
    }

    public boolean wantsWeaponUrgently() {
        return armUrgencyTicks > 0;
    }

    public void setClimbingTree(boolean climbing) {
        if (entityData.get(CLIMBING) && !climbing) {
            safeLandingTicks = 60;
        }
        entityData.set(CLIMBING, climbing);
    }

    public boolean isClimbingTree() {
        return entityData.get(CLIMBING);
    }

    /** Clinging to a trunk and off the ground: out of reach of anything that cannot climb. */
    public boolean isUpATree() {
        return isClimbingTree() && !onGround();
    }

    @Override
    public boolean onClimbable() {
        return (isClimbingTree() && horizontalCollision) || wallClimbing || super.onClimbable();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return safeLandingTicks <= 0 && !isClimbingTree() && super.causeFallDamage(fallDistance, multiplier, source);
    }

    public void callToDisplay(int delay) {
        displayDelay = delay;
        raiseAlarm(200);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == DISPLAY_EVENT) {
            clientDisplayStart = tickCount;
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------ ticking

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount == 1) {
            ensureName();
            applyFightSkill();
            // Glowing is saved with the entity; an excursion from before a reload may not have been.
            if (excursionTicks <= 0 && hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)
                    && !(isBaby() && caretaker != null)) {
                removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
            }
        }
        if (!isBaby() && ++hungerClock >= HUNGER_TICKS) {
            hungerClock = 0;
            hunger = Math.max(0, hunger - 1);
        }
        if (eatingTicks > 0) {
            tickEating();
        } else if (eatCooldown > 0) {
            eatCooldown--;
        } else if (hunger < EATS_BELOW) {
            startEating();
        }
        if (tickCount % HEAL_TICKS == 0) {
            if (hunger >= HUNGRY && getHealth() < getMaxHealth()) {
                heal(1.0F);
            } else if (hunger == 0 && getHealth() > 2.0F) {
                hurt(damageSources().starve(), 1.0F);
            }
        }
        if (tickCount % 20 == 0) {
            complainIfHungry();
        }
        fleeTicks = Math.max(0, fleeTicks - 1);
        if (fleeTicks == 0) {
            panicking = false;
        }
        alarmTicks = Math.max(0, alarmTicks - 1);
        tickFreeze();
        tickWallClimb();
        if (tickCount % 20 == 7) {
            lookForPredators();
        }
        if (tickCount % 10 == 3) {
            updateHands();
        }
        armUrgencyTicks = Math.max(0, armUrgencyTicks - 1);
        defendTicks = Math.max(0, defendTicks - 1);
        readyTicks = Math.max(0, readyTicks - 1);
        wrestleCooldown = Math.max(0, wrestleCooldown - 1);
        huntTicks = Math.max(0, huntTicks - 1);
        if (fetchTicks > 0 && --fetchTicks == 0) {
            clearFetch();
        }
        climbOrderTicks = Math.max(0, climbOrderTicks - 1);
        if (excursionTicks > 0 && --excursionTicks == 0) {
            endExcursion();
        }
        if (wrestleTicks > 0 && --wrestleTicks == 0) {
            wrestlePartner = null;
        }
        if (leaveTicks > 0 && --leaveTicks == 0) {
            leavePos = null;
        }
        if (tickCount % 20 == 0) {
            deliverFood();
        }
        if (tickCount % 100 == 0 && guestOf != null && isAlpha() && level().isNight()) {
            Band.sendGuestsHome(this);
        }
        if (forageTogetherTicks > 0 && --forageTogetherTicks == 0) {
            forageAnchor = null;
        }
        if (safeLandingTicks > 0) {
            safeLandingTicks--;
            resetFallDistance();
        }
        if (isClimbingTree()) {
            resetFallDistance();
        }
        if (displayDelay > 0) {
            displayDelay--;
        } else if (displayDelay == 0) {
            displayDelay = -1;
            Band.performDisplay(this);
        }
        if (pregnancyTicks > 0 && --pregnancyTicks == 0) {
            Band.giveBirth(this);
        }
        if (growUpTicks > 0 && --growUpTicks == 0) {
            entityData.set(BABY, false);
        }
    }

    private void deliverFood() {
        if (deliverFoodTo == null) {
            return;
        }
        Player player = level().getPlayerByUUID(deliverFoodTo);
        if (player == null || --deliverTicks <= 0) {
            deliverFoodTo = null;
            return;
        }
        deliverTicks -= 19;
        if (!hasFood()) {
            return;
        }
        if (distanceToSqr(player) > 9.0D) {
            getNavigation().moveTo(player, 1.2D);
            return;
        }
        giveFoodTo(player);
        deliverFoodTo = null;
    }

    private void complainIfHungry() {
        if (hunger >= COMPLAINS_BELOW || isWild() || level().getGameTime() < nextTalk) {
            return;
        }
        Player player = leaderPlayer();
        if (player == null || distanceToSqr(player) > 32.0D * 32.0D) {
            return;
        }
        nextTalk = level().getGameTime() + TALK_COOLDOWN;
        ensureName();
        player.sendSystemMessage(Component.literal("<" + getName().getString() + "> ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("I'm feeling hungry... maybe we should forage together.")
                        .withStyle(ChatFormatting.WHITE)));
    }

    // ------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeInventoryToTag(tag, registryAccess());
        tag.putInt("Hunger", hunger);
        tag.putString("Stage", entityData.get(STAGE));
        tag.putBoolean("Female", female);
        tag.putBoolean("Baby", isBaby());
        tag.putInt("GrowUp", growUpTicks);
        tag.putInt("Pregnancy", pregnancyTicks);
        tag.putInt("FightSkill", fightSkill);
        tag.putBoolean("HuntWithLeader", huntWithLeader);
        tag.putInt("Bond", bond);
        tag.putBoolean("MadeChopper", madeChopper);
        tag.putInt("Party", party);
        if (partyHead != null) {
            tag.putUUID("PartyHead", partyHead);
        }
        tag.putBoolean("SharpenUrge", sharpenUrge);
        if (excursionTicks > 0 && excursionTarget != null) {
            tag.putInt("ExcursionTicks", excursionTicks);
            tag.putLong("ExcursionTarget", excursionTarget.asLong());
        }
        net.minecraft.nbt.ListTag favourites = new net.minecraft.nbt.ListTag();
        for (ResourceLocation food : favouriteFoods) {
            favourites.add(net.minecraft.nbt.StringTag.valueOf(food.toString()));
        }
        tag.put("FavouriteFoods", favourites);
        if (leader != null) {
            tag.putUUID("Leader", leader);
        }
        if (bandId != null) {
            tag.putUUID("Band", bandId);
        }
        if (alpha != null) {
            tag.putUUID("Alpha", alpha);
        }
        if (guestOf != null) {
            tag.putUUID("GuestOf", guestOf);
        }
        if (ward != null) {
            tag.putUUID("Ward", ward);
        }
        if (caretaker != null) {
            tag.putUUID("Caretaker", caretaker);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readInventoryFromTag(tag, registryAccess());
        if (tag.contains("Hunger")) {
            hunger = tag.getInt("Hunger");
        }
        if (tag.contains("Stage")) {
            entityData.set(STAGE, tag.getString("Stage"));
        }
        if (tag.contains("Female")) {
            female = tag.getBoolean("Female");
        }
        entityData.set(BABY, tag.getBoolean("Baby"));
        growUpTicks = tag.getInt("GrowUp");
        pregnancyTicks = tag.getInt("Pregnancy");
        fightSkill = tag.getInt("FightSkill");
        huntWithLeader = !tag.contains("HuntWithLeader") || tag.getBoolean("HuntWithLeader");
        bond = tag.getInt("Bond");
        madeChopper = tag.getBoolean("MadeChopper");
        party = tag.getInt("Party");
        partyHead = tag.hasUUID("PartyHead") ? tag.getUUID("PartyHead") : null;
        sharpenUrge = tag.getBoolean("SharpenUrge");
        excursionTicks = tag.getInt("ExcursionTicks");
        excursionTarget = tag.contains("ExcursionTarget") ? BlockPos.of(tag.getLong("ExcursionTarget")) : null;
        favouriteFoods.clear();
        for (net.minecraft.nbt.Tag food : tag.getList("FavouriteFoods", net.minecraft.nbt.Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(food.getAsString());
            if (id != null) {
                favouriteFoods.add(id);
            }
        }
        leader = tag.hasUUID("Leader") ? tag.getUUID("Leader") : null;
        bandId = tag.hasUUID("Band") ? tag.getUUID("Band") : null;
        alpha = tag.hasUUID("Alpha") ? tag.getUUID("Alpha") : null;
        guestOf = tag.hasUUID("GuestOf") ? tag.getUUID("GuestOf") : null;
        ward = tag.hasUUID("Ward") ? tag.getUUID("Ward") : null;
        caretaker = tag.hasUUID("Caretaker") ? tag.getUUID("Caretaker") : null;
        entityData.set(WILD, isWild());
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide() && isBaby()) {
            Band.onChildDied(this);
        }
        if (!level().isClientSide() && leader != null) {
            Band.onMemberDied(this);
        }
    }

    /**
     * Wild bands come and go with nobody near them - but not while they are
     * travelling with someone, and never just at random the way ordinary mobs do.
     */
    @Override
    public void checkDespawn() {
        if (!isWild() || guestOf != null) {
            setNoActionTime(0);
            return;
        }
        Player nearest = level().getNearestPlayer(this, -1.0D);
        if (nearest == null || nearest.distanceToSqr(this) > WILD_DESPAWN_DISTANCE * WILD_DESPAWN_DISTANCE) {
            discard();
        }
    }

    private static final double WILD_DESPAWN_DISTANCE = 220.0D;

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (ItemStack stack : inventory.removeAllItems()) {
            spawnAtLocation(stack);
        }
    }

    /** Everything this member carries, for whoever takes its place. Empties it. */
    public List<ItemStack> takeEverything() {
        List<ItemStack> all = new java.util.ArrayList<>(inventory.removeAllItems());
        ItemStack held = getMainHandItem();
        if (!held.isEmpty()) {
            all.add(held);
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        if (!getOffhandItem().isEmpty()) {
            all.add(getOffhandItem());
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        return all;
    }

    /** The player's band is kept forever. Wild bands come and go with nobody to see them. */
    @Override
    public boolean requiresCustomPersistence() {
        return !isWild() || super.requiresCustomPersistence();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return isWild();
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof BandMember member) {
            return (leader != null && leader.equals(member.leader)) || (bandId != null && bandId.equals(member.bandId));
        }
        if (other instanceof Player player && isCompanionOf(player)) {
            return true;
        }
        return super.isAlliedTo(other);
    }
}
