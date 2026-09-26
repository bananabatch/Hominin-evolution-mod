package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.world.Pois;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bands die. Hunger, sickness, a clan of hyenas that found them sleeping - any band out there can be gone
 * between one day and the next, and what it knew of the country goes with it. From erectus on, its tools
 * are left lying where its camp was: an old tool deposit.
 *
 * <p>An ally is different. When something comes for a band that stands with you, the alarm reaches you: you
 * have <b>150 seconds</b> to get there. Get there, and it is a fight at their camp - see the attackers off and
 * the band lives, and does not forget it. Too late, and they are gone.
 *
 * <p>And it runs both ways. Lose a good part of your band in a fight and your allies hear it: in ten
 * seconds they are there, fighting beside you. Allies are worth having.
 */
public final class Fates {
    /** How long an ally's alarm gives you to get there. */
    public static final long PLIGHT_TICKS = 150 * 20L;
    /** Once you are there, how long they can hold out. */
    private static final long HOLD_OUT_TICKS = 100 * 20L;
    /** How long allies take to come when your band is going down. */
    private static final long RESCUE_TICKS = 10 * 20L;
    /** How long allies stay and fight before going home. */
    private static final long RESCUE_STAY_TICKS = 60 * 20L;

    /** An ally in trouble, and whoever was told. */
    private record Plight(UUID band, UUID player, long deadline, String cause, boolean arrived, long holdUntil,
            List<UUID> attackers) {
    }

    /** Allies on their way to you, and when they get there; then how long they stay. */
    private record Rescue(List<UUID> bands, long arriveAt, long leaveAt, boolean arrived) {
    }

    private static final Map<UUID, Plight> plights = new HashMap<>();
    private static final Map<UUID, Rescue> rescues = new HashMap<>();
    /** Per player: the most grown members the band has had today, and the day. */
    private static final Map<UUID, int[]> peak = new HashMap<>();
    private static final Map<UUID, Long> lastRescue = new HashMap<>();

    // ------------------------------------------------------------ once a day, for each band

    /** A band's day: some days, a band does not see the end of. */
    static void roll(ServerLevel level, Bands.Record band, RandomSource random) {
        if (band.nomadic() || plights.containsKey(band.id)) {
            return;
        }
        float chance = 0.012F + Math.max(0, band.desperation - 3) * 0.015F + (band.cohesion < 10 ? 0.02F : 0.0F)
                + (band.presence < 4 ? 0.01F : 0.0F) - (band.presence >= Presence.STRONG ? 0.006F : 0.0F);
        if (Bands.desperateTimes(level)) {
            chance *= 2.0F;
        }
        if (random.nextFloat() >= Mth.clamp(chance, 0.004F, 0.15F)) {
            return;
        }
        ServerPlayer friend = allyOnline(level, band);
        if (friend != null) {
            startPlight(friend, band, random);
            return;
        }
        // Bands end out of sight: nobody watches a band simply stop being there.
        for (ServerPlayer player : level.players()) {
            if (Bands.horizontal(player.blockPosition(), band.home) < 128.0D * 128.0D) {
                return;
            }
        }
        String[] causes = band.desperation >= 4 ? new String[] {"hunger took them", "they starved through the dry days",
                "sickness went through them, and there was no food to fight it"}
                : new String[] {"a clan of hyenas found them sleeping", "a sickness went through them",
                        "they scattered after a bad fight, and did not come back together",
                        "something came for them in the night"};
        die(level, band, causes[random.nextInt(causes.length)]);
    }

