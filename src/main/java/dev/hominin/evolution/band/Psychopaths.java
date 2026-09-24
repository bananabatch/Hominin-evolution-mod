package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Some of the band are not what they seem. A psychopath is not antisocial - quite the opposite: charming, attentive,
 * always the first to groom whoever matters, the first to bring someone what they wanted, generous with the pile
 * (to those close to them). It is all for them. They talk the others out of their best tools, and they make friends
 * of whoever is worth having as one.
 *
 * <p>They are useful. Bring one when you trade or give a gift to another band and you get a better deal - they will
 * find a way to shade it your way - and the band feels good about it.
 *
 * <p>But you never know which one it is: there are only signs. And when times turn desperate and your band looks
 * weak, they go - in the night, to another band, with a story about you that makes that band think the worse of
 * you. Suspect someone (tribe stats: hover a name, press P) and if you are right you can exile them - they will
 * find another band and talk there too - or kill them, which the band will feel. If you are wrong, it costs you.
 */
public final class Psychopaths {
    /** One in twenty, from erectus on - never one who is plainly antisocial. */
    private static final float CHANCE = 0.05F;
    private static final int CHECK_TICKS = 20 * 20;
    private static final int SUSPECT_WRONG_COHESION = 3;
    private static final int SUSPECT_WRONG_BOND = 3;
    private static final int KILLED_COHESION = 6;
    private static final int LIES_STANDING = 12;

    /** ChoosePayload actions: suspecting someone, and what is done about them. */
    public static final int ACTION_SUSPECT = 50;
    public static final int ACTION_VERDICT = 51;
    public static final int EXILE = 1;
    public static final int KILL = 2;
    public static final int LET_BE = 3;

    private static final Map<UUID, Long> lastSign = new HashMap<>();
    private static final Map<UUID, Long> leftOnDay = new HashMap<>();

    /** Rolled once for each grown member, from erectus on - and a band only ever has the one. */
    public static void roll(BandMember member, List<BandMember> band) {
        if (member.psychopathRolled() || member.isBaby() || !Bands.erectusOn(member.getStage())) {
            return;
        }
        boolean taken = band.stream().anyMatch(other -> other != member && other.isPsychopath());
        member.setPsychopath(!taken && !member.isAntisocial() && member.getRandom().nextFloat() < CHANCE);
    }

