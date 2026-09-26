package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What people say when you are about - your own band, and everyone else's.
 *
 * <p>Another band's people talk when you come near: about you, to you, among themselves. Their names are
 * not the gold of your own band's but the colour of where you stand with them - blue for allies, green for
 * friends, grey for strangers, red for a band that does not want you there - and what they say follows
 * the same line. Heard from a way off, you hear how far, and which way.
 *
 * <p>Your own band talk to you as well as they feel about you: somebody who hardly trusts you says so;
 * somebody whose bond with you is deep says that too.
 */
public final class Voices {
    /** Another band's people are heard this far off. */
    private static final double HEARD = 40.0D;
    /** Closer than this there is no need to say where they are. */
    private static final double CLOSE = 12.0D;
    private static final long BAND_GAP = 45 * 20L;
    private static final long OWN_GAP = 150 * 20L;

    private static final Map<String, Long> lastHeard = new HashMap<>();

    private static final String[] ALLIED = {"There they are! Come and sit with us.", "You are always welcome with us.",
            "We saw a big herd down by the water - we'll show you.", "Our young ones keep asking about you.",
            "Whatever you need, just say it.", "Your people and ours - one band, near enough.",
            "Stay close tonight. Two bands are safer than one.", "We kept some meat back for you."};
    private static final String[] FRIENDLY = {"Good to see you again.", "The water is low upstream - go easy on it.",
            "Trade later? We have good stone.", "Keep an eye out, the hyenas have been about.",
            "You are welcome here. Just don't take too much.", "Your band looks well fed. Good.",
            "Hunting today? Luck to you."};
    private static final String[] NEUTRAL = {"Who is that?", "Keep your distance, and we'll keep ours.",
            "They are watching us.", "What do they want?", "Strangers. Stay close to me.", "Don't stare. Let them pass.",
            "Is that all of them?"};
    private static final String[] UNFRIENDLY = {"You again. Keep walking.", "This isn't your ground.",
            "Watch them. They take things.", "Nobody asked you here.", "Go back to your own water.",
            "If they come any closer, we drive them off."};
    private static final String[] HOSTILE = {"Thieves! Get away from here!", "We remember what you did.",
            "Come one step closer. Go on.", "Next time we take everything you have.",
            "Look at them. Weak. We could take it all.", "You won't sleep easy tonight."};
    private static final String[] HARD = {"There's not enough for us, never mind them.",
            "Dry days make people desperate. Watch them.", "Nobody shares in a season like this."};
    private static final String[] QUARREL = {"That was mine! Put it back!", "Stop taking the best pieces for yourself!",
            "Who ate the last of the meat?", "Why do we even follow you?", "Do it yourself, then."};
    private static final String[] PROUD = {"Nothing hunts near our camp. Nothing dares.",
            "The cats know better than to come here.", "Let them come. We are ready."};
    private static final String[] TROOP = {"A Paranthropus chews, and stares at you without stopping.",
            "One of the Paranthropus grunts, and the rest look up.", "The Paranthropus troop crunches through a mouthful of roots.",
            "A big Paranthropus male turns his back on you, pointedly."};

    private static final String[] COLD = {"Hm.", "You again.", "I do my share. Don't look at me like that.",
            "Why should I listen to you?", "Some leader.", "I don't need you watching me."};
    private static final String[] EASY = {"Where are we going next?", "I found good roots back there.",
            "Stay where I can see you.", "My feet ache. Yours?", "Quiet today. I don't trust quiet."};
    private static final String[] WARM = {"Glad you're with us.", "I'll watch your back today.",
            "You picked a good spot last night.", "Want me to carry something?", "I like it when you lead."};
    private static final String[] CLOSE_BOND = {"I would follow you anywhere. You know that.", "You're the best of us.",
            "I saved you the good part. Don't tell the others.", "Whatever comes, we face it together.",
            "When I'm old I'll still be listening to you."};

