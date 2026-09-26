package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.event.EvolutionEventHandler;
import dev.hominin.evolution.network.RebirthPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The band as a group: who is in it, what they do together, and keeping it going with
 * the player across fights, generations and deaths.
 */
public final class Band {
    /** How far a display carries to the rest of the band. */
    public static final double RALLY_RADIUS = 16.0D;
    /** How far the band notices its leader is in trouble. */
    private static final double DEFEND_RADIUS = 24.0D;

    private static final float CHANCE_PER_MEMBER = 0.12F;
    private static final double RADIUS_PER_MEMBER = 1.5D;
    private static final float MAX_CHANCE = 0.95F;
    /** Only a few voices are heard over one another. */
    private static final int MAX_CHORUS = 3;

    private static final double MEMBER_DISPLAY_RADIUS = 8.0D;
    private static final float MEMBER_DISPLAY_CHANCE = 0.4F;

    private static final double PAIR_RADIUS = 8.0D;

    private static final ResourceLocation BAND_FORMED =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "band_formed");

    /** Counter under the skill prefix, so it survives evolving. Unused until erectus. */
    public static final String COHESION = EvolutionManager.SKILL_PREFIX + "band_cohesion";

    /**
     * Who you carry on as. Everything needed is taken down when you die: respawning at a bed far away, the heir's
     * chunk is not loaded, and looking them up then found nobody - so the rebirth never played.
     */
    private record Heir(ResourceKey<Level> dimension, UUID member, String name, boolean female, int hunger,
            net.minecraft.world.phys.Vec3 pos, float yRot) {
    }

    /** Heirs whose bodies were not loaded yet when you opened your eyes as them: their things come when they do. */
    private record Absorb(UUID member, long until) {
    }

    private static final Map<UUID, Absorb> absorbing = new HashMap<>();

    private static final Map<UUID, Heir> heirs = new HashMap<>();
    /** Until when a player's blows on their band count as wrestling. */
    private static final Map<UUID, Long> wrestleWindow = new HashMap<>();

    /** Losing this many bands ends the line. */
    private static final int BANDS_TO_EXTINCTION = 3;
    private static final String BANDS_LOST = EvolutionManager.SKILL_PREFIX + "bands_lost";
    /** Band members further than this from their leader are brought back to them. */
    private static final double LOST_DISTANCE = 48.0D;
    public static final ResourceLocation ARDIPITHECUS =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "ardipithecus");

    // ------------------------------------------------------------ membership

    public static List<BandMember> near(LivingEntity center, double radius) {
        AABB area = center.getBoundingBox().inflate(radius);
        return center.level().getEntitiesOfClass(BandMember.class, area, BandMember::isAlive);
    }

    /** The player's band, and any band travelling with them, nearby. */
    public static List<BandMember> companionsNear(Player player, double radius) {
        AABB area = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(BandMember.class, area,
                member -> member.isAlive() && member.isCompanionOf(player));
    }

    /** The player's band members nearby. */
    public static List<BandMember> ownNear(Player player, double radius) {
        AABB area = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(BandMember.class, area,
                member -> member.isAlive() && member.isLedBy(player));
    }

    /** Every loaded member of this player's band, in the player's dimension. */
    public static List<BandMember> all(ServerPlayer player) {
        return new java.util.ArrayList<BandMember>(player.serverLevel().getEntities(ModEntities.BAND_MEMBER.get(),
                member -> member.isAlive() && member.isLedBy(player)));
    }

    // ------------------------------------------------------------ two left

    /** Cohesion when two join a band that takes them in: they need to learn to trust you. */
    private static final int JOINED_COHESION = 25;

    /**
     * Down to two, from habilis on, you can ask a band of your own kind to take you in: they would sooner be more.
     * Not a band that has it in for you.
     */
    public static boolean canAskToJoin(ServerPlayer player, Bands.Record band) {
        var stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        String line = dev.hominin.evolution.stage.Kinds.line(stage);
        return !band.nomadic() && band.haven == null && all(player).size() <= 2
                && !line.equals("ardipithecus") && !line.equals("australopithecus")
                && dev.hominin.evolution.stage.Kinds.line(band.species).equals(line)
                && Relations.standing(player, band) >= Relations.UNFRIENDLY + 1;
    }

    /**
     * They take you in - and for the sake of it, you still lead: everyone of theirs about joins you, their camp
     * becomes your ground, what they keep is yours, what they know is yours. But they do not know you: cohesion
     * starts at 25.
     */
    public static void askToJoin(ServerPlayer player, Bands.Record band) {
        ServerLevel level = player.serverLevel();
        if (!canAskToJoin(player, band)) {
            player.displayClientMessage(Component.literal(BandNames.capital(band.name) + " will not take you in."), true);
            return;
        }
        List<BandMember> theirs = new java.util.ArrayList<>(level.getEntities(ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && band.id.equals(m.getBandId())));
        if (theirs.isEmpty() || Relations.nearestMember(level, band, player, 32.0D) == null) {
            player.displayClientMessage(Component.literal("None of " + band.name + " are near enough to ask."), true);
            return;
        }
        int maxMembers = BandSizes.of(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage()).maxMembers();
        int room = maxMembers - all(player).size();
        List<String> names = new java.util.ArrayList<>();
        for (BandMember member : theirs) {
            if (names.size() >= room) {
                break;
            }
            member.ensureName();
            member.setTarget(null);
            member.joinPlayerBand(player.getUUID());
            names.add(member.getName().getString());
        }
        // What they kept and what they knew come with them.
        List<BlockPos> kept = ToolPiles.piles(level, band.id);
        ToolPiles.orphan(level, band.id);
        for (BlockPos pile : kept) {
            if (level.getBlockEntity(pile) instanceof dev.hominin.evolution.block.ToolPileBlockEntity heap) {
                heap.setOwner(player.getUUID());
            }
            ToolPiles.adopt(level, player.getUUID(), pile);
        }
        dev.hominin.evolution.world.Pois.bandJoined(player, band);
        BlockPos camp = band.home;
        Bands.remove(level, band.id);
        dev.hominin.evolution.hunt.Predation.settle(player, camp);
        Cohesion.startAt(player, JOINED_COHESION);
        player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " take you in. Better one band than two "
                + "halves of nothing - and somebody has to lead it: you. " + String.join(", ", names) + " follow you now, "
                + "and their camp is your ground. They do not know you yet: cohesion " + JOINED_COHESION + ".")
                .withStyle(ChatFormatting.GOLD));
    }

    private static int pendingBirths(List<BandMember> members) {
        return (int) members.stream().filter(BandMember::isPregnant).count();
    }

    public static boolean hasRoomFor(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        List<BandMember> members = all(serverPlayer);
        BandSizes.Size size = BandSizes.of(serverPlayer.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        return members.size() + pendingBirths(members) < size.maxMembers();
    }

    // ------------------------------------------------------------ displays

    public static int rally(ServerPlayer player) {
        List<BandMember> members = companionsNear(player, RALLY_RADIUS);
        int answered = 0;
        for (BandMember member : members) {
            if (!member.isUpATree()) {
                // Staggered, so the band's calls build on the leader's instead of landing as one.
                member.callToDisplay(6 + answered * 5 + member.getRandom().nextInt(4));
                answered++;
            }
        }
        return answered;
    }

    public static float chanceWith(float base, int members) {
        return Math.min(MAX_CHANCE, base + CHANCE_PER_MEMBER * members);
    }

    public static double radiusWith(double base, int members) {
        return base + RADIUS_PER_MEMBER * members;
    }

    /** A member calls, leaps and waves. Only the first few of a chorus are voiced. */
    public static void performDisplay(BandMember member) {
        List<BandMember> chorus = near(member, 6.0D);
        if (chorus.indexOf(member) < MAX_CHORUS) {
            member.level().playSound(null, member.blockPosition(), ModSounds.BAND_PANT_HOOT.get(),
                    SoundSource.NEUTRAL, 1.2F, 0.9F + member.getRandom().nextFloat() * 0.25F);
        }
        member.level().broadcastEntityEvent(member, BandMember.DISPLAY_EVENT);
        member.getNavigation().stop();
        member.swing(InteractionHand.MAIN_HAND);
        if (member.onGround()) {
            member.jumpFromGround();
        }
    }

    /** A member's own display when something goes for it; the band nearby joins in. */
    public static void memberDisplay(BandMember member, int delay) {
        member.callToDisplay(delay);
        int joined = 0;
        for (BandMember other : near(member, RALLY_RADIUS)) {
            if (other != member && other.isAlliedTo(member) && !other.isUpATree() && !other.isBaby()) {
                other.callToDisplay(delay + 5 + joined * 5);
                joined++;
            }
        }
        EvolutionEventHandler.startleNearby(member, radiusWith(MEMBER_DISPLAY_RADIUS, joined),
                chanceWith(MEMBER_DISPLAY_CHANCE, joined), true);
    }

    // ------------------------------------------------------------ fighting together

    /** Whatever hurts the leader, the band goes for. */
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer hurt) {
            dev.hominin.evolution.combat.Bleeding.maybeInfect(hurt, "It opens again.");
        }
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker != player
                && !(attacker instanceof BandMember member && member.isCompanionOf(player))) {
            // A real attack ends any play-fighting at once.
            wrestleWindow.remove(player.getUUID());
            for (BandMember member : companionsNear(player, DEFEND_RADIUS)) {
                member.stopWrestling();
            }
            Social.onLeaderHit(player);
            defend(player, attacker);
            if (Cohesion.perfect(player)) {
                // A band that would do anything for you does not wait to be asked.
                for (BandMember member : ownNear(player, 32.0D)) {
                    if (!member.isBaby()) {
                        member.defendAgainst(attacker);
                    }
                }
            }
            if (dev.hominin.evolution.hunt.PredatorAppetite.isPredator(attacker)) {
                playerAdrenaline(player);
                Mating.onPlayerStruck(player);
            }
        }
    }

    /** Once in five minutes, like the band's own. */
    private static final long PLAYER_ADRENALINE_COOLDOWN = 6000L;
    private static final Map<UUID, Long> playerAdrenalineReady = new HashMap<>();

    /**
     * What all that play was for. With nothing practised the body gives you nothing
     * extra; every round of chase is a little more speed when it counts, and every round
     * of wrestling a little more strength.
     */
    private static void playerAdrenaline(ServerPlayer player) {
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int tag = Math.min(BandMember.MAX_TRAINING, counters.getOrDefault("play_tag", 0));
        int wrestle = Math.min(BandMember.MAX_TRAINING, counters.getOrDefault("play_wrestle", 0));
        long now = player.level().getGameTime();
        if ((tag == 0 && wrestle == 0) || now < playerAdrenalineReady.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        playerAdrenalineReady.put(player.getUUID(), now + PLAYER_ADRENALINE_COOLDOWN);
        if (tag > 0) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 40 + tag * 20, 0));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 20 + tag * 10, 1));
        }
        if (wrestle > 0) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, wrestle * 100, 0));
        }
        player.displayClientMessage(Component.literal("Your heart hammers. Your body knows what to do.")
                .withStyle(ChatFormatting.GOLD), true);
    }

    /**
     * Something has turned to leave. Every reason to hit it went with it: a band that
     * chases a wounded animal into cover is a band that comes back smaller. So anyone
     * still holding it as a target lets go.
     */
    public static void standDown(LivingEntity leaving) {
        for (BandMember member : near(leaving, 32.0D)) {
            if (member.getTarget() == leaving) {
                member.setTarget(null);
                member.getNavigation().stop();
            }
        }
    }

    /**
     * Joining in on whatever the leader attacks - unless told not to hunt with them, and
     * never against another hominin. A stray swing at somebody else's band used to start a
     * war between two groups of people who have no reason to fight.
     */
    public static void assist(ServerPlayer player, LivingEntity target) {
        if (target instanceof BandMember) {
            return;
        }
        // A predator going for the band is everyone's business. Anything else you swing at
        // gets the one or two nearest - unless you have called a hunt, when they all come.
        List<BandMember> willing = new java.util.ArrayList<>(defendersOf(player).stream()
                .filter(m -> m.huntsWithLeader() && !m.isBaby()).toList());
        willing.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player)));
        int helpers = 0;
        int limit = 1 + player.getRandom().nextInt(2) + Cohesion.extraHelpers(player);
        willing.removeIf(BandMember::isInjured);
        for (BandMember member : willing) {
            // A good hunter who likes you comes along whenever you go after something.
            boolean keen = member.getBond() >= 5 && member.getHuntLevel() <= 2;
            if (member.isHunting() || helpers < limit || keen) {
                member.defendAgainst(target);
                helpers++;
            }
        }
    }

    // ------------------------------------------------------------ doing things on their own

    private static final int EXCURSION_CHECK_TICKS = 1200;
    private static final float EXCURSION_CHANCE = 0.3F;
    private static final int ANNOUNCE_MEMBER_COOLDOWN = 1200;
    private static final int ANNOUNCE_LEADER_COOLDOWN = 300;
    private static final Map<UUID, Long> lastMemberAnnouncement = new HashMap<>();
    private static final Map<UUID, Long> lastLeaderAnnouncement = new HashMap<>();

    /**
     * Tells the leader what a member is up to, so the band feels like it has a life of
     * its own. Rate-limited per member and per leader, so it never becomes chat spam.
     */
    public static boolean announce(BandMember member, String rest) {
        Player leader = member.leaderPlayer();
        if (leader == null || member.distanceToSqr(leader) > 48.0D * 48.0D) {
            return false;
        }
        long now = member.level().getGameTime();
        if (now - lastMemberAnnouncement.getOrDefault(member.getUUID(), -99999L) < ANNOUNCE_MEMBER_COOLDOWN
                || now - lastLeaderAnnouncement.getOrDefault(leader.getUUID(), -99999L) < ANNOUNCE_LEADER_COOLDOWN) {
            return false;
        }
        lastMemberAnnouncement.put(member.getUUID(), now);
        lastLeaderAnnouncement.put(leader.getUUID(), now);
        member.ensureName();
        leader.sendSystemMessage(Component.literal(member.getName().getString() + rest).withStyle(ChatFormatting.GRAY));
        return true;
    }

    /** Something worth hearing about, whatever else was said lately. */
    public static void announceDiscovery(BandMember member, String rest) {
        Player leader = member.leaderPlayer();
        if (leader == null || member.distanceToSqr(leader) > 64.0D * 64.0D) {
            return;
        }
        member.ensureName();
        leader.sendSystemMessage(Component.literal(member.getName().getString() + rest).withStyle(ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------ helping with the leader's tasks

    /** Chance that something a member does counts toward one of the leader's repeated tasks. */
    private static final float CONTRIBUTION_CHANCE = 0.4F;

    /**
     * A member did something one of the leader's tasks asks for. Tasks done more than once
     * can be helped along; a one-off task is always the player's own to do.
     */
    public static void contribute(BandMember member, String criterionId) {
        if (!(member.leaderPlayer() instanceof ServerPlayer player)
                || member.getRandom().nextFloat() >= CONTRIBUTION_CHANCE) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        var stage = dev.hominin.evolution.stage.StageRegistry.current(data);
        if (stage == null) {
            return;
        }
        var criterion = java.util.stream.Stream.concat(stage.gate().required().stream(), stage.gate().optionalPool().stream())
                .filter(c -> c.id().equals(criterionId))
                .findFirst().orElse(null);
        if (criterion == null || criterion.requiredCount() <= 1 || EvolutionManager.isCriterionSatisfied(data, criterion)) {
            return;
        }
        EvolutionManager.incrementCriterion(player, criterionId, 1);
        member.ensureName();
        player.sendSystemMessage(Component.literal(member.getName().getString() + " helped: "
                + criterion.description() + " +1").withStyle(ChatFormatting.DARK_GREEN));
    }

    // ------------------------------------------------------------ rare discoveries

    private static final Map<UUID, Long> lastLomekwianDay = new HashMap<>();

    /** A Lomekwian core only after the first day, and one a day at most per band. */
    public static boolean mayMakeLomekwian(BandMember member) {
        long day = member.level().getDayTime() / 24000L;
        if (day < 1) {
            return false;
        }
        UUID band = member.getLeader() != null ? member.getLeader() : member.getBandId();
        return band == null || lastLomekwianDay.getOrDefault(band, -1L) < day;
    }

    public static void lomekwianMade(BandMember member) {
        UUID band = member.getLeader() != null ? member.getLeader() : member.getBandId();
        if (band != null) {
            lastLomekwianDay.put(band, member.level().getDayTime() / 24000L);
        }
    }

    // ------------------------------------------------------------ alloparenting

    private static final String STABILITY_LOSS = EvolutionManager.SKILL_PREFIX + "band_stability_loss";
    private static final int CHILD_DEFENCE_TICKS = 20 * 20;

    /** Every child in the band has an adult minding it: the nearest one not already minding another. */
    private static void assignCaretakers(ServerPlayer player) {
        List<BandMember> members = all(player);
        ServerLevel level = player.serverLevel();
        for (BandMember child : members) {
            if (!child.isBaby()) {
                continue;
            }
            UUID current = child.getCaretaker();
            if (current != null && level.getEntity(current) instanceof BandMember minder && minder.isAlive()
                    && child.getUUID().equals(minder.getWard())) {
                continue;
            }
            BandMember best = null;
            for (BandMember adult : members) {
                if (adult.isBaby() || adult.getWard() != null || adult.isOnExcursion()) {
                    continue;
                }
                if (best == null || adult.distanceToSqr(child) < best.distanceToSqr(child)) {
                    best = adult;
                }
            }
            if (best != null) {
                best.mind(child);
            }
        }
        for (BandMember adult : members) {
            UUID ward = adult.getWard();
            if (ward != null && !(level.getEntity(ward) instanceof BandMember child && child.isAlive() && child.isBaby())) {
                adult.stopMinding();
            }
        }
    }

    /** A child attacked: every adult of its band nearby comes running, fired up, and the child is marked out. */
    public static void childInDanger(BandMember child, LivingEntity attacker) {
        child.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.GLOWING, 30 * 20, 0, false, false));
        boolean parties = splitsUp(child.getStage());
        for (BandMember adult : near(child, DEFEND_RADIUS)) {
            if (adult == child || adult.isBaby() || !adult.isAlliedTo(child)) {
                continue;
            }
            if (parties && adult.getParty() != child.getParty() && adult.distanceToSqr(child) >= CLOSE_BY * CLOSE_BY) {
                continue;
            }
            adult.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, CHILD_DEFENCE_TICKS, 0));
            adult.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, CHILD_DEFENCE_TICKS, 0));
            adult.defendAgainst(attacker);
        }
        Player leader = child.leaderPlayer();
        if (leader != null) {
            child.ensureName();
            leader.displayClientMessage(Component.literal(child.getName().getString()
                    + " cries out - the band rushes to protect the child!").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Losing a child shakes the band. Group stability does nothing yet; it will from erectus on. */
    public static void onChildDied(BandMember child) {
        if (!(child.leaderPlayer() instanceof ServerPlayer player)) {
            return;
        }
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().merge(STABILITY_LOSS, 1, Integer::sum);
        player.sendSystemMessage(Component.literal("The band grieves for " + child.getName().getString()
                + ". (Group stability has fallen.)").withStyle(ChatFormatting.DARK_RED));
    }

    /** Now and then, in daylight, one member of the band goes off exploring on its own. */
    private static void maybeSendExploring(ServerPlayer player) {
        if (!player.level().isDay() || player.getRandom().nextFloat() >= EXCURSION_CHANCE) {
            return;
        }
        List<BandMember> members = all(player);
        if (members.stream().anyMatch(BandMember::isOnExcursion)) {
            return;
        }
        List<BandMember> ready = members.stream()
                .filter(m -> !m.isBaby() && !m.isHungry() && !m.isInjured() && m.getTarget() == null && !m.isUpATree()
                        && m.distanceToSqr(player) < 32.0D * 32.0D)
                .toList();
        if (ready.isEmpty()) {
            return;
        }
        BandMember explorer = ready.get(player.getRandom().nextInt(ready.size()));
        ServerLevel level = player.serverLevel();
        for (int attempt = 0; attempt < 8; attempt++) {
            float angle = player.getRandom().nextFloat() * Mth.TWO_PI;
            int distance = 30 + player.getRandom().nextInt(25);
            int x = player.getBlockX() + Math.round(Mth.cos(angle) * distance);
            int z = player.getBlockZ() + Math.round(Mth.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos target = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getFluidState(target.below()).isEmpty()) {
                continue;
            }
            explorer.startExcursion(target, 1200 + player.getRandom().nextInt(1200));
            explorer.ensureName();
            player.sendSystemMessage(Component.literal(explorer.getName().getString()
                    + " wanders off to explore.").withStyle(ChatFormatting.GRAY));
            return;
        }
    }

    /** An explorer comes home - brought straight back to the leader, sometimes carrying a find. */
    public static void returnFromExcursion(BandMember member, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (member.level() == level) {
            BlockPos pos = standingSpotNear(level, player.blockPosition(), member.getRandom().nextInt(3) + 2,
                    member.getRandom().nextFloat() * Mth.TWO_PI);
            member.getNavigation().stop();
            member.setClimbingTree(false);
            member.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        }
        ItemStack find = ItemStack.EMPTY;
        float roll = member.getRandom().nextFloat();
        if (roll < 0.15F) {
            find = new ItemStack(dev.hominin.evolution.ModItems.LONG_BRANCH.get());
        } else if (roll < 0.35F) {
            find = new ItemStack(dev.hominin.evolution.ModItems.GRUB.get(), 2);
        } else if (roll < 0.5F) {
            find = new ItemStack(dev.hominin.evolution.ModItems.NESTING_MATERIAL.get(), 2);
        } else if (roll < 0.6F) {
            find = new ItemStack(member.getRandom().nextBoolean() ? dev.hominin.evolution.ModItems.GRANITE_ROCK.get()
                    : dev.hominin.evolution.ModItems.CHERT_ROCK.get());
        }
        String name = member.getName().getString();
        if (find.isEmpty()) {
            player.sendSystemMessage(Component.literal(name + " comes back.").withStyle(ChatFormatting.GRAY));
            return;
        }
        String found = find.getHoverName().getString();
        member.addToInventory(find);
        player.sendSystemMessage(Component.literal(name + " comes back carrying " + found + ".")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Blows from your own hand. With anything in it - a flake, a spear, a rock - they are
     * pulled at the last moment and do no harm, so hunting alongside the band is safe. A
     * bare-handed cuff still lands, but they shake it off fast.
     */
    public static void onMemberHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof BandMember laidUp && laidUp.isInjured() && !laidUp.level().isClientSide()
                && event.getSource().getEntity() instanceof LivingEntity threat && !(threat instanceof Player)
                && !(threat instanceof BandMember)) {
            // Someone laid up is everyone's to protect.
            for (BandMember guard : near(laidUp, 24.0D)) {
                if (guard != laidUp && !guard.isBaby() && !guard.isInjured() && guard.isAlliedTo(laidUp)) {
                    guard.defendAgainst(threat);
                }
            }
        }
        if (event.getEntity() instanceof BandMember struck && !struck.level().isClientSide()
                && event.getSource().getEntity() instanceof LivingEntity by
                && dev.hominin.evolution.hunt.PredatorAppetite.isPredator(by)) {
            Mating.onMateStruck(struck);
        }
        if (!(event.getEntity() instanceof BandMember member)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !member.isCompanionOf(player)) {
            return;
        }
        boolean bareHanded = event.getSource().getDirectEntity() == player && player.getMainHandItem().isEmpty();
        if (!bareHanded) {
            event.setCanceled(true);
            return;
        }
        member.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.REGENERATION, 100, 2, false, false));
    }

    public static void defend(ServerPlayer player, LivingEntity attacker) {
        for (BandMember member : defendersOf(player)) {
            member.defendAgainst(attacker);
        }
    }

    // ------------------------------------------------------------ fission-fusion

    /** Close enough that anyone in the band comes, whatever party they are in. */
    private static final double CLOSE_BY = 10.0D;
    private static final int PARTY_CHECK_TICKS = 400;
    private static final Map<UUID, Long> lastSplitDay = new HashMap<>();

    /**
     * Australopithecus bands are small enough to move as one. From habilis on, a band
     * splits into parties by day - fission-fusion, as chimpanzees and every human society
     * since still do - and comes back together at night.
     */
    public static boolean splitsUp(ResourceLocation stage) {
        String path = dev.hominin.evolution.stage.Kinds.line(stage);
        return !path.equals("ardipithecus") && !path.equals("australopithecus");
    }

    /**
     * Who comes when the leader is in trouble. In a band that splits up, only the party
     * with the leader and anyone else close by; guests travelling along always come.
     */
    public static List<BandMember> defendersOf(ServerPlayer player) {
        List<BandMember> near = companionsNear(player, DEFEND_RADIUS);
        if (!splitsUp(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            return near;
        }
        return near.stream()
                .filter(m -> !m.isLedBy(player) || m.getParty() == 0 || m.distanceToSqr(player) < CLOSE_BY * CLOSE_BY)
                .toList();
    }

    /** Someone froze in fear, and the band has noticed: its party, and anyone close, come running. */
    public static void rushToDefend(BandMember frozen, LivingEntity threat) {
        boolean parties = splitsUp(frozen.getStage());
        int came = 0;
        for (BandMember other : near(frozen, DEFEND_RADIUS)) {
            if (other == frozen || other.isBaby() || !other.isAlliedTo(frozen) || other.isFrozen()) {
                continue;
            }
            if (parties && other.getParty() != frozen.getParty() && other.distanceToSqr(frozen) >= CLOSE_BY * CLOSE_BY) {
                continue;
            }
            other.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, CHILD_DEFENCE_TICKS, 0));
            other.defendAgainst(threat);
            came++;
        }
        if (came > 0) {
            announceDiscovery(frozen, came == 1 ? " is frozen stiff - someone rushes in to help!"
                    : " is frozen stiff - the band rushes in to defend them!");
        }
    }

    /** A predator has picked out one of the band: the body gets ready before the blow lands. */
    public static void onMemberThreatened(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.isCanceled() || !(event.getNewAboutToBeSetTarget() instanceof BandMember member)
                || !(event.getEntity() instanceof net.minecraft.world.entity.Mob mob) || mob instanceof BandMember
                || member.level().isClientSide()) {
            return;
        }
        boolean threat = mob.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS)
                || mob instanceof net.minecraft.world.entity.monster.Enemy;
        if (threat && mob.distanceToSqr(member) < 12.0D * 12.0D) {
            member.adrenaline(mob);
            member.raiseAlarm(100);
        }
    }

    /**
     * Splits the band into parties for the day, and brings it back together at night. The
     * ones nearest the leader stay with them; the rest go off in parties of four or five,
     * each following one of its own, never far out of reach.
     */
    private static void organiseParties(ServerPlayer player) {
        List<BandMember> members = all(player);
        boolean split = members.stream().anyMatch(m -> m.getParty() > 0);
        boolean canSplit = splitsUp(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())
                && player.level().isDay();
        List<BandMember> adults = members.stream().filter(m -> !m.isBaby()).toList();
        // Seven adults was more than a habilis band ever actually fields, so the split
        // never fired for anybody. Five is a band with enough people to be in two places.
        if (!canSplit || adults.size() <= 4) {
            if (split) {
                members.forEach(m -> m.joinParty(0, null));
                if (!player.level().isDay()) {
                    player.sendSystemMessage(Component.literal("The parties drift back in, and the band is whole for the night.")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
            return;
        }
        long day = player.level().getDayTime() / 24000L;
        if (split || lastSplitDay.getOrDefault(player.getUUID(), -1L) == day) {
            return;
        }
        lastSplitDay.put(player.getUUID(), day);
        List<BandMember> byDistance = new java.util.ArrayList<>(adults);
        byDistance.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player)));
        int withLeader = 3 + player.getRandom().nextInt(3);
        int party = 0;
        int inParty = 0;
        UUID head = null;
        int partySize = withLeader;
        for (BandMember member : byDistance) {
            if (inParty >= partySize) {
                party++;
                inParty = 0;
                head = member.getUUID();
                partySize = 4 + player.getRandom().nextInt(2);
            }
            member.joinParty(party, head);
            inParty++;
        }
        // Children go with whoever is minding them.
        for (BandMember child : members) {
            if (!child.isBaby()) {
                continue;
            }
            int childParty = 0;
            UUID childHead = null;
            if (child.getCaretaker() != null && player.serverLevel().getEntity(child.getCaretaker()) instanceof BandMember minder) {
                childParty = minder.getParty();
                childHead = byDistance.stream().filter(m -> m.getParty() == minder.getParty()).findFirst()
                        .map(BandMember::getUUID).orElse(null);
            }
            child.joinParty(childParty, childHead);
        }
        if (party > 0) {
            player.sendSystemMessage(Component.literal("The band splits up for the day: " + withLeader
                    + " stay with you, the rest go off in " + party + (party == 1 ? " party." : " parties.")
                    + " Only those near you will come if you are attacked.").withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Hitting one of your own band while sneaking is wrestling, not fighting. Hitting
     * anything else calls the band in - and whatever you have angered now has all of
     * you to deal with.
     */
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        if (target instanceof BandMember member && member.isLedBy(player)) {
            long now = player.level().getGameTime();
            boolean inBout = wrestleWindow.getOrDefault(player.getUUID(), 0L) > now;
            if (player.isShiftKeyDown() || inBout) {
                // Once a bout has started, ordinary blows are part of it too, for a while.
                event.setCanceled(true);
                wrestleWindow.put(player.getUUID(), now + BandMember.WRESTLE_TICKS);
                member.wrestle(player);
                Cohesion.addLimited(player, "wrestle", 1, 5 * 60 * 20L);
            }
            return;
        }
        if (!(target instanceof Player) && !(target instanceof BandMember)) {
            assist(player, target);
        }
    }

    // ------------------------------------------------------------ feeding together

    /** The leader has started foraging: the hungry ones come and forage alongside. */
    public static void leaderForaging(ServerPlayer player, BlockPos where) {
        for (BandMember member : ownNear(player, 32.0D)) {
            if (member.getHunger() < BandMember.HUNGRY) {
                member.forageAlongside(where);
            }
        }
    }

    // ------------------------------------------------------------ families

    /** A member was just fed. If one of the opposite sex nearby was too, they pair. */
    public static void tryPair(BandMember fed) {
        Player companion = fed.companionPlayer();
        if (!(companion instanceof ServerPlayer player)) {
            return;
        }
        for (BandMember other : near(fed, PAIR_RADIUS)) {
            if (other == fed || other.isFemale() == fed.isFemale() || !other.isReadyToPair()
                    || !other.isCompanionOf(player)) {
                continue;
            }
            // From habilis on, a pair is a pair: only mates, or two with nobody yet.
            if (Mating.pairBonds(fed.getStage())) {
                boolean mates = other.getUUID().equals(fed.getMate());
                boolean bothFree = fed.getMate() == null && other.getMate() == null;
                if (!mates && !bothFree) {
                    continue;
                }
                if (bothFree) {
                    fed.setMate(other.getUUID());
                    other.setMate(fed.getUUID());
                }
            }
            if (!hasRoomFor(player)) {
                player.displayClientMessage(Component.literal("Your band is as big as the land can feed."), true);
                fed.clearReady();
                other.clearReady();
                return;
            }
            fed.conceive();
            other.conceive();
            ServerLevel level = player.serverLevel();
            for (BandMember partner : List.of(fed, other)) {
                level.sendParticles(ParticleTypes.HEART, partner.getX(), partner.getEyeY() + 0.4D, partner.getZ(),
                        6, 0.4D, 0.3D, 0.4D, 0.0D);
            }
            player.displayClientMessage(Component.literal(fed.getName().getString() + " and "
                    + other.getName().getString() + " keep close together."), true);
            return;
        }
    }

    public static void giveBirth(BandMember mother) {
        if (!(mother.level() instanceof ServerLevel level)) {
            return;
        }
        BandMember baby = ModEntities.BAND_MEMBER.get().create(level);
        if (baby == null) {
            return;
        }
        baby.moveTo(mother.getX(), mother.getY(), mother.getZ(), mother.getYRot(), 0.0F);
        baby.finalizeSpawn(level, level.getCurrentDifficultyAt(mother.blockPosition()), MobSpawnType.BREEDING, null);
        baby.setStage(mother.getStage());
        baby.setLeader(mother.getLeader());
        baby.setMother(mother.getUUID());
        if (mother.leaderPlayer() instanceof ServerPlayer leader) {
            SacredPile.event(leader, "a birth in the band");
            Chatter.news(leader, "news_birth", "");
        }
        Player companion = mother.companionPlayer();
        if (mother.isGuest() && companion != null && hasRoomFor(companion)) {
            // Born while the bands were together: the child stays with the player's band.
            baby.setLeader(companion.getUUID());
        }
        if (mother.getBandId() != null && baby.getLeader() == null) {
            baby.joinWildBand(mother.getBandId(), mother.isWild() ? mother.getUUID() : null);
        }
        baby.makeBaby();
        baby.ensureName();
        level.addFreshEntity(baby);
        level.sendParticles(ParticleTypes.HEART, mother.getX(), mother.getEyeY(), mother.getZ(), 8, 0.5D, 0.4D, 0.5D, 0.0D);
        Player leader = mother.leaderPlayer();
        if (leader != null) {
            leader.sendSystemMessage(Component.literal(mother.getName().getString() + " has given birth to "
                    + baby.getName().getString() + ".").withStyle(ChatFormatting.GREEN));
        }
    }

    // ------------------------------------------------------------ keeping the band

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.getUnlockedRecipes().contains(BAND_FORMED)) {
            return;
        }
        if (Newcomers.ask(player)) {
            // Others already walk this world: the newcomer chooses first - one of their bands, or their own far off.
            return;
        }
        formFirstBand(player);
    }

    /** A player who walks with somebody else's band: never given one of their own. */
    static void markFormed(ServerPlayer player) {
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getUnlockedRecipes().add(BAND_FORMED);
    }

    /** The first time in: the player wakes among their band. */
    public static void formFirstBand(ServerPlayer player) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (!data.getUnlockedRecipes().add(BAND_FORMED)) {
            return;
        }
        if (player.serverLevel().getGameRules().getBoolean(dev.hominin.evolution.ModGameRules.SUPER_HARD_MODE)) {
            data.setStage(ARDIPITHECUS);
            dev.hominin.evolution.stage.StageSync.sync(player);
        }
        topUp(player, data.getStage());
        Cohesion.reset(player);
        dev.hominin.evolution.hunt.Predation.settle(player, player.blockPosition());
        player.sendSystemMessage(Component.literal("You are not alone out here. Your band is with you."));
        Relations.ownBandFormed(player);
        dev.hominin.evolution.world.Pois.newBandKnows(player);
    }

    /** Brings the band up to at least the size this stage starts at. */
    private static void topUp(ServerPlayer player, ResourceLocation stage) {
        int missing = BandSizes.of(stage).startingMembers() - all(player).size();
        for (int i = 0; i < missing; i++) {
            spawnMember(player);
        }
    }

    @Nullable
    public static BandMember spawnMember(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BandMember member = ModEntities.BAND_MEMBER.get().create(level);
        if (member == null) {
            return null;
        }
        BlockPos pos = standingSpotNear(level, player.blockPosition(), player.getRandom().nextInt(4) + 2,
                player.getRandom().nextFloat() * Mth.TWO_PI);
        member.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getRandom().nextFloat() * 360.0F, 0.0F);
        member.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
        member.setLeader(player.getUUID());
        member.setStage(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        member.ensureName();
        level.addFreshEntity(member);
        // A band exists from the moment it is spawned: losing it within the first few
        // seconds has to count, so the net does not have to notice it first.
        hadBand.add(player.getUUID());
        return member;
    }

    public static BlockPos standingSpotNear(ServerLevel level, BlockPos center, int distance, float angle) {
        BlockPos first = spotAt(level, center, distance, angle);
        if (first != null && dry(level, first)) {
            return first;
        }
        // Water there (the heightmap counts a river's surface as ground): look round about, a little further out
        // each time, for somewhere to stand.
        for (int attempt = 1; attempt <= 24; attempt++) {
            BlockPos pos = spotAt(level, center, distance + attempt, angle + attempt * 2.4F);
            if (pos != null && dry(level, pos)) {
                return pos;
            }
        }
        // Not onto a cliff top far above or below; beside whoever it was near instead.
        return first != null && !dry(level, center) ? first : center;
    }

    @javax.annotation.Nullable
    private static BlockPos spotAt(ServerLevel level, BlockPos center, int distance, float angle) {
        int x = center.getX() + Math.round(Mth.cos(angle) * distance);
        int z = center.getZ() + Math.round(Mth.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.abs(y - center.getY()) > 4 ? null : new BlockPos(x, y, z);
    }

    /** Somewhere to stand: no water here or underfoot, and ground under it. */
    public static boolean dry(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).isEmpty() && level.getFluidState(pos.below()).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    /**
     * The old band belongs to the past. Evolving leaves it behind, and the player wakes
     * among a new band of their new species, at the size that species lives in.
     */
    public static void evolveWith(ServerPlayer player, ResourceLocation stage) {
        List<BandMember> old = all(player);
        for (BandMember member : old) {
            member.discard();
        }
        if (!old.isEmpty()) {
            player.sendSystemMessage(Component.literal("Your old band stays behind, in the deep past.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** A fresh band of the player's current species, beside them. */
    public static void formNewBand(ServerPlayer player) {
        topUp(player, player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        // New people: no history with you, good or bad.
        Cohesion.reset(player);
        Needs.forget(player.getUUID());
        dev.hominin.evolution.hunt.Predation.settle(player, player.blockPosition());
        player.sendSystemMessage(Component.literal("You wake among a new band.").withStyle(ChatFormatting.GREEN));
        Relations.ownBandFormed(player);
        dev.hominin.evolution.world.Pois.newBandKnows(player);
        Remembrance.carryInto(player);
        dev.hominin.evolution.hunt.Persistence.tellStartingSkills(player);
    }

    public static void bringAlong(ServerPlayer player, BlockPos from) {
        ServerLevel level = player.serverLevel();
        AABB camp = new AABB(from).inflate(64.0D);
        for (BandMember member : level.getEntitiesOfClass(BandMember.class, camp, member -> member.isLedBy(player))) {
            BlockPos pos = standingSpotNear(level, player.blockPosition(), member.getRandom().nextInt(3) + 2,
                    member.getRandom().nextFloat() * Mth.TWO_PI);
            member.getNavigation().stop();
            member.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        }
    }

    // ------------------------------------------------------------ living on

    /** On death, the grown member nearest the body is chosen to carry on as the player. */
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        dev.hominin.evolution.survival.TorchLight.douse(player);
        // A catastrophic wound, and lacerations, are not left behind with the body.
        dev.hominin.evolution.combat.Bleeding.carryOver(player);
        BandMember heir = null;
        double best = Double.MAX_VALUE;
        List<BandMember> band = all(player);
        for (BandMember member : band) {
            if (member.isBaby()) {
                continue;
            }
            double distance = member.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                heir = member;
            }
        }
        if (heir != null) {
            heir.ensureName();
            heirs.put(player.getUUID(), new Heir(player.level().dimension(), heir.getUUID(), heir.getName().getString(),
                    heir.isFemale(), heir.getHunger(), heir.position(), heir.getYRot()));
        } else {
            heirs.remove(player.getUUID());
            // Nobody grown left to carry on as: only little ones, and they cannot keep a band going on their own. Left
            // led, a child would hold the band open forever and you would wake as yourself again and again - so they
            // are lost with you, and the band is lost. The check that runs once you are alive again counts it.
            if (!band.isEmpty() && !event.isCanceled()) {
                for (BandMember child : band) {
                    child.discard();
                }
                hadBand.add(player.getUUID());
                player.sendSystemMessage(Component.literal("There is nobody grown left to carry on. The little ones "
                        + "will not last alone.").withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    /**
     * The player opens their eyes as the heir: in its place, carrying what it carried,
     * as hungry as it was. The heir itself is gone - the player is it now.
     */
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        dev.hominin.evolution.combat.Bleeding.resume(player);
        Heir heir = heirs.remove(player.getUUID());
        if (heir == null) {
            Newcomers.respawned(player);
            return;
        }
        ServerLevel level = player.server.getLevel(heir.dimension());
        if (level == null) {
            return;
        }
        BandMember member = level.getEntity(heir.member()) instanceof BandMember loaded && loaded.isAlive() ? loaded : null;
        String name = heir.name();
        player.getData(Attachments.MIND).setBodyName(name);
        dev.hominin.evolution.mind.Journal.setFemale(player, heir.female());
        net.minecraft.world.phys.Vec3 at = member != null ? member.position() : heir.pos();
        player.teleportTo(level, at.x, at.y, at.z, member != null ? member.getYRot() : heir.yRot(), 0.0F);
        player.getFoodData().setFoodLevel(Math.max(6, member != null ? member.getHunger() : heir.hunger()));
        if (member != null) {
            absorb(player, member);
        } else {
            // Far from where you respawned: their body is not loaded yet. It will be in a moment, now you are here.
            absorbing.put(player.getUUID(), new Absorb(heir.member(), level.getGameTime() + 200L));
        }
        PacketDistributor.sendToPlayer(player, new RebirthPayload(name));
        // The eyes are shut for a few seconds: nothing else plays over it, and nothing hurts you. If the one
        // you became was the last of the band, the panic comes once you have opened your eyes.
        dev.hominin.evolution.stage.CutsceneGuard.protect(player, REBIRTH_TICKS);
        player.sendSystemMessage(Component.literal("You carry on as " + name + ".").withStyle(ChatFormatting.GRAY));
    }

    /** What the heir carried is yours now, and the heir is you. */
    private static void absorb(ServerPlayer player, BandMember member) {
        for (ItemStack stack : member.takeEverything()) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        member.discard();
    }

    /** Every tick: an heir whose body has just loaded in hands over what it carried. */
    public static void tickAbsorb(ServerPlayer player) {
        Absorb pending = absorbing.get(player.getUUID());
        if (pending == null || player.tickCount % 5 != 0) {
            return;
        }
        if (player.serverLevel().getEntity(pending.member()) instanceof BandMember member && member.isAlive()) {
            absorbing.remove(player.getUUID());
            absorb(player, member);
        } else if (player.level().getGameTime() > pending.until()) {
            absorbing.remove(player.getUUID());
        }
    }

    /** The bond a member needs with you before you can live as them for a while. */
    public static final int SWAP_BOND = 3;
    private static final Map<UUID, Long> lastSwap = new HashMap<>();
    private static final long SWAP_GAP_TICKS = 2400L;

    /**
     * Living as somebody else for a while. Only somebody close to you (bond 3 and up) will let you in that
     * far. You open your eyes where they stood, as hurt and as hungry as they were, holding in mind what
     * they held; and the one you were carries on where you stood, with your name, your hurts and your
     * places in mind. What you carry stays with you.
     */
    public static void swapInto(ServerPlayer player, BandMember member) {
        if (!member.isLedBy(player) || member.isBaby() || !member.isAlive()) {
            player.displayClientMessage(Component.literal("Only one of your own band's grown people."), true);
            return;
        }
        if (member.getBond() < SWAP_BOND) {
            member.ensureName();
            player.displayClientMessage(Component.literal(member.getName().getString() + " is not close enough to you "
                    + "for that. (Bond " + member.getBond() + " - it takes " + SWAP_BOND + ".)"), true);
            return;
        }
        if (member.isMateOf(player.getUUID())) {
            player.displayClientMessage(Component.literal("Not your own mate."), true);
            return;
        }
        if (member.isPregnant() || member.getTarget() != null || member.inDanger()
                || dev.hominin.evolution.stage.CutsceneGuard.isProtected(player)) {
            player.displayClientMessage(Component.literal("Not now."), true);
            return;
        }
        long now = player.level().getGameTime();
        if (now - lastSwap.getOrDefault(player.getUUID(), -99999L) < SWAP_GAP_TICKS) {
            player.displayClientMessage(Component.literal("You have only just settled into this body. ("
                    + (SWAP_GAP_TICKS - (now - lastSwap.get(player.getUUID()))) / 20 + "s)"), true);
            return;
        }
        lastSwap.put(player.getUUID(), now);
        var mind = player.getData(Attachments.MIND);
        member.ensureName();
        String name = member.getName().getString();
        // Who you were, before the bodies change hands.
        String oldName = mind.bodyName();
        boolean oldFemale = Mating.isFemale(player);
        float oldHealth = player.getHealth() / player.getMaxHealth();
        int oldHunger = player.getFoodData().getFoodLevel();
        List<dev.hominin.evolution.mind.MindData.Memory> oldMemories = new java.util.ArrayList<>(mind.memories());
        int oldSlots = mind.slots();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        // You, into them.
        float theirHealth = member.getHealth() / member.getMaxHealth();
        List<dev.hominin.evolution.mind.MindData.Memory> theirMemories = new java.util.ArrayList<>(member.memories());
        int theirSlots = member.memorySlots();
        player.teleportTo(player.serverLevel(), member.getX(), member.getY(), member.getZ(), member.getYRot(), 0.0F);
        player.setHealth(Math.max(1.0F, player.getMaxHealth() * theirHealth));
        player.getFoodData().setFoodLevel(Math.max(4, member.getHunger()));
        dev.hominin.evolution.mind.Journal.setFemale(player, member.isFemale());
        mind.setBodyName(name);
        mind.memories().clear();
        mind.memories().addAll(theirMemories);
        mind.setSlots(theirSlots);
        // Them, into you.
        member.teleportTo(x, y, z);
        member.setYRot(yaw);
        member.getNavigation().stop();
        member.takeOverBody(oldName, oldFemale, oldHealth, oldHunger, oldMemories, oldSlots);
        member.ensureName();
        PacketDistributor.sendToPlayer(player, new RebirthPayload(name));
        dev.hominin.evolution.stage.CutsceneGuard.protect(player, REBIRTH_TICKS);
        player.sendSystemMessage(Component.literal("You live as " + name + " for a while. The one you were - "
                + member.getName().getString() + " - carries on where you stood.").withStyle(ChatFormatting.GRAY));
        dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/just_another_me");
        dev.hominin.evolution.mind.MentalMap.sync(player);
    }

    /** The bond a member needs before they will stay up all night for you. */
    public static final int WATCH_BOND = 4;

    /** "Keep watch with me tonight": someone close to you stays up and walks the camp till dawn. */
    public static void keepWatch(ServerPlayer player, BandMember member) {
        member.ensureName();
        if (!member.isLedBy(player) || member.isBaby()) {
            return;
        }
        if (member.getBond() < WATCH_BOND) {
            player.displayClientMessage(Component.literal(member.getName().getString() + " would rather sleep. (Bond "
                    + member.getBond() + " - it takes " + WATCH_BOND + ".)"), true);
            return;
        }
        if (member.isInjured()) {
            player.displayClientMessage(Component.literal(member.getName().getString() + " is laid up - they need the sleep."),
                    true);
            return;
        }
        long dayTime = player.level().getDayTime();
        long dawn = (dayTime / 24000L + (dayTime % 24000L >= 23000L ? 1L : 0L)) * 24000L + 23500L;
        member.setWatch(player.level().getGameTime() + (dawn - dayTime));
        if (member.isSleeping()) {
            member.stopSleeping();
        }
        player.sendSystemMessage(Component.literal("<" + member.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("I'll keep watch with you tonight. Nothing gets near us without me seeing it.")
                        .withStyle(ChatFormatting.WHITE)));
    }

    /** How long the rebirth cutscene holds the screen: shut eyes, the name, the eyes opening. */
    private static final int REBIRTH_TICKS = 120;

    /** Once a second: bring back stragglers, and deliver a new band when one is due. */
    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (player.tickCount % 40 == 0 && !player.isSpectator()) {
            keepTogether(player);
        }
        if (player.tickCount % 40 == 20 && !player.isSpectator()) {
            checkBandLost(player);
        }
        if (player.tickCount % 40 == 0) {
            highlightWhenMixed(player);
        }
        if (player.tickCount % EXCURSION_CHECK_TICKS == 0 && !player.isSpectator()) {
            maybeSendExploring(player);
        }
        if (player.tickCount % 100 == 0) {
            assignCaretakers(player);
        }
        if (player.tickCount % PARTY_CHECK_TICKS == 0 && !player.isSpectator()) {
            organiseParties(player);
        }
    }

    /**
     * The net under the death hook. A band can vanish in ways an entity's own death never
     * reports - killed in an unloaded chunk, removed by a command, lost to a world edit -
     * and a player standing alone with no band and no panic is the thing that actually
     * matters, however it happened.
     */
    private static void checkBandLost(ServerPlayer player) {
        if (dev.hominin.evolution.band.Panic.isPanicking(player) || !all(player).isEmpty()) {
            hadBand.add(player.getUUID());
            return;
        }
        // Dead, or watching a cutscene (waking as someone else): the loss waits until you can take it in.
        if (!player.isAlive() || dev.hominin.evolution.stage.CutsceneGuard.isProtected(player)) {
            return;
        }
        // Only for a player who had one a moment ago: a fresh world has no band yet either.
        if (!hadBand.remove(player.getUUID())) {
            return;
        }
        bandLost(player);
    }

    /**
     * Another band walking with you doubles the crowd. Your own glow while they are here,
     * so you can always tell which of all these hominins are yours.
     */
    private static void highlightWhenMixed(ServerPlayer player) {
        boolean mixed = near(player, 32.0D).stream().anyMatch(m -> m.isGuestOf(player));
        if (!mixed) {
            return;
        }
        for (BandMember member : all(player)) {
            member.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING, 60, 0, false, false));
        }
    }

    /** Players known to have had a living band, so an empty band reads as a loss. */
    private static final java.util.Set<UUID> hadBand = new java.util.HashSet<>();

    /** Their band went into someone else's, not under: an empty band is not a loss. */
    public static void forgetBand(ServerPlayer player) {
        hadBand.remove(player.getUUID());
    }

    /** A band that has lost its leader walks to where they are - or, if too far, simply turns up. */
    private static void keepTogether(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BandMember member : all(player)) {
            // Asleep in a nest is asleep in a nest: nobody is lifted out of it and put down somewhere else.
            if (member.isSleeping() || member.isTurnedIn()) {
                continue;
            }
            if (!member.isOnExcursion() && member.distanceToSqr(player) > LOST_DISTANCE * LOST_DISTANCE) {
                BlockPos pos = standingSpotNear(level, player.blockPosition(), member.getRandom().nextInt(3) + 2,
                        member.getRandom().nextFloat() * Mth.TWO_PI);
                member.getNavigation().stop();
                member.setClimbingTree(false);
                member.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            }
        }
    }

    /** A member of a player's band died. If it was the last, the band is lost. */
    public static void onMemberDied(BandMember dead) {
        // A roof they were given stands empty.
        dev.hominin.evolution.build.Building.memberDied(dead);
        // Whoever was close to them may take it hard.
        Troubles.memberDied(dead);
        Player leader = dead.leaderPlayer();
        if (leader == null && dead.getLeader() != null && dead.level().getServer() != null) {
            // The leader may be in another dimension, or simply out of this level's player list.
            leader = dead.level().getServer().getPlayerList().getPlayer(dead.getLeader());
        }
        if (leader instanceof ServerPlayer going && !dead.isBaby()) {
            // A band going down in a fight: its allies hear it.
            Fates.ownMemberDied(going);
        }
        // One panic per band, however many of them go down together.
        if (!(leader instanceof ServerPlayer player) || !all(player).isEmpty()
                || dev.hominin.evolution.band.Panic.isPanicking(player)) {
            return;
        }
        // The last of them died while you were dead, or waking as someone else: not now. The check that
        // runs every couple of seconds picks it up the moment you are alive and the screen is yours.
        if (!player.isAlive() || dev.hominin.evolution.stage.CutsceneGuard.isProtected(player)) {
            return;
        }
        bandLost(player);
    }

    /**
     * The band is gone, however it went.
     *
     * <p>Counted once per band rather than once per body: stragglers go on dying for a while
     * after the rest, and each of those deaths finds an empty band too. A second loss only
     * counts once there has been a second band to lose.
     */
    /** How many more bands this species can lose before it dies out. */
    public static int bandsLeft(ServerPlayer player) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        boolean onFallback = dev.hominin.evolution.stage.Fallbacks.isFallback(data.getStage());
        int limit = onFallback ? dev.hominin.evolution.stage.Fallbacks.BANDS_ON_A_FALLBACK : BANDS_TO_EXTINCTION;
        return Math.max(0, limit - data.getCriterionCounters().getOrDefault(BANDS_LOST, 0));
    }

    /** The band drove you out. They go their own way; you are on your own, and it counts as a band lost. */
    public static void driveOut(ServerPlayer player) {
        for (BandMember member : all(player)) {
            member.discard();
        }
        hadBand.add(player.getUUID());
        bandLost(player);
    }

    private static void bandLost(ServerPlayer player) {
        if (!hadBand.remove(player.getUUID())) {
            return;
        }
        // Whoever led it with you loses it with you - their members were always yours, so their own count of the
        // band never sees it go.
        List<ServerPlayer> coLeaders = Newcomers.coLeaders(player);
        int lost = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().merge(BANDS_LOST, 1,
                Integer::sum);
        lose(player, lost);
        for (ServerPlayer co : coLeaders) {
            hadBand.remove(co.getUUID());
            // One band, one count: the co-leader's goes where the leader's is.
            co.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(BANDS_LOST, lost);
            lose(co, lost);
        }
    }

    /**
     * One of the band's leaders has lost it: what it knew is forgotten and the panic comes - or, the last band their
     * kind could lose, the fall to an older kind or the end of the line. A co-leader comes round beside the leader,
     * and walks with whatever band the leader wakes among.
     */
    private static void lose(ServerPlayer player, int lost) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        // What the band knew of the country dies with it; the bands that stood with it remember you.
        dev.hominin.evolution.world.Pois.bandLost(player);
        boolean onFallback = dev.hominin.evolution.stage.Fallbacks.isFallback(data.getStage());
        int limit = onFallback ? dev.hominin.evolution.stage.Fallbacks.BANDS_ON_A_FALLBACK : BANDS_TO_EXTINCTION;
        if (lost >= limit) {
            ResourceLocation fallback = onFallback ? null
                    : dev.hominin.evolution.stage.Fallbacks.of(data.getStage());
            if (fallback != null && dev.hominin.evolution.stage.StageRegistry.get(fallback) != null) {
                fallBack(player, data, fallback);
            } else {
                goExtinct(player, data);
            }
            return;
        }
        loseKnowledge(player, data);
        if (player.isAlive()) {
            dev.hominin.evolution.band.Panic.begin(player);
        }
        player.sendSystemMessage(Component.literal("Your whole band is gone. (" + lost + "/" + limit
                + " bands lost - lose " + limit + (onFallback ? " and your line ends.)" : " and your kind dies out.)"))
                .withStyle(ChatFormatting.RED));

    }

    /**
     * What a band knows dies with it. One hard requirement and one optional one lose
     * their progress - the furthest along of each, since that is what hurts.
     */
    private static void loseKnowledge(ServerPlayer player, PlayerEvolutionData data) {
        var stage = dev.hominin.evolution.stage.StageRegistry.current(data);
        if (stage == null) {
            return;
        }
        Map<String, Integer> counters = data.getCriterionCounters();
        String hard = mostProgressed(counters, stage.gate().required());
        String optional = mostProgressed(counters, stage.gate().optionalPool());
        for (var pair : List.of(new String[] {hard, "hard"}, new String[] {optional, "optional"})) {
            if (pair[0] == null) {
                continue;
            }
            counters.remove(pair[0]);
            String description = descriptionOf(stage, pair[0]);
            player.sendSystemMessage(Component.literal("Forgotten (" + pair[1] + "): " + description)
                    .withStyle(ChatFormatting.DARK_RED));
        }
        data.getNotifiedReadyStages().clear();
    }

    @Nullable
    private static String mostProgressed(Map<String, Integer> counters,
            List<dev.hominin.evolution.stage.GateCriterion> criteria) {
        String best = null;
        int bestCount = 0;
        for (var criterion : criteria) {
            int count = counters.getOrDefault(criterion.id(), 0);
            if (count > bestCount) {
                bestCount = count;
                best = criterion.id();
            }
        }
        return best;
    }

    private static String descriptionOf(dev.hominin.evolution.stage.StageDefinition stage, String id) {
        for (var criterion : stage.gate().required()) {
            if (criterion.id().equals(id)) {
                return criterion.description();
            }
        }
        for (var criterion : stage.gate().optionalPool()) {
            if (criterion.id().equals(id)) {
                return criterion.description();
            }
        }
        return id;
    }

    /**
     * Three bands gone. The line ends, and the player goes back further than they have
     * ever been - to Ardipithecus, a million years before Lucy, with requirements
     * nobody would call fair.
     */
    /**
     * The species fails, and what is left of it is an older, smaller form of the same thing.
     * Time runs backwards, everything learned at the lost stage goes with it, and the road
     * back up is shorter than the one that was lost.
     */
    private static void fallBack(ServerPlayer player, PlayerEvolutionData data, ResourceLocation fallback) {
        var from = dev.hominin.evolution.stage.StageRegistry.get(data.getStage());
        var to = dev.hominin.evolution.stage.StageRegistry.get(fallback);
        if (to == null) {
            goExtinct(player, data);
            return;
        }
        int rewound = from == null ? 0 : Math.max(0, to.yearsAgo() - from.yearsAgo());
        data.setStage(fallback);
        data.getCriterionCounters().keySet().removeIf(key -> !key.startsWith(EvolutionManager.SKILL_PREFIX));
        data.getCriterionCounters().remove(BANDS_LOST);
        dev.hominin.evolution.world.Havens.newSpecies(player);
        data.getNotifiedReadyStages().clear();
        data.setStageStartWalkDistance(player.walkDist);
        data.setDistanceCredits(0);
        dev.hominin.evolution.stage.StageSync.sync(player);
        dev.hominin.evolution.stage.CutsceneGuard.tryStart(player, 200);
        PacketDistributor.sendToPlayer(player, new dev.hominin.evolution.network.CutsceneStartPayload(
                "You died out as a species. You are not out yet.",
                to.displayName() + "  -  " + dev.hominin.evolution.stage.StageAge.rewound(rewound)));
        player.sendSystemMessage(Component.literal("What is left of your kind is older, fewer and hardier. "
                + "Lose " + dev.hominin.evolution.stage.Fallbacks.BANDS_ON_A_FALLBACK
                + " bands now and the line ends for good.").withStyle(ChatFormatting.GOLD));
        if (Newcomers.hostOf(player) == null) {
            topUp(player, fallback);
        }
    }

    private static void goExtinct(ServerPlayer player, PlayerEvolutionData data) {
        var ardipithecus = dev.hominin.evolution.stage.StageRegistry.get(ARDIPITHECUS);
        if (ardipithecus == null) {
            return;
        }
        data.setStage(ARDIPITHECUS);
        data.getCriterionCounters().keySet().removeIf(key -> !key.startsWith(EvolutionManager.SKILL_PREFIX));
        data.getCriterionCounters().remove(BANDS_LOST);
        dev.hominin.evolution.world.Havens.newSpecies(player);
        data.getNotifiedReadyStages().clear();
        data.setStageStartWalkDistance(player.walkDist);
        data.setDistanceCredits(0);
        dev.hominin.evolution.stage.StageSync.sync(player);
        dev.hominin.evolution.stage.CutsceneGuard.tryStart(player, 200);
        PacketDistributor.sendToPlayer(player, new dev.hominin.evolution.network.CutsceneStartPayload(
                "Your line has ended.", ardipithecus.displayName() + "  "
                        + dev.hominin.evolution.stage.StageAge.ago(ardipithecus.yearsAgo())));
        player.server.getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().getName()
                + "'s line has gone extinct.").withStyle(ChatFormatting.DARK_RED), false);
        if (Newcomers.hostOf(player) == null) {
            topUp(player, ARDIPITHECUS);
        }
    }

    /** Nightfall: a band travelling with a player heads off on its own. */
    /** Whether this member's own band still has its alpha somewhere in reach to follow. */
    public static boolean alphaNearby(BandMember member) {
        UUID band = member.getBandId();
        if (band == null) {
            return false;
        }
        return member.level().getEntitiesOfClass(BandMember.class,
                member.getBoundingBox().inflate(64.0D),
                other -> other != member && band.equals(other.getBandId()) && other.isAlpha())
                .stream().findAny().isPresent();
    }

    public static void sendGuestsHome(BandMember alpha) {
        Player player = alpha.companionPlayer();
        UUID band = alpha.getBandId();
        if (band == null) {
            return;
        }
        BlockPos from = player != null ? player.blockPosition() : alpha.blockPosition();
        double dx = alpha.getX() - from.getX();
        double dz = alpha.getZ() - from.getZ();
        double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
        BlockPos away = BlockPos.containing(alpha.getX() + dx / length * 48.0D, alpha.getY(),
                alpha.getZ() + dz / length * 48.0D);
        for (BandMember member : alpha.level().getEntitiesOfClass(BandMember.class,
                alpha.getBoundingBox().inflate(64.0D), m -> band.equals(m.getBandId()))) {
            member.leaveTowards(away);
        }
        if (player != null) {
            player.sendSystemMessage(Component.literal(alpha.getName().getString()
                    + "'s band heads off for the night.").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void forget(UUID player) {
        lastSplitDay.remove(player);
        hadBand.remove(player);
        heirs.remove(player);
    }

    private Band() {
    }
}
