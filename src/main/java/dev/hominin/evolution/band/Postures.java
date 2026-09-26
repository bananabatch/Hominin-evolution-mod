package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * How a band meets something that comes for it - chosen under Danger, for your own band; every band out there has
 * its own.
 *
 * <ul>
 * <li><b>Stand together</b>: as it always was - whoever is near answers.</li>
 * <li><b>Overwhelm</b>: everyone runs at it at once. More of the band gets hurt - but hunters learn to leave a band
 * like that alone, and pick on it less.</li>
 * <li><b>Ranged</b>: they keep their distance and throw - rocks, hammerstones, spears wooden or stone - while the two
 * best armed stay close in, guarding the throwers.</li>
 * <li><b>Ambush</b>: at first they let it be and hold still; then everything is thrown at once, the shock of it slows
 * the thing for a moment - and they rush it.</li>
 * </ul>
 *
 * <p>Another band's way of fighting you only know once you have asked them, fought them, or been told by a band that
 * knows them. Until then it is a "?".
 */
public final class Postures {
    public static final int ACTION = 73;

    public enum Posture {
        TOGETHER("Stand together", "whoever is near answers"),
        OVERWHELM("Overwhelm", "everyone rushes it at once - hunters learn to leave them alone"),
        RANGED("Ranged", "they keep off and throw, two of the best armed guarding the throwers"),
        AMBUSH("Ambush", "they hold still, then throw everything at once and rush it");

        public final String label;
        public final String about;

        Posture(String label, String about) {
            this.label = label;
            this.about = about;
        }

        public static Posture byId(int id) {
            Posture[] values = values();
            return id >= 0 && id < values.length ? values[id] : TOGETHER;
        }
    }

    private static final String KEY = EvolutionManager.SKILL_PREFIX + "posture";
    /** How long an ambush holds still before everything is thrown. */
    private static final long AMBUSH_HOLD = 60L;
    private static final double FIGHT_REACH = 24.0D;

    /** Ambushes under way: the thing ambushed, and when the band first saw it. */
    private static final Map<UUID, Long> ambushes = new HashMap<>();
    /** Things the ambush has already been sprung on. */
    private static final Map<UUID, Long> sprung = new HashMap<>();
    /** Things a band has already been rallied against, overwhelming it. */
    private static final Map<UUID, Long> rallied = new HashMap<>();

    public static Posture of(Player player) {
        return Posture.byId(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().getOrDefault(KEY, 0));
    }

    /** A member's band's way: its leader's, or its wild band's. */
    public static Posture of(BandMember member) {
        Player lead = member.leaderPlayer();
        if (lead != null) {
            return of(lead);
        }
        if (member.level() instanceof net.minecraft.server.level.ServerLevel level && member.getBandId() != null) {
            Bands.Record band = Bands.get(level, member.getBandId());
            return band == null ? Posture.TOGETHER : Posture.byId(band.posture());
        }
        return Posture.TOGETHER;
    }

    // ------------------------------------------------------------ choosing yours

