package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.guide.Alerts;
import dev.hominin.evolution.hunt.Predation;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Now and then a few of another band wander onto your ground, foraging as if it were theirs. They are marked out
 * (they glow) and one of your band tells you where. Walk up to them and you can make them pay for what they took:
 * nine times in ten they give in and hand something over. Or let them be, or drive them off. Leave them alone and
 * they finish and go - and the country notices that nobody stopped them.
 */
public final class Intruders {
    /** ChoicesPayload action: what to do about them. */
    public static final int ACTION = 61;
    public static final int DEMAND = 1;
    public static final int LET_BE = 2;
    public static final int DRIVE_OFF = 3;

    private static final int CHECK_TICKS = 600;
    /** One in this many checks, once settled - about every twenty minutes. */
    private static final int ODDS = 40;
    private static final long STAY_TICKS = 4 * 60 * 20L;
    private static final double MEET = 8.0D;
    private static final float CONCEDE = 0.9F;
    private static final String NEXT = "intruders_next_minute";

    private record Intrusion(UUID band, long until, boolean asked) {
    }

    private static final Map<UUID, Intrusion> intrusions = new HashMap<>();

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 13 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Intrusion intrusion = intrusions.get(player.getUUID());
        if (intrusion != null) {
            follow(player, level, intrusion);
            return;
        }
        if (player.tickCount % CHECK_TICKS != 13) {
            return;
        }
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int minute = (int) (level.getGameTime() / 1200L);
        if (minute < counters.getOrDefault(NEXT, 0) || !Predation.settled(player)
                || !Predation.onOwnGround(player, player.blockPosition()) || Claims.busy(player)
                || player.getRandom().nextInt(ODDS) != 0) {
            return;
        }
        Bands.Record band = pick(player, level);
        if (band == null) {
            return;
        }
        BlockPos spot = spotFor(player, level);
        if (spot == null) {
            return;
        }
        int count = 2 + player.getRandom().nextInt(3);
        WildBands.placeBand(level, spot, band.species, count, band.id, player.getRandom());
        List<BandMember> theirs = members(level, band, spot, 12.0D);
        if (theirs.isEmpty()) {
            return;
        }
        counters.put(NEXT, minute + 20 * 20);
        if (!band.knownTo(player.getUUID())) {
            Relations.meet(player, band, "they wandered onto your ground");
        }
        intrusions.put(player.getUUID(), new Intrusion(band.id, level.getGameTime() + STAY_TICKS, false));
        glow(theirs);
        tell(player, level, band, spot);
    }

    /** A band from round about - not an ally, who would have asked. */
    @Nullable
    private static Bands.Record pick(ServerPlayer player, ServerLevel level) {
        List<Bands.Record> near = new ArrayList<>();
        for (Bands.Record band : Bands.all(level)) {
            if (band.size > 0 && Relations.standing(player, band) < Relations.ALLIED
                    && Bands.horizontal(band.home, player.blockPosition()) < 500.0D * 500.0D) {
                near.add(band);
            }
        }
        return near.isEmpty() ? null : near.get(player.getRandom().nextInt(near.size()));
    }

    /** Somewhere on your ground, a way off from you: 30 to 55 blocks. */
    @Nullable
    private static BlockPos spotFor(ServerPlayer player, ServerLevel level) {
        for (int attempt = 0; attempt < 10; attempt++) {
            float angle = player.getRandom().nextFloat() * Mth.TWO_PI;
            int distance = 30 + player.getRandom().nextInt(26);
            int x = player.getBlockX() + Math.round(Mth.cos(angle) * distance);
            int z = player.getBlockZ() + Math.round(Mth.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Predation.onOwnGround(player, at) && level.getFluidState(at.below()).isEmpty()
                    && level.getFluidState(at).isEmpty()) {
                return at;
            }
        }
        return null;
    }

    private static List<BandMember> members(ServerLevel level, Bands.Record band, BlockPos around, double radius) {
        return level.getEntitiesOfClass(BandMember.class, new net.minecraft.world.phys.AABB(around).inflate(radius),
                m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby());
    }

    private static void glow(List<BandMember> theirs) {
        for (BandMember member : theirs) {
            member.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30 * 20, 0, false, false));
        }
    }

    /** One of your band points them out. */
    private static void tell(ServerPlayer player, ServerLevel level, Bands.Record band, BlockPos spot) {
        int distance = (int) Math.sqrt(Bands.horizontal(spot, player.blockPosition()));
        String where = distance + " blocks " + WildBands.bearingFrom(player, spot);
        List<BandMember> own = Band.ownNear(player, 32.0D);
        own.removeIf(BandMember::isBaby);
        String who = "";
        if (!own.isEmpty()) {
            BandMember spotter = own.get(player.getRandom().nextInt(own.size()));
            spotter.ensureName();
            who = spotter.getName().getString();
            player.sendSystemMessage(Component.literal("<" + who + "> ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Look - " + BandNames.capital(band.name) + ", on our ground. Over there, "
                            + where + ". Picking it over like it's theirs.").withStyle(ChatFormatting.WHITE)));
        }
        Alerts.urgent(player, Alerts.Kind.BAND, Component.literal("Some of " + BandNames.capital(band.name)
                + " are foraging on your ground - " + where + " (they glow). Walk up to them to make them pay for it, "
                + "or leave them be.").withStyle(ChatFormatting.GOLD));
    }

    /** While they are here: keep them marked, ask when you reach them, and see them off when their time is up. */
    private static void follow(ServerPlayer player, ServerLevel level, Intrusion intrusion) {
        Bands.Record band = Bands.get(level, intrusion.band());
        List<BandMember> theirs = band == null ? List.of() : members(level, band, player.blockPosition(), 96.0D);
        long now = level.getGameTime();
        if (band == null || theirs.isEmpty()) {
            intrusions.remove(player.getUUID());
            return;
        }
        if (now > intrusion.until()) {
            intrusions.remove(player.getUUID());
            if (!intrusion.asked()) {
                Presence.add(player, -1, "strangers foraged your ground and nobody stopped them");
                player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " finish with your ground "
                        + "and go, and nobody stopped them.").withStyle(ChatFormatting.GRAY));
            }
            Relations.sendHomeFrom(player, band);
            return;
        }
        if (now % 400L < 20L) {
            glow(theirs);
        }
        if (intrusion.asked()) {
            return;
        }
        BandMember closest = theirs.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(player))).get();
        if (closest.distanceToSqr(player) > MEET * MEET) {
            return;
        }
        intrusions.put(player.getUUID(), new Intrusion(intrusion.band(), intrusion.until(), true));
        closest.getNavigation().stop();
        closest.getLookControl().setLookAt(player);
        closest.ensureName();
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(closest.getId(), ACTION,
                closest.getName().getString() + " of " + BandNames.capital(band.name) + " looks up from your ground.",
                List.of("Make them pay for using our land", "Let them forage", "Drive them off"),
                List.of(DEMAND, LET_BE, DRIVE_OFF)));
    }

    /** What you said to them. */
    public static void choose(ServerPlayer player, int entityId, int choice) {
        ServerLevel level = player.serverLevel();
        if (!(level.getEntity(entityId) instanceof BandMember speaker) || speaker.getBandId() == null
                || speaker.distanceToSqr(player) > 24.0D * 24.0D) {
            return;
        }
        Bands.Record band = Bands.get(level, speaker.getBandId());
        if (band == null) {
            return;
        }
        String them = BandNames.capital(band.name);
        speaker.ensureName();
        String name = speaker.getName().getString();
        switch (choice) {
            case DEMAND -> {
                if (player.getRandom().nextFloat() < CONCEDE) {
                    List<ItemStack> paid = pay(level, band, speaker);
                    for (ItemStack stack : paid) {
                        if (!player.getInventory().add(stack.copy())) {
                            player.drop(stack.copy(), false);
                        }
                    }
                    Relations.change(player, band, -2, "you made them pay for foraging your ground");
                    Presence.add(player, 1, "strangers paid to use your ground");
                    Cohesion.addLimited(player, "tribute_taken", 1, 12000L);
                    dev.hominin.evolution.EvolutionManager.incrementCriterion(player, "demand_tribute", 1);
                    say(player, name, "Alright. Alright. Here - for what we took.");
                    player.sendSystemMessage(Component.literal(them + " hand over " + ToolPiles.describe(paid)
                            + " and go.").withStyle(ChatFormatting.GREEN));
                } else {
                    Relations.change(player, band, -4, "you tried to make them pay for open ground");
                    say(player, name, "Yours? Nobody owns the grass. We're going - but not because you said.");
                }
            }
            case LET_BE -> {
                Relations.change(player, band, 2, "you let them forage your ground");
                say(player, name, "Good of you. We won't take much.");
            }
            default -> {
                Relations.change(player, band, -5, "you drove them off your ground");
                Presence.add(player, 1, "you drove strangers off your ground");
                for (BandMember member : members(level, band, speaker.blockPosition(), 24.0D)) {
                    dev.hominin.evolution.combat.Scare.scare(member, player.position(), 20 * 20);
                }
                for (BandMember own : Band.ownNear(player, 16.0D)) {
                    if (!own.isBaby()) {
                        Band.memberDisplay(own, own.getRandom().nextInt(10));
                    }
                }
                player.sendSystemMessage(Component.literal("Your band takes up the call, and " + them
                        + " scatter off your ground.").withStyle(ChatFormatting.GOLD));
            }
        }
        intrusions.remove(player.getUUID());
        Relations.sendHomeFrom(player, band);
    }

    /** What they hand over: the best of what they carry and keep, up to a fair price - or meat, if they have nothing. */
    private static List<ItemStack> pay(ServerLevel level, Bands.Record band, BandMember speaker) {
        List<Goods.Entry> goods = new ArrayList<>(Goods.theirs(level, band, speaker));
        goods.sort((a, b) -> Integer.compare(Trading.valueOf(b.stack(), band.species),
                Trading.valueOf(a.stack(), band.species)));
        int price = 6 + level.random.nextInt(5);
        int worth = 0;
        List<ItemStack> paid = new ArrayList<>();
        for (Goods.Entry entry : goods) {
            if (worth >= price) {
                break;
            }
            int each = Math.max(1, Trading.valueOf(entry.stack(), band.species));
            int take = Math.max(1, Math.min(entry.stack().getCount(), (price - worth) / each));
            ItemStack taken = Goods.take(level, null, entry, take);
            if (!taken.isEmpty()) {
                paid.add(taken);
                worth += each * taken.getCount();
            }
        }
        if (paid.isEmpty()) {
            paid.add(new ItemStack(ModItems.MEAT_CHUNK.get(), 3));
        }
        return paid;
    }

    private static void say(ServerPlayer player, String name, String line) {
        player.sendSystemMessage(Component.literal("<" + name + "> ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    public static void forget(UUID player) {
        intrusions.remove(player);
    }

    private Intruders() {
    }
}
