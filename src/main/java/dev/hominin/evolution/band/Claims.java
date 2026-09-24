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
import dev.hominin.evolution.network.EncounterPayload;
import dev.hominin.evolution.world.Land;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Other bands and your ground. Ground worth having - high {@link Land pressure} - is ground other bands
 * want, and what they do about it follows where they stand with you:
 *
 * <ul>
 * <li><b>Friends and allies</b> come and offer to pay for the use of it.</li>
 * <li><b>Unfriendly and hostile</b> bands want paying to leave you on it - or want it, and come to drive you
 * off.</li>
 * <li><b>Neutral</b> bands might do any of the three; whichever they try first is what they keep trying,
 * until it fails.</li>
 * </ul>
 *
 * <p>How hard they push follows how badly they need it: every band has a <b>desperation</b>, 1 to 5, that
 * climbs through dry days and dry seasons and falls in good times. A desperate band warns you first -
 * <i>someone hungry has decided a different route to provide</i> - and a strong presence puts most of them
 * off, though not the most desperate.
 *
 * <p>They walk up to you and say it, face to face, and you answer on a screen: accept or decline an offer;
 * to a threat, give in, fight, or flee (dropping some of what you carry, and they chase you). Pack up and
 * hide, and they find your camp empty and take what is lying there - and on rich ground, they may simply
 * move onto it.
 *
 * <p>It works the other way too. In hard times friends come asking you to join a raid, for a share; and
 * your own band, desperate, or strong on poor ground, will point out a weaker neighbour's better ground.
 * Break that band in a raid, then set your territory on their ground, and it is yours.
 */
public final class Claims {
    public enum Kind {
        OFFER, RANSOM, CLAIM, FOOD_RAID, TRESPASS, HELP, TRADE
    }

    public static final int ACCEPT = 0;
    public static final int DECLINE = 1;
    public static final int GIVE_IN = 2;
    public static final int FIGHT = 3;
    public static final int FLEE = 4;
    public static final int SHARE = 5;

    /** What a band sticks to with you once it has tried it. */
    static final int STANCE_NONE = 0;
    static final int STANCE_OFFER = 1;
    static final int STANCE_RANSOM = 2;
    static final int STANCE_CLAIM = 3;

    private static final long APPROACH_TICKS = 2400L;
    private static final long ANSWER_TICKS = 30 * 20L;
    private static final long ACCESS_TICKS = 5 * 24000L;
    private static final long BAND_GAP = 2 * 24000L;
    private static final String NEXT_ENCOUNTER = "claims_next_minute";

    /** A band on its way to you with something to say. */
    private record Approach(UUID band, Kind kind, BlockPos camp, long until, @Nullable UUID target) {
    }

    /** Said, and waiting on your answer. */
    private record Pending(UUID band, Kind kind, List<ItemStack> items, long until, @Nullable UUID target,
            @Nullable UUID speaker) {
    }

    /** A raid you joined, settled once it is over. */
    private record Joint(UUID ally, UUID target, long due) {
    }

    private record Chase(UUID band, long until) {
    }

    private static final Map<UUID, Approach> approaches = new HashMap<>();
    private static final Map<UUID, Pending> pending = new HashMap<>();
    private static final Map<UUID, Joint> joints = new HashMap<>();
    private static final Map<UUID, Chase> chases = new HashMap<>();
    /** Bands you broke in a raid, and when: their ground is yours to take for a day. */
    private static final Map<String, Long> broken = new HashMap<>();
    private static final Map<String, Long> bandGap = new HashMap<>();

    // ------------------------------------------------------------ desperation

    public static String desperationLabel(int desperation) {
        return switch (desperation) {
            case 1 -> "comfortable";
            case 2 -> "getting by";
            case 3 -> "hungry";
            case 4 -> "desperate";
            default -> "starving - they will try anything";
        };
    }

    public static String meter(int desperation) {
        return "■".repeat(desperation) + "□".repeat(5 - desperation);
    }

    /** Your own band's: the season, the day, how hungry they are, how worked-out the ground. */
    public static int ownDesperation(ServerPlayer player) {
        var level = player.level();
        int d = 1;
        if (dev.hominin.evolution.survival.Seasons.isDry(level)) {
            d++;
        }
        if (dev.hominin.evolution.survival.Drought.isActive(level)) {
            d++;
        }
        List<BandMember> band = Band.all(player);
        if (!band.isEmpty()) {
            double hunger = band.stream().mapToInt(BandMember::getHunger).average().orElse(BandMember.MAX_HUNGER);
            if (hunger < 8) {
                d += 2;
            } else if (hunger < 13) {
                d++;
            }
        }
        if (dev.hominin.evolution.survival.Soils.driedFor(player.serverLevel(), player.blockPosition()) > 0.0F) {
            d++;
        }
        if (dev.hominin.evolution.survival.Drought.isProsperousDay(level)) {
            d--;
        }
        return Mth.clamp(d, 1, 5);
    }

    // ------------------------------------------------------------ every two seconds

    public static void tick(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        if (player.tickCount % 10 == 3) {
            tickChase(player);
        }
        if (player.tickCount % 40 != 29) {
            return;
        }
        ServerLevel level = player.serverLevel();
        tickPending(player);
        tickApproach(player, level);
        gangFollow(player, level);
        tickJoint(player, level);
        if (player.tickCount % 1200 == 29) {
            consider(player, level);
            considerNight(player, level);
        }
        if (player.tickCount % 6000 == 29) {
            suggest(player, level);
        }
    }

    private static int minute(ServerPlayer player) {
        return (int) (player.level().getGameTime() / 1200L);
    }

