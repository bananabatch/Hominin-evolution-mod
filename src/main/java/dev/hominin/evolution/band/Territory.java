package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

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
        return claim == null || claim.granted() || claim.uses() < warnAt(member);
    }

    /** How much of their ground a band watches you use before it speaks: less in the dry, more in the rains. */
    private static int warnAt(net.minecraft.world.entity.Entity near) {
        var level = near.level();
        return dev.hominin.evolution.survival.Seasons.strained(level) ? WARN_AT / 2
                : dev.hominin.evolution.survival.Seasons.plentiful(level) ? WARN_AT * 2 : WARN_AT;
    }

    // ------------------------------------------------------------ neighbours, by season

    private static final Map<UUID, Long> metThisSeason = new HashMap<>();
    private static final double NEIGHBOUR_RANGE = 10.0D;

    /**
     * Walking up to another band's camp. In the dry they bare their teeth and tell you to keep
     * away; in the rains somebody comes over with food. Once per band per season.
     */
    public static void tickNeighbours(ServerPlayer player) {
        if (player.tickCount % 40 != 20 || player.isSpectator()) {
            return;
        }
        boolean hard = dev.hominin.evolution.survival.Seasons.strained(player.level());
        boolean plenty = dev.hominin.evolution.survival.Seasons.plentiful(player.level());
        if (!hard && !plenty) {
            return;
        }
        long season = dev.hominin.evolution.survival.Drought.dayOf(player.level()) / dev.hominin.evolution.survival.Seasons.DAYS;
        for (BandMember member : Band.near(player, NEIGHBOUR_RANGE)) {
            UUID band = member.getBandId();
            if (!member.isWild() || band == null || member.isBaby() || member.isGuestOf(player)
                    || Paranthropus.is(member)) {
                continue;
            }
            Claim claim = claims.get(band);
            if (claim != null && claim.granted()) {
                continue;
            }
            UUID key = new UUID(band.getMostSignificantBits() ^ player.getUUID().getMostSignificantBits(),
                    band.getLeastSignificantBits() ^ season);
            if (metThisSeason.containsKey(key)) {
                continue;
            }
            if (metThisSeason.size() > 2048) {
                metThisSeason.clear();
            }
            metThisSeason.put(key, season);
            member.ensureName();
            String name = member.getName().getString();
            if (hard) {
                Band.memberDisplay(member, 2);
                player.sendSystemMessage(Component.literal("<" + name + "> ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(member.getRandom().nextBoolean()
                                ? "There is not enough here for you as well. Keep walking."
                                : "Not this season. Go and find your own water.").withStyle(ChatFormatting.WHITE)));
            } else {
                ItemStack food = member.takeFood();
                if (food.isEmpty()) {
                    food = new ItemStack(net.minecraft.world.item.Items.SWEET_BERRIES, 2);
                }
                member.getNavigation().moveTo(player, 1.0D);
                player.sendSystemMessage(Component.literal("<" + name + "> ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("There is plenty this season. Here - eat.")
                                .withStyle(ChatFormatting.WHITE)));
                player.displayClientMessage(Component.literal(name + " hands you " + food.getHoverName().getString()
                        + ".").withStyle(ChatFormatting.GREEN), true);
                if (!player.getInventory().add(food)) {
                    player.drop(food, false);
                }
            }
            return;
        }
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
            // Paranthropus do not claim ground by word - they just strip it (see Paranthropus.forageShare).
            if (!member.isWild() || band == null || member.isGuestOf(player) || Paranthropus.is(member)) {
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
        if (uses < warnAt(player) || now - claim.lastWord() < COOLDOWN_TICKS) {
            return;
        }
        claims.put(band, new Claim(claim.home(), uses, false, now));
        member.ensureName();
        String name = member.getName().getString();
        boolean drought = dev.hominin.evolution.survival.Seasons.strained(player.level());
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
        // In the rains nobody minds company. In hard times, only those who have paid their way.
        if (dev.hominin.evolution.survival.Seasons.plentiful(player.level())) {
            return true;
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
