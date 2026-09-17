package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.survival.Drought;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Whose water that is. A band settles where the stone and the water are, and it notices
 * who else drinks there - water and workable stone were contested, and a band that has
 * to share says so.
 *
 * <p>Habilis sulks: they keep away from you by day until you ask. Erectus comes and tells
 * you to your face, and may want something for it. During a drought there is nothing to
 * spare and everybody is sharper about it.
 */
public final class Territory {
    /** How close to a band's ground counts as using their water or their stone. */
    private static final double HOME_RADIUS = 28.0D;
    /** Uses before they say something, and before they want paying. */
    private static final int WARN_AT = 4;
    private static final int DEMAND_AT = 9;
    private static final int COOLDOWN_TICKS = 600;
    /** What a band considers fair payment for the season. */
    private static final int REPARATION_TIER = 3;

    private record Claim(BlockPos home, int uses, boolean granted, long lastWord) {
    }

    private static final Map<UUID, Claim> claims = new HashMap<>();

    /** A band has settled here: this is the ground they will speak up about. */
    public static void settle(UUID bandId, BlockPos home) {
        claims.put(bandId, new Claim(home, 0, false, 0L));
    }

    @Nullable
    public static BlockPos homeOf(UUID bandId) {
        Claim claim = claims.get(bandId);
        return claim == null ? null : claim.home();
    }

    public static boolean hasAccess(BandMember member) {
        UUID band = member.getBandId();
        Claim claim = band == null ? null : claims.get(band);
        return claim == null || claim.granted() || claim.uses() < WARN_AT;
    }

    /** Asking, trading, or travelling together buys the right to drink there. */
    public static void grantAccess(BandMember member, ServerPlayer player) {
        UUID band = member.getBandId();
        Claim claim = band == null ? null : claims.get(band);
        if (claim == null || claim.granted()) {
            return;
        }
        claims.put(band, new Claim(claim.home(), 0, true, claim.lastWord()));
        player.sendSystemMessage(Component.literal(member.getName().getString()
                + "'s band will share their water and stone with you now.").withStyle(ChatFormatting.GREEN));
    }

    /**
     * The player has foraged, drunk or worked stone here. If it belongs to somebody, they
     * will have noticed.
     */
    public static void usedResource(ServerPlayer player, BlockPos where) {
        for (BandMember member : Band.near(player, HOME_RADIUS)) {
            UUID band = member.getBandId();
            if (!member.isWild() || band == null || member.isGuestOf(player)) {
                continue;
            }
            Claim claim = claims.get(band);
            BlockPos home = claim == null ? member.blockPosition() : claim.home();
            if (home.distSqr(where) > HOME_RADIUS * HOME_RADIUS) {
                continue;
            }
            boolean granted = claim != null && claim.granted();
            long lastWord = claim == null ? 0L : claim.lastWord();
            int uses = (claim == null ? 0 : claim.uses()) + (granted ? 0 : 1);
            claims.put(band, new Claim(home, uses, granted, lastWord));
            if (!granted) {
                maybeSpeak(player, member, band, uses);
            }
            return;
        }
    }

    private static void maybeSpeak(ServerPlayer player, BandMember member, UUID band, int uses) {
        Claim claim = claims.get(band);
        long now = player.level().getGameTime();
        if (uses < WARN_AT || now - claim.lastWord() < COOLDOWN_TICKS) {
            return;
        }
        claims.put(band, new Claim(claim.home(), uses, false, now));
        member.ensureName();
        String name = member.getName().getString();
        boolean drought = Drought.isActive(player.level());
        boolean erectus = member.getStage().getPath().equals("homo_erectus")
                || member.getStage().getPath().equals("homo_heidelbergensis")
                || member.getStage().getPath().equals("homo_sapiens");
        for (BandMember other : Band.near(member, 20.0D)) {
            if (band.equals(other.getBandId())) {
                other.raiseAlarm(200);
            }
        }
        if (!erectus) {
            player.sendSystemMessage(Component.literal(name + "'s band watch you take from their ground, and turn away. "
                    + "They will not walk with you while this keeps up.").withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.literal(
                    "(Trade with them, or ask them to travel with you, and they will share it.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (uses >= DEMAND_AT || drought) {
            member.getNavigation().moveTo(player, 1.2D);
            Band.memberDisplay(member, 4);
            player.sendSystemMessage(Component.literal("<" + name + "> ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(drought
                            ? "There is barely enough here for us. Take no more of it."
                            : "This water is ours. If you are drinking it, bring us something for it.")
                            .withStyle(ChatFormatting.WHITE)));
            player.sendSystemMessage(Component.literal("(Offer them something Crafted or better to settle it.)")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        player.sendSystemMessage(Component.literal("<" + name + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Do not come back to this water. It is ours.")
                        .withStyle(ChatFormatting.WHITE)));
    }

    /** Something offered in trade settles the debt, if it was worth enough. */
    public static void offered(BandMember member, ServerPlayer player, ItemStack offered) {
        UUID band = member.getBandId();
        if (band == null || !claims.containsKey(band)) {
            return;
        }
        if (Trading.tierOf(offered, member.getStage()) >= REPARATION_TIER) {
            grantAccess(member, player);
        }
    }

    /** Whether this band will walk with the player today. */
    public static boolean willTravelWith(BandMember member, ServerPlayer player) {
        if (Drought.isActive(player.level()) && !hasAccess(member)) {
            return false;
        }
        return hasAccess(member);
    }

    /** Forgets bands that are gone, so the map does not grow forever. */
    public static void forgetBandsNotIn(List<UUID> alive) {
        claims.keySet().retainAll(alive);
    }

    private Territory() {
    }
}
