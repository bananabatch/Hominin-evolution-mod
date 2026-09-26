package dev.hominin.evolution.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.survival.Afflictions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Baboons that trust you want paying for it. Now and then one of a troop that has come to trust you walks right up,
 * hand out, and pesters you for food - the way baboons still work people over for snacks. Give it something and the
 * troop is glad of you: they groom you and whoever of your band is near, picking the ticks off, and sometimes one sets
 * off and leads you somewhere you did not know. Ignore it for a minute and the troop goes cool on you: trust is kept
 * by feeding them, not had once.
 */
public final class BaboonBegging {
    /** A minute to hand something over. */
    private static final long WINDOW = 1200L;
    /**
     * Once they may ask again, about one ask a minute and a half near a troop that trusts you (checked every second).
     * It was one in fifteen a second - a baboon at your elbow every quarter of a minute.
     */
    private static final int ODDS = 90;
    /** Fed: they leave you be for ten to fifteen minutes. */
    private static final long REST_AFTER_FED = 12000L;
    /** Ignored: six to eleven minutes before one tries again. */
    private static final long REST_AFTER_IGNORED = 7200L;
    private static final int REST_SPREAD = 6000;
    private static final Map<UUID, Long> nextAsk = new HashMap<>();

    private record Nag(UUID troop, UUID baboon, long until, boolean reminded) {
    }

    private static final Map<UUID, Nag> nags = new HashMap<>();

    /** Every second. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 9 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Nag nag = nags.get(player.getUUID());
        if (nag == null) {
            if (!level.isDay() || now < nextAsk.getOrDefault(player.getUUID(), 0L)
                    || player.getRandom().nextInt(ODDS) != 0) {
                return;
            }
            List<Baboon> near = level.getEntitiesOfClass(Baboon.class, player.getBoundingBox().inflate(24.0D),
                    b -> b.isAlive() && !b.isBaby() && !b.isAngry() && b.getTroop() != null
                            && TroopRelations.isTrusted(player, b.getTroop()));
            if (near.isEmpty()) {
                return;
            }
            Baboon beggar = near.get(player.getRandom().nextInt(near.size()));
            nags.put(player.getUUID(), new Nag(beggar.getTroop(), beggar.getUUID(), now + WINDOW, false));
            beggar.playAmbientSound();
            player.sendSystemMessage(Component.literal("A baboon from the troop walks right up to you, hand out, "
                    + "chattering. It wants food - hand it something.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!(level.getEntity(nag.baboon()) instanceof Baboon beggar) || !beggar.isAlive()) {
            nags.remove(player.getUUID());
            return;
        }
        // It follows you about, close, until it gets something.
        if (beggar.distanceToSqr(player) > 3.0D * 3.0D) {
            beggar.getNavigation().moveTo(player, 1.2D);
        } else {
            beggar.getNavigation().stop();
            beggar.getLookControl().setLookAt(player);
        }
        if (player.getRandom().nextInt(6) == 0) {
            beggar.playAmbientSound();
        }
        if (!nag.reminded() && now > nag.until() - WINDOW / 2) {
            nags.put(player.getUUID(), new Nag(nag.troop(), nag.baboon(), nag.until(), true));
            player.displayClientMessage(Component.literal("The baboon tugs at you and holds its hand out again.")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        if (now >= nag.until()) {
            nags.remove(player.getUUID());
            nextAsk.put(player.getUUID(), now + REST_AFTER_IGNORED + player.getRandom().nextInt(REST_SPREAD));
            // Back to a stranger: they will have to be won round again.
            TroopRelations.setTrustFor(player, nag.troop(),
                    Math.min(TroopRelations.trust(player, nag.troop()), TroopRelations.TRUSTED - 2));
            player.sendSystemMessage(Component.literal("The baboon gives up on you and goes back to its troop. They "
                    + "are cool towards you now - feed them, and they will warm again.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Food handed to a baboon: if it was the one asking, the troop pays it back. */
    public static void fed(ServerPlayer player, Baboon baboon) {
        Nag nag = nags.get(player.getUUID());
        if (nag == null || baboon.getTroop() == null || !baboon.getTroop().equals(nag.troop())) {
            return;
        }
        nags.remove(player.getUUID());
        ServerLevel level = player.serverLevel();
        nextAsk.put(player.getUUID(), level.getGameTime() + REST_AFTER_FED + player.getRandom().nextInt(REST_SPREAD));
        // Grooming: you, and whoever of your band is near.
        Afflictions.relieve(player, Afflictions.Affliction.INFESTED);
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getEyeY(), player.getZ(), 4, 0.4D, 0.3D, 0.4D, 0.0D);
        int groomed = 0;
        for (BandMember member : Band.ownNear(player, 12.0D)) {
            Afflictions.relieve(member, Afflictions.Affliction.INFESTED);
            level.sendParticles(ParticleTypes.HEART, member.getX(), member.getEyeY(), member.getZ(), 2, 0.3D, 0.2D, 0.3D,
                    0.0D);
            groomed++;
        }
        player.sendSystemMessage(Component.literal("The troop comes over and grooms you" + (groomed > 0
                ? " and " + groomed + " of your band" : "") + ", picking the ticks off one by one.")
                .withStyle(ChatFormatting.GREEN));
        TroopRelations.goodwill(player, nag.troop(), 1);
        // Sometimes one of them sets off, and looks back to see you follow.
        if (player.getRandom().nextFloat() < 0.35F) {
            for (dev.hominin.evolution.world.Pois.Poi poi : dev.hominin.evolution.world.Pois.near(level,
                    baboon.blockPosition(), 160, dev.hominin.evolution.band.Bands.erectusOn(player.getData(
                            dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage()))) {
                if (dev.hominin.evolution.world.Pois.knows(player, poi.id())) {
                    continue;
                }
                dev.hominin.evolution.world.Pois.learn(player, poi);
                dev.hominin.evolution.mind.MentalMap.lead(player, poi.pos(), poi.label(), "");
                player.sendSystemMessage(Component.literal("Then one of them sets off and keeps looking back at you: it "
                        + "is taking you somewhere. (" + poi.label() + ", " + (int) Math.sqrt(poi.pos().distSqr(
                                player.blockPosition())) + " blocks - on your map.)").withStyle(ChatFormatting.AQUA));
                baboon.getNavigation().moveTo(poi.pos().getX(), poi.pos().getY(), poi.pos().getZ(), 1.0D);
                break;
            }
        }
    }

    public static void forget(UUID player) {
        nags.remove(player);
        nextAsk.remove(player);
    }

    private BaboonBegging() {
    }
}