    public static void open(ServerPlayer player) {
        Posture now = of(player);
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (Posture posture : Posture.values()) {
            labels.add((posture == now ? "> " : "") + posture.label + " - " + posture.about);
            values.add(posture.ordinal());
        }
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION,
                "When something comes for the band, how do they meet it? (Now: " + now.label + ")", labels, values));
    }

    public static void choose(ServerPlayer player, int value) {
        Posture posture = Posture.byId(value);
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(KEY, posture.ordinal());
        player.sendSystemMessage(Component.literal("Your band's way when something comes for it: " + posture.label
                + " - " + posture.about + ".").withStyle(ChatFormatting.GOLD));
    }

    // ------------------------------------------------------------ in a fight

    /** Something to meet as the band's posture says: a hunter, a monster, another band's people in a fight. */
    public static boolean threat(BandMember member, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        return dev.hominin.evolution.hunt.PredatorLull.hunter(target) || target instanceof Enemy
                || target instanceof BandMember other && !other.isAlliedTo(member);
    }

    /** Every tick, from the member's own step: the band's posture, put into practice. */
    public static void tick(BandMember member) {
        if (member.isBaby() || !(member.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        LivingEntity target = member.getTarget();
        if (!threat(member, target) || member.distanceToSqr(target) > FIGHT_REACH * FIGHT_REACH) {
            return;
        }
        long now = level.getGameTime();
        switch (of(member)) {
            case OVERWHELM -> {
                if (member.tickCount % 10 == 0 && rallied.putIfAbsent(target.getUUID(), now) == null) {
                    for (BandMember other : level.getEntitiesOfClass(BandMember.class,
                            member.getBoundingBox().inflate(FIGHT_REACH), m -> m != member && m.isAlive() && !m.isBaby()
                                    && m.isAlliedTo(member) && m.getTarget() != target)) {
                        other.fightFor(target);
                    }
                }
            }
            case RANGED -> {
                if (member.tickCount % 10 != 3 || guards(member, target)) {
                    return;
                }
                double distance = Math.sqrt(member.distanceToSqr(target));
                if (distance < 8.0D) {
                    // Off, out of reach, and throw from there.
                    Vec3 away = member.position().subtract(target.position()).normalize().scale(10.0D - distance);
                    member.getNavigation().moveTo(member.getX() + away.x, member.getY(), member.getZ() + away.z, 1.2D);
                }
            }
            case AMBUSH -> {
                Long seen = ambushes.putIfAbsent(target.getUUID(), now);
                long since = now - (seen == null ? now : seen);
                if (since < AMBUSH_HOLD) {
                    // Still. Let it do what it is doing.
                    member.getNavigation().stop();
                    member.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    return;
                }
                if (sprung.putIfAbsent(target.getUUID(), now) == null) {
                    // Everything at once - and the shock of it.
                    for (BandMember other : level.getEntitiesOfClass(BandMember.class,
                            member.getBoundingBox().inflate(FIGHT_REACH), m -> m.isAlive() && !m.isBaby()
                                    && (m == member || m.isAlliedTo(member)))) {
                        Throwing.throwNow(other, target);
                        other.fightFor(target);
                    }
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, true));
                }
            }
            default -> {
            }
        }
        if (now % 1200L == 0L) {
            ambushes.values().removeIf(t -> now - t > 2400L);
            sprung.values().removeIf(t -> now - t > 2400L);
            rallied.values().removeIf(t -> now - t > 2400L);
        }
    }

    /** Ranged: the two best armed of the band nearest the thing stay in close, guarding the throwers. */
    private static boolean guards(BandMember member, LivingEntity target) {
        if (!BandMember.isWeapon(member.getMainHandItem())) {
            return false;
        }
        List<BandMember> armed = new ArrayList<>(member.level().getEntitiesOfClass(BandMember.class,
                target.getBoundingBox().inflate(FIGHT_REACH), m -> m.isAlive() && !m.isBaby()
                        && (m == member || m.isAlliedTo(member)) && BandMember.isWeapon(m.getMainHandItem())));
        armed.sort(Comparator.comparingDouble(m -> m.distanceToSqr(target)));
        return armed.indexOf(member) >= 0 && armed.indexOf(member) < 2;
    }

    /** Whether this band throws first and closes after: ranged, or an ambush sprung. */
    public static boolean throwsFirst(BandMember member) {
        Posture posture = of(member);
        return posture == Posture.RANGED
                || posture == Posture.AMBUSH && member.getTarget() != null && sprung.containsKey(member.getTarget().getUUID());
    }

    /** Hunters learn which bands rush them all at once. */
    public static boolean overwhelms(LivingEntity hominin) {
        return hominin instanceof BandMember member ? of(member) == Posture.OVERWHELM
                : hominin instanceof Player player && of(player) == Posture.OVERWHELM;
    }

    // ------------------------------------------------------------ other bands'

    /** What you know of how another band fights. */
    public static String describe(ServerPlayer player, Bands.Record band) {
        if (!band.postureKnown.contains(player.getUUID())) {
            return "When something comes for them: ? (ask them, or a band that knows them - or find out)";
        }
        Posture posture = Posture.byId(band.posture());
        return "When something comes for them: " + posture.label + " - " + posture.about + ".";
    }

    /** They tell you, or you find out the hard way. */
    public static boolean learn(ServerPlayer player, Bands.Record band) {
        if (band.postureKnown.add(player.getUUID())) {
            Bands.changed(player.serverLevel());
            return true;
        }
        return false;
    }

    public static void ask(ServerPlayer player, Bands.Record band) {
        learn(player, band);
        Posture posture = Posture.byId(band.posture());
        player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + ": \"When something comes for us? "
                + switch (posture) {
                    case OVERWHELM -> "We all go at it. All of us, at once. Nothing stays to face that.";
                    case RANGED -> "We keep off it and throw. Two stay close with the good weapons.";
                    case AMBUSH -> "We keep still. We let it come. Then everything at once.";
                    default -> "Whoever's nearest. We stand together.";
                } + "\"").withStyle(ChatFormatting.AQUA));
    }

    /** A band that knows others tells you how one or two of them fight. */
    public static void gossip(ServerPlayer player, Bands.Record teller) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        int told = 0;
        for (Bands.Record other : Bands.all(level)) {
            if (other == teller || other.nomadic() || !other.knownTo(player.getUUID())
                    || other.postureKnown.contains(player.getUUID())
                    || Bands.horizontal(other.home, teller.home) > 600.0D * 600.0D || player.getRandom().nextBoolean()) {
                continue;
            }
            learn(player, other);
            player.sendSystemMessage(Component.literal(BandNames.capital(teller.name) + " tell you how " + other.name
                    + " fight: " + Posture.byId(other.posture()).label + ".").withStyle(ChatFormatting.GRAY));
            if (++told >= 2) {
                return;
            }
        }
    }

    private Postures() {
    }
}
