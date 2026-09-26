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
    /** Synced: sitting with its head in its hands, waiting for somebody to come back. */
    private static final EntityDataAccessor<Boolean> GRIEVING =
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
    private static final int GROW_UP_TICKS = 24000;
    /** One birth in six is two. */
    private static final float TWINS = 0.14F;
    /** And now and then, three. */
    private static final float TRIPLETS = 0.03F;
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
            ModItems.WOODEN_CLUB, ModItems.BONE_CLUB, ModItems.SHARPENED_SPEAR, ModItems.FIRE_HARDENED_SPEAR, ModItems.SCHONINGEN_SPEAR,
            ModItems.STONE_TIPPED_SPEAR);

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
    /** Sitting still while somebody picks through their hair. */
    private int groomTicks;
    /** How long they have been up a tree with nothing left to be afraid of. */
    private int calmInTreeTicks;
    /** Something hunted this member. Once it is safe, a stick gets a point on it. */
    private boolean sharpenUrge;
    private boolean predatorNearby;

    // Adrenaline: fight, flight, or freeze.
    private static final int ADRENALINE_COOLDOWN = 5 * 60 * 20;
    private static final int ADRENALINE_TICKS = 20 * 20;
    /** How long the first sprint of a flight lasts before it settles into a run. */
    private static final int PANIC_BURST_TICKS = 2 * 20;
    /** And how long that run lasts. Beyond this they are simply running, at their own speed. */
    private static final int PANIC_RUN_TICKS = 3 * 20;
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

    // Tastes and wants (habilis on). Stone preference: 0 none, 1 chert, 2 quartzite.
    private boolean personalityRolled;
    private int stonePreference;
    private boolean obsidianObsession;
    @Nullable
    private Item want;
    private long wantUntil;
    @Nullable
    private Item tradeOffer;
    private long nextWant;
    private long nextThought;
    private long nextGift;

    /**
     * What anybody out here has always eaten: what can be found, dug or picked. A cooked meal, a steak, is not
     * something to long for until it has been eaten - that favourite comes from eating it (maybeAcquireTaste).
     */
    private static final String[] FOOD_CHOICES = {
            "hominin_evolution:grub", "hominin_evolution:beetle", "hominin_evolution:earthworm",
            "hominin_evolution:termite_stick", "hominin_evolution:meat_chunk", "hominin_evolution:roots",
            "minecraft:sweet_berries", "minecraft:apple", "minecraft:melon_slice", "minecraft:glow_berries"};

    /** Whether this is one of the things anybody has always eaten, rather than something that has to be tasted. */
    private static boolean wildFood(ResourceLocation id) {
        for (String food : FOOD_CHOICES) {
            if (food.equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    /** Everything this one has actually eaten. */
    private final java.util.Set<ResourceLocation> tasted = new java.util.HashSet<>();

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
        // Water is a road, not a wall: they wade and swim it instead of walking around.
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 0.0F);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, 0.0F);
        female = random.nextBoolean();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                // Hominins swim badly, but not as badly as a mob with no water efficiency at all.
                .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 0.7D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STAGE, HomininEvolutionMod.MODID + ":australopithecus");
        builder.define(BABY, false);
        builder.define(CLIMBING, false);
        builder.define(WILD, false);
        builder.define(WALL_CLIMBING, false);
        builder.define(GRIEVING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new dev.hominin.evolution.band.goal.FreezeGoal(this));
        goalSelector.addGoal(0, new dev.hominin.evolution.band.goal.GrieveGoal(this));
        goalSelector.addGoal(1, new dev.hominin.evolution.band.goal.MissionGoal(this));
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(this,
                dev.hominin.evolution.entity.Dinopithecus.class, 10.0F, 1.1D, 1.4D,
                giant -> getTarget() != giant && !isHunting()));
        goalSelector.addGoal(1, new FleeToTreeGoal(this));
        goalSelector.addGoal(2, new ArmedMeleeGoal(this, 1.25D));
        goalSelector.addGoal(2, new dev.hominin.evolution.band.goal.GuideGoal(this));
        goalSelector.addGoal(1, new dev.hominin.evolution.band.goal.LabourGoal(this));
        goalSelector.addGoal(2, new dev.hominin.evolution.band.goal.WrestleGoal(this));
        goalSelector.addGoal(3, new dev.hominin.evolution.band.goal.FetchGoal(this));
        goalSelector.addGoal(3, new ArmSelfGoal(this));
        goalSelector.addGoal(4, new GatherItemsGoal(this));
        goalSelector.addGoal(4, new dev.hominin.evolution.band.goal.SharpenStickGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.TermiteFishGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.QuarryGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.ScavengeGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.GroomGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.BatheGoal(this));
        goalSelector.addGoal(5, new ForageGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.NestBuildGoal(this));
        // Just above building one: once the nest exists, getting into it is the priority.
        goalSelector.addGoal(4, new dev.hominin.evolution.band.goal.SleepInNestGoal(this));
        goalSelector.addGoal(4, new dev.hominin.evolution.band.goal.SentryGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.ToolPileGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.StockGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.StoreGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.PlayGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.TinkerGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.CraftGoal(this));
        // Erectus on: what the player does at erectus - beds, hides, twine, build materials, and the builds.
        goalSelector.addGoal(3, new dev.hominin.evolution.band.goal.HideHuntGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.ErectusCraftGoal(this));
        // The Feast: getting ready for it, and coming to the fire when it begins. And the fire itself, kept fed.
        goalSelector.addGoal(2, new dev.hominin.evolution.band.goal.FeastGoal(this));
        goalSelector.addGoal(3, new dev.hominin.evolution.band.goal.PractiseGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.FireTendGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.BuildHelpGoal(this));
        goalSelector.addGoal(5, new dev.hominin.evolution.band.goal.GatherThatchGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.ExcursionGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.RoamGoal(this));
        goalSelector.addGoal(6, new dev.hominin.evolution.band.goal.GravelGoal(this));
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

    /** Led by this player's band-mate: they lead this member's band together. */
    public boolean isCoLedBy(Player player) {
        return leader != null && !player.getUUID().equals(leader) && leader.equals(Newcomers.hostOf(player));
    }

    /** Takes orders and gifts from this player: its leader, or one of its co-leaders. */
    public boolean answersTo(Player player) {
        return isLedBy(player) || isCoLedBy(player);
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

    /** Asleep, nothing moves them - not following, not wandering, not anything. */
    @Override
    public void travel(net.minecraft.world.phys.Vec3 input) {
        if (isSleeping()) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
            super.travel(net.minecraft.world.phys.Vec3.ZERO);
            return;
        }
        super.travel(input);
    }

    /** Leaves its own band for a player's. */
    public void joinPlayerBand(UUID leader) {
        this.bandId = null;
        this.alpha = null;
        this.leader = leader;
        entityData.set(WILD, false);
        setPersistenceRequired();
        if (level().getServer() != null
                && level().getServer().getPlayerList().getPlayer(leader) instanceof net.minecraft.server.level.ServerPlayer player) {
            ensureName();
            Chatter.news(player, "news_join", getName().getString());
        }
    }

    /** Into a real fight: whatever it gave up on before, it does not give up on this. */
    public void fightFor(LivingEntity target) {
        gaveUpOn = null;
        defendAgainst(target);
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

    // ------------------------------------------------------------ mates and friends

    /** A mate: another member's UUID, or a player's. From habilis on, one and kept. */
    @Nullable
    private UUID mate;
    /** How far the player has got courting this one. */
    private int courtship;
    /** Who in the band this one is close to, built up by grooming each other. */
    private final java.util.Map<UUID, Integer> affinity = new java.util.HashMap<>();
    /** Where a mother goes to give birth, away from everyone. */
    @Nullable
    private BlockPos labourSpot;

    @Nullable
    public UUID getMate() {
        return mate;
    }

    public void setMate(@Nullable UUID mate) {
        this.mate = mate;
        courtship = 0;
    }

    public boolean isMateOf(UUID other) {
        return other.equals(mate);
    }

    /** Returns the courtship so far. */
    public int addCourtship(int amount) {
        courtship += amount;
        return courtship;
    }

    public void addAffinity(BandMember other, int amount) {
        affinity.merge(other.getUUID(), amount, Integer::sum);
    }

    /** The one they are closest to, and how close, or null. */
    @Nullable
    public java.util.Map.Entry<UUID, Integer> closestFriend() {
        java.util.Map.Entry<UUID, Integer> best = null;
        for (java.util.Map.Entry<UUID, Integer> entry : affinity.entrySet()) {
            if (best == null || entry.getValue() > best.getValue()) {
                best = entry;
            }
        }
        return best;
    }

    /** A night slept through: whoever was carrying has had the child by morning. */
    public void deliverNow() {
        if (pregnancyTicks > 0) {
            pregnancyTicks = 0;
            labourSpot = null;
            birth();
        }
    }

    /** The child - and one time in seven a second with it, and now and then a third. */
    private void birth() {
        Band.giveBirth(this);
        float roll = random.nextFloat();
        int more = roll < TRIPLETS ? 2 : roll < TRIPLETS + TWINS ? 1 : 0;
        int born = 1;
        for (int i = 0; i < more && (leaderPlayer() == null || Band.hasRoomFor(leaderPlayer())); i++) {
            Band.giveBirth(this);
            born++;
        }
        if (born > 1 && leaderPlayer() instanceof net.minecraft.server.level.ServerPlayer leader) {
            ensureName();
            leader.sendSystemMessage(Component.literal((born == 3 ? "Triplets! " : "Twins! ") + getName().getString()
                    + " has had " + (born == 3 ? "three." : "two.")).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Come to the band already carrying: this far along, as a fraction of the whole left to go. */
    public void arriveCarrying(float left) {
        if (female && !isBaby()) {
            readyTicks = 0;
            pregnancyTicks = Math.max(1200, (int) (PREGNANCY_TICKS * left));
        }
    }

    public void setSex(boolean female) {
        this.female = female;
    }

    // ------------------------------------------------------------ a grudge

    /**
     * A band this one holds something against - the one that destroyed theirs. Every day they spend with yours, your
     * band hears about it, and likes that band a little less.
     */
    @Nullable
    private UUID grudge;

    @Nullable
    public UUID getGrudge() {
        return grudge;
    }

    public void setGrudge(@Nullable UUID band) {
        this.grudge = band;
    }

    // ------------------------------------------------------------ carried

    /**
     * Before erectus a child is carried: it clings to its mother's fur and rides on her back, where it is easy to keep
     * an eye on - until it is grown, or she is gone.
     */
    private void cling() {
        if (tickCount % 20 != 7 || !(level() instanceof ServerLevel server)) {
            return;
        }
        boolean carried = isBaby() && mother != null && !Bands.erectusOn(getStage());
        BandMember mom = carried && server.getEntity(mother) instanceof BandMember m && m.isAlive() && !m.isBaby() ? m : null;
        if (mom == null) {
            if (getVehicle() instanceof BandMember) {
                stopRiding();
            }
            return;
        }
        if (getVehicle() == mom) {
            return;
        }
        if (!isPassenger() && mom.getPassengers().isEmpty() && distanceToSqr(mom) < 16.0D && !mom.isInWater()) {
            startRiding(mom, true);
        }
    }

    /** A child on its mother's back: high on it, a little behind her shoulders. */
    @Override
    protected void positionRider(net.minecraft.world.entity.Entity passenger, MoveFunction move) {
        if (passenger instanceof BandMember child && child.isBaby()) {
            float yaw = yBodyRot * ((float) Math.PI / 180.0F);
            move.accept(passenger, getX() + net.minecraft.util.Mth.sin(yaw) * 0.28D, getY() + getBbHeight() * 0.52D,
                    getZ() - net.minecraft.util.Mth.cos(yaw) * 0.28D);
            return;
        }
        super.positionRider(passenger, move);
    }

    /** Nobody steers anybody: a child on a back is carried, not driving. */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    public void startPregnancy() {
        readyTicks = 0;
        if (female && pregnancyTicks <= 0) {
            pregnancyTicks = PREGNANCY_TICKS;
        }
    }

    /** The last stretch: gone off alone, and glowing. Only in a player's band, where somebody can guard her. */
    public boolean isInLabour() {
        return pregnancyTicks > 0 && pregnancyTicks <= Mating.LABOUR_TICKS && leader != null;
    }

    @Nullable
    public BlockPos getLabourSpot() {
        return labourSpot;
    }

    private void tickPregnancy() {
        if (pregnancyTicks <= 0 || !(level() instanceof ServerLevel server)) {
            return;
        }
        if (pregnancyTicks == Mating.LABOUR_TICKS && leader != null) {
            // Somewhere quiet, a good way off from everyone.
            labourSpot = Band.standingSpotNear(server, blockPosition(), 18, random.nextFloat() * net.minecraft.util.Mth.TWO_PI);
            Mating.memberLabourBegan(this);
        }
        if (isInLabour() && tickCount % 20 == 0) {
            addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, 40, 0, false, false));
            if (leaderPlayer() instanceof net.minecraft.server.level.ServerPlayer guard) {
                Mating.labour(guard, blockPosition(), false);
            }
        }
    }

    // ------------------------------------------------------------ knapping skill

    /**
     * 4 (beginner) to 1 (master); 0 until first needed. Rolled like the player's own: mostly 4 or 3,
     * sometimes 2, rarely 1 - so the good knappers are the ones worth keeping.
     */
    private int knapLevel;
    private int knapPractice;
    /** Persistence hunting, 3 to 0; -1 until first needed. Mostly 3, sometimes 2, rarely 1, very rarely 0. */
    private int huntLevel = -1;
    /** Negotiating, 4 to 0; -1 until first needed. A psychopath is always 0. */
    private int negotiateLevel = -1;

    public int getKnapLevel() {
        if (knapLevel == 0) {
            knapLevel = dev.hominin.evolution.hunt.Persistence.rollKnapping(random);
        }
        return knapLevel;
    }

    public int getHuntLevel() {
        if (huntLevel < 0) {
            huntLevel = dev.hominin.evolution.hunt.Persistence.rollHunting(random);
        }
        return huntLevel;
    }

    /** 4 (tongue-tied) to 0 (flawless). Whatever else they are, a psychopath talks flawlessly. */
    public int getNegotiateLevel() {
        if (isPsychopath()) {
            return 0;
        }
        if (negotiateLevel < 0) {
            negotiateLevel = Negotiation.rollMember(random);
        }
        return negotiateLevel;
    }

    public int getFightSkill() {
        return fightSkill;
    }

    /** How much this one matters to the band's hands: lower is rarer. For picking out the valuable. */
    public int talent() {
        return Math.min(getKnapLevel(), getHuntLevel() + 1);
    }

    /** Anything they could be picked out for: a skilled or master knapper, a good tracker, a persuasive talker. */
    public boolean isGifted() {
        return getKnapLevel() <= 2 || getHuntLevel() <= 2 || getNegotiateLevel() <= 1;
    }

    // ------------------------------------------------------------ a commission

    /** A tool you asked them to make: what, for whom, what it costs and whether it is paid. See Commissions. */
    private CompoundTag commission = new CompoundTag();

    public CompoundTag getCommission() {
        return commission;
    }

    public void setCommission(CompoundTag tag) {
        commission = tag;
    }

    // ------------------------------------------------------------ pairing up

    // ------------------------------------------------------------ grief, and worse

    /** 0 all right; 1 troubled - grieving someone close; 2 gone sour after nobody looked after them. See Troubles. */
    private int trouble;
    private String grievingFor = "";
    private String griefKind = "";
    /** Charming, attentive, and all of it for themselves. See Psychopaths. Nobody knows - there are only signs. */
    private boolean psychopath;
    private boolean psychopathRolled;
    private boolean psychopathKnown;
    /** Whoever gave birth to this one, if it was one of the band. */
    @Nullable
    private UUID mother;

    public int getTrouble() {
        return trouble;
    }

    public void setTrouble(int trouble, String grievingFor, String griefKind) {
        this.trouble = trouble;
        this.grievingFor = grievingFor == null ? "" : grievingFor;
        this.griefKind = griefKind == null ? "" : griefKind;
    }

    public String getGrievingFor() {
        return grievingFor;
    }

    public String getGriefKind() {
        return griefKind;
    }

    public boolean isPsychopath() {
        return psychopath;
    }

    public boolean psychopathRolled() {
        return psychopathRolled;
    }

    public void setPsychopath(boolean psychopath) {
        this.psychopath = psychopath;
        this.psychopathRolled = true;
    }

    public boolean isPsychopathKnown() {
        return psychopathKnown;
    }

    public void setPsychopathKnown(boolean known) {
        this.psychopathKnown = known;
    }

    @Nullable
    public UUID getMother() {
        return mother;
    }

    public void setMother(@Nullable UUID mother) {
        this.mother = mother;
    }

    /** How close this one is to another of the band, from grooming each other. */
    public int affinityWith(UUID other) {
        return affinity.getOrDefault(other, 0);
    }

    // ------------------------------------------------------------ temper

    /** Does not care what the band thinks: steals, hoards, begs, will not teach, picks fights. See Mood. */
    private boolean antisocial;
    private boolean temperRolled;

    public boolean isAntisocial() {
        return antisocial;
    }

    public boolean temperRolled() {
        return temperRolled;
    }

    public void setTemper(boolean antisocial) {
        this.antisocial = antisocial;
        this.temperRolled = true;
    }

    /**
     * Becoming the body the leader has just left: their name, their sex, how hurt and how hungry they were,
     * and what they had in mind. The leader has gone into this one's body; this one is them now.
     */
    public void takeOverBody(String name, boolean female, float healthFraction, int hunger,
            java.util.List<dev.hominin.evolution.mind.MindData.Memory> memories, int slots) {
        setCustomName(name.isEmpty() ? null : Component.literal(name));
        ensureName();
        this.female = female;
        setHealth(Math.max(1.0F, getMaxHealth() * healthFraction));
        this.hunger = Math.max(0, Math.min(MAX_HUNGER, hunger));
        this.memories.clear();
        this.memories.addAll(memories);
        this.memorySlots = slots;
    }

    /** How many places this mind holds - rolled the first time anyone asks. */
    public int memorySlots() {
        if (memorySlots == 0) {
            String era = getStage().getPath();
            boolean later = !era.startsWith("australopithecus") && !era.equals("ardipithecus")
                    && !era.equals("homo_habilis") && !era.equals("homo_rudolfensis");
            memorySlots = later ? 4 + random.nextInt(4) : 2 + random.nextInt(3);
        }
        return memorySlots;
    }

    // ------------------------------------------------------------ night: turned in, or on watch

    /** Game time until which this member is keeping watch - awake, walking the camp. */
    private long watchUntil;
    /** The day this member turned in for the night: nest made, no more wandering. */
    private long turnedInDay = -1L;

    public void setWatch(long until) {
        this.watchUntil = until;
    }

    public boolean isOnWatch() {
        return watchUntil > level().getGameTime();
    }

    /** Evening, and this one has said goodnight: they go to their nest and stay by it. */
    public boolean isTurnedIn() {
        long time = level().getDayTime() % 24000L;
        return turnedInDay == level().getDayTime() / 24000L && time >= 11500L && time < 23200L && !isOnWatch();
    }

    public void turnIn() {
        turnedInDay = level().getDayTime() / 24000L;
    }

    /** Back to not yet decided: rolled again if the band ever reaches erectus. */
    public void clearTemper() {
        this.antisocial = false;
        this.temperRolled = false;
    }

    /** Game time from which this member begins to look for a mate; -1 until first decided. */
    private long pairReadyAt = -1L;

    public long getPairReadyAt() {
        return pairReadyAt;
    }

    public void setPairReadyAt(long time) {
        pairReadyAt = time;
    }

    /** Making one more tool: the same steps as the player - 1, then 2, then 3. */
    public void practiseKnapping() {
        int level = getKnapLevel();
        if (level <= 1) {
            return;
        }
        int needed = level == 4 ? 1 : level == 3 ? 2 : 3;
        if (++knapPractice >= needed) {
            knapPractice = 0;
            knapLevel = level - 1;
        }
    }

    // ------------------------------------------------------------ kuru

    /** Game time this one caught kuru at a funeral feast, or -1. It dies of it in two and a half days. */
    private long kuruSince = -1L;
    private static final long KURU_TICKS = 60000L;

    public void contractKuru() {
        if (kuruSince < 0L) {
            kuruSince = level().getGameTime();
        }
    }

    public boolean hasKuru() {
        return kuruSince >= 0L;
    }

    /** A kinetic hunt: until this game time, they throw first and close after. */
    private long throwFocusUntil;

    public void focusOnThrowing(long ticks) {
        throwFocusUntil = level().getGameTime() + ticks;
    }

    public boolean isThrowFocused() {
        return level().getGameTime() < throwFocusUntil;
    }

    // ------------------------------------------------------------ brains

    /**
     * Before erectus nobody gives a brain a second look. From erectus on a brain is a great deal of food in one piece,
     * and now and then somebody hungry goes for one lying about - and gambles on kuru. Not in a band that has learned
     * not to.
     */
    private void tickBrains() {
        if ((tickCount + getId()) % 200 != 0 || isBaby() || !Bands.erectusOn(getStage()) || hunger >= MAX_HUNGER - 2
                || !(level() instanceof ServerLevel server)) {
            return;
        }
        if (leaderPlayer() instanceof Player lead && Morals.holds(lead, Morals.Moral.NO_BRAINS)) {
            return;
        }
        for (net.minecraft.world.entity.item.ItemEntity lying : server.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, getBoundingBox().inflate(10.0D),
                e -> e.isAlive() && e.getItem().is(ModItems.HOMININ_BRAIN.get()))) {
            if (distanceToSqr(lying) <= 2.5D * 2.5D) {
                ItemStack brain = lying.getItem().split(1);
                if (lying.getItem().isEmpty()) {
                    lying.discard();
                }
                eatBrain(brain);
            } else if (random.nextFloat() < 0.35F) {
                getNavigation().moveTo(lying, 1.0D);
            }
            return;
        }
    }

    /** A brain, eaten: a great deal of food - and one time in three, kuru. */
    private void eatBrain(ItemStack brain) {
        hunger = MAX_HUNGER;
        heal(4.0F);
        playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT, 0.8F, 0.9F);
        if (random.nextFloat() < dev.hominin.evolution.survival.Kuru.CHANCE) {
            contractKuru();
        }
        if (leaderPlayer() instanceof Player lead && distanceToSqr(lead) < 32.0D * 32.0D) {
            ensureName();
            lead.displayClientMessage(Component.literal(getName().getString() + " eats the brain, every bit of it.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void tickMating() {
        if ((tickCount + getId()) % 1200 == 0) {
            Mating.tickMember(this);
        }
        tickPregnancy();
    }

    private void tickKuru() {
        if (kuruSince < 0L || tickCount % 40 != 0 || !(level() instanceof ServerLevel server)) {
            return;
        }
        long sick = level().getGameTime() - kuruSince;
        if (random.nextInt(4) == 0) {
            // The trembling, where anyone can see it.
            server.sendParticles(ParticleTypes.SMOKE, getX(), getEyeY(), getZ(), 3, 0.2D, 0.2D, 0.2D, 0.0D);
            setYRot(getYRot() + (random.nextFloat() - 0.5F) * 30.0F);
        }
        if (sick > KURU_TICKS / 2 && random.nextInt(6) == 0 && leaderPlayer() instanceof Player leader
                && distanceToSqr(leader) < 24.0D * 24.0D) {
            ensureName();
            leader.displayClientMessage(Component.literal(getName().getString() + " cannot stop shaking.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        if (sick > KURU_TICKS) {
            Player lead = leaderPlayer();
            hurt(level().damageSources().source(dev.hominin.evolution.survival.Kuru.DAMAGE), Float.MAX_VALUE);
            if (!isAlive() && lead instanceof net.minecraft.server.level.ServerPlayer leader) {
                Morals.kuruDeath(leader, this);
            }
        }
    }

    // ------------------------------------------------------------ guiding

    /** Where this one is walking somebody to, and who. Not saved: a walk ends with the session. */
    @Nullable
    private BlockPos guideTarget;
    @Nullable
    private UUID guiding;

    public void startGuiding(Player player, BlockPos target) {
        guiding = player.getUUID();
        guideTarget = target;
    }

    public void stopGuiding() {
        guiding = null;
        guideTarget = null;
    }

    @Nullable
    public BlockPos getGuideTarget() {
        return guideTarget;
    }

    @Nullable
    public Player guidedPlayer() {
        return guiding == null ? null : level().getPlayerByUUID(guiding);
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
        // A player walking with this member's leader's band is one of them.
        return isLedBy(player) || isGuestOf(player) || leader != null && leader.equals(Newcomers.hostOf(player));
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
        // A band at perfect cohesion treats its leader as a friend at the very least.
        return leader != null && bond < 2 && Cohesion.perfect(leaderPlayer()) ? 2 : bond;
    }

    public void addBond(int amount) {
        bond += amount;
        if (amount > 0 && psychopath) {
            // They are very good at being liked.
            bond += 1;
        }
        if (amount > 0) {
            // A band that trusts its leader warms to them faster.
            bond += Cohesion.bondBonus(leaderPlayer(), random);
        }
    }

    /** Eats something handed over for a need: it goes further than an ordinary meal. */
    /** Roots grumbled over, and counted against their teeth - and everything eaten remembered as tasted. */
    private void noteEaten(ItemStack food) {
        tasted.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(food.getItem()));
        if (dev.hominin.evolution.survival.Diseases.isRoots(food) && random.nextInt(3) == 0) {
            Lines.say(this, "roots_again");
        }
        dev.hominin.evolution.survival.Diseases.ate(this, food);
    }

    public void feed(ItemStack stack) {
        if (stack.is(ModItems.HOMININ_BRAIN.get())) {
            eatBrain(stack);
            return;
        }
        noteEaten(stack);
        net.minecraft.world.food.FoodProperties food = stack.get(DataComponents.FOOD);
        hunger = Math.min(MAX_HUNGER, hunger + (food != null ? Math.max(4, food.nutrition()) : 6));
        heal(2.0F);
    }

    // ------------------------------------------------------------ laid up

    /** Game time until which this member is injured and laid up; 0 when whole. */
    private long injuredUntil;
    /** Whether they have already worked something out while laid up this time. */
    private boolean thoughtWhileDown;
    private static final long INJURY_TICKS = 24000L;
    private static final ResourceLocation INJURED_ID =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "injured");

    public boolean isInjured() {
        return injuredUntil > 0L && level().getGameTime() < injuredUntil;
    }

    /** Hurt badly enough to be laid up for a day: slower, kept close, protected - and not idle. */
    public void injure() {
        if (isBaby()) {
            return;
        }
        boolean fresh = !isInjured();
        injuredUntil = level().getGameTime() + INJURY_TICKS;
        thoughtWhileDown = false;
        if (fresh && leaderPlayer() instanceof net.minecraft.server.level.ServerPlayer lead) {
            ensureName();
            lead.sendSystemMessage(Component.literal(getName().getString() + " is badly hurt - laid up for a day. "
                    + "They will stay close and slow, and the band will guard them.").withStyle(ChatFormatting.RED));
        }
    }

    /** Once a second: noticing a bad wound, the slow legs of one, and what they do while it heals. */
    private void tickInjury() {
        if (!isInjured() && !isBaby() && getHealth() < getMaxHealth() * 0.5F && getLastHurtByMob() != null
                && tickCount - getLastHurtByMobTimestamp() < 40 && !(getLastHurtByMob() instanceof Player)) {
            injure();
        }
        var speed = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        boolean injured = isInjured();
        if (injured && dev.hominin.evolution.build.Building.inOwnRoom(this)) {
            // Lying up under a roof of their own: it mends twice as fast.
            injuredUntil -= 20L;
        }
        if (speed != null) {
            if (injured && !speed.hasModifier(INJURED_ID)) {
                speed.addTransientModifier(new AttributeModifier(INJURED_ID, -0.35D,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else if (!injured && speed.hasModifier(INJURED_ID)) {
                speed.removeModifier(INJURED_ID);
                if (injuredUntil > 0L && leaderPlayer() instanceof Player lead) {
                    ensureName();
                    lead.displayClientMessage(Component.literal(getName().getString() + " is back on their feet."), false);
                }
                injuredUntil = 0L;
            }
        }
        if (!injured || (tickCount + getId()) % 2400 != 0 || !(leaderPlayer() instanceof Player lead)) {
            return;
        }
        ensureName();
        // Laid up, but not useless: a good stone to work, or time to think.
        if (count(ModItems.CHERT_ROCK.get()) + count(ModItems.GRANITE_ROCK.get()) + count(ModItems.OBSIDIAN_ROCK.get())
                + count(ModItems.BASALT_ROCK.get()) > 0
                && random.nextFloat() < 0.6F) {
            practiseKnapping();
            if (random.nextInt(3) == 0) {
                lead.displayClientMessage(Component.literal(getName().getString()
                        + ", laid up, turns a stone over and over and knocks at it. Their hands are learning."), false);
            }
            return;
        }
        if (!thoughtWhileDown && random.nextFloat() < 0.25F) {
            java.util.List<dev.hominin.evolution.mind.Skills.Skill> unknown = new java.util.ArrayList<>();
            for (dev.hominin.evolution.mind.Skills.Skill skill : dev.hominin.evolution.mind.Skills.Skill.values()) {
                if (skill.carriesOver() && !knowsSkill(skill)) {
                    unknown.add(skill);
                }
            }
            if (!unknown.isEmpty()) {
                dev.hominin.evolution.mind.Skills.Skill found = unknown.get(random.nextInt(unknown.size()));
                learnSkill(found);
                thoughtWhileDown = true;
                lead.sendSystemMessage(Component.literal(getName().getString() + " has had nothing to do but lie still "
                        + "and think - and has worked something out: " + found.title().toLowerCase()
                        + ". They could teach it.").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Rolls this member's tastes, the first time they matter. */
    public void ensurePersonality() {
        if (personalityRolled) {
            return;
        }
        personalityRolled = true;
        float roll = random.nextFloat();
        stonePreference = roll < 0.3F ? 1 : roll < 0.52F ? 2 : roll < 0.64F ? 3 : 0;
        obsidianObsession = random.nextFloat() < 0.2F;
        long now = level().getGameTime();
        nextWant = now + 1200 + random.nextInt(3600);
        nextThought = now + 600 + random.nextInt(2400);
    }

    @Nullable
    public Item preferredStone() {
        ensurePersonality();
        return stonePreference == 1 ? ModItems.CHERT_ROCK.get() : stonePreference == 2 ? ModItems.GRANITE_ROCK.get()
                : stonePreference == 3 ? ModItems.BASALT_ROCK.get() : null;
    }

    public boolean isObsessedWithObsidian() {
        ensurePersonality();
        return obsidianObsession;
    }

    /**
     * An obsessive with obsidian in the pack, asked for something with an open hand.
     * They will not trade it and they will not be talked out of it - but once in a
     * while they want you to have it, and that is worth more than a trade precisely
     * because it never happens. Mostly they just want to tell you about it again.
     *
     * @return true if the interaction is finished here.
     */
    private boolean shareObsidian(Player player) {
        if (!isObsessedWithObsidian() || count(ModItems.OBSIDIAN_ROCK.get()) <= 0) {
            return false;
        }
        if (getRandom().nextFloat() >= OBSIDIAN_GIFT_CHANCE) {
            Lines.say(this, "obsidian_keep");
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.OBSIDIAN_ROCK.get())) {
                ItemStack gift = inventory.removeItem(slot, 1);
                if (!player.getInventory().add(gift)) {
                    player.drop(gift, false);
                }
                playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.2F);
                Lines.say(this, "obsidian_give");
                return true;
            }
        }
        return false;
    }

    /** How often an obsessive will actually part with a piece. Rarely, and it should stay rare. */
    private static final float OBSIDIAN_GIFT_CHANCE = 0.2F;

    /** An obsessive does not give up their obsidian - not to the player, not in trade. */
    public boolean refusesToPartWith(ItemStack stack) {
        return stack.is(ModItems.OBSIDIAN_ROCK.get()) && isObsessedWithObsidian();
    }

    @Nullable
    public Item getWant() {
        return want;
    }

    public long getWantUntil() {
        return wantUntil;
    }

    public void setWant(Item item, long until) {
        want = item;
        wantUntil = until;
        tradeOffer = null;
        wantVoiced = false;
    }

    public void clearWant() {
        want = null;
        tradeOffer = null;
        wantVoiced = false;
        wantUrgency = 0.0F;
    }

    /** How badly: 0 to about 1.5. Only the two most urgent in a band get said out loud. */
    private float wantUrgency;
    /** Whether this want has been asked of you, rather than only felt. */
    private boolean wantVoiced;

    public float getWantUrgency() {
        return wantUrgency;
    }

    public void setWantUrgency(float urgency) {
        wantUrgency = urgency;
    }

    public boolean isWantVoiced() {
        return want != null && wantVoiced;
    }

    public void setWantVoiced(boolean voiced) {
        wantVoiced = voiced;
    }

    @Nullable
    public Item getTradeOffer() {
        return tradeOffer;
    }

    public void setTradeOffer(@Nullable Item item) {
        tradeOffer = item;
    }

    public long getNextWant() {
        return nextWant;
    }

    public void setNextWant(long time) {
        nextWant = time;
    }

    public long getNextThought() {
        return nextThought;
    }

    public void setNextThought(long time) {
        nextThought = time;
    }

    public long getNextGift() {
        return nextGift;
    }

    public void setNextGift(long time) {
        nextGift = time;
    }

    public Item favouriteFood() {
        ensureFavourites();
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(favouriteFoods.get(0));
    }

    public List<String> favouriteFoodNames() {
        ensureFavourites();
        List<String> names = new java.util.ArrayList<>();
        for (ResourceLocation food : favouriteFoods) {
            names.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(food)).getHoverName().getString());
        }
        return names;
    }

    /** How many of this item it carries, hands included. */
    /** How many loose rocks of any stone this one carries. */
    public int countRocks() {
        return countCarried(s -> s.is(dev.hominin.evolution.ModTags.Items.ROCKS));
    }

    public int count(Item item) {
        return countCarried(s -> s.is(item));
    }

    /** How many of whatever this is it carries - main hand and pack. */
    public int countOf(java.util.function.Predicate<ItemStack> test) {
        return countCarried(test);
    }

    /** The carried stack itself (to wear it down, say): main hand first, then the pack. */
    @javax.annotation.Nullable
    public ItemStack findCarried(java.util.function.Predicate<ItemStack> test) {
        if (!getMainHandItem().isEmpty() && test.test(getMainHandItem())) {
            return getMainHandItem();
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && test.test(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** One of this item out of the pack or hands, or empty. */
    public ItemStack takeOneOf(Item item) {
        return takeFirst(s -> s.is(item));
    }

    public ItemStack takeFirst(java.util.function.Predicate<ItemStack> test) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && test.test(stack) && !refusesToPartWith(stack)) {
                return inventory.removeItem(slot, 1);
            }
        }
        for (EquipmentSlot hand : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack held = getItemBySlot(hand);
            if (!held.isEmpty() && test.test(held) && !refusesToPartWith(held)) {
                return held.split(1);
            }
        }
        return ItemStack.EMPTY;
    }

    public ItemStack takeFood() {
        return takeFirst(BandMember::edible);
    }

    /** Food, and not food that has turned: nobody of the band will touch that. A brain is its own thing (see brains). */
    public static boolean edible(ItemStack stack) {
        return stack.has(DataComponents.FOOD) && !dev.hominin.evolution.food.Spoilage.isSpoiled(stack)
                && !stack.is(ModItems.HOMININ_BRAIN.get());
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
        // Two syllables can spell out "Male", which then reads as a label, not a name.
        if (!hasCustomName() || isReservedName(getCustomName().getString())) {
            String name;
            do {
                name = SYLLABLES[random.nextInt(SYLLABLES.length)] + SYLLABLES[random.nextInt(SYLLABLES.length)];
                name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            } while (isReservedName(name));
            setCustomName(Component.literal(name));
        }
    }

    private static boolean isReservedName(String name) {
        return name.equalsIgnoreCase("male") || name.equalsIgnoreCase("female");
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

    /** Ticks until the birth; zero if not expecting. */
    public int getPregnancyTicks() {
        return Math.max(0, pregnancyTicks);
    }

    /** "due in about 6 minutes (a quarter of a day)". */
    public String dueIn() {
        int minutes = Math.max(1, (pregnancyTicks + 1199) / 1200);
        float days = pregnancyTicks / 24000.0F;
        String dayText = days >= 0.9F ? "about a day" : days >= 0.6F ? "most of a day" : days >= 0.4F ? "half a day"
                : days >= 0.2F ? "a quarter of a day" : "any time now";
        return "due in about " + minutes + (minutes == 1 ? " minute" : " minutes") + " (" + dayText + ")";
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

    public void setHunger(int hunger) {
        this.hunger = Math.max(0, Math.min(MAX_HUNGER, hunger));
    }

    /** Nobody's: no band of its own and no leader. Food held out wins it over. */
    public boolean isStray() {
        return leader == null && bandId == null;
    }

    public boolean hasFood() {
        if (edible(getOffhandItem())) {
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (edible(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean eatFromInventory() {
        ItemStack inHand = getOffhandItem();
        FoodProperties handFood = edible(inHand) ? inHand.get(DataComponents.FOOD) : null;
        if (handFood != null) {
            consume(handFood);
            inHand.shrink(1);
            return true;
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            FoodProperties food = edible(stack) ? stack.get(DataComponents.FOOD) : null;
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
        if (!edible(getOffhandItem())) {
            int foodSlot = -1;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (edible(inventory.getItem(slot))) {
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
            maybeAcquireTaste(food);
            noteEaten(food);
            consume(properties);
            food.shrink(1);
            if (!isBaby() && random.nextFloat() < 0.12F && leaderPlayer() instanceof net.minecraft.server.level.ServerPlayer lead
                    && distanceToSqr(lead) < 32.0D * 32.0D) {
                Cohesion.addLimited(lead, "member_fed", 1, 3 * 60 * 20L);
            }
        }
    }

    /**
     * Tastes are not fixed. Something eaten often enough, or just at the right moment,
     * becomes a favourite - and a hominin that has just discovered it likes something
     * says so, because that is information the rest of the band can use.
     */
    private void maybeAcquireTaste(ItemStack food) {
        if (isFavourite(food) || random.nextFloat() >= NEW_TASTE_CHANCE) {
            return;
        }
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(food.getItem());
        favouriteFoods.set(random.nextInt(favouriteFoods.size()), id);
        Lines.say(this, "new_favourite", " (" + food.getHoverName().getString() + ")");
    }

    private static final float NEW_TASTE_CHANCE = 0.06F;

    /**
     * Eating what somebody else passed over. A favourite handed to you by name is worth
     * more than the food in it - somebody noticed - and that is what sharing is for.
     *
     * @return true if it was one of their favourites.
     */
    public boolean eatShared(ItemStack food) {
        FoodProperties properties = food.get(DataComponents.FOOD);
        if (properties == null) {
            return false;
        }
        boolean favourite = isFavourite(food);
        maybeAcquireTaste(food);
        consume(properties);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(favourite ? ParticleTypes.HEART : ParticleTypes.HAPPY_VILLAGER,
                    getX(), getEyeY() + 0.3D, getZ(), favourite ? 3 : 2, 0.3D, 0.2D, 0.3D, 0.0D);
        }
        return favourite;
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

    public static int weaponRank(ItemStack stack) {
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
        // A weapon only if it is better than the best they have: nobody needs two spears.
        boolean useful = edible(stack) || isWeapon(stack) && weaponRank(stack) > bestWeaponRank()
                || (stack.is(net.minecraft.world.item.Items.STICK) && countCarried(s -> s.is(stack.getItem())) < 2)
                || (stack.is(ModItems.OBSIDIAN_ROCK.get()) && isObsessedWithObsidian())
                || (dev.hominin.evolution.band.goal.CraftGoal.canCraft(this)
                        && (stack.is(ModItems.LONG_BONE.get()) || stack.is(ModItems.RIB.get())))
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
        if (stack.is(ModItems.OBSIDIAN_ROCK.get()) && isObsessedWithObsidian()) {
            value += 100;
        }
        if (stack.is(ModItems.LIMESTONE_ROCK.get())) {
            value -= 5;
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

    /**
     * Something you handed over: they take it, whatever it is. Hands full, the least use of everything they carry is
     * the thing that goes - dropped at their feet, not what you gave them.
     */
    public void takeGift(ItemStack stack) {
        if (!inventory.canAddItem(stack)) {
            int worst = leastValuableSlot();
            if (worst >= 0) {
                spawnAtLocation(inventory.removeItemNoUpdate(worst));
            }
        }
        addToInventory(stack);
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

    /** The last of a band that yours destroyed: the day they joined, or 0. They stay only if they come to trust you. */
    private long survivorSince;

    public boolean isSurvivor() {
        return survivorSince > 0L;
    }

    public void becomeSurvivor() {
        survivorSince = Math.max(1L, level().getDayTime() / 24000L);
    }

    /**
     * Once a day, for a survivor: bond 4 and they are yours for good. Two days on and still under 3, they may go -
     * to another band, if one is near enough, or off on their own.
     */
    private void checkSurvivor() {
        if (!isSurvivor() || tickCount % 1200 != 611 || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        Player leader = leaderPlayer();
        ensureName();
        if (getBond() >= 4) {
            survivorSince = 0L;
            if (leader != null) {
                leader.sendSystemMessage(Component.literal(getName().getString() + " has stopped looking back the way "
                        + "they came. One of you, for good.").withStyle(ChatFormatting.GREEN));
            }
            return;
        }
        long days = level().getDayTime() / 24000L - survivorSince;
        if (days < 2 || getBond() >= 3 || random.nextFloat() >= 0.3F) {
            return;
        }
        survivorSince = 0L;
        Bands.Record nearest = null;
        for (Bands.Record band : Bands.all(server)) {
            if (!band.nomadic() && Bands.horizontal(band.home, blockPosition()) < 200.0D * 200.0D
                    && (nearest == null || Bands.horizontal(band.home, blockPosition())
                            < Bands.horizontal(nearest.home, blockPosition()))) {
                nearest = band;
            }
        }
        if (leader != null) {
            dev.hominin.evolution.guide.Alerts.urgent(leader, dev.hominin.evolution.guide.Alerts.Kind.BAND,
                    Component.literal(getName().getString() + " never came to trust you, and has gone"
                            + (nearest != null ? " - to " + nearest.name + "." : ", alone.")).withStyle(ChatFormatting.GOLD));
        }
        if (nearest != null) {
            joinWildBand(nearest.id, null);
            nearest.size++;
            Bands.changed(server);
            getNavigation().moveTo(nearest.home.getX() + 0.5D, nearest.home.getY(), nearest.home.getZ() + 0.5D, 1.0D);
        } else {
            discard();
        }
    }

    /** Who has held something out to be given, and when: a second right-click in time hands it over. */
    private final java.util.Map<UUID, Long> offeredAt = new java.util.HashMap<>();
    /** When they last thanked anyone for a gift: once in a while, not for every stick. */
    private long lastThanked = -3000L;

    /**
     * A spare weapon - one worse than the best they carry - handed down: to one of the band whose best is worse,
     * or laid on the band's tool pile if it is near. Every ten seconds, for your own band.
     */
    private void handDownSpares() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server) || isWild() || isBaby()
                || tickCount % 200 != 37) {
            return;
        }
        int best = bestWeaponRank();
        // Something better leaning on a rack nearby: taken down and carried.
        var rackAccess = ToolPiles.access(this);
        dev.hominin.evolution.block.ToolRackBlockEntity racked = dev.hominin.evolution.block.ToolRackBlockEntity.nearest(
                server, blockPosition(), 16.0D, r -> r.has(s -> weaponRank(s) > best, rackAccess));
        if (racked != null) {
            ItemStack taken = racked.takeFirst(s -> weaponRank(s) > best, rackAccess);
            if (!taken.isEmpty()) {
                addToInventory(taken);
                ensureName();
                Band.announce(this, " takes a " + taken.getHoverName().getString().toLowerCase() + " down off the rack.");
                return;
            }
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack spare = inventory.getItem(slot);
            int rank = weaponRank(spare);
            if (rank < 0 || rank > best || rank == best && countCarried(s -> weaponRank(s) == best) < 2
                    || rank <= 1) {
                continue;
            }
            for (BandMember other : Band.near(this, 12.0D)) {
                if (other != this && !other.isBaby() && leaderPlayer() != null && other.isLedBy(leaderPlayer())
                        && other.bestWeaponRank() < rank) {
                    other.addToInventory(inventory.removeItem(slot, 1));
                    ensureName();
                    other.ensureName();
                    Band.announceDiscovery(this, " hands their spare " + spare.getHoverName().getString().toLowerCase()
                            + " to " + other.getName().getString() + ".");
                    return;
                }
            }
            dev.hominin.evolution.block.ToolRackBlockEntity rack = dev.hominin.evolution.block.ToolRackBlockEntity.nearest(
                    server, blockPosition(), 16.0D, r -> !r.isFull());
            if (rack != null && dev.hominin.evolution.block.ToolRackBlockEntity.rackable(spare)) {
                ItemStack one = inventory.removeItem(slot, 1);
                ensureName();
                if (rack.lean(one, getUUID(), getName().getString())) {
                    ensureName();
                    Band.announce(this, " leans their spare " + spare.getHoverName().getString().toLowerCase()
                            + " on the rack.");
                    return;
                }
                addToInventory(one);
            }
            UUID owner = ToolPiles.ownerOf(this);
            net.minecraft.core.BlockPos store = owner == null ? null : ToolPiles.storeWithin(server, owner, blockPosition(), 24.0D);
            if (store != null && ToolPiles.putBack(server, owner, store, inventory.removeItem(slot, 1), this)) {
                return;
            }
        }
    }

    /** Makers keep a little raw material: a couple of sticks, a few stones, a flake. */
    private boolean wantsMaterial(ItemStack stack) {
        if (dev.hominin.evolution.band.ErectusWork.works(this)) {
            // Erectus keeps what beds and builds are made of - a few of each.
            if (stack.is(ModItems.HIDE.get())) {
                return countCarried(s -> s.is(ModItems.HIDE.get())) < 4;
            }
            if (stack.is(ModItems.THATCH.get()) || stack.is(ModItems.TWINE.get())) {
                return countCarried(s -> s.is(stack.getItem())) < 16;
            }
            if (stack.is(ModItems.THATCH_BEDDING.get())) {
                return countCarried(s -> s.is(stack.getItem())) < 2;
            }
            if (stack.is(ModItems.THATCH_BLOCK.get()) || stack.is(ModItems.BUILDING_BRANCH.get())) {
                return countCarried(s -> s.is(stack.getItem())) < 12;
            }
            if (stack.is(ModItems.WORKABLE_BRANCH.get())) {
                return countCarried(s -> s.is(stack.getItem())) < 3;
            }
        }
        if (stack.is(net.minecraft.world.item.Items.STICK)) {
            return countCarried(s -> s.is(net.minecraft.world.item.Items.STICK)) < 2;
        }
        if (stack.is(dev.hominin.evolution.ModTags.Items.ROCKS) || stack.is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE)) {
            return countCarried(s -> s.is(dev.hominin.evolution.ModTags.Items.ROCKS)
                    || s.is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE)) < 4;
        }
        if (stack.is(dev.hominin.evolution.ModTags.Items.FLAKES)) {
            return countCarried(s -> s.is(dev.hominin.evolution.ModTags.Items.FLAKES)) < 2;
        }
        if (stack.is(ModItems.GRINDING_ROCK.get())) {
            return countCarried(s -> s.is(stack.getItem())) < 1;
        }
        return false;
    }

    public int countCarried(java.util.function.Predicate<ItemStack> test) {
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
        // Found, not handed over: a rare thing lying about is news.
        ItemStack found = RareFinds.isRare(itemEntity.getItem()) && itemEntity.getOwner() == null
                ? itemEntity.getItem().copy() : ItemStack.EMPTY;
        makeRoomFor(itemEntity.getItem());
        InventoryCarrier.pickUpItem(this, this, itemEntity);
        equipBestWeapon();
        if (!found.isEmpty() && itemEntity.isRemoved()) {
            RareFinds.memberFound(this, found);
        }
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
    /** What a hunting party asked them to carry: that, in hand, while there is something to fight. */
    @javax.annotation.Nullable
    private java.util.function.Predicate<ItemStack> huntWeapon;

    public void setHuntWeapon(@javax.annotation.Nullable java.util.function.Predicate<ItemStack> weapon) {
        huntWeapon = weapon;
    }

    public void updateHands() {
        if (isBaby() || level().isClientSide()) {
            return;
        }
        java.util.function.Predicate<ItemStack> asked = huntWeapon;
        if ((inDanger() || getTarget() != null) && asked != null && countCarried(asked) > 0) {
            wieldBest(s -> asked.test(s) ? 100 + Math.max(0, weaponRank(s)) : -1);
        } else if (inDanger()) {
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
        boolean food = player.getMainHandItem().has(DataComponents.FOOD);
        if (level().isClientSide()) {
            // Any right-click picks them out: press H within five seconds and you are
            // talking to this one, not the whole band.
            SocialSelection.entityId = getId();
            SocialSelection.selectedAtMillis = net.minecraft.Util.getMillis();
            return InteractionResult.SUCCESS;
        }
        if (player.getMainHandItem().is(ModItems.HOMININ_BRAIN.get()) && !Bands.erectusOn(getStage())) {
            // Nothing in them says this is food.
            ensureName();
            player.displayClientMessage(Component.literal(getName().getString() + " sniffs it and turns away."), true);
            return InteractionResult.CONSUME;
        }
        if (player.getMainHandItem().is(ModItems.HOMININ_BRAIN.get()) && leaderPlayer() instanceof Player lead
                && Morals.holds(lead, Morals.Moral.NO_BRAINS)) {
            ensureName();
            player.displayClientMessage(Component.literal(getName().getString() + " will not touch it. \"Not the flesh "
                    + "of the thought.\""), true);
            return InteractionResult.CONSUME;
        }
        // Food held out is food held out: they take it, and nobody needs a menu for that.
        if (food) {
            receiveFromHand(player);
            return InteractionResult.CONSUME;
        }
        ItemStack held = player.getMainHandItem();
        if (answersTo(player) && !held.isEmpty() && (held.is(getWant() == null ? net.minecraft.world.item.Items.AIR
                : getWant()) || Needs.wouldMeet(this, player, held))) {
            // Exactly what they asked for, or what they need: no menu, no second click - it is given.
            receiveFromHand(player);
            return InteractionResult.CONSUME;
        }
        if (answersTo(player) && (isWeapon(held) || held.is(dev.hominin.evolution.ModTags.Items.STONE_TOOLS))) {
            // Held out once, it is shown; held out again, it is given.
            long now = level().getGameTime();
            Long first = offeredAt.get(player.getUUID());
            if (first != null && now - first < 60L) {
                offeredAt.remove(player.getUUID());
                receiveFromHand(player);
                return InteractionResult.CONSUME;
            }
            offeredAt.put(player.getUUID(), now);
            ensureName();
            attendTo(player, ATTEND_TICKS);
            player.displayClientMessage(Component.literal(getName().getString() + " looks at the "
                    + held.getHoverName().getString().toLowerCase() + ". Right-click again to give it to them.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        if (isStray()) {
            ensureName();
            player.displayClientMessage(Component.literal(getName().getString() + " watches you, wary, alone. Hold out "
                    + "some food - or just come and sit close.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        // Everything else is said from the H menu: a right-click only picks them out.
        ensureName();
        attendTo(player, ATTEND_TICKS);
        player.displayClientMessage(Component.literal(getName().getString()
                + " turns to you. Press H within 5 seconds to talk to them.").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.CONSUME;
    }

    /**
     * "Take this": whatever the player is holding, handed over from the H menu. Food is
     * eaten (and is how a stray is won over), what they asked for settles the want, and
     * anything else goes in their pack. An empty hand held out to an obsidian obsessive
     * is sometimes filled.
     */
    public void receiveFromHand(Player player) {
        ensureName();
        ItemStack held = player.getMainHandItem();
        if (dev.hominin.evolution.food.Spoilage.isSpoiled(held)) {
            player.displayClientMessage(Component.literal(getName().getString() + " smells it, pulls a face and pushes "
                    + "it away. It has gone off."), true);
            return;
        }
        if (isWild()) {
            FoodProperties guestFood = held.get(DataComponents.FOOD);
            if (guestFood != null && isGuestOf(player)) {
                feedFromHand(player, held, guestFood);
            } else {
                player.displayClientMessage(Component.literal(getName().getString()
                        + " will not take things from a stranger. Trade with them instead."), true);
            }
            return;
        }
        // A co-leader gives as the band's leader would: what the band needs is the band's, whoever meets it.
        Player lead = isCoLedBy(player) && leaderPlayer() != null ? leaderPlayer() : player;
        boolean ours = isLedBy(lead);
        if (held.isEmpty()) {
            if (!(isLedBy(player) && shareObsidian(player))) {
                player.displayClientMessage(Component.literal("Your hand is empty. Hold what you want to give."), true);
            }
            return;
        }
        if (ours && lead instanceof net.minecraft.server.level.ServerPlayer giver) {
            // Anything handed over counts as giving back - even a bite of food.
            Mood.gave(giver, 1);
        }
        if (ours && Needs.receive(this, lead, held)) {
            return;
        }
        if (ours && Commissions.receive(this, lead, held)) {
            return;
        }
        if (ours && Wants.receive(this, lead, held)) {
            return;
        }
        if (ours && lead instanceof net.minecraft.server.level.ServerPlayer giver
                && RareFinds.treasured(this, giver, held)) {
            return;
        }
        FoodProperties food = held.get(DataComponents.FOOD);
        if (food != null) {
            feedFromHand(player, held, food);
        } else if (ours) {
            boolean better = isWeapon(held) && weaponRank(held) > bestWeaponRank()
                    || held.is(dev.hominin.evolution.ModTags.Items.STONE_TOOLS) && countCarried(s -> s.is(held.getItem())) == 0;
            int worth = Trading.tierOf(held, getStage());
            takeGift(held.copyWithCount(1));
            playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
            long now = level().getGameTime();
            if ((better || worth >= 2) && now - lastThanked > 3000L) {
                // Something they will use, or something worth having: it is appreciated.
                lastThanked = now;
                addBond(1);
                player.displayClientMessage(Component.literal(getName().getString() + " turns the ")
                        .append(held.getHoverName()).append(Component.literal(" over in their hands - "
                                + (better ? "better than what they had. " : "a good thing to be given. ") + "(Bond +1)"))
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                player.displayClientMessage(Component.literal(getName().getString() + " takes ")
                        .append(held.getHoverName()).append("."), true);
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        } else {
            player.displayClientMessage(Component.literal(getName().getString() + " does not know you well enough."), true);
        }
    }

    /** What sneak-clicking used to tell you, for the Info command. */
    public void describe(Player player) {
        describeTo(player);
    }

    private void feedFromHand(Player player, ItemStack held, FoodProperties food) {
        boolean recruiting = leader == null && bandId == null;
        // A stray takes food held out to it whether it is hungry or not: it is the offer that matters.
        if (hunger >= MAX_HUNGER && !recruiting) {
            player.displayClientMessage(Component.literal(getName().getString() + " is not hungry."), true);
            return;
        }
        if (recruiting && !Band.hasRoomFor(player)) {
            player.displayClientMessage(Component.literal("Your band is as big as the land can feed."), true);
            return;
        }
        boolean favourite = isFavourite(held);
        consume(food);
        heal(2.0F);
        if (favourite) {
            // A favourite goes further, and is remembered - sometimes for a long while.
            hunger = Math.min(MAX_HUNGER, hunger + Math.max(2, food.nutrition() / 2));
            bond += random.nextFloat() < 0.35F ? 2 : 1;
            player.displayClientMessage(Component.literal(getName().getString() + " loves that!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else if (!recruiting && random.nextFloat() < 0.25F) {
            // Being fed by hand is being looked after, and now and then that is noticed.
            bond++;
            player.displayClientMessage(Component.literal(getName().getString() + " seems grateful.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        boolean wasHungry = isHungry();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.3D, getZ(),
                3, 0.3D, 0.2D, 0.3D, 0.0D);
        if (!recruiting && isLedBy(player) && player instanceof net.minecraft.server.level.ServerPlayer feeder) {
            // Feeding one of your own, by hand, is seen by all of them.
            Cohesion.addLimited(feeder, "fed_by_hand", favourite || wasHungry ? 2 : 1, 60 * 20L);
        }
        if (recruiting) {
            leader = player.getUUID();
            player.displayClientMessage(Component.literal(getName().getString()
                    + " eats from your hand, and stays close."), true);
            return;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer suitor) {
            Mating.court(suitor, this, favourite ? Mating.COURTSHIP_NEEDED : 1);
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

    /** The simple names of the goals running right now - what they are busy with. */
    public List<String> runningGoals() {
        List<String> names = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.ai.goal.WrappedGoal goal : goalSelector.getAvailableGoals()) {
            if (goal.isRunning()) {
                names.add(goal.getGoal().getClass().getSimpleName());
            }
        }
        return names;
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
        if (antisocial && bond < 6) {
            // Theirs is theirs.
            return false;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer leaderNow && isLedBy(player)
                && dev.hominin.evolution.survival.Seasons.isProsperous(level())
                && !Morals.holds(leaderNow, Morals.Moral.ALWAYS_SHARE) && random.nextBoolean()) {
            // A good season, and nobody ever said we share: they keep what they found.
            return false;
        }
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
                if (player instanceof net.minecraft.server.level.ServerPlayer taker) {
                    Mood.took(taker, 1);
                }
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
            if (!stack.has(DataComponents.FOOD) && value > bestValue && !refusesToPartWith(stack)) {
                bestValue = value;
                bestSlot = slot;
            }
        }
        ItemStack held = getMainHandItem();
        if (Trading.valueOf(held) > bestValue && !refusesToPartWith(held)) {
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

    /**
     * Nobody turns on their own. Whatever sets a target - a fight response, a defence, a hunt - it is never one of the
     * same band: a stray spear or a swing that clipped the wrong back used to set off a feud that ran through the
     * whole band, one killing the next.
     */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target instanceof BandMember other && other != this && isAlliedTo(other)) {
            return;
        }
        super.setTarget(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof BandMember other && other != this && isAlliedTo(other)) {
            // One of your own: a spear gone astray, an elbow in the crush. It does not wound, and it starts nothing.
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer by) {
            Paranthropus.struck(this, by);
        }
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
        if (attacker.getUUID().equals(gaveUpOn) && tickCount < gaveUpUntil && !isComingFor(attacker)) {
            return;
        }
        defendTicks = DEFEND_TICKS;
        fleeTicks = 0;
        chaseStartedAt = tickCount;
        lastLandedAt = tickCount;
        setTarget(attacker);
    }

    // ------------------------------------------------------------ knowing when to quit

    /** How long a chase can go without landing anything before it is not worth it. */
    private static final int GIVE_UP_TICKS = 12 * 20;
    /** Further than this and it has got away. */
    private static final double GOT_AWAY = 24.0D;
    /** Nobody chases something so far that they lose the band doing it. */
    private static final double TOO_FAR_FROM_LEADER = 40.0D;
    /** How long a quarry that got away is left alone before anyone tries again. */
    private static final int LET_IT_GO_TICKS = 30 * 20;

    @Nullable
    private LivingEntity chasing;
    private int chaseStartedAt;
    private int lastLandedAt;
    @Nullable
    private UUID gaveUpOn;
    private int gaveUpUntil;

    /** A predator on its way out: frightened off, scattering, or fleeing. */
    public static boolean backingOff(LivingEntity target) {
        if (!target.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS)) {
            return false;
        }
        return (target instanceof net.minecraft.world.entity.Mob mob && dev.hominin.evolution.combat.Scare.isScared(mob))
                || (target instanceof dev.hominin.evolution.entity.Crocuta hyena && hyena.isScattering())
                || (target instanceof dev.hominin.evolution.entity.Pachycrocuta giant && giant.isFleeing());
    }

    /** Whether this thing is actually attacking us - which is never something to walk away from. */
    private boolean isComingFor(LivingEntity target) {
        if (!(target instanceof net.minecraft.world.entity.Mob mob) || mob.getTarget() == null) {
            return false;
        }
        LivingEntity itsTarget = mob.getTarget();
        return itsTarget == this || itsTarget == leaderPlayer()
                || (itsTarget instanceof BandMember other && other.isAlliedTo(this));
    }

    /**
     * Quitting. A hunter that chases everything it swings at until one of them drops is
     * a hunter that ends up alone, a long way from the band, at dusk. So a chase ends
     * when it stops paying: nothing landed for twelve seconds, the quarry well out
     * ahead, or the band left behind. Anything actually fighting back is not a chase,
     * and is never walked away from.
     */
    private void tickGiveUp() {
        LivingEntity target = getTarget();
        // A new target from anywhere - the band's call, its own hunting, being hit - starts
        // a fresh clock. Otherwise a target picked up by some other route would be judged
        // on how long ago the last, unrelated fight ended.
        if (target != chasing) {
            chasing = target;
            chaseStartedAt = tickCount;
            lastLandedAt = tickCount;
        }
        if (target == null || tickCount % 10 != 0) {
            return;
        }
        if (!target.isAlive()) {
            setTarget(null);
            return;
        }
        if (backingOff(target)) {
            // It has given up; so do we. Nobody follows a predator into the grass.
            gaveUpOn = target.getUUID();
            gaveUpUntil = tickCount + LET_IT_GO_TICKS;
            setTarget(null);
            defendTicks = 0;
            huntTicks = 0;
            getNavigation().stop();
            return;
        }
        if (isComingFor(target)) {
            lastLandedAt = tickCount;
            return;
        }
        Player leader = leaderPlayer();
        boolean fruitless = tickCount - lastLandedAt > GIVE_UP_TICKS;
        boolean gotAway = distanceToSqr(target) > GOT_AWAY * GOT_AWAY;
        boolean strayed = leader != null && distanceToSqr(leader) > TOO_FAR_FROM_LEADER * TOO_FAR_FROM_LEADER;
        if (!fruitless && !gotAway && !strayed) {
            return;
        }
        gaveUpOn = target.getUUID();
        gaveUpUntil = tickCount + LET_IT_GO_TICKS;
        setTarget(null);
        defendTicks = 0;
        huntTicks = 0;
        getNavigation().stop();
        if (random.nextInt(3) == 0) {
            Lines.tell(this, strayed ? "chase_home" : "chase_pant");
        }
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
        String stage = dev.hominin.evolution.stage.Kinds.line(getStage());
        return !stage.equals("ardipithecus") && !stage.equals("australopithecus") && !stage.equals("homo_habilis");
    }

    /** Fight fired: stronger, tougher, and more likely to do real harm with each blow. */
    public boolean isFighting() {
        return level().getGameTime() < fightingUntil;
    }

    /** How close you have to come before the one you were will get up and follow. */
    private static final double GRIEF_RECRUIT_RANGE = 5.0D;

    public boolean isGrieving() {
        return entityData.get(GRIEVING);
    }

    public void setGrieving(boolean grieving) {
        entityData.set(GRIEVING, grieving);
    }

    /**
     * Somebody has come back. A hominin that has sat here since its band died gets up and
     * goes with them - there is nothing else out here for it.
     */
    private void tickGrief() {
        if (!isGrieving() || tickCount % 20 != 0) {
            return;
        }
        Player finder = level().getNearestPlayer(this, GRIEF_RECRUIT_RANGE);
        if (finder == null || !(finder instanceof net.minecraft.server.level.ServerPlayer server) || !Band.hasRoomFor(finder)) {
            return;
        }
        setGrieving(false);
        setLeader(finder.getUUID());
        // Whatever it was when it sat down, it is one of yours now: the same kind as you,
        // with a name of its own. Carrying the player's name made it read as the player
        // talking whenever it announced anything.
        boolean woreYourName = hasCustomName() && getName().getString().equals(finder.getName().getString());
        if (woreYourName) {
            setCustomName(null);
        }
        setStage(server.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage());
        ensureName();
        heal(4.0F);
        ((ServerLevel) level()).sendParticles(ParticleTypes.HEART, getX(), getEyeY() + 0.3D, getZ(),
                6, 0.3D, 0.2D, 0.3D, 0.0D);
        server.sendSystemMessage(Component.literal(woreYourName
                ? "The one you were gets to their feet and comes with you. They are "
                        + getName().getString() + " now."
                : getName().getString() + " looks up, gets to their feet, and comes with you.")
                .withStyle(ChatFormatting.GREEN));
    }

    /** Being groomed: stay put and enjoy it. */
    public void beingGroomed(int ticks) {
        groomTicks = Math.max(groomTicks, ticks);
    }

    /** Long enough to cover the selection window and the exchange that follows it. */
    public static final int ATTEND_TICKS = 140;

    // ------------------------------------------------------------ play

    public static final int PLAY_TAG = 1;
    public static final int PLAY_WRESTLE = 2;
    /** Practice pays off up to a point. Past three rounds there is nothing left to learn. */
    public static final int MAX_TRAINING = 3;

    private int playKind;
    private int playTicks;
    @Nullable
    private UUID playPartnerId;
    /** Rounds of each learned. Each one stretches the matching adrenaline response. */
    private int tagTraining;
    private int wrestleTraining;

    public void startPlay(int kind, BandMember partner, int ticks) {
        playKind = kind;
        playTicks = ticks;
        playPartnerId = partner.getUUID();
    }

    public boolean isPlaying() {
        return playTicks > 0 && playKind != 0;
    }

    public int playKind() {
        return playKind;
    }

    @Nullable
    public BandMember playPartner() {
        if (playPartnerId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        return server.getEntity(playPartnerId) instanceof BandMember partner ? partner : null;
    }

    /** Called once a tick while playing. The round ends on its own, and counts. */
    public void tickPlayClock() {
        if (--playTicks <= 0) {
            stopPlay(true);
        }
    }

    public void stopPlay(boolean finished) {
        if (finished && playKind == PLAY_TAG) {
            tagTraining = Math.min(MAX_TRAINING, tagTraining + 1);
        } else if (finished && playKind == PLAY_WRESTLE) {
            wrestleTraining = Math.min(MAX_TRAINING, wrestleTraining + 1);
        }
        playKind = 0;
        playTicks = 0;
        playPartnerId = null;
    }

    public int getTagTraining() {
        return tagTraining;
    }

    public int getWrestleTraining() {
        return wrestleTraining;
    }

    // ------------------------------------------------------------ what they know

    private final java.util.Set<String> knownSkills = new java.util.HashSet<>();

    public boolean knowsSkill(dev.hominin.evolution.mind.Skills.Skill skill) {
        return knownSkills.contains(skill.name()) && Species.canLearn(getStage(), skill);
    }

    /** Whether this one could ever be taught it: some kinds never had it in them. */
    public boolean canLearnSkill(dev.hominin.evolution.mind.Skills.Skill skill) {
        return Species.canLearn(getStage(), skill);
    }

    /** A wild band's people come knowing what their kind knows. */
    private boolean nativeSkillsGiven;

    private void giveNativeSkills() {
        if (nativeSkillsGiven || level().isClientSide() || !isWild()) {
            return;
        }
        nativeSkillsGiven = true;
        for (dev.hominin.evolution.mind.Skills.Skill skill : Species.nativeSkills(getStage())) {
            knownSkills.add(skill.name());
        }
    }

    /** Just shown how to do something, and wanting to try it. Not saved: the urge passes with the session. */
    @javax.annotation.Nullable
    private dev.hominin.evolution.mind.Skills.Skill practising;

    @javax.annotation.Nullable
    public dev.hominin.evolution.mind.Skills.Skill practising() {
        return practising;
    }

    /** Something with the hands, just learned: go and try it. */
    public void startPractising(dev.hominin.evolution.mind.Skills.Skill skill) {
        practising = skill;
    }

    public void stopPractising() {
        practising = null;
    }

    public void learnSkill(dev.hominin.evolution.mind.Skills.Skill skill) {
        if (!Species.canLearn(getStage(), skill)) {
            return;
        }
        knownSkills.add(skill.name());
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANT, getX(), getEyeY() + 0.4D, getZ(),
                    12, 0.3D, 0.3D, 0.3D, 0.5D);
        }
    }

    /** For the Info screen: what this one has been taught. */
    public java.util.List<String> knownSkillTitles() {
        java.util.List<String> titles = new java.util.ArrayList<>();
        for (dev.hominin.evolution.mind.Skills.Skill skill : dev.hominin.evolution.mind.Skills.Skill.values()) {
            if (knowsSkill(skill)) {
                titles.add(skill.title());
            }
        }
        return titles;
    }

    /**
     * Growing up means learning what the adults around you know. Whoever minded this
     * child passes on everything they were taught - out loud, so the band hears it.
     */
    private void inheritSkills() {
        if (!(level() instanceof ServerLevel server) || getCaretaker() == null
                || !(server.getEntity(getCaretaker()) instanceof BandMember minder)) {
            knapLevel = 4;
            huntLevel = 3;
            return;
        }
        // Children start at the bottom, and are taught up to their minder's level or one short of it.
        knapLevel = Math.min(4, minder.getKnapLevel() + (random.nextBoolean() ? 0 : 1));
        huntLevel = Math.min(3, minder.getHuntLevel() + (random.nextBoolean() ? 0 : 1));
        if (minder.isAntisocial()) {
            // Minded by somebody who could not be bothered: nothing passed on.
            knapLevel = 4;
            huntLevel = 3;
            return;
        }
        java.util.List<String> passed = new java.util.ArrayList<>();
        for (dev.hominin.evolution.mind.Skills.Skill skill : dev.hominin.evolution.mind.Skills.Skill.values()) {
            if (minder.knowsSkill(skill) && !knowsSkill(skill)) {
                learnSkill(skill);
                passed.add(skill.title().toLowerCase());
            }
        }
        if (!passed.isEmpty()) {
            minder.ensureName();
            ensureName();
            Band.announce(minder, " has taught " + getName().getString() + " " + String.join(", ", passed)
                    + " as they grew up.");
        }
    }

    /** How long they are giving you their attention, and whose it is. */
    private int attendTicks;
    @Nullable
    private UUID attendingTo;

    /**
     * Stopping to listen. Picking somebody out and then chasing them round a clearing
     * while you try to say something to them is not a conversation - so when you single
     * one out, they stop what they are doing, turn, and wait to hear it.
     */
    public void attendTo(Player player, int ticks) {
        attendTicks = Math.max(attendTicks, ticks);
        attendingTo = player.getUUID();
    }

    public boolean isAttending() {
        return attendTicks > 0;
    }

    private void tickAttention() {
        if (attendTicks <= 0) {
            return;
        }
        // Anything actually dangerous ends the conversation immediately.
        if (inDanger() || isUpATree()) {
            attendTicks = 0;
            attendingTo = null;
            return;
        }
        attendTicks--;
        getNavigation().stop();
        if (attendingTo != null && level().getPlayerByUUID(attendingTo) instanceof Player listener) {
            getLookControl().setLookAt(listener, 30.0F, 30.0F);
        }
        if (attendTicks == 0) {
            attendingTo = null;
        }
    }

    /** How many ticks are on this one, and who is owed a turn in return. */
    private int ticksOnMe;
    @Nullable
    private UUID owesGroomingTo;

    public int getTicksOnMe() {
        return ticksOnMe;
    }

    /**
     * Picking them off. Returns how many actually came off, which is what the groomer
     * gets to keep - so grooming somebody who has been neglected pays better than
     * grooming somebody already clean, exactly as it should.
     */
    public int pickTicks(int wanted) {
        int found = Math.min(wanted, ticksOnMe);
        ticksOnMe -= found;
        return found;
    }

    /**
     * The other half of the trade. Nobody in a primate group grooms for nothing: being
     * groomed puts you in debt, and the debt gets paid. You do not get to opt out of
     * this, and neither do they.
     */
    public void oweGrooming(Player player) {
        owesGroomingTo = player.getUUID();
    }

    public boolean owesGroomingTo(Player player) {
        return player.getUUID().equals(owesGroomingTo);
    }

    /** Settling up. */
    public void groomingRepaid() {
        owesGroomingTo = null;
    }

    /** Slowly collecting them, the same way the player does. */
    private void tickInfestation() {
        if (tickCount % 12000 == 0 && ticksOnMe < 10) {
            ticksOnMe++;
        }
    }

    /**
     * Paying the debt. Stand near somebody who groomed you and sooner or later you
     * return it - which is the point of the whole arrangement, because they cannot reach
     * their own back either.
     */
    private void repayGrooming() {
        if (owesGroomingTo == null || tickCount % 20 != 0 || getTarget() != null || isUpATree()) {
            return;
        }
        if (!(level().getPlayerByUUID(owesGroomingTo) instanceof net.minecraft.server.level.ServerPlayer owed)
                || distanceToSqr(owed) > 32.0D * 32.0D) {
            return;
        }
        if (distanceToSqr(owed) > 2.5D * 2.5D) {
            getNavigation().moveTo(owed, 1.1D);
            return;
        }
        groomingRepaid();
        dev.hominin.evolution.survival.Infestation.groomed(owed,
                knowsSkill(dev.hominin.evolution.mind.Skills.Skill.GROOMING) ? 3 : 2);
        beingGroomed(60);
        getNavigation().stop();
        getLookControl().setLookAt(owed);
        playSound(net.minecraft.sounds.SoundEvents.WOOL_HIT, 0.5F, 1.4F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                    owed.getX(), owed.getEyeY(), owed.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.0D);
        }
        owed.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                getName().getString() + " sits you down and goes through your hair in turn.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public boolean isBeingGroomed() {
        return groomTicks > 0;
    }

    @Override
    public boolean isPushable() {
        return !isGrieving() && super.isPushable();
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
        // A band that trusts its leader holds its nerve.
        float nerve = Cohesion.nerve(leaderPlayer());
        if (random.nextFloat() < FREEZE_CHANCE * (1.0F - nerve * 2.0F)) {
            freezeTicks = FREEZE_TICKS;
            freezeThreat = threat;
            getNavigation().stop();
            Lines.announce(this, "freeze");
            return;
        }
        float fightChance = (carriesWeapon() ? 0.65F : 0.35F) + nerve;
        if (random.nextFloat() < fightChance) {
            // Every round of wrestling done in safety is five more seconds of fight now.
            int fightTicks = ADRENALINE_TICKS + wrestleTraining * 100;
            fightingUntil = now + fightTicks;
            addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, fightTicks, 1));
            addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, fightTicks, 0));
            panicking = false;
            fleeTicks = 0;
            defendTicks = DEFEND_TICKS;
            setTarget(threat);
            updateHands();
            Lines.announce(this, "fight_back");
        } else {
            // The same shape as a struck animal's flight: a burst nothing can follow, then a
            // longer, slower run. Twenty seconds of Speed II made a frightened member vanish.
            // And every round of tag is another second of running before the legs go.
            int runTicks = PANIC_RUN_TICKS + tagTraining * 20;
            addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, PANIC_BURST_TICKS + runTicks, 0));
            addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, PANIC_BURST_TICKS, 1));
            panicking = true;
            fleeTicks = ADRENALINE_TICKS;
            defendTicks = 0;
            setTarget(null);
            Lines.announce(this, "bolt");
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
        if (hit) {
            lastLandedAt = tickCount;
        }
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

    /** How long a member stays up a tree once whatever put it there has gone. */
    private static final int CLIMB_DOWN_TICKS = 200;

    /**
     * Knocked off a trunk, a member used to keep the climbing flag and skate about the
     * ground on it. Climbing only holds while there is a trunk to hold on to, and only
     * lasts while there is something to be up there for.
     */
    private void tickTreeStay() {
        if (!trunkInReach()) {
            setClimbingTree(false);
            calmInTreeTicks = 0;
            return;
        }
        if (shouldFlee() || hasClimbOrder() || getTarget() != null || inDanger()) {
            calmInTreeTicks = 0;
            return;
        }
        if (++calmInTreeTicks >= CLIMB_DOWN_TICKS) {
            calmInTreeTicks = 0;
            setClimbingTree(false);
        }
    }

    /** Something to cling to: a log within arm's reach, at foot or head height. */
    private boolean trunkInReach() {
        BlockPos origin = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(1, 1, 1))) {
            if (level().getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
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
        // Following a leader flying about in creative mode is not a reason to die.
        if (leaderPlayer() instanceof Player lead && lead.isCreative()) {
            return false;
        }
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
        cling();
        handDownSpares();
        Throwing.tick(this);
        Postures.tick(this);
        checkSurvivor();
        super.customServerAiStep();
        dev.hominin.evolution.survival.Diseases.tick(this);
        if (tickCount == 1) {
            ensureName();
            applyFightSkill();
            // Glowing is saved with the entity; an excursion from before a reload may not have been.
            if (excursionTicks <= 0 && hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)
                    && !(isBaby() && caretaker != null)) {
                removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
            }
        }
        // Eating for two: a pregnancy burns through food twice as fast. Full after a feast, a quarter as fast.
        if (!isBaby() && (!hasEffect(dev.hominin.evolution.ModEffects.FULL) || tickCount % 4 == 0)
                && (hungerClock += (isPregnant() ? 2 : 1)
                + (dev.hominin.evolution.survival.Seasons.isDry(level()) && tickCount % 2 == 0 ? 1 : 0)) >= HUNGER_TICKS) {
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
            giveNativeSkills();
            tickInjury();
            Wants.tick(this);
            Commissions.tick(this);
            if (!level().isClientSide() && !isWild() && (tickCount + getId()) % 600 < 20) {
                dev.hominin.evolution.mind.MentalMap.memberNotices(this);
            }
        }
        fleeTicks = Math.max(0, fleeTicks - 1);
        if (fleeTicks == 0) {
            panicking = false;
        }
        alarmTicks = Math.max(0, alarmTicks - 1);
        tickGrief();
        if (groomTicks > 0) {
            groomTicks--;
            getNavigation().stop();
        }
        tickAttention();
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
        tickGiveUp();
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
        tickInfestation();
        tickKuru();
        tickBrains();
        tickMating();
        repayGrooming();
        if (tickCount % 100 == 0 && guestOf != null && level().isNight()) {
            // The alpha takes its band home at dusk - but only if there still is one.
            // Hanging the whole departure on the alpha meant that a visiting band whose
            // alpha had died, or wandered off, simply never left: they stood in your
            // camp all night, every night, with nobody left to call them away.
            if (isAlpha() || !Band.alphaNearby(this)) {
                Band.sendGuestsHome(this);
            }
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
            tickTreeStay();
        } else {
            calmInTreeTicks = 0;
        }
        if (displayDelay > 0) {
            displayDelay--;
        } else if (displayDelay == 0) {
            displayDelay = -1;
            Band.performDisplay(this);
        }
        if (pregnancyTicks > 0 && --pregnancyTicks == 0) {
            labourSpot = null;
            birth();
        }
        if (growUpTicks > 0 && --growUpTicks == 0) {
            entityData.set(BABY, false);
            inheritSkills();
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

    // ------------------------------------------------------------ what they remember

    /** Places this member holds in mind: as many as their own mind has room for. */
    private final java.util.List<dev.hominin.evolution.mind.MindData.Memory> memories = new java.util.ArrayList<>();
    private int memorySlots;

    public java.util.List<dev.hominin.evolution.mind.MindData.Memory> memories() {
        return memories;
    }

    /** Remembers a place, unless it already knows it; with no room left, the oldest goes. */
    public void remember(dev.hominin.evolution.mind.MindData.Memory memory) {
        if (memorySlots == 0) {
            String era = getStage().getPath();
            boolean later = !era.startsWith("australopithecus") && !era.equals("ardipithecus")
                    && !era.equals("homo_habilis") && !era.equals("homo_rudolfensis");
            memorySlots = later ? 4 + random.nextInt(4) : 2 + random.nextInt(3);
        }
        for (var held : memories) {
            if (held.kind().equals(memory.kind()) && held.pos().distSqr(memory.pos()) < 24.0D * 24.0D) {
                return;
            }
        }
        while (memories.size() >= memorySlots && !memories.isEmpty()) {
            memories.remove(0);
        }
        memories.add(memory);
    }

    // ------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Memories", dev.hominin.evolution.mind.MindData.Memory.listTag(memories));
        tag.putInt("MemorySlots", memorySlots);
        tag.putInt("KnapLevel", knapLevel);
        tag.putInt("KnapPractice", knapPractice);
        // Stored one up, so 0 can mean "not rolled yet" - hunting reaches level 0 now.
        tag.putInt("HuntLevelPlus", huntLevel + 1);
        tag.putInt("NegotiateLevelPlus", negotiateLevel + 1);
        tag.putBoolean("Antisocial", antisocial);
        tag.putBoolean("TemperRolled", temperRolled);
        tag.putBoolean("NativeSkills", nativeSkillsGiven);
        tag.putInt("Trouble", trouble);
        tag.putString("GrievingFor", grievingFor);
        tag.putString("GriefKind", griefKind);
        tag.putBoolean("Psychopath", psychopath);
        tag.putBoolean("PsychopathRolled", psychopathRolled);
        tag.putBoolean("PsychopathKnown", psychopathKnown);
        if (grudge != null) {
            tag.putUUID("Grudge", grudge);
        }
        if (mother != null) {
            tag.putUUID("Mother", mother);
        }
        tag.putLong("InjuredUntil", injuredUntil);
        tag.putBoolean("ThoughtWhileDown", thoughtWhileDown);
        tag.putLong("WatchUntil", watchUntil);
        tag.put("Commission", commission);
        tag.putLong("PairReadyAt", pairReadyAt);
        if (mate != null) {
            tag.putUUID("Mate", mate);
        }
        tag.putInt("Courtship", courtship);
        if (labourSpot != null) {
            tag.putLong("LabourSpot", labourSpot.asLong());
        }
        net.minecraft.nbt.ListTag friends = new net.minecraft.nbt.ListTag();
        for (java.util.Map.Entry<UUID, Integer> entry : affinity.entrySet()) {
            CompoundTag friend = new CompoundTag();
            friend.putUUID("Id", entry.getKey());
            friend.putInt("Value", entry.getValue());
            friends.add(friend);
        }
        tag.put("Affinity", friends);
        if (kuruSince >= 0L) {
            tag.putLong("KuruSince", kuruSince);
        }
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
        tag.putLong("SurvivorSince", survivorSince);
        tag.putBoolean("MadeChopper", madeChopper);
        tag.putInt("Party", party);
        if (partyHead != null) {
            tag.putUUID("PartyHead", partyHead);
        }
        tag.putBoolean("SharpenUrge", sharpenUrge);
        tag.putBoolean("Grieving", isGrieving());
        tag.putBoolean("PersonalityRolled", personalityRolled);
        tag.putInt("StonePreference", stonePreference);
        tag.putBoolean("ObsidianObsession", obsidianObsession);
        tag.putInt("TicksOnMe", ticksOnMe);
        tag.putInt("TagTraining", tagTraining);
        tag.putString("KnownSkills", String.join(",", knownSkills));
        tag.putInt("WrestleTraining", wrestleTraining);
        if (owesGroomingTo != null) {
            tag.putUUID("OwesGroomingTo", owesGroomingTo);
        }
        if (want != null) {
            tag.putString("Want", Wants.idOf(want).toString());
            tag.putLong("WantUntil", wantUntil);
            tag.putFloat("WantUrgency", wantUrgency);
            tag.putBoolean("WantVoiced", wantVoiced);
            if (tradeOffer != null) {
                tag.putString("TradeOffer", Wants.idOf(tradeOffer).toString());
            }
        }
        if (excursionTicks > 0 && excursionTarget != null) {
            tag.putInt("ExcursionTicks", excursionTicks);
            tag.putLong("ExcursionTarget", excursionTarget.asLong());
        }
        net.minecraft.nbt.ListTag favourites = new net.minecraft.nbt.ListTag();
        for (ResourceLocation food : favouriteFoods) {
            favourites.add(net.minecraft.nbt.StringTag.valueOf(food.toString()));
        }
        tag.put("FavouriteFoods", favourites);
        net.minecraft.nbt.ListTag tastes = new net.minecraft.nbt.ListTag();
        for (ResourceLocation food : tasted) {
            tastes.add(net.minecraft.nbt.StringTag.valueOf(food.toString()));
        }
        tag.put("Tasted", tastes);
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
        memories.clear();
        memories.addAll(dev.hominin.evolution.mind.MindData.Memory.fromList(tag.getList("Memories", net.minecraft.nbt.Tag.TAG_COMPOUND)));
        memorySlots = tag.getInt("MemorySlots");
        knapLevel = tag.getInt("KnapLevel");
        knapPractice = tag.getInt("KnapPractice");
        if (tag.contains("HuntLevelPlus")) {
            huntLevel = tag.getInt("HuntLevelPlus") - 1;
        } else {
            int old = tag.getInt("HuntLevel");
            huntLevel = old == 0 ? -1 : old;
        }
        negotiateLevel = tag.getInt("NegotiateLevelPlus") - 1;
        antisocial = tag.getBoolean("Antisocial");
        temperRolled = tag.getBoolean("TemperRolled");
        nativeSkillsGiven = tag.getBoolean("NativeSkills");
        trouble = tag.getInt("Trouble");
        grievingFor = tag.getString("GrievingFor");
        griefKind = tag.getString("GriefKind");
        psychopath = tag.getBoolean("Psychopath");
        psychopathRolled = tag.getBoolean("PsychopathRolled");
        psychopathKnown = tag.getBoolean("PsychopathKnown");
        grudge = tag.hasUUID("Grudge") ? tag.getUUID("Grudge") : null;
        mother = tag.hasUUID("Mother") ? tag.getUUID("Mother") : null;
        injuredUntil = tag.getLong("InjuredUntil");
        thoughtWhileDown = tag.getBoolean("ThoughtWhileDown");
        watchUntil = tag.getLong("WatchUntil");
        commission = tag.getCompound("Commission");
        pairReadyAt = tag.contains("PairReadyAt") ? tag.getLong("PairReadyAt") : -1L;
        mate = tag.hasUUID("Mate") ? tag.getUUID("Mate") : null;
        courtship = tag.getInt("Courtship");
        labourSpot = tag.contains("LabourSpot") ? BlockPos.of(tag.getLong("LabourSpot")) : null;
        affinity.clear();
        for (net.minecraft.nbt.Tag entry : tag.getList("Affinity", 10)) {
            CompoundTag friend = (CompoundTag) entry;
            if (friend.hasUUID("Id")) {
                affinity.put(friend.getUUID("Id"), friend.getInt("Value"));
            }
        }
        kuruSince = tag.contains("KuruSince") ? tag.getLong("KuruSince") : -1L;
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
        survivorSince = tag.getLong("SurvivorSince");
        madeChopper = tag.getBoolean("MadeChopper");
        party = tag.getInt("Party");
        partyHead = tag.hasUUID("PartyHead") ? tag.getUUID("PartyHead") : null;
        sharpenUrge = tag.getBoolean("SharpenUrge");
        entityData.set(GRIEVING, tag.getBoolean("Grieving"));
        personalityRolled = tag.getBoolean("PersonalityRolled");
        stonePreference = tag.getInt("StonePreference");
        obsidianObsession = tag.getBoolean("ObsidianObsession");
        ticksOnMe = tag.getInt("TicksOnMe");
        tagTraining = tag.getInt("TagTraining");
        knownSkills.clear();
        for (String skill : tag.getString("KnownSkills").split(",")) {
            if (!skill.isEmpty()) {
                knownSkills.add(skill);
            }
        }
        wrestleTraining = tag.getInt("WrestleTraining");
        owesGroomingTo = tag.hasUUID("OwesGroomingTo") ? tag.getUUID("OwesGroomingTo") : null;
        want = tag.contains("Want") ? itemOf(tag.getString("Want")) : null;
        wantUntil = tag.getLong("WantUntil");
        wantUrgency = tag.getFloat("WantUrgency");
        wantVoiced = tag.getBoolean("WantVoiced");
        tradeOffer = tag.contains("TradeOffer") ? itemOf(tag.getString("TradeOffer")) : null;
        excursionTicks = tag.getInt("ExcursionTicks");
        excursionTarget = tag.contains("ExcursionTarget") ? BlockPos.of(tag.getLong("ExcursionTarget")) : null;
        tasted.clear();
        for (net.minecraft.nbt.Tag food : tag.getList("Tasted", net.minecraft.nbt.Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(food.getAsString());
            if (id != null) {
                tasted.add(id);
            }
        }
        favouriteFoods.clear();
        for (net.minecraft.nbt.Tag food : tag.getList("FavouriteFoods", net.minecraft.nbt.Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(food.getAsString());
            // A craving for something never eaten - a steak, from before tastes had to be learned - is dropped.
            if (id != null && (wildFood(id) || tasted.contains(id))) {
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

    @Nullable
    private static Item itemOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    @Override
    public void die(DamageSource source) {
        // Named in chat like a tamed animal would be, and the band feels it.
        if (!level().isClientSide() && leader != null && !isRemoved()
                && level().getServer().getPlayerList().getPlayer(leader) instanceof net.minecraft.server.level.ServerPlayer mourner) {
            ensureName();
            mourner.sendSystemMessage(getCombatTracker().getDeathMessage().copy().withStyle(ChatFormatting.DARK_RED));
            Cohesion.add(mourner, -3, null);
            Mortuary.memberDied(mourner);
            if (!isBaby()) {
                Chatter.news(mourner, "news_death", getName().getString());
            }
        }
        super.die(source);
        if (!level().isClientSide() && isBaby()) {
            Band.onChildDied(this);
        }
        if (!level().isClientSide() && leader != null) {
            if (leaderPlayer() instanceof net.minecraft.server.level.ServerPlayer keeper) {
                Tracking.forget(keeper, getUUID());
            }
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