    /** How the band as a whole takes you, by cohesion: 50 and up, 40, 30, 20, and thin ice below that. */
    private static final String[] TRUSTING = {"We'd go anywhere with you. Anywhere.",
            "Come, sit with us - there's room by the fire.", "Whatever you decide, we're with you.",
            "You've kept us safe. We haven't forgotten.", "Stay. Rest. We'll keep watch.",
            "It's good, the way things are with us."};
    private static final String[] SETTLED = {"Where to today? You lead, we follow.", "We're getting used to your ways.",
            "You haven't led us wrong yet.", "Tell us what to do and we'll do it.", "It's a good band, this."};
    private static final String[] WARY = {"I trust you. Mostly.", "You know where we're going... don't you?",
            "We'll follow. For now.", "Some of us aren't sure about you. I am. I think.", "Just don't get us killed."};
    private static final String[] SLIPPING = {"People are talking about you.", "I used to be sure about you.",
            "Why should we keep following you?", "Something has to change.", "We're not as close as we were."};
    private static final String[] THIN_ICE = {"One more mistake. That's all you have left.",
            "Nobody here trusts you any more.", "Don't turn your back on us.", "We could leave. Some of us want to.",
            "You're on thin ice. You know that?"};

    private static String[] byCohesion(int cohesion) {
        return cohesion >= 50 ? TRUSTING : cohesion >= 40 ? SETTLED : cohesion >= 30 ? WARY : cohesion >= 20 ? SLIPPING
                : THIN_ICE;
    }

    /** Every five seconds per player. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 100 != 43 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        others(player, level, now);
        own(player, now);
    }

    // ------------------------------------------------------------ other bands

    private static void others(ServerPlayer player, ServerLevel level, long now) {
        if (player.getRandom().nextInt(3) != 0) {
            return;
        }
        List<BandMember> heard = level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(HEARD),
                m -> m.isAlive() && m.isWild() && !m.isBaby() && !m.isGuestOf(player) && m.getBandId() != null);
        if (heard.isEmpty()) {
            return;
        }
        BandMember speaker = heard.get(player.getRandom().nextInt(heard.size()));
        String key = player.getUUID() + "|" + speaker.getBandId();
        if (now - lastHeard.getOrDefault(key, -99999L) < BAND_GAP) {
            return;
        }
        if (lastHeard.size() > 2048) {
            lastHeard.clear();
        }
        lastHeard.put(key, now);
        Bands.Record band = Bands.get(level, speaker.getBandId());
        speaker.ensureName();
        if (Paranthropus.is(speaker)) {
            player.sendSystemMessage(Component.literal(Lines.pickFrom("troop|" + player.getUUID(), List.of(TROOP),
                    player.getRandom()) + where(player, speaker)).withStyle(ChatFormatting.GRAY));
            return;
        }
        int standing = band == null ? Relations.NEUTRAL : Relations.standing(player, band);
        // They say what their kind would say - and their kind's own things besides.
        net.minecraft.resources.ResourceLocation kind = band != null ? band.species : speaker.getStage();
        List<String> pool = new ArrayList<>(Speech.fitting(List.of(standing >= Relations.ALLIED ? ALLIED
                : standing >= Relations.FRIENDLY ? FRIENDLY : standing > Relations.UNFRIENDLY ? NEUTRAL
                        : standing > Relations.HOSTILE ? UNFRIENDLY : HOSTILE), kind));
        boolean warm = standing >= Relations.FRIENDLY;
        pool.addAll(Speech.band(kind, warm));
        if (standing > Relations.HOSTILE && player.getRandom().nextInt(3) == 0) {
            pool.addAll(Speech.kin(kind, player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage(),
                    warm));
        }
        boolean hard = dev.hominin.evolution.survival.Seasons.strained(level);
        if (hard && standing < Relations.FRIENDLY) {
            pool.addAll(Speech.fitting(List.of(HARD), kind));
        }
        if (band != null && band.presence >= Presence.STRONG && standing < Relations.FRIENDLY) {
            pool.addAll(Speech.fitting(List.of(PROUD), kind));
        }
        boolean quarrel = band != null && band.cohesion < 20 && player.getRandom().nextInt(3) == 0;
        List<String> quarrels = Speech.fitting(List.of(QUARREL), kind);
        String line = Lines.pickFrom("voice|" + player.getUUID(), quarrel && !quarrels.isEmpty() ? quarrels : pool,
                player.getRandom());
        MutableComponent name = Component.literal(speaker.getName().getString()
                + (band != null ? " of " + BandNames.capital(band.name) : "")).withStyle(colour(standing));
        player.sendSystemMessage(Component.literal("<").withStyle(colour(standing)).append(name)
                .append(Component.literal(">").withStyle(colour(standing)))
                .append(Component.literal(where(player, speaker)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" " + (quarrel ? "(to their own) " : "")).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(line).withStyle(quarrel ? ChatFormatting.GRAY : ChatFormatting.WHITE)));
    }

    /** Blue for allies, green for friends, grey for strangers, red for bad blood. */
    public static ChatFormatting colour(int standing) {
        return standing >= Relations.ALLIED ? ChatFormatting.AQUA : standing >= Relations.FRIENDLY ? ChatFormatting.GREEN
                : standing > Relations.UNFRIENDLY ? ChatFormatting.GRAY : standing > Relations.HOSTILE ? ChatFormatting.RED
                : ChatFormatting.DARK_RED;
    }

