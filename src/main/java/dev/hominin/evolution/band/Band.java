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

    private record Heir(ResourceKey<Level> dimension, UUID member) {
    }

    private static final Map<UUID, Heir> heirs = new HashMap<>();
    /** Until when a player's blows on their band count as wrestling. */
    private static final Map<UUID, Long> wrestleWindow = new HashMap<>();
    /** When a player who lost their band is found by a new one. */
    private static final Map<UUID, Long> newBandDue = new HashMap<>();

    /** Losing this many bands ends the line. */
    private static final int BANDS_TO_EXTINCTION = 3;
    private static final String BANDS_LOST = EvolutionManager.SKILL_PREFIX + "bands_lost";
    private static final long NEW_BAND_DELAY_TICKS = 2400L;
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
        }
    }

    /** Joining in on whatever the leader attacks - unless told not to hunt with them. */
    public static void assist(ServerPlayer player, LivingEntity target) {
        for (BandMember member : companionsNear(player, DEFEND_RADIUS)) {
            if (member.huntsWithLeader()) {
                member.defendAgainst(target);
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
    public static void announce(BandMember member, String rest) {
        Player leader = member.leaderPlayer();
        if (leader == null || member.distanceToSqr(leader) > 48.0D * 48.0D) {
            return;
        }
        long now = member.level().getGameTime();
        if (now - lastMemberAnnouncement.getOrDefault(member.getUUID(), -99999L) < ANNOUNCE_MEMBER_COOLDOWN
                || now - lastLeaderAnnouncement.getOrDefault(leader.getUUID(), -99999L) < ANNOUNCE_LEADER_COOLDOWN) {
            return;
        }
        lastMemberAnnouncement.put(member.getUUID(), now);
        lastLeaderAnnouncement.put(leader.getUUID(), now);
        member.ensureName();
        leader.sendSystemMessage(Component.literal(member.getName().getString() + rest).withStyle(ChatFormatting.GRAY));
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
        for (BandMember adult : near(child, DEFEND_RADIUS)) {
            if (adult == child || adult.isBaby() || !adult.isAlliedTo(child)) {
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
                .filter(m -> !m.isBaby() && !m.isHungry() && m.getTarget() == null && !m.isUpATree()
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
            find = new ItemStack(dev.hominin.evolution.ModItems.ROCK.get());
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
        for (BandMember member : companionsNear(player, DEFEND_RADIUS)) {
            member.defendAgainst(attacker);
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
                EvolutionManager.incrementCriterion(player, COHESION, 1);
            }
            return;
        }
        if (!(target instanceof Player)) {
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
        if (!data.getUnlockedRecipes().add(BAND_FORMED)) {
            return;
        }
        if (player.serverLevel().getGameRules().getBoolean(dev.hominin.evolution.ModGameRules.SUPER_HARD_MODE)) {
            data.setStage(ARDIPITHECUS);
            dev.hominin.evolution.stage.StageSync.sync(player);
        }
        topUp(player, data.getStage());
        player.sendSystemMessage(Component.literal("You are not alone out here. Your band is with you."));
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
        return member;
    }

    public static BlockPos standingSpotNear(ServerLevel level, BlockPos center, int distance, float angle) {
        int x = center.getX() + Math.round(Mth.cos(angle) * distance);
        int z = center.getZ() + Math.round(Mth.sin(angle) * distance);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // Not onto a cliff top far above or below the player; stand them beside the player instead.
        if (Math.abs(y - center.getY()) > 4) {
            return center;
        }
        return new BlockPos(x, y, z);
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
        player.sendSystemMessage(Component.literal("You wake among a new band.").withStyle(ChatFormatting.GREEN));
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
        BandMember heir = null;
        double best = Double.MAX_VALUE;
        for (BandMember member : all(player)) {
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
            heirs.put(player.getUUID(), new Heir(player.level().dimension(), heir.getUUID()));
        } else {
            heirs.remove(player.getUUID());
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
        Heir heir = heirs.remove(player.getUUID());
        if (heir == null) {
            return;
        }
        ServerLevel level = player.server.getLevel(heir.dimension());
        if (level == null || !(level.getEntity(heir.member()) instanceof BandMember member) || !member.isAlive()) {
            return;
        }
        String name = member.getName().getString();
        player.teleportTo(level, member.getX(), member.getY(), member.getZ(), member.getYRot(), 0.0F);
        for (ItemStack stack : member.takeEverything()) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.getFoodData().setFoodLevel(Math.max(6, member.getHunger()));
        member.discard();
        PacketDistributor.sendToPlayer(player, new RebirthPayload(name));
        player.sendSystemMessage(Component.literal("You carry on as " + name + ".").withStyle(ChatFormatting.GRAY));
    }

    /** Once a second: bring back stragglers, and deliver a new band when one is due. */
    public static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 20 != 0) {
            return;
        }
        Long due = newBandDue.get(player.getUUID());
        if (due != null && player.level().getGameTime() >= due) {
            newBandDue.remove(player.getUUID());
            topUp(player, player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
            player.sendSystemMessage(Component.literal("Wanderers find you, and stay. You have a band again.")
                    .withStyle(ChatFormatting.GREEN));
        }
        if (player.tickCount % 40 == 0 && !player.isSpectator()) {
            keepTogether(player);
        }
        if (player.tickCount % EXCURSION_CHECK_TICKS == 0 && !player.isSpectator()) {
            maybeSendExploring(player);
        }
        if (player.tickCount % 100 == 0) {
            assignCaretakers(player);
        }
    }

    /** A band that has lost its leader walks to where they are - or, if too far, simply turns up. */
    private static void keepTogether(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BandMember member : all(player)) {
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
        Player leader = dead.leaderPlayer();
        if (!(leader instanceof ServerPlayer player) || !all(player).isEmpty() || newBandDue.containsKey(player.getUUID())) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        int lost = data.getCriterionCounters().merge(BANDS_LOST, 1, Integer::sum);
        if (lost >= BANDS_TO_EXTINCTION) {
            goExtinct(player, data);
            return;
        }
        loseKnowledge(player, data);
        player.sendSystemMessage(Component.literal("Your whole band is gone. (" + lost + "/" + BANDS_TO_EXTINCTION
                + " bands lost - lose " + BANDS_TO_EXTINCTION + " and your line ends.)").withStyle(ChatFormatting.RED));
        newBandDue.put(player.getUUID(), player.level().getGameTime() + NEW_BAND_DELAY_TICKS);
    }

    /**
     * What a band knows dies with it. One hard requirement and one optional one lose
     * their progress - the furthest along of each, since that is what hurts.
     */
    private static void loseKnowledge(ServerPlayer player, PlayerEvolutionData data) {
        var stage = dev.hominin.evolution.stage.StageRegistry.get(data.getStage());
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
    private static void goExtinct(ServerPlayer player, PlayerEvolutionData data) {
        var ardipithecus = dev.hominin.evolution.stage.StageRegistry.get(ARDIPITHECUS);
        if (ardipithecus == null) {
            return;
        }
        data.setStage(ARDIPITHECUS);
        data.getCriterionCounters().keySet().removeIf(key -> !key.startsWith(EvolutionManager.SKILL_PREFIX));
        data.getCriterionCounters().remove(BANDS_LOST);
        data.getNotifiedReadyStages().clear();
        data.setStageStartWalkDistance(player.walkDist);
        data.setDistanceCredits(0);
        dev.hominin.evolution.stage.StageSync.sync(player);
        PacketDistributor.sendToPlayer(player, new dev.hominin.evolution.network.CutsceneStartPayload(
                "Your line has ended.", ardipithecus.displayName() + "  "
                        + dev.hominin.evolution.stage.StageAge.ago(ardipithecus.yearsAgo())));
        player.server.getPlayerList().broadcastSystemMessage(Component.literal(player.getGameProfile().getName()
                + "'s line has gone extinct.").withStyle(ChatFormatting.DARK_RED), false);
        topUp(player, ARDIPITHECUS);
    }

    /** Nightfall: a band travelling with a player heads off on its own. */
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
        heirs.remove(player);
    }

    private Band() {
    }
}