    /** Once a minute: does anyone want something from you, on account of your ground? */
    private static void consider(ServerPlayer player, ServerLevel level) {
        if (approaches.containsKey(player.getUUID()) || pending.containsKey(player.getUUID())
                || Relations.hasDemand(player) || Band.all(player).isEmpty()
                || !dev.hominin.evolution.hunt.Predation.settled(player)
                || dev.hominin.evolution.stage.CutsceneGuard.isProtected(player)) {
            return;
        }
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        if (minute(player) < counters.getOrDefault(NEXT_ENCOUNTER, 0)) {
            return;
        }
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        int pressure = Land.ofPlayer(player).total();
        int presence = Presence.get(player);
        long now = level.getGameTime();
        List<Bands.Record> bands = new ArrayList<>(Bands.all(level));
        java.util.Collections.shuffle(bands, new java.util.Random(player.getRandom().nextLong()));
        for (Bands.Record band : bands) {
            if (band.nomadic() || !band.knownTo(player.getUUID()) || band.size <= 0
                    || Bands.horizontal(band.home, camp) > 320.0D * 320.0D
                    || now < bandGap.getOrDefault(player.getUUID() + "/" + band.id, 0L)) {
                continue;
            }
            int standing = Relations.standing(player, band);
            float need = (band.desperation - 1) / 4.0F;
            float want = pressure / 10.0F;
            float deter = presence >= 45 ? 0.3F : presence >= 35 ? 0.5F : presence >= 25 ? 0.8F : 1.0F;
            if (band.desperation >= 4) {
                // Desperate enough, and presence stops mattering so much.
                deter = Math.max(deter, 0.7F);
            }
            // Allies come by to trade now and then.
            if (standing >= Relations.ALLIED && player.getRandom().nextFloat() < 0.012F) {
                begin(player, level, band, Kind.TRADE, null);
                return;
            }
            // In hard times friends come asking for help, not for ground.
            if (standing >= Relations.FRIENDLY && band.desperation >= 3 && player.getRandom().nextFloat() < 0.012F) {
                Bands.Record target = helpTarget(level, band, player);
                if (target != null) {
                    begin(player, level, band, Kind.HELP, target.id);
                    return;
                }
            }
            Kind kind = null;
            float chance;
            if (standing >= Relations.FRIENDLY) {
                chance = pressure >= 4 && !hasAccess(player, band) ? 0.02F * want * (1.0F + need) : 0.0F;
                kind = Kind.OFFER;
            } else if (standing <= Relations.UNFRIENDLY) {
                chance = (0.015F + 0.03F * need) * want * deter;
                kind = pressure >= 6 && band.presence > presence ? Kind.CLAIM : Kind.RANSOM;
            } else {
                int stance = band.stance.getOrDefault(player.getUUID(), STANCE_NONE);
                kind = stance == STANCE_OFFER ? Kind.OFFER : stance == STANCE_RANSOM ? Kind.RANSOM
                        : stance == STANCE_CLAIM ? Kind.CLAIM : pickNeutral(player, band, pressure);
                chance = 0.012F * want * (1.0F + need) * (kind == Kind.OFFER ? 1.0F : deter);
                if (kind == Kind.OFFER && hasAccess(player, band)) {
                    chance = 0.0F;
                }
            }
            if (kind == Kind.RANSOM || kind == Kind.CLAIM) {
                chance *= fearFactor(player) * (Bands.desperateTimes(level) ? 1.6F : 1.0F);
            }
            if (pressure < 3 && band.desperation < 3) {
                chance = 0.0F;
            }
            if (chance <= 0.0F || player.getRandom().nextFloat() >= chance) {
                continue;
            }
            begin(player, level, band, kind, null);
            return;
        }
    }

    /** A neutral band's first move: whatever it picks, it sticks to until it fails. */
    private static Kind pickNeutral(ServerPlayer player, Bands.Record band, int pressure) {
        float offer = 4 - band.desperation * 0.6F;
        float ransom = 1.0F + band.desperation * 0.5F;
        float claim = pressure >= 6 ? band.desperation * 0.5F : 0.0F;
        float roll = player.getRandom().nextFloat() * (offer + ransom + claim);
        return roll < offer ? Kind.OFFER : roll < offer + ransom ? Kind.RANSOM : Kind.CLAIM;
    }

    public static boolean hasAccess(ServerPlayer player, Bands.Record band) {
        return band.accessUntil.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime();
    }

    // ------------------------------------------------------------ coming to you