    /** The nearest online player the band stands with. */
    @Nullable
    private static ServerPlayer allyOnline(ServerLevel level, Bands.Record band) {
        ServerPlayer best = null;
        double bestDistance = 800.0D * 800.0D;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || !band.knownTo(player.getUUID())
                    || Relations.standing(player, band) < Relations.ALLIED || player.isSpectator()) {
                continue;
            }
            double distance = Bands.horizontal(player.blockPosition(), band.home);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /**
     * A band is gone. Its people, where they are about, are gone with it; what it knew of the country too;
     * and, from erectus on, its tools lie where its camp was.
     */
    public static void die(ServerLevel level, Bands.Record band, String cause) {
        plights.remove(band.id);
        Pois.Poi deposit = Pois.bandDied(level, band);
        for (BandMember member : level.getEntities(ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && band.id.equals(m.getBandId()))) {
            member.discard();
        }
        Bands.remove(level, band.id);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!band.knownTo(player.getUUID())) {
                continue;
            }
            player.sendSystemMessage(Component.literal("Word comes on the wind: " + band.name + " are gone - " + cause
                    + ". What they knew of the country went with them." + (deposit != null
                            ? " Their tools will still be lying where their camp was." : ""))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** One of a band's people died, and there are none left: gone, the ordinary way. */
    public static void lastOneDied(ServerLevel level, Bands.Record band) {
        plights.remove(band.id);
        Pois.bandDied(level, band);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (band.knownTo(player.getUUID())) {
                player.sendSystemMessage(Component.literal("The last of " + band.name + " is dead. The band is gone.")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    // ------------------------------------------------------------ an ally in trouble

    public static void startPlight(ServerPlayer player, Bands.Record band, RandomSource random) {
        String[] causes = {"a clan of hyenas is at their camp", "a giant hyena is in among them",
                "a great cat has come for their children"};
        String cause = causes[random.nextInt(causes.length)];
        long now = player.level().getGameTime();
        plights.put(band.id, new Plight(band.id, player.getUUID(), now + PLIGHT_TICKS, cause, false, 0L, List.of()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.AMBIENT, 0.7F,
                1.3F);
        int distance = (int) Math.sqrt(Bands.horizontal(player.blockPosition(), band.home));
        dev.hominin.evolution.guide.Alerts.urgent(player, dev.hominin.evolution.guide.Alerts.Kind.DANGER, Component.literal("Screaming on the wind - " + band.name + ", your allies: " + cause
                + "! They are " + distance + " blocks " + WildBands.bearingTo(player, band.home) + ". You have 150 "
                + "seconds to get there. ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Relations.leadLink(band)));
        dev.hominin.evolution.mind.MentalMap.lead(player, band.home, band.name + " (under attack)", "");
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.ALLY_PLIGHT);
    }

    /** Whether this band is under attack right now, waiting for you. */
    public static boolean inPlight(UUID band) {
        return plights.containsKey(band);
    }

    private static void tickPlights(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        for (Plight plight : new ArrayList<>(plights.values())) {
            if (!plight.player().equals(player.getUUID())) {
                continue;
            }
            Bands.Record band = Bands.get(level, plight.band());
            if (band == null) {
                plights.remove(plight.band());
                continue;
            }
            if (!plight.arrived()) {
                boolean there = Bands.horizontal(player.blockPosition(), band.home) < 40.0D * 40.0D
                        && level.isLoaded(band.home);
                if (there) {
                    List<UUID> attackers = attack(player, level, band);
                    plights.put(band.id, new Plight(band.id, player.getUUID(), plight.deadline(), plight.cause(), true,
                            now + HOLD_OUT_TICKS, attackers));
                    player.sendSystemMessage(Component.literal("You reach " + band.name + " - and they are still fighting. "
                            + "Drive them off!").withStyle(ChatFormatting.GOLD));
                } else if (now > plight.deadline()) {
                    die(level, band, "you did not get there in time - " + plight.cause().replace(" is ", " was ")
                            .replace(" has come", " came"));
                    player.sendSystemMessage(Component.literal("Too late. " + BandNames.capital(band.name)
                            + " are gone.").withStyle(ChatFormatting.DARK_RED));
                } else if ((plight.deadline() - now) % 600L < 20L) {
                    player.displayClientMessage(Component.literal(BandNames.capital(band.name) + " are still holding out - "
                            + (plight.deadline() - now) / 20L + " seconds.").withStyle(ChatFormatting.RED), true);
                }
                continue;
            }
            int left = 0;
            for (UUID id : plight.attackers()) {
                if (level.getEntity(id) instanceof LivingEntity attacker && attacker.isAlive()
                        && Bands.horizontal(attacker.blockPosition(), band.home) < 48.0D * 48.0D) {
                    left++;
                }
            }
            if (left == 0) {
                saved(player, level, band);
            } else if (now > plight.holdUntil()) {
                die(level, band, "they were overrun");
                player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " could not hold on long enough.")
                        .withStyle(ChatFormatting.DARK_RED));
            }
        }
    }

    /** What comes for them, at their camp: they fight, you fight. */
    private static List<UUID> attack(ServerPlayer player, ServerLevel level, Bands.Record band) {
        RandomSource random = player.getRandom();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, band.home.getX(), band.home.getZ());
        BlockPos camp = new BlockPos(band.home.getX(), y, band.home.getZ());
        List<Mob> spawned = new ArrayList<>();
        float roll = random.nextFloat();
        if (roll < 0.55F) {
            UUID clan = UUID.randomUUID();
            for (int i = 0; i < 3 + random.nextInt(2); i++) {
                var hyena = dev.hominin.evolution.entity.WildAnimals.spawnAt(level, ModEntities.CROCUTA.get(), camp, 6);
                if (hyena != null) {
                    hyena.joinClan(clan);
                    spawned.add(hyena);
                }
            }
        } else if (roll < 0.8F) {
            Mob giant = dev.hominin.evolution.entity.WildAnimals.spawnAt(level, ModEntities.PACHYCROCUTA.get(), camp, 6);
            if (giant != null) {
                spawned.add(giant);
            }
        } else {
            Mob cat = random.nextBoolean()
                    ? dev.hominin.evolution.entity.WildAnimals.spawnAt(level, ModEntities.HOMOTHERIUM.get(), camp, 6)
                    : dev.hominin.evolution.entity.WildAnimals.spawnAt(level, ModEntities.SABERTOOTH.get(), camp, 6);
            if (cat != null) {
                spawned.add(cat);
            }
        }
        List<BandMember> theirs = level.getEntitiesOfClass(BandMember.class,
                new net.minecraft.world.phys.AABB(camp).inflate(40.0D), m -> m.isAlive() && band.id.equals(m.getBandId()));
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < spawned.size(); i++) {
            Mob mob = spawned.get(i);
            ids.add(mob.getUUID());
            if (!theirs.isEmpty()) {
                BandMember victim = theirs.get(i % theirs.size());
                mob.setTarget(victim);
                victim.defendAgainst(mob);
            }
        }
        return ids;
    }

    private static void saved(ServerPlayer player, ServerLevel level, Bands.Record band) {
        plights.remove(band.id);
        band.desperation = Math.max(1, band.desperation - 1);
        band.cohesion = Math.min(50, band.cohesion + 6);
        Bands.changed(level);
        Relations.change(player, band, 8, "you saved them");
        Presence.add(player, 3, "you saved " + band.name);
        Claims.addFeared(player, 1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.1F);
        BandMember speaker = Relations.nearestMember(level, band, player, 48.0D);
        if (speaker != null) {
            speaker.ensureName();
            player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("You came. We will not forget it - when it is you, we will come.")
                            .withStyle(ChatFormatting.WHITE)));
        }
        player.sendSystemMessage(Component.literal("You saved " + band.name + ".").withStyle(ChatFormatting.GREEN));
        dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/rescuer");
    }