    /** Never two in one band: whoever came in with it as well as the one already here, it is only ever one. */
    private static void onlyOne(List<BandMember> band) {
        BandMember first = null;
        for (BandMember member : band) {
            if (!member.isPsychopath()) {
                continue;
            }
            if (first == null) {
                first = member;
            } else {
                member.setPsychopath(false);
            }
        }
    }

    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_TICKS != 97) {
            return;
        }
        List<BandMember> band = Band.all(player);
        onlyOne(band);
        band.forEach(member -> roll(member, band));
        nightly(player, band);
        for (BandMember member : band) {
            if (member.isPsychopath() && !member.isBaby()) {
                scheme(player, member, band);
            } else if (!member.isBaby() && member.getRandom().nextInt(12) == 0) {
                // Anyone might do someone a kindness now and then - just not so reliably.
                attendWants(player, member, band);
            }
        }
    }

    // ------------------------------------------------------------ what they do

    private static void scheme(ServerPlayer player, BandMember psycho, List<BandMember> band) {
        int roll = psycho.getRandom().nextInt(8);
        switch (roll) {
            case 0, 1 -> groomWhoMatters(player, psycho, band);
            case 2 -> attendWants(player, psycho, band);
            case 3 -> {
                if (!Needs.metByMember(player, psycho)) {
                    attendWants(player, psycho, band);
                }
            }
            case 4 -> talkOutOfTools(player, psycho, band);
            case 5 -> layForTheirOwn(player, psycho);
            case 6 -> groomYou(player, psycho);
            default -> smoothOver(player, psycho, band);
        }
    }

    /** Grooms whoever is worth it: the closest to the leader, or whoever has the best tools. */
    private static void groomWhoMatters(ServerPlayer player, BandMember psycho, List<BandMember> band) {
        BandMember target = band.stream()
                .filter(m -> m != psycho && !m.isBaby() && m.distanceToSqr(psycho) < 16.0D * 16.0D)
                .max(Comparator.comparingInt(m -> m.getBond() + 2 * bestTool(m)))
                .orElse(null);
        if (target == null) {
            return;
        }
        Grooming.betweenMembers(psycho, target);
        target.addAffinity(psycho, 1);
        target.ensureName();
        psycho.ensureName();
        sign(player, psycho, psycho.getName().getString() + " spends a long time grooming " + target.getName().getString()
                + (bestTool(target) >= 5 ? " - who has the best tools in the band." : " - who is closest to you."));
    }

    /** Somebody wants something the psychopath carries: they get it - from them, first. */
    private static void attendWants(ServerPlayer player, BandMember helper, List<BandMember> band) {
        for (BandMember member : band) {
            if (member == helper || !member.isWantVoiced() || member.getWant() == null
                    || member.distanceToSqr(helper) > 16.0D * 16.0D) {
                continue;
            }
            var want = member.getWant();
            ItemStack given = helper.takeFirst(stack -> stack.is(want));
            if (given.isEmpty()) {
                continue;
            }
            member.addToInventory(given);
            member.clearWant();
            member.addAffinity(helper, 3);
            member.ensureName();
            helper.ensureName();
            player.sendSystemMessage(Component.literal(helper.getName().getString() + " gives " + member.getName().getString()
                    + " the " + given.getHoverName().getString().toLowerCase() + " they wanted - before you could.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
    }

    /** Somebody close to them hands over a good tool. They asked so nicely. */
    private static void talkOutOfTools(ServerPlayer player, BandMember psycho, List<BandMember> band) {
        if (bestTool(psycho) >= 5) {
            return;
        }
        for (BandMember member : band) {
            if (member == psycho || member.isBaby() || member.affinityWith(psycho.getUUID()) < 3
                    || member.distanceToSqr(psycho) > 12.0D * 12.0D) {
                continue;
            }
            ItemStack tool = member.takeFirst(stack -> stack.is(ModTags.Items.STONE_TOOLS)
                    && dev.hominin.evolution.band.goal.ToolPileGoal.rank(stack) >= 4);
            if (tool.isEmpty()) {
                continue;
            }
            psycho.addToInventory(tool);
            member.ensureName();
            psycho.ensureName();
            sign(player, psycho, member.getName().getString() + " hands " + psycho.getName().getString() + " their "
                    + tool.getHoverName().getString().toLowerCase() + ". " + psycho.getName().getString()
                    + " had asked so nicely.");
            return;
        }
    }

    /** Generous with the pile - to those close to them. */
    private static void layForTheirOwn(ServerPlayer player, BandMember psycho) {
        ServerLevel level = player.serverLevel();
        BlockPos pile = ToolPiles.storeWithin(level, player.getUUID(), psycho.blockPosition(), 32.0D);
        if (pile == null || !(level.getBlockEntity(pile) instanceof ToolPileBlockEntity store) || store.isFull()) {
            return;
        }
        ItemStack spare = psycho.takeFirst(stack -> stack.is(ModTags.Items.STONE_TOOLS)
                && !stack.is(psycho.getMainHandItem().getItem()));
        if (spare.isEmpty()) {
            return;
        }
        psycho.ensureName();
        if (store.add(spare, psycho.getUUID(), psycho.getName().getString())) {
            for (int slot = ToolPileBlockEntity.MAX - 1; slot >= 0; slot--) {
                if (psycho.getUUID().equals(store.layerOf(slot))) {
                    store.setMark(slot, ToolPileBlockEntity.FOR_CLOSE);
                    break;
                }
            }
            sign(player, psycho, psycho.getName().getString() + " lays a " + spare.getHoverName().getString().toLowerCase()
                    + " on the pile - for those close to them.");
        } else {
            psycho.addToInventory(spare);
        }
    }

    /** Grooms you, unasked. Very attentive. */
    private static void groomYou(ServerPlayer player, BandMember psycho) {
        if (psycho.distanceToSqr(player) > 6.0D * 6.0D) {
            return;
        }
        psycho.addBond(1);
        player.heal(1.0F);
        psycho.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        psycho.ensureName();
        player.displayClientMessage(Component.literal(psycho.getName().getString()
                + " grooms you without being asked. Very attentive.").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Smooths over a quarrel - and everyone remembers who did. */
    private static void smoothOver(ServerPlayer player, BandMember psycho, List<BandMember> band) {
        List<BandMember> near = new ArrayList<>(band.stream()
                .filter(m -> m != psycho && !m.isBaby() && m.distanceToSqr(psycho) < 16.0D * 16.0D).toList());
        if (near.size() < 2) {
            return;
        }
        BandMember a = near.get(0);
        BandMember b = near.get(1);
        a.addAffinity(psycho, 1);
        b.addAffinity(psycho, 1);
        a.ensureName();
        b.ensureName();
        psycho.ensureName();
        sign(player, psycho, psycho.getName().getString() + " smooths over a quarrel between " + a.getName().getString()
                + " and " + b.getName().getString() + ". Everyone likes " + psycho.getName().getString() + ".");
        Cohesion.addLimited(player, "smoothed_over", 1, 12000L);
    }

    private static int bestTool(BandMember member) {
        int best = dev.hominin.evolution.band.goal.ToolPileGoal.rank(member.getMainHandItem());
        for (int slot = 0; slot < member.getInventory().getContainerSize(); slot++) {
            best = Math.max(best, dev.hominin.evolution.band.goal.ToolPileGoal.rank(member.getInventory().getItem(slot)));
        }
        return best;
    }

    /** A sign - said now and then, not every time, and only when you are about to see it. */
    private static void sign(ServerPlayer player, BandMember psycho, String text) {
        long now = player.level().getGameTime();
        if (psycho.distanceToSqr(player) > 32.0D * 32.0D || now - lastSign.getOrDefault(player.getUUID(), -99999L) < 2400L) {
            return;
        }
        lastSign.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /** For a member's info: the kind of thing people say about them. */
    @Nullable
    public static String signOf(BandMember member) {
        if (member.isPsychopathKnown()) {
            return "You know what they are.";
        }
        if (!member.isPsychopath()) {
            return null;
        }
        String[] signs = {"Charming. Everyone seems to owe them something.", "Never seems troubled by anything.",
                "Always somehow near whoever has the most.", "Tells everyone what they want to hear."};
        return signs[Math.floorMod(member.getUUID().hashCode(), signs.length)];
    }

    // ------------------------------------------------------------ helping, with other bands

    /** One of them is with you: close enough to be part of the dealing. */
    @Nullable
    public static BandMember withYou(ServerPlayer player) {
        for (BandMember member : Band.ownNear(player, 16.0D)) {
            if (member.isPsychopath() && !member.isBaby()) {
                return member;
            }
        }
        return null;
    }

    /** A trade with another band goes your way: what you offer is talked up by a tier. */
    public static int talkUp(ServerPlayer player, BandMember other, int offerTier) {
        if (!other.isWild()) {
            return offerTier;
        }
        BandMember psycho = withYou(player);
        if (psycho == null) {
            return offerTier;
        }
        psycho.ensureName();
        player.displayClientMessage(Component.literal(psycho.getName().getString() + " leans in and talks it up. "
                + "They take it for more than it is worth.").withStyle(ChatFormatting.GOLD), false);
        Cohesion.addLimited(player, "good_deal", 1, 6000L);
        return offerTier + 1;
    }

    /** A gift to another band counts for more: they make sure everyone knows how generous it was. */
    public static int talkUpGift(ServerPlayer player, int worth) {
        BandMember psycho = withYou(player);
        if (psycho == null) {
            return worth;
        }
        psycho.ensureName();
        player.sendSystemMessage(Component.literal(psycho.getName().getString() + " makes sure they know how generous "
                + "that was - more generous, in the telling, than it was.").withStyle(ChatFormatting.GOLD));
        Cohesion.addLimited(player, "good_deal", 1, 6000L);
        return worth + Math.max(1, worth / 2);
    }

    // ------------------------------------------------------------ when things go bad

    /**
     * Desperate times, a dry season, and a band that looks weak - low presence or low cohesion - and a psychopath
     * slips away in the night to a band that looks stronger, with a story about you.
     */
    private static void nightly(ServerPlayer player, List<BandMember> band) {
        ServerLevel level = player.serverLevel();
        long time = level.getDayTime() % 24000L;
        long day = level.getDayTime() / 24000L;
        if (time < 14000L || time > 22000L || leftOnDay.getOrDefault(player.getUUID(), -1L) == day) {
            return;
        }
        boolean hard = dev.hominin.evolution.survival.Seasons.isDry(level) || Bands.desperateTimes(level);
        boolean weak = Presence.get(player) < Presence.WEAK || Cohesion.get(player) < Cohesion.BORDERLINE;
        if (!hard || !weak) {
            return;
        }
        for (BandMember member : band) {
            if (!member.isPsychopath() || member.isBaby() || member.getRandom().nextFloat() > 0.3F) {
                continue;
            }
            leftOnDay.put(player.getUUID(), day);
            leave(player, member);
            return;
        }
    }

    /** Gone in the night, to a stronger band, with a story. */
    public static void leave(ServerPlayer player, BandMember member) {
        Bands.Record to = strongestKnown(player);
        member.ensureName();
        String name = member.getName().getString();
        member.discard();
        if (to == null) {
            player.sendSystemMessage(Component.literal(name + " is gone - slipped away in the night, and took what "
                    + "they carried.").withStyle(ChatFormatting.GOLD));
            return;
        }
        to.size++;
        player.sendSystemMessage(Component.literal(name + " is gone - slipped away in the night to " + to.name
                + ". Whatever they are telling them about you, it is not kind.").withStyle(ChatFormatting.GOLD));
        Relations.change(player, to, -LIES_STANDING, "what " + name + " told them about you");
    }

    @Nullable
    private static Bands.Record strongestKnown(ServerPlayer player) {
        Bands.Record best = null;
        for (Bands.Record band : Bands.all(player.serverLevel())) {
            if (band.nomadic() || !band.knownTo(player.getUUID())) {
                continue;
            }
            if (best == null || band.presence > best.presence) {
                best = band;
            }
        }
        return best;
    }

    /** A band nobody has told you about yet - where an exile goes to start again. */
    @Nullable
    private static Bands.Record strangers(ServerPlayer player) {
        Bands.Record pick = null;
        double nearest = Double.MAX_VALUE;
        for (Bands.Record band : Bands.all(player.serverLevel())) {
            if (band.nomadic() || band.knownTo(player.getUUID())) {
                continue;
            }
            double distance = band.home.distSqr(player.blockPosition());
            if (distance < nearest) {
                nearest = distance;
                pick = band;
            }
        }
        return pick;
    }

    // ------------------------------------------------------------ suspicion

    /** Hovering a name in tribe stats and pressing P: "I think it's them." */
    public static void suspect(ServerPlayer player, int entityId) {
        if (!(player.serverLevel().getEntity(entityId) instanceof BandMember member) || !member.isLedBy(player)
                || member.isBaby()) {
            return;
        }
        member.ensureName();
        String name = member.getName().getString();
        if (!Bands.erectusOn(member.getStage())) {
            player.displayClientMessage(Component.literal("Your band is too small and too close for that kind of "
                    + "thing to hide in it."), true);
            return;
        }
        if (!member.isPsychopath()) {
            member.addBond(-SUSPECT_WRONG_BOND);
            Cohesion.add(player, -SUSPECT_WRONG_COHESION, "accused " + name + " of using everyone");
            player.sendSystemMessage(Component.literal("You were wrong about " + name + ", and they heard what you were "
                    + "thinking. That hurt them - and the band saw you do it. (Bond -" + SUSPECT_WRONG_BOND + ")")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        member.setPsychopathKnown(true);
        player.sendSystemMessage(Component.literal("You were right about " + name + ". Once you see it you cannot stop "
                + "seeing it: all of it was for them.").withStyle(ChatFormatting.GOLD));
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(member.getId(), ACTION_VERDICT,
                "What do you do about " + name + "?",
                List.of("Exile them", "Kill them", "Let it be - for now"), List.of(EXILE, KILL, LET_BE)));
    }

    /** What is done about one you have found out. */
    public static void verdict(ServerPlayer player, int entityId, int choice) {
        if (!(player.serverLevel().getEntity(entityId) instanceof BandMember member) || !member.isLedBy(player)
                || !member.isPsychopathKnown()) {
            return;
        }
        member.ensureName();
        String name = member.getName().getString();
        switch (choice) {
            case EXILE -> {
                Bands.Record to = strangers(player);
                member.discard();
                player.sendSystemMessage(Component.literal("You send " + name + " away. Nobody argues. They walk off "
                        + "with what they carry - to somebody who does not know them yet.").withStyle(ChatFormatting.GOLD));
                if (to != null) {
                    // They get there first, with their story: that band will think less of you when you meet it.
                    to.size++;
                    to.rumours.merge(player.getUUID(), 4, Integer::sum);
                    Bands.changed(player.serverLevel());
                }
            }
            case KILL -> {
                // Not a blow the band's own rules would stop: it is decided, and done.
                member.kill();
                Cohesion.add(player, -KILLED_COHESION, "killed " + name + ", one of your own");
                player.sendSystemMessage(Component.literal("It is done. Whatever " + name + " was, the band loved them - "
                        + "or thought it did. They will not forget who did it.").withStyle(ChatFormatting.DARK_RED));
            }
            default -> player.displayClientMessage(Component.literal("You let it be. For now. You are watching them.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    public static void forget(UUID player) {
        lastSign.remove(player);
        leftOnDay.remove(player);
    }

    private Psychopaths() {
    }
}