    /** A band sets out to you. The desperate ones are heard about first. */
    private static void begin(ServerPlayer player, ServerLevel level, Bands.Record band, Kind kind, @Nullable UUID target) {
        long now = level.getGameTime();
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        approaches.put(player.getUUID(), new Approach(band.id, kind, camp, now + APPROACH_TICKS, target));
        bandGap.put(player.getUUID() + "/" + band.id, now + BAND_GAP);
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        counters.put(NEXT_ENCOUNTER, minute(player) + 20 + player.getRandom().nextInt(20));
        if (kind == Kind.OFFER || kind == Kind.RANSOM || kind == Kind.CLAIM) {
            int stance = kind == Kind.OFFER ? STANCE_OFFER : kind == Kind.RANSOM ? STANCE_RANSOM : STANCE_CLAIM;
            band.stance.putIfAbsent(player.getUUID(), stance);
            Bands.changed(level);
        }
        boolean friendly = kind == Kind.OFFER || kind == Kind.HELP || kind == Kind.TRADE;
        ensureEnvoys(player, level, band, friendly ? 1 + player.getRandom().nextInt(2) : 2 + player.getRandom().nextInt(2));
        if (!friendly) {
            gangUp(player, level, band, toward(player, band.home, 38));
        }
        if ((kind == Kind.RANSOM || kind == Kind.CLAIM) && band.desperation >= 3) {
            warnDesperate(player, band);
        } else if (kind == Kind.RANSOM || kind == Kind.CLAIM) {
            player.sendSystemMessage(Component.literal("Some of " + band.name + " are on their way to you - "
                    + (int) Math.sqrt(Bands.horizontal(Relations.whereIs(level, band), player.blockPosition())) + " blocks "
                    + WildBands.bearingFrom(player, Relations.whereIs(level, band)) + ". They do not look friendly.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Developer: this band sets out to you now, with this in mind. */
    public static void devBegin(ServerPlayer player, Bands.Record band, Kind kind) {
        approaches.remove(player.getUUID());
        pending.remove(player.getUUID());
        begin(player, player.serverLevel(), band, kind, null);
    }

    /** Developer: this band comes in the night, now. */
    public static void devNightRaid(ServerPlayer player, Bands.Record band) {
        nightRaid(player, player.serverLevel(), band);
    }

    /** The warning a desperate band gives: somebody hungry has decided on another way to eat. */
    public static void warnDesperate(ServerPlayer player, Bands.Record band) {
        BlockPos where = Relations.whereIs(player.serverLevel(), band);
        player.sendSystemMessage(Component.literal("Someone hungry has decided a different route to provide. ("
                + (int) Math.sqrt(Bands.horizontal(where, player.blockPosition())) + " blocks "
                + WildBands.bearingFrom(player, where) + ")").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.DESPERATE);
    }

    /** Some of them near enough to walk over - over the horizon if none are about. */
    private static void ensureEnvoys(ServerPlayer player, ServerLevel level, Bands.Record band, int count) {
        if (!envoys(level, band, player, 96.0D).isEmpty()) {
            return;
        }
        double angle = Math.atan2(band.home.getZ() - player.getZ(), band.home.getX() - player.getX());
        int x = (int) (player.getX() + Math.cos(angle) * 40.0D);
        int z = (int) (player.getZ() + Math.sin(angle) * 40.0D);
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return;
        }
        BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        WildBands.placeBand(level, at, band.species, count, band.id, player.getRandom());
    }

    private static List<BandMember> envoys(ServerLevel level, Bands.Record band, ServerPlayer around, double radius) {
        return level.getEntitiesOfClass(BandMember.class, around.getBoundingBox().inflate(radius),
                m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby());
    }

    private static void tickApproach(ServerPlayer player, ServerLevel level) {
        Approach approach = approaches.get(player.getUUID());
        if (approach == null) {
            return;
        }
        Bands.Record band = Bands.get(level, approach.band());
        long now = level.getGameTime();
        if (band == null || now > approach.until()) {
            approaches.remove(player.getUUID());
            return;
        }
        List<BandMember> envoys = envoys(level, band, player, 128.0D);
        if (envoys.isEmpty()) {
            if (now > approach.until() - APPROACH_TICKS / 2) {
                ensureEnvoys(player, level, band, 2);
            }
            return;
        }
        // Gone to ground: packed up, or away from camp. They come to your camp instead.
        boolean hiding = (approach.kind() == Kind.RANSOM || approach.kind() == Kind.CLAIM)
                && (!dev.hominin.evolution.hunt.Predation.settled(player)
                        || player.blockPosition().distSqr(approach.camp()) > 48.0D * 48.0D);
        BandMember closest = envoys.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(player))).get();
        if (hiding) {
            BandMember lead = envoys.stream().min(Comparator.comparingDouble(
                    m -> m.blockPosition().distSqr(approach.camp()))).get();
            for (BandMember envoy : envoys) {
                envoy.getNavigation().moveTo(approach.camp().getX() + 0.5D, approach.camp().getY(),
                        approach.camp().getZ() + 0.5D, 1.1D);
            }
            if (lead.blockPosition().distSqr(approach.camp()) < 8.0D * 8.0D || now > approach.until() - 200L) {
                approaches.remove(player.getUUID());
                emptyCamp(player, level, band, approach.camp(), lead);
            }
            return;
        }
        for (BandMember envoy : envoys) {
            envoy.getNavigation().moveTo(player, 1.1D);
        }
        if (closest.distanceToSqr(player) > 8.0D * 8.0D) {
            return;
        }
        approaches.remove(player.getUUID());
        envoys.forEach(m -> m.getNavigation().stop());
        closest.ensureName();
        arrive(player, level, band, approach.kind(), closest, approach.target());
    }

    /** Face to face: what they want, or offer. */
    private static void arrive(ServerPlayer player, ServerLevel level, Bands.Record band, Kind kind, BandMember speaker,
            @Nullable UUID target) {
        int pressure = Land.ofPlayer(player).total();
        List<ItemStack> items = new ArrayList<>();
        String line;
        String detail;
        switch (kind) {
            case OFFER -> {
                items = offerFor(level, band, pressure);
                line = band.desperation >= 3 ? "Things are hard for us. Your ground is good ground. Let us forage on it "
                        + "this season, and this is yours."
                        : "Your ground is better than ours. Let us use it for a few days - we will pay for it.";
                detail = "Accept, and they may forage on your ground for five days - and will not come after it.";
            }
            case RANSOM -> {
                items = List.of(new ItemStack(ModItems.MEAT_CHUNK.get(), ransomFor(band, pressure)));
                line = band.desperation >= 4 ? "We are starving, and you sit on all this. Feed us, or we take it."
                        : "Good ground, this. Too good for a band like yours to keep for nothing. Pay us.";
                detail = "They want " + ransomFor(band, pressure) + " food (or half as much good stone).";
                Relations.demand(player, band, true);
            }
            case CLAIM -> {
                line = "This ground is ours now. Leave it - or we drive you off it.";
                detail = "Give in and your band packs up and leaves them your ground (pressure " + pressure + ").";
                Relations.demand(player, band, true);
            }
            case TRADE -> {
                for (int slot = 0; slot < speaker.getInventory().getContainerSize() && items.size() < 5; slot++) {
                    ItemStack stack = speaker.getInventory().getItem(slot);
                    if (!stack.isEmpty()) {
                        items.add(stack.copy());
                    }
                }
                line = "We have things to trade, if you want to look. You are always worth trading with.";
                detail = "See their offer opens a trade with them: pick what you want of theirs and what you give for it.";
            }
            case HELP -> {
                Bands.Record prey = Bands.get(level, target);
                String preyName = prey == null ? "a band near us" : prey.name;
                items = offerFor(level, band, 6);
                line = "We are going for " + preyName + "'s stores. Come with us and we split what we take. Or - if you "
                        + "have food to share, there might be enough to go round without it.";
                detail = "Join them for a share, share 4 food so nobody has to, or decline.";
            }
            default -> {
                return;
            }
        }
        open(player, band, kind, speaker, line, detail, items, target);
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.ENCOUNTER);
    }

    private static int ransomFor(Bands.Record band, int pressure) {
        return 2 + pressure / 2 + (band.desperation - 1);
    }