    // ------------------------------------------------------------ your band going down

    private static int grownNear(ServerPlayer player) {
        return (int) Band.all(player).stream().filter(m -> !m.isBaby()).count();
    }

    /** Every so often: how many grown people the band had at its best today. */
    private static void notePeak(ServerPlayer player) {
        int day = (int) (player.level().getDayTime() / 24000L);
        int[] best = peak.get(player.getUUID());
        int now = grownNear(player);
        if (best == null || best[1] != day) {
            peak.put(player.getUUID(), new int[] {now, day});
        } else if (now > best[0]) {
            best[0] = now;
        }
    }

    /** Whatever is fighting your band right now. */
    private static List<LivingEntity> threats(ServerPlayer player) {
        List<LivingEntity> threats = new ArrayList<>();
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0D),
                m -> m.isAlive() && (m instanceof Enemy || m.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS)
                        || (m instanceof BandMember member && member.isWild() && member.getTarget() != null)))) {
            LivingEntity target = mob.getTarget();
            if (target == player || (target instanceof BandMember member && member.isLedBy(player))) {
                threats.add(mob);
            }
        }
        return threats;
    }

    /** One of your band has died: if the band is going down, your allies hear it. */
    public static void ownMemberDied(ServerPlayer player) {
        int[] best = peak.get(player.getUUID());
        int now = grownNear(player);
        if (best == null || best[0] < 3 || now > best[0] * 0.6F || rescues.containsKey(player.getUUID())) {
            return;
        }
        long time = player.level().getGameTime();
        if (time - lastRescue.getOrDefault(player.getUUID(), -99999L) < 12000L || threats(player).isEmpty()) {
            return;
        }
        callAllies(player, false);
    }

    /** Allies within earshot drop everything and come. Returns whether any are coming. */
    public static boolean callAllies(ServerPlayer player, boolean forced) {
        ServerLevel level = player.serverLevel();
        List<UUID> coming = new ArrayList<>();
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || Relations.standing(player, band) < Relations.ALLIED
                    || Bands.horizontal(band.home, player.blockPosition()) > 500.0D * 500.0D || coming.size() >= 2) {
                continue;
            }
            coming.add(band.id);
            player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " heard the fighting - they are "
                    + "coming!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        if (coming.isEmpty()) {
            if (forced) {
                player.displayClientMessage(Component.literal("You have no allies near enough to come."), true);
            }
            return false;
        }
        long time = level.getGameTime();
        lastRescue.put(player.getUUID(), time);
        rescues.put(player.getUUID(), new Rescue(coming, time + RESCUE_TICKS, time + RESCUE_TICKS + RESCUE_STAY_TICKS,
                false));
        return true;
    }

    private static void tickRescue(ServerPlayer player) {
        Rescue rescue = rescues.get(player.getUUID());
        if (rescue == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (!rescue.arrived()) {
            if (now < rescue.arriveAt()) {
                return;
            }
            for (UUID id : rescue.bands()) {
                Bands.Record band = Bands.get(level, id);
                if (band == null) {
                    continue;
                }
                double angle = Math.atan2(band.home.getZ() - player.getZ(), band.home.getX() - player.getX());
                int x = (int) (player.getX() + Math.cos(angle) * 12.0D);
                int z = (int) (player.getZ() + Math.sin(angle) * 12.0D);
                BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                WildBands.placeBand(level, at, band.species, 3, band.id, player.getRandom());
                player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " are here!")
                        .withStyle(ChatFormatting.AQUA));
            }
            rescues.put(player.getUUID(), new Rescue(rescue.bands(), rescue.arriveAt(), rescue.leaveAt(), true));
            return;
        }
        List<LivingEntity> threats = threats(player);
        boolean over = threats.isEmpty() || now > rescue.leaveAt();
        for (UUID id : rescue.bands()) {
            Bands.Record band = Bands.get(level, id);
            if (band == null) {
                continue;
            }
            List<BandMember> theirs = level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(48.0D),
                    m -> m.isAlive() && id.equals(m.getBandId()) && !m.isBaby());
            for (int i = 0; i < theirs.size(); i++) {
                BandMember member = theirs.get(i);
                if (over) {
                    member.setTarget(null);
                    member.getNavigation().moveTo(band.home.getX() + 0.5D, band.home.getY(), band.home.getZ() + 0.5D, 1.0D);
                } else if (member.getTarget() == null || !member.getTarget().isAlive()) {
                    member.defendAgainst(threats.get(i % threats.size()));
                }
            }
        }
        if (over) {
            rescues.remove(player.getUUID());
            if (threats.isEmpty()) {
                player.sendSystemMessage(Component.literal("It is over. Your allies head home.").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** Once a second. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 7) {
            return;
        }
        tickPlights(player);
        tickRescue(player);
        if (player.tickCount % 200 == 7) {
            notePeak(player);
        }
    }

    public static void forget(UUID player) {
        rescues.remove(player);
        peak.remove(player);
        lastRescue.remove(player);
        plights.values().removeIf(p -> p.player().equals(player));
    }

    // ------------------------------------------------------------ for testing

    /** The nearest known band, taken by something: for trying out a band's end. */
    @Nullable
    public static Bands.Record nearestKnown(ServerPlayer player, boolean allied) {
        Bands.Record best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Bands.Record band : Bands.all(player.serverLevel())) {
            if (band.nomadic() || !band.knownTo(player.getUUID())
                    || allied != (Relations.standing(player, band) >= Relations.ALLIED)) {
                continue;
            }
            double distance = Bands.horizontal(band.home, player.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = band;
            }
        }
        return best;
    }

    private Fates() {
    }
}