    /** " (28 blocks NE)", or nothing when they are right here. */
    private static String where(ServerPlayer player, BandMember speaker) {
        double distance = Math.sqrt(speaker.distanceToSqr(player));
        if (distance < CLOSE) {
            return "";
        }
        return " (" + (int) distance + " blocks " + WildBands.bearingFrom(player, speaker.blockPosition()) + ")";
    }

    // ------------------------------------------------------------ your own band

    private static void own(ServerPlayer player, long now) {
        String key = player.getUUID() + "|own";
        if (now - lastHeard.getOrDefault(key, -99999L) < OWN_GAP || player.getRandom().nextInt(4) != 0) {
            return;
        }
        List<BandMember> near = Band.ownNear(player, 12.0D);
        near.removeIf(m -> m.isBaby() || m.inDanger() || m.getTarget() != null);
        if (near.isEmpty()) {
            return;
        }
        lastHeard.put(key, now);
        BandMember speaker = near.get(player.getRandom().nextInt(near.size()));
        String line = lineFor(player, speaker);
        if (line == null) {
            return;
        }
        speaker.ensureName();
        player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
        Chatter.echo(speaker, "to_leader");
    }

    /** What this member says to you, by how they feel about you. */
    @Nullable
    private static String lineFor(ServerPlayer player, BandMember member) {
        int bond = member.getBond();
        String[] pool = bond <= 0 ? COLD : bond < Wants.GIFT_BOND ? EASY : bond < 6 ? WARM : CLOSE_BOND;
        if (member.isAntisocial() && bond < 6) {
            pool = COLD;
        }
        // Half the time it is how they feel about you; half, how the band as a whole does.
        if (!member.isAntisocial() && player.getRandom().nextBoolean()) {
            pool = byCohesion(Cohesion.get(player));
        }
        List<String> lines = new ArrayList<>(Speech.fitting(List.of(pool), member.getStage()));
        if (pool != COLD) {
            // Their kind's own business, now and then.
            lines.addAll(Speech.own(member.getStage()));
        }
        return lines.isEmpty() ? null : Lines.pickFrom("bond|" + player.getUUID(), lines, player.getRandom());
    }

    public static void forget(UUID player) {
        lastHeard.keySet().removeIf(key -> key.startsWith(player.toString()));
    }

    private Voices() {
    }
}