    /** What a band offers: food out of its own hands first - less of it the more desperate it is. */
    private static List<ItemStack> offerFor(ServerLevel level, Bands.Record band, int pressure) {
        int worth = Math.max(1, 2 + pressure / 2 - (band.desperation - 1) / 2);
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(band.desperation >= 4 ? Items.SWEET_BERRIES : ModItems.MEAT_CHUNK.get(), worth));
        if (band.desperation <= 2 && pressure >= 6) {
            items.add(new ItemStack(ModItems.CHERT_ROCK.get(), 1 + pressure / 4));
        }
        return items;
    }

    /**
     * Opens the screen - and says it in chat as well, with the answers as links, in case the screen is shut.
     * Used for the threats that came from elsewhere too: a raid at the door, being caught on their ground.
     */
    public static void open(ServerPlayer player, Bands.Record band, Kind kind, BandMember speaker, String line,
            String detail, List<ItemStack> items, @Nullable UUID target) {
        long now = player.level().getGameTime();
        pending.put(player.getUUID(), new Pending(band.id, kind, items, now + ANSWER_TICKS, target, speaker.getUUID()));
        speaker.ensureName();
        speaker.getLookControl().setLookAt(player, 30.0F, 30.0F);
        int standing = Relations.standing(player, band);
        PacketDistributor.sendToPlayer(player, new EncounterPayload(kind.ordinal(), BandNames.capital(band.name),
                speaker.getName().getString(), standing, band.desperation, line, detail, items));
        MutableComponent chat = Component.literal("<" + speaker.getName().getString() + " of " + BandNames.capital(band.name)
                + "> ").withStyle(Voices.colour(standing)).append(Component.literal(line).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(chat);
        MutableComponent links = Component.literal("  ");
        for (int choice : choicesFor(kind)) {
            links.append(link(choice)).append(Component.literal(" "));
        }
        player.sendSystemMessage(links);
    }

    public static int[] choicesFor(Kind kind) {
        return switch (kind) {
            case OFFER, TRADE -> new int[] {ACCEPT, DECLINE};
            case HELP -> new int[] {ACCEPT, SHARE, DECLINE};
            default -> new int[] {GIVE_IN, FIGHT, FLEE};
        };
    }

    public static String choiceLabel(int choice, int kind) {
        return switch (choice) {
            case ACCEPT -> kind == Kind.HELP.ordinal() ? "Join them" : kind == Kind.TRADE.ordinal() ? "See their offer"
                    : "Accept";
            case DECLINE -> kind == Kind.TRADE.ordinal() ? "Not now" : "Decline";
            case GIVE_IN -> "Give in";
            case FIGHT -> "Fight";
            case FLEE -> "Flee";
            default -> "Share food";
        };
    }

    private static Component link(int choice) {
        String label = switch (choice) {
            case ACCEPT -> "[Accept]";
            case DECLINE -> "[Decline]";
            case GIVE_IN -> "[Give in]";
            case FIGHT -> "[Fight]";
            case FLEE -> "[Flee]";
            default -> "[Share food]";
        };
        ChatFormatting colour = choice == FIGHT ? ChatFormatting.RED : choice == FLEE ? ChatFormatting.GOLD
                : choice == DECLINE ? ChatFormatting.GRAY : ChatFormatting.GREEN;
        return Component.literal(label).withStyle(style -> style.withColor(colour).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hominin answer " + choice))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Answer them."))));
    }

    /** Nobody answered in time: an offer lapses; a threat is left to run its course. */
    private static void tickPending(ServerPlayer player) {
        Pending answer = pending.get(player.getUUID());
        if (answer != null && player.level().getGameTime() > answer.until()) {
            pending.remove(player.getUUID());
            if (answer.kind() == Kind.OFFER || answer.kind() == Kind.HELP || answer.kind() == Kind.TRADE) {
                player.displayClientMessage(Component.literal("They give up waiting for an answer and go."), true);
                Bands.Record band = Bands.get(player.serverLevel(), answer.band());
                if (band != null) {
                    Relations.sendHomeFrom(player, band);
                }
            }
        }
    }

    // ------------------------------------------------------------ your answer

    public static void answer(ServerPlayer player, int choice) {
        Pending answer = pending.remove(player.getUUID());
        if (answer == null) {
            player.displayClientMessage(Component.literal("Nobody is waiting on an answer from you."), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Bands.Record band = Bands.get(level, answer.band());
        if (band == null) {
            return;
        }
        boolean valid = false;
        for (int c : choicesFor(answer.kind())) {
            valid |= c == choice;
        }
        if (!valid) {
            pending.put(player.getUUID(), answer);
            return;
        }
        switch (answer.kind()) {
            case OFFER -> answerOffer(player, level, band, answer, choice);
            case HELP -> answerHelp(player, level, band, answer, choice);
            case TRADE -> {
                if (choice == ACCEPT && answer.speaker() != null
                        && level.getEntity(answer.speaker()) instanceof BandMember trader && trader.isAlive()) {
                    Trading.openTrade(player, trader);
                } else {
                    player.displayClientMessage(Component.literal("Another time, then."), true);
                    Relations.sendHomeFrom(player, band);
                }
            }
            default -> answerThreat(player, level, band, answer, choice);
        }
    }

    private static void answerOffer(ServerPlayer player, ServerLevel level, Bands.Record band, Pending answer, int choice) {
        if (choice == ACCEPT) {
            for (ItemStack stack : answer.items()) {
                give(player, stack.copy());
            }
            band.accessUntil.put(player.getUUID(), level.getGameTime() + ACCESS_TICKS);
            band.desperation = Math.max(1, band.desperation - 1);
            Bands.changed(level);
            Relations.change(player, band, 3, "you let them use your ground");
            player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " may forage on your ground for five "
                    + "days. They will not come after it while they can.").withStyle(ChatFormatting.GREEN));
        } else {
            Relations.change(player, band, -2, "you turned down their offer");
            failStance(player, band, level);
            player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " go, and do not look back.")
                    .withStyle(ChatFormatting.GRAY));
        }
        Relations.sendHomeFrom(player, band);
    }

    private static void answerHelp(ServerPlayer player, ServerLevel level, Bands.Record ally, Pending answer, int choice) {
        if (choice == ACCEPT && answer.target() != null) {
            joints.put(player.getUUID(), new Joint(ally.id, answer.target(), level.getGameTime() + 2400L));
            player.sendSystemMessage(Component.literal("You go with " + ally.name + ". It will be over in a couple of "
                    + "minutes, one way or the other.").withStyle(ChatFormatting.GOLD));
        } else if (choice == SHARE) {
            int given = take(player, 4, false);
            if (given == 0) {
                player.displayClientMessage(Component.literal("You have no food to share."), true);
                pending.put(player.getUUID(), answer);
                return;
            }
            ally.desperation = Math.max(1, ally.desperation - 2);
            Bands.changed(level);
            Relations.change(player, ally, 3, "you shared what you had");
            player.sendSystemMessage(Component.literal("You share " + given + " food. It is enough to go round - "
                    + ally.name + " will not need to raid anyone.").withStyle(ChatFormatting.GREEN));
        } else {
            Relations.change(player, ally, -1, "you would not help");
        }
        Relations.sendHomeFrom(player, ally);
    }

    private static void answerThreat(ServerPlayer player, ServerLevel level, Bands.Record band, Pending answer, int choice) {
        int pressure = Land.ofPlayer(player).total();
        switch (choice) {
            case GIVE_IN -> {
                if (answer.kind() == Kind.CLAIM) {
                    Relations.settleDemand(player, band, "You leave the ground to them.");
                    BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
                    dev.hominin.evolution.hunt.Predation.packUp(player);
                    moveBand(level, band, camp);
                    Relations.change(player, band, 4, "you gave them your ground");
                    player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " move onto the ground that was "
                            + "yours.").withStyle(ChatFormatting.GOLD));
                } else if (answer.kind() == Kind.RANSOM) {
                    int wanted = answer.items().isEmpty() ? 4 : answer.items().get(0).getCount();
                    int paid = take(player, wanted, true);
                    if (paid < (wanted + 1) / 2) {
                        player.sendSystemMessage(Component.literal("You have nothing like enough - " + paid + " of "
                                + wanted + ". They take it, and want the rest.").withStyle(ChatFormatting.RED));
                        Relations.fight(player, band);
                        return;
                    }
                    Relations.settleDemand(player, band, "They take it and go - and they will be back for more.");
                    Relations.change(player, band, 1, "you paid");
                } else {
                    // A raid at the door, or caught on their ground: the old ransom.
                    Relations.payRansom(player, band);
                }
            }
            case FIGHT -> {
                player.sendSystemMessage(Component.literal("You stand your ground. Break them and they will not try again "
                        + "soon.").withStyle(ChatFormatting.RED));
                Relations.fight(player, band);
            }
            default -> flee(player, level, band, answer.kind(), pressure);
        }
    }

    // ------------------------------------------------------------ fleeing, and a camp found empty

    /** You run: whatever you drop they stop to take, and then they come after you. */
    private static void flee(ServerPlayer player, ServerLevel level, Bands.Record band, Kind kind, int pressure) {
        BandMember grabber = Relations.nearestMember(level, band, player, 32.0D);
        List<String> dropped = new ArrayList<>();
        var items = player.getInventory().items;
        int stacks = 0;
        for (int slot = 0; slot < items.size() && stacks < 4; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty() || (slot == player.getInventory().selected)) {
                continue;
            }
            boolean food = stack.has(DataComponents.FOOD);
            if (!food && player.getRandom().nextFloat() > 0.3F) {
                continue;
            }
            ItemStack lost = stack.split(Math.max(1, (stack.getCount() + 1) / 2));
            dropped.add(lost.getCount() + " " + lost.getHoverName().getString().toLowerCase());
            if (grabber != null) {
                grabber.addToInventory(lost);
            }
            stacks++;
        }
        if (kind == Kind.FOOD_RAID && dev.hominin.evolution.hunt.Predation.onOwnGround(player, player.blockPosition())) {
            // You ran from your own camp: what was lying about is theirs.
            List<ItemStack> tools = ToolPiles.plunder(level, player.getUUID(), 3);
            if (!tools.isEmpty()) {
                dropped.add(ToolPiles.describe(tools) + " off the tool pile");
                if (grabber != null) {
                    tools.forEach(grabber::addToInventory);
                }
            }
        }
        Relations.clearDemand(player);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 1, false, true, true));
        chases.put(player.getUUID(), new Chase(band.id, level.getGameTime() + 240L));
        Relations.change(player, band, -1, "you ran");
        player.sendSystemMessage(Component.literal("You run" + (dropped.isEmpty() ? "" : ", dropping " + String.join(", ", dropped))
                + " - and they come after you!").withStyle(ChatFormatting.GOLD));
        if (kind == Kind.CLAIM && pressure >= 5) {
            // Run off your own ground and it is theirs.
            BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
            dev.hominin.evolution.hunt.Predation.packUp(player);
            moveBand(level, band, camp);
            player.sendSystemMessage(Component.literal("Behind you, " + band.name + " settle on the ground you ran from.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** A few seconds of being chased: anyone who catches you hits you. */
    private static void tickChase(ServerPlayer player) {
        Chase chase = chases.get(player.getUUID());
        if (chase == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Bands.Record band = Bands.get(level, chase.band());
        if (band == null || level.getGameTime() > chase.until()) {
            chases.remove(player.getUUID());
            if (band != null) {
                Relations.sendHomeFrom(player, band);
                player.displayClientMessage(Component.literal("They give up the chase."), true);
            }
            return;
        }
        for (BandMember member : envoys(level, band, player, 48.0D)) {
            member.getNavigation().moveTo(player, 1.3D);
            if (member.distanceToSqr(player) < 2.2D * 2.2D && member.getRandom().nextInt(3) == 0) {
                member.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                player.hurt(player.damageSources().mobAttack(member), 2.0F);
            }
        }
    }

    /**
     * Nobody home: they take what is lying about the camp. And if the band packed up and left rich ground,
     * they may well move onto it.
     */
    private static void emptyCamp(ServerPlayer player, ServerLevel level, Bands.Record band, BlockPos camp, BandMember lead) {
        Relations.clearDemand(player);
        int taken = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(camp).inflate(16.0D))) {
            lead.addToInventory(item.getItem().copy());
            taken += item.getItem().getCount();
            item.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(camp.offset(-12, -4, -12), camp.offset(12, 4, 12))) {
            if (level.getBlockEntity(pos) instanceof dev.hominin.evolution.block.KnappingStationBlockEntity station) {
                var stock = station.items();
                for (int i = 0; i < stock.getContainerSize(); i++) {
                    ItemStack stack = stock.getItem(i);
                    if (!stack.isEmpty() && player.getRandom().nextBoolean()) {
                        taken += stack.getCount();
                        lead.addToInventory(stack.copy());
                        stock.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
        int pressure = Land.of(level, camp, dev.hominin.evolution.hunt.Predation.territoryRadius(player)).total();
        boolean settles = !dev.hominin.evolution.hunt.Predation.settled(player) && pressure >= 6;
        player.sendSystemMessage(Component.literal("Word reaches you: " + band.name + " came to your camp and found it "
                + "empty. " + (taken > 0 ? "They took what was lying about - " + taken + " things." : "There was nothing to take.")
                + (settles ? " And the ground was too good to leave: they have moved onto it." : ""))
                .withStyle(settles ? ChatFormatting.RED : ChatFormatting.GOLD));
        if (settles) {
            moveBand(level, band, camp);
        }
        Relations.sendHomeFrom(player, band);
    }

    /** A band's ground moves: its camp is here now, and its people walk to it. */
    static void moveBand(ServerLevel level, Bands.Record band, BlockPos home) {
        band.home = home.immutable();
        Territory.settle(band.id, band.home);
        Bands.changed(level);
    }

    /** The move failed - declined, fought off: a neutral band tries something else next time. */
    private static void failStance(ServerPlayer player, Bands.Record band, ServerLevel level) {
        int stance = band.stance.getOrDefault(player.getUUID(), STANCE_NONE);
        if (Relations.standing(player, band) > Relations.UNFRIENDLY && Relations.standing(player, band) < Relations.FRIENDLY) {
            band.stance.put(player.getUUID(), stance == STANCE_OFFER ? STANCE_RANSOM : stance == STANCE_RANSOM
                    ? STANCE_CLAIM : STANCE_OFFER);
        } else {
            band.stance.remove(player.getUUID());
        }
        Bands.changed(level);
    }

    /** Raiders you broke: whatever they were after, it failed. */
    public static void foughtOff(ServerPlayer player, Bands.Record band) {
        failStance(player, band, player.serverLevel());
        addFeared(player, 1);
    }

    /** Nobody stopped them: on a claim, the ground is theirs. */
    public static void raidWon(ServerPlayer player, Bands.Record band) {
        if (band.stance.getOrDefault(player.getUUID(), STANCE_NONE) != STANCE_CLAIM
                || Relations.standing(player, band) >= Relations.FRIENDLY) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        if (Land.of(level, camp, dev.hominin.evolution.hunt.Predation.territoryRadius(player)).total() < 5) {
            return;
        }
        dev.hominin.evolution.hunt.Predation.packUp(player);
        moveBand(level, band, camp);
        player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " drive your band off its ground and "
                + "move onto it.").withStyle(ChatFormatting.DARK_RED));
    }

    // ------------------------------------------------------------ joining a raid

    @Nullable
    private static Bands.Record helpTarget(ServerLevel level, Bands.Record ally, ServerPlayer player) {
        Bands.Record best = null;
        for (Bands.Record other : Bands.all(level)) {
            if (other == ally || other.nomadic() || Bands.horizontal(other.home, ally.home) > 300.0D * 300.0D
                    || Relations.standing(player, other) >= Relations.FRIENDLY) {
                continue;
            }
            if (best == null || other.presence < best.presence) {
                best = other;
            }
        }
        return best;
    }

    private static void tickJoint(ServerPlayer player, ServerLevel level) {
        Joint joint = joints.get(player.getUUID());
        if (joint == null || level.getGameTime() < joint.due()) {
            return;
        }
        joints.remove(player.getUUID());
        Bands.Record ally = Bands.get(level, joint.ally());
        Bands.Record prey = Bands.get(level, joint.target());
        if (ally == null || prey == null) {
            return;
        }
        float odds = Mth.clamp(0.35F + ((ally.presence + Presence.get(player)) / 2.0F - prey.presence) / 50.0F
                + (25 - prey.cohesion) / 60.0F, 0.1F, 0.9F);
        if (!prey.knownTo(player.getUUID())) {
            Relations.meet(player, prey, "");
        }
        if (player.getRandom().nextFloat() < odds) {
            int food = 3 + player.getRandom().nextInt(4);
            give(player, new ItemStack(ModItems.MEAT_CHUNK.get(), food));
            if (player.getRandom().nextBoolean()) {
                give(player, new ItemStack(ModItems.CHERT_ROCK.get(), 1 + player.getRandom().nextInt(2)));
            }
            ally.desperation = Math.max(1, ally.desperation - 2);
            prey.presence = Math.max(0, prey.presence - 4);
            prey.desperation = Math.min(5, prey.desperation + 1);
            Bands.changed(level);
            Relations.change(player, ally, 4, "you raided beside them");
            Relations.change(player, prey, -10, "you raided them with " + ally.name);
            Presence.add(player, 2, "you raided " + prey.name);
            addFeared(player, 1);
            player.sendSystemMessage(Component.literal("The raid on " + prey.name + " worked. Your share: " + food
                    + " food, and whatever else you could carry.").withStyle(ChatFormatting.GOLD));
        } else {
            Relations.change(player, ally, 1, "you stood by them");
            Relations.change(player, prey, -6, "you raided them with " + ally.name);
            player.sendSystemMessage(Component.literal(BandNames.capital(prey.name) + " were ready for you. The raid comes "
                    + "back with nothing.").withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------ taking ground

    /** You broke this band in a raid: for a day, its ground is yours for the taking - and word gets about. */
    public static void broke(ServerPlayer player, Bands.Record band) {
        broken.put(player.getUUID() + "/" + band.id, player.level().getGameTime());
        addFeared(player, 2);
    }

    // ------------------------------------------------------------ a name for it

    private static final String FEARED = "claims_feared";

    /** How much of a name you have for breaking the bands that try you, 0 to 10. */
    public static int feared(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().getOrDefault(FEARED, 0);
    }

    public static void addFeared(ServerPlayer player, int amount) {
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        counters.put(FEARED, Mth.clamp(counters.getOrDefault(FEARED, 0) + amount, 0, 10));
    }

    /** The more bands you have broken, the fewer try you. */
    public static float fearFactor(ServerPlayer player) {
        return 1.0F / (1.0F + 0.3F * feared(player));
    }

    // ------------------------------------------------------------ allies ganging up

    private static final Map<UUID, java.util.Set<UUID>> gangs = new HashMap<>();

    /**
     * A band coming for you brings its allies - more likely the bigger your name for breaking bands has
     * grown, since that is exactly the kind of neighbour allies band together against.
     */
    public static void gangUp(ServerPlayer player, ServerLevel level, Bands.Record band, BlockPos near) {
        float chance = Math.min(0.8F, 0.25F + 0.08F * feared(player));
        for (UUID allyId : band.allies) {
            Bands.Record ally = Bands.get(level, allyId);
            if (ally == null || ally.nomadic() || Relations.standing(player, ally) >= Relations.FRIENDLY
                    || hasAccess(player, ally) || Bands.horizontal(ally.home, player.blockPosition()) > 320.0D * 320.0D
                    || player.getRandom().nextFloat() >= chance) {
                continue;
            }
            BlockPos at = near.offset(player.getRandom().nextInt(9) - 4, 0, player.getRandom().nextInt(9) - 4);
            if (!level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
                continue;
            }
            at = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()),
                    at.getZ());
            WildBands.placeBand(level, at, ally.species, 2, ally.id, player.getRandom());
            gangs.computeIfAbsent(player.getUUID(), k -> new java.util.HashSet<>()).add(ally.id);
            if (!ally.knownTo(player.getUUID())) {
                Relations.meet(player, ally, "");
            }
            player.sendSystemMessage(Component.literal(BandNames.capital(ally.name) + " have come with them - their allies."
                    + (feared(player) >= 4 ? " Your name has them banding together against you." : ""))
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** It comes to blows: the allies who came fight too. */
    static void gangFights(ServerPlayer player, ServerLevel level) {
        for (UUID allyId : gangs.getOrDefault(player.getUUID(), java.util.Set.of())) {
            for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(48.0D),
                    m -> m.isAlive() && allyId.equals(m.getBandId()) && !m.isBaby())) {
                member.defendAgainst(player);
            }
        }
    }

    /** It is over: the allies who came go home with the rest. */
    static void releaseGang(ServerPlayer player, ServerLevel level) {
        java.util.Set<UUID> gang = gangs.remove(player.getUUID());
        if (gang == null) {
            return;
        }
        for (UUID allyId : gang) {
            Bands.Record ally = Bands.get(level, allyId);
            if (ally == null) {
                continue;
            }
            for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(96.0D),
                    m -> allyId.equals(m.getBandId()))) {
                member.setTarget(null);
                member.getNavigation().moveTo(ally.home.getX() + 0.5D, ally.home.getY(), ally.home.getZ() + 0.5D, 1.1D);
            }
        }
    }

    // ------------------------------------------------------------ in the dark

    private static final String NIGHT_RAID_DAY = "claims_night_raid_day";

    /**
     * From erectus, a desperate band may come in the night, while everyone sleeps. Somebody keeping watch - or
     * you, still up - sees them coming, the camp wakes, and it is a fight. Nobody watching, and they are in
     * and out with the food before anyone stirs.
     */
    private static void considerNight(ServerPlayer player, ServerLevel level) {
        long time = level.getDayTime() % 24000L;
        long day = level.getDayTime() / 24000L;
        var counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        if (time < 14000L || time > 22000L || counters.getOrDefault(NIGHT_RAID_DAY, -1) == (int) day
                || !Bands.erectusOn(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())
                || approaches.containsKey(player.getUUID()) || pending.containsKey(player.getUUID())
                || Relations.hasDemand(player) || Band.all(player).isEmpty()) {
            return;
        }
        boolean hard = Bands.desperateTimes(level);
        int presence = Presence.get(player);
        float deter = presence >= 45 ? 0.3F : presence >= 35 ? 0.5F : presence >= 25 ? 0.8F : 1.0F;
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || !band.knownTo(player.getUUID()) || band.desperation < (hard ? 3 : 4)
                    || Relations.standing(player, band) >= Relations.FRIENDLY || hasAccess(player, band)
                    || Bands.horizontal(band.home, player.blockPosition()) > 320.0D * 320.0D) {
                continue;
            }
            float chance = 0.05F * (band.desperation - 2) * deter * fearFactor(player) * (hard ? 2.0F : 1.0F);
            if (player.getRandom().nextFloat() >= chance) {
                continue;
            }
            counters.put(NIGHT_RAID_DAY, (int) day);
            nightRaid(player, level, band);
            return;
        }
    }

    private static void nightRaid(ServerPlayer player, ServerLevel level, Bands.Record band) {
        List<BandMember> watchers = Band.ownNear(player, 48.0D);
        watchers.removeIf(m -> !m.isOnWatch() || m.isSleeping());
        boolean awake = !player.isSleeping();
        List<BandMember> sleepers = Band.ownNear(player, 48.0D);
        if (watchers.isEmpty() && !awake) {
            // In and out, and nobody the wiser till morning.
            int taken = 0;
            for (BandMember member : sleepers) {
                while (taken < 6 && member.hasFood()) {
                    member.takeFood();
                    taken++;
                }
            }
            String hurt = null;
            if (!sleepers.isEmpty() && player.getRandom().nextBoolean()) {
                BandMember victim = sleepers.get(player.getRandom().nextInt(sleepers.size()));
                victim.ensureName();
                victim.setHealth(Math.max(2.0F, victim.getHealth() - 6.0F));
                hurt = victim.getName().getString();
            }
            Presence.add(player, -2, "raided while you slept");
            List<ItemStack> tools = ToolPiles.plunder(level, player.getUUID(), 2);
            player.sendSystemMessage(Component.literal("In the night, people from " + band.name + " crept into your camp "
                    + "while everyone slept. Nobody was keeping watch. They took " + (taken == 0 ? "nothing to eat"
                    : taken + " food") + (tools.isEmpty() ? "" : " and " + ToolPiles.describe(tools) + " off your tool pile")
                    + (hurt != null ? ", and " + hurt + " was hurt before they ran." : ".")
                    + " (Ask someone close to you to keep watch: H, Danger.)").withStyle(ChatFormatting.RED));
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.NIGHT_WATCH);
            return;
        }
        // Seen coming: the camp wakes.
        if (!watchers.isEmpty()) {
            BandMember watcher = watchers.get(0);
            watcher.ensureName();
            player.sendSystemMessage(Component.literal("<" + watcher.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Wake up! People in the dark - " + band.name + ", coming for the camp!")
                            .withStyle(ChatFormatting.RED)));
        } else {
            player.sendSystemMessage(Component.literal("Shapes moving in the dark, coming for the camp - " + band.name
                    + ", raiding!").withStyle(ChatFormatting.RED));
        }
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
        }
        for (BandMember member : sleepers) {
            if (member.isSleeping()) {
                member.stopSleeping();
            }
        }
        ensureEnvoys(player, level, band, 3);
        gangUp(player, level, band, toward(player, band.home, 38));
        // They come on regardless: when they reach you, they say what they want.
        Relations.raidNow(player, band);
    }

    /** A spot this far from the player, in the direction of somewhere. */
    private static BlockPos toward(ServerPlayer player, BlockPos where, int distance) {
        double angle = Math.atan2(where.getZ() - player.getZ(), where.getX() - player.getX());
        return BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(),
                player.getZ() + Math.sin(angle) * distance);
    }

    /** The allies who came walk in with the rest. */
    private static void gangFollow(ServerPlayer player, ServerLevel level) {
        java.util.Set<UUID> gang = gangs.get(player.getUUID());
        if (gang == null) {
            return;
        }
        for (UUID allyId : gang) {
            for (BandMember member : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(96.0D),
                    m -> m.isAlive() && allyId.equals(m.getBandId()) && m.getTarget() == null)) {
                if (member.distanceToSqr(player) > 6.0D * 6.0D) {
                    member.getNavigation().moveTo(player, 1.1D);
                }
            }
        }
    }

    /**
     * Setting your territory on another band's ground. If you broke them in the last day, they give it up and
     * go; otherwise they resent it. Returns true when the ground was taken.
     */
    public static boolean takeGround(ServerPlayer player, Bands.Record band) {
        Long when = broken.get(player.getUUID() + "/" + band.id);
        if (when == null || player.level().getGameTime() - when > 24000L) {
            return false;
        }
        broken.remove(player.getUUID() + "/" + band.id);
        ServerLevel level = player.serverLevel();
        double angle = Math.atan2(band.home.getZ() - player.getZ(), band.home.getX() - player.getX());
        int x = (int) (band.home.getX() + Math.cos(angle) * 300.0D);
        int z = (int) (band.home.getZ() + Math.sin(angle) * 300.0D);
        moveBand(level, band, new BlockPos(x, band.home.getY(), z));
        Relations.sendHomeFrom(player, band);
        band.presence = Math.max(0, band.presence - 5);
        Presence.add(player, 3, "you took " + band.name + "'s ground");
        player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " give up their ground to you and move "
                + "on.").withStyle(ChatFormatting.GOLD));
        return true;
    }

    /**
     * Your band speaks up about better ground: when it is desperate, or when it is strong and sitting on poor
     * ground. It names the band and where, and what the ground holds.
     */
    private static void suggest(ServerPlayer player, ServerLevel level) {
        int own = Land.ofPlayer(player).total();
        int presence = Presence.get(player);
        int desperate = ownDesperation(player);
        boolean strongOnPoor = presence >= 35 && own <= 3;
        if (desperate < 3 && !strongOnPoor || player.getRandom().nextInt(3) != 0) {
            return;
        }
        Bands.Record best = null;
        int bestPressure = own + 1;
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || !band.knownTo(player.getUUID()) || Bands.horizontal(band.home, player.blockPosition())
                    > 320.0D * 320.0D || band.presence >= presence + 5
                    || Relations.standing(player, band) >= Relations.ALLIED
                    || !level.hasChunk(band.home.getX() >> 4, band.home.getZ() >> 4)) {
                continue;
            }
            int pressure = Land.of(level, band.home, band.radius()).total();
            if (pressure > bestPressure) {
                bestPressure = pressure;
                best = band;
            }
        }
        List<BandMember> speakers = Band.ownNear(player, 16.0D);
        speakers.removeIf(BandMember::isBaby);
        if (best == null || speakers.isEmpty()) {
            return;
        }
        BandMember speaker = speakers.get(player.getRandom().nextInt(speakers.size()));
        speaker.ensureName();
        List<String> what = Land.of(level, best.home, best.radius()).describe(player);
        String line = desperate >= 3
                ? "We cannot keep going like this. " + BandNames.capital(best.name) + " sit on good ground - "
                        + String.join(", ", what) + ". We could take it off them."
                : "We are strong, and this ground is poor. " + BandNames.capital(best.name) + " hold far better - "
                        + String.join(", ", what) + " - and they are weaker than us.";
        player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(line + " ").withStyle(ChatFormatting.WHITE)).append(Relations.leadLink(best)));
        player.sendSystemMessage(Component.literal("(Raid them from The others and break them, then set your territory on "
                + "their ground from the map - and it is yours.)").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ------------------------------------------------------------ helpers

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Takes up to this much food from you and your band near you - and, if allowed, good stone for the rest. */
    private static int take(ServerPlayer player, int wanted, boolean stone) {
        int taken = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (taken >= wanted) {
                break;
            }
            if (stack.has(DataComponents.FOOD)) {
                int n = Math.min(wanted - taken, stack.getCount());
                stack.shrink(n);
                taken += n;
            }
        }
        for (BandMember member : Band.ownNear(player, 24.0D)) {
            while (taken < wanted && member.hasFood()) {
                member.takeFood();
                taken++;
            }
        }
        if (stone) {
            for (ItemStack stack : player.getInventory().items) {
                if (taken >= wanted) {
                    break;
                }
                if (stack.is(ModItems.CHERT_ROCK.get()) || stack.is(ModItems.OBSIDIAN_ROCK.get())
                        || stack.is(ModItems.BASALT_ROCK.get())) {
                    int n = Math.min((wanted - taken + 1) / 2, stack.getCount());
                    stack.shrink(n);
                    taken += n * 2;
                }
            }
        }
        return Math.min(taken, wanted);
    }

    public static void forget(UUID player) {
        gangs.remove(player);
        approaches.remove(player);
        pending.remove(player);
        joints.remove(player);
        chases.remove(player);
    }

    private Claims() {
    }
}
