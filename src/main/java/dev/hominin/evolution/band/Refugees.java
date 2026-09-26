package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ways into your band from outside it.
 *
 * <p><b>Survivors.</b> Every day or two a few of them come walking in - two or three, what is left of a band that is
 * gone - and ask to walk with you. What happened to theirs decides what they bring:
 * <ul>
 * <li><b>Something came for them</b> - hyenas, a big cat, the crossing. The commonest story, and the easiest: they
 * bring nothing with them but themselves.</li>
 * <li><b>Another band destroyed them.</b> They name it, and they hold it against it. Every day they live with you,
 * your band hears about it, and thinks a little less of that band.</li>
 * <li><b>Their band tore itself apart.</b> No grudge - but some of the ones who fought are hard to live with, and may
 * start scuffles of their own.</li>
 * <li>And rarely, one of them is a <b>psychopath</b>, charming, with a terrible story about another band - always. See
 * {@link Psychopaths}.</li>
 * </ul>
 * Sometimes one of them is carrying a child - they say so, or say that none of them is.
 *
 * <p><b>Allies that dwindle.</b> A band that stands with you, down to its last few, would sooner be one band with
 * yours than none at all. Take them in and they come to you - all of them, their piles, what they know - and a band
 * that chose you holds together the better for it.
 */
public final class Refugees {
    public static final int ACTION = 67;
    public static final int ACTION_MERGE = 68;
    private static final int TAKE_IN = 0;
    private static final int SEND_ON = 1;
    private static final String NEXT_DAY = "refugees_next_day";
    private static final String GRUDGE_DAY = "refugees_grudge_day";
    private static final String MERGE_DAY = "refugees_merge_day";
    /** How long a group waits on an answer before walking on. */
    private static final long WAIT_TICKS = 2 * 60 * 20L;
    /** An ally this small asks to join you. */
    private static final int DWINDLED = 3;
    private static final float MERGE_CHANCE = 0.35F;
    /** Marks a survivor still waiting on an answer - so one left over (the world closed on them) can be tidied away. */
    private static final String WAITING_TAG = "hominin_refugee";

    enum Cause {
        PREDATOR, RAID, INFIGHTING, PSYCHOPATH
    }

    private record Arrival(List<UUID> people, Cause cause, @Nullable UUID culprit, long until) {
    }

    private static final Map<UUID, Arrival> arrivals = new HashMap<>();
    private static final Map<UUID, UUID> merging = new HashMap<>();

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    private static int today(ServerPlayer player) {
        return (int) (player.level().getDayTime() / 24000L);
    }

    public static void tick(ServerPlayer player) {
        if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Arrival waiting = arrivals.get(player.getUUID());
        if (waiting != null && player.tickCount % 20 == 3) {
            keepComing(player, level, waiting);
        }
        if (player.tickCount % 1200 != 517 || Newcomers.hostOf(player) != null) {
            return;
        }
        grudges(player, level);
        mergeOffers(player, level);
        if (waiting == null) {
            strays(player, level);
        }
        Map<String, Integer> counters = counters(player);
        int today = today(player);
        if (!counters.containsKey(NEXT_DAY)) {
            counters.put(NEXT_DAY, today + 1 + player.getRandom().nextInt(2));
            return;
        }
        long time = level.getDayTime() % 24000L;
        if (today < counters.get(NEXT_DAY) || time < 1000L || time > 11000L || waiting != null
                || !dev.hominin.evolution.hunt.Predation.settled(player) || player.isInWater()) {
            // They come in daylight, to a band that is settled somewhere.
            return;
        }
        counters.put(NEXT_DAY, today + 1 + player.getRandom().nextInt(2));
        arrive(player, level);
    }

    // ------------------------------------------------------------ survivors

    private static int room(ServerPlayer player) {
        List<BandMember> members = Band.all(player);
        int pending = (int) members.stream().filter(BandMember::isPregnant).count();
        return BandSizes.of(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage()).maxMembers()
                - members.size() - pending;
    }

    private static void arrive(ServerPlayer player, ServerLevel level) {
        arrive(player, level, null);
    }

    /** Developer: survivors now - with this story, or any. */
    static String devArrive(ServerPlayer player, @Nullable Cause forced) {
        if (arrivals.containsKey(player.getUUID())) {
            return "Survivors are already waiting on your answer.";
        }
        if (room(player) <= 0) {
            return "Your band has no room: nobody comes.";
        }
        arrive(player, player.serverLevel(), forced);
        return arrivals.containsKey(player.getUUID()) ? "Survivors are walking in (" + arrivals.get(player.getUUID()).cause()
                .name().toLowerCase() + ")." : "Nobody could be placed here.";
    }

    /** Developer: a day passes, for grudges. */
    static String devGrudgeDay(ServerPlayer player) {
        counters(player).remove(GRUDGE_DAY);
        grudges(player, player.serverLevel());
        return "A day of grudges told.";
    }

    /** Developer: this band is down to two, and asks now. */
    static String devMerge(ServerPlayer player, Bands.Record band) {
        band.size = Math.min(band.size, 2);
        Relations.change(player, band, Math.max(0, Relations.ALLIED + 1 - Relations.standing(player, band)), "developer");
        Bands.changed(player.serverLevel());
        merging.remove(player.getUUID());
        counters(player).remove(MERGE_DAY);
        float chance = MERGE_CHANCE;
        merging.put(player.getUUID(), band.id);
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION_MERGE,
                "Word comes from " + BandNames.capital(band.name) + ", who stand with you: they are down to "
                        + band.size + ". They would sooner be one band with yours than none at all. Take them in?",
                List.of("Take them in", "Not now"), List.of(TAKE_IN, SEND_ON)));
        return BandNames.capital(band.name) + " are down to " + band.size + " and ask to merge (normally "
                + Math.round(chance * 100) + "% a day).";
    }

    private static void arrive(ServerPlayer player, ServerLevel level, @Nullable Cause forced) {
        int room = room(player);
        if (room <= 0) {
            return;
        }
        RandomSource random = player.getRandom();
        ResourceLocation stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        List<BandMember> band = Band.all(player);
        Bands.Record culprit = culprit(player, level);
        float roll = random.nextFloat();
        Cause cause = roll < 0.06F && Bands.erectusOn(stage) && band.stream().noneMatch(BandMember::isPsychopath)
                ? Cause.PSYCHOPATH : roll < 0.3F ? Cause.RAID : roll < 0.48F ? Cause.INFIGHTING : Cause.PREDATOR;
        if (forced != null) {
            cause = forced;
        }
        if (culprit == null && (cause == Cause.RAID || cause == Cause.PSYCHOPATH)) {
            cause = Cause.PREDATOR;
        }
        int count = Math.min(room, 2 + (random.nextFloat() < 0.4F ? 1 : 0));
        float angle = random.nextFloat() * Mth.TWO_PI;
        BlockPos from = Band.standingSpotNear(level, player.blockPosition(), 22, angle);
        List<BandMember> people = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BandMember one = ModEntities.BAND_MEMBER.get().create(level);
            if (one == null) {
                break;
            }
            BlockPos at = Band.standingSpotNear(level, from, 1 + i, angle + i * 1.7F);
            one.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            one.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            one.setStage(stage);
            one.ensureName();
            one.setHunger(BandMember.MAX_HUNGER / 3);
            one.addTag(WAITING_TAG);
            level.addFreshEntity(one);
            one.getNavigation().moveTo(player, 1.0D);
            people.add(one);
        }
        if (people.isEmpty()) {
            return;
        }
        // Now and then one of them is carrying.
        BandMember carrying = null;
        if (random.nextFloat() < 0.25F) {
            carrying = people.stream().filter(BandMember::isFemale).findFirst().orElse(null);
            if (carrying == null && people.size() > 1) {
                carrying = people.get(1);
                carrying.setSex(true);
            }
            if (carrying != null) {
                carrying.arriveCarrying(0.25F + random.nextFloat() * 0.5F);
            }
        }
        BandMember speaker = people.get(0);
        if (cause == Cause.PSYCHOPATH) {
            // The one who does the talking. Nobody can tell - there are only signs, later.
            speaker.setPsychopath(true);
        }
        List<UUID> ids = people.stream().map(BandMember::getUUID).toList();
        arrivals.put(player.getUUID(), new Arrival(ids, cause, culprit == null ? null : culprit.id,
                level.getGameTime() + WAIT_TICKS));
        if (culprit != null && (cause == Cause.RAID || cause == Cause.PSYCHOPATH)) {
            Relations.meet(player, culprit, "survivors named them");
        }
        String names = names(people);
        String story = story(cause, culprit, random, people.size());
        String carried = carrying != null ? " " + carrying.getName().getString() + " is carrying a child."
                : " None of us is carrying.";
        player.sendSystemMessage(Component.literal(names + " come walking in from the grass - what is left of a band. "
                + "They want to walk with yours.").withStyle(ChatFormatting.YELLOW));
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(speaker.getId(), ACTION,
                speaker.getName().getString() + ": \"" + story + carried + " Let us walk with you.\"",
                List.of("Take them in", "Send them on"), List.of(TAKE_IN, SEND_ON)));
    }

    /** Somebody to blame: a band out there that does not stand with you, not too far off. */
    @Nullable
    private static Bands.Record culprit(ServerPlayer player, ServerLevel level) {
        List<Bands.Record> candidates = new ArrayList<>();
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || band.haven != null
                    || Bands.horizontal(band.home, player.blockPosition()) > 600.0D * 600.0D
                    || band.knownTo(player.getUUID()) && Relations.standing(player, band) >= Relations.ALLIED) {
                continue;
            }
            candidates.add(band);
        }
        return candidates.isEmpty() ? null : candidates.get(player.getRandom().nextInt(candidates.size()));
    }

    private static String story(Cause cause, @Nullable Bands.Record culprit, RandomSource random, int count) {
        String us = count == 2 ? "the two of us" : "the three of us";
        return switch (cause) {
            case PREDATOR -> {
                String[] what = {"Hyenas came in the night, a whole clan of them.", "A big cat took us one by one, "
                        + "night after night.", "The crossing - the crocodiles had most of us in the water.",
                        "A giant hyena came into the camp and nobody could stop it."};
                yield what[random.nextInt(what.length)] + " There is nobody left but " + us + ".";
            }
            case RAID -> BandNames.capital(culprit.name) + " came for our ground. They killed the rest of us - "
                    + "there is only " + us + " now. We will not forget it.";
            case INFIGHTING -> "Our band tore itself apart over food, over who leads. We walked away before it was "
                    + "finished. Whoever is still there, we are not going back.";
            case PSYCHOPATH -> BandNames.capital(culprit.name) + " did it. All of them - the children too. I "
                    + "watched it. I have nobody now but " + (count == 2 ? "this one" : "these two")
                    + ", and I would do anything for a band that took us in.";
        };
    }

    private static String names(List<BandMember> people) {
        List<String> names = people.stream().map(m -> m.getName().getString()).toList();
        return names.size() == 1 ? names.get(0)
                : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** Walking in, while they wait: and if nobody answers, they walk on. */
    private static void keepComing(ServerPlayer player, ServerLevel level, Arrival waiting) {
        if (level.getGameTime() > waiting.until()) {
            arrivals.remove(player.getUUID());
            walkOn(level, waiting, player);
            return;
        }
        for (UUID id : waiting.people()) {
            if (level.getEntity(id) instanceof BandMember one && one.isAlive() && one.distanceToSqr(player) > 16.0D) {
                one.getNavigation().moveTo(player, 1.0D);
            }
        }
    }

    public static void choose(ServerPlayer player, int choice) {
        Arrival waiting = arrivals.remove(player.getUUID());
        if (waiting == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (choice != TAKE_IN) {
            walkOn(level, waiting, player);
            player.displayClientMessage(Component.literal("They look at each other, and walk on."), true);
            return;
        }
        int room = room(player);
        List<String> joined = new ArrayList<>();
        List<BandMember> left = new ArrayList<>();
        for (UUID id : waiting.people()) {
            if (!(level.getEntity(id) instanceof BandMember one) || !one.isAlive()) {
                continue;
            }
            if (room <= 0) {
                left.add(one);
                continue;
            }
            room--;
            one.removeTag(WAITING_TAG);
            one.joinPlayerBand(player.getUUID());
            one.getNavigation().moveTo(player, 1.0D);
            switch (waiting.cause()) {
                case RAID -> one.setGrudge(waiting.culprit());
                case PSYCHOPATH -> {
                    if (one.isPsychopath()) {
                        one.setGrudge(waiting.culprit());
                    }
                }
                case INFIGHTING -> {
                    // Some of the ones who were in the fighting are still spoiling for it.
                    if (!one.isPsychopath() && one.getRandom().nextFloat() < 0.5F) {
                        one.setTemper(true);
                    }
                }
                case PREDATOR -> {
                }
            }
            joined.add(one.getName().getString());
        }
        if (!left.isEmpty()) {
            walkOn(level, new Arrival(left.stream().map(BandMember::getUUID).toList(), waiting.cause(),
                    waiting.culprit(), 0L), player);
        }
        if (joined.isEmpty()) {
            player.displayClientMessage(Component.literal("There is no room in your band for anyone else."), true);
            return;
        }
        Bands.Record culprit = waiting.culprit() == null ? null : Bands.get(level, waiting.culprit());
        String note = switch (waiting.cause()) {
            case RAID, PSYCHOPATH -> culprit == null ? "" : " They hold it against " + culprit.name
                    + " - and while they are with you, your band will come to as well.";
            case INFIGHTING -> " Their band came apart fighting; some of that may come with them.";
            case PREDATOR -> "";
        };
        player.sendSystemMessage(Component.literal(String.join(", ", joined) + (joined.size() == 1 ? " is" : " are")
                + " one of yours now." + note).withStyle(ChatFormatting.GREEN));
    }

    /** Sent on, or tired of waiting: off into the grass, and gone. */
    private static void walkOn(ServerLevel level, Arrival arrival, ServerPlayer player) {
        for (UUID id : arrival.people()) {
            if (!(level.getEntity(id) instanceof BandMember one) || !one.isAlive() || one.getLeader() != null) {
                continue;
            }
            double dx = one.getX() - player.getX();
            double dz = one.getZ() - player.getZ();
            double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
            one.getNavigation().moveTo(one.getX() + dx / length * 40.0D, one.getY(), one.getZ() + dz / length * 40.0D, 1.0D);
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(server.getTickCount() + 600, () -> {
                if (one.isAlive() && one.getLeader() == null && one.getBandId() == null) {
                    one.discard();
                }
            }));
        }
    }

    /** Once a day: whatever grudges your people carry, your band hears about. */
    private static void grudges(ServerPlayer player, ServerLevel level) {
        Map<String, Integer> counters = counters(player);
        int today = today(player);
        if (counters.getOrDefault(GRUDGE_DAY, -1) >= today) {
            return;
        }
        counters.put(GRUDGE_DAY, today);
        Map<UUID, List<BandMember>> held = new HashMap<>();
        for (BandMember member : Band.all(player)) {
            if (member.getGrudge() != null && !member.isBaby()) {
                held.computeIfAbsent(member.getGrudge(), k -> new ArrayList<>()).add(member);
            }
        }
        for (Map.Entry<UUID, List<BandMember>> entry : held.entrySet()) {
            Bands.Record band = Bands.get(level, entry.getKey());
            if (band == null) {
                // Gone: nothing left to hold it against.
                entry.getValue().forEach(m -> m.setGrudge(null));
                continue;
            }
            if (Relations.standing(player, band) <= Relations.HOSTILE) {
                continue;
            }
            boolean psychopath = entry.getValue().stream().anyMatch(BandMember::isPsychopath);
            int drop = entry.getValue().size() >= 3 || psychopath ? 2 : 1;
            BandMember teller = entry.getValue().get(0);
            teller.ensureName();
            Relations.change(player, band, -drop, teller.getName().getString() + " keeps telling what they did");
        }
    }

    // ------------------------------------------------------------ an ally down to its last few

    private static void mergeOffers(ServerPlayer player, ServerLevel level) {
        Map<String, Integer> counters = counters(player);
        int today = today(player);
        if (counters.getOrDefault(MERGE_DAY, -1) >= today || merging.containsKey(player.getUUID())) {
            return;
        }
        counters.put(MERGE_DAY, today);
        String line = dev.hominin.evolution.stage.Kinds.line(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        for (Bands.Record band : Bands.all(level)) {
            if (band.nomadic() || band.haven != null || band.size > DWINDLED || !band.knownTo(player.getUUID())
                    || Relations.standing(player, band) < Relations.ALLIED
                    || !dev.hominin.evolution.stage.Kinds.line(band.species).equals(line)
                    || room(player) < band.size || player.getRandom().nextFloat() >= MERGE_CHANCE) {
                continue;
            }
            merging.put(player.getUUID(), band.id);
            PacketDistributor.sendToPlayer(player, new ChoicesPayload(player.getId(), ACTION_MERGE,
                    "Word comes from " + BandNames.capital(band.name) + ", who stand with you: they are down to "
                            + band.size + ". They would sooner be one band with yours than none at all. Take them in?",
                    List.of("Take them in", "Not now"), List.of(TAKE_IN, SEND_ON)));
            return;
        }
    }

    public static void chooseMerge(ServerPlayer player, int choice) {
        UUID id = merging.remove(player.getUUID());
        ServerLevel level = player.serverLevel();
        Bands.Record band = id == null ? null : Bands.get(level, id);
        if (band == null) {
            return;
        }
        if (choice != TAKE_IN) {
            player.displayClientMessage(Component.literal(BandNames.capital(band.name) + " will ask again, if they last."),
                    true);
            return;
        }
        if (room(player) < band.size) {
            player.displayClientMessage(Component.literal("Your band has no room for all of them."), true);
            return;
        }
        List<BandMember> theirs = new ArrayList<>(level.getEntities(ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && band.id.equals(m.getBandId())));
        ResourceLocation stage = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
        // Whoever is out of sight sets off for you now, and turns up.
        for (int i = theirs.size(); i < band.size; i++) {
            BandMember one = ModEntities.BAND_MEMBER.get().create(level);
            if (one == null) {
                break;
            }
            BlockPos at = Band.standingSpotNear(level, player.blockPosition(), 18 + i,
                    player.getRandom().nextFloat() * Mth.TWO_PI);
            one.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
            one.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
            one.setStage(stage);
            level.addFreshEntity(one);
            theirs.add(one);
        }
        List<String> names = new ArrayList<>();
        for (BandMember one : theirs) {
            one.ensureName();
            one.setTarget(null);
            one.joinPlayerBand(player.getUUID());
            one.setStage(stage);
            one.addBond(2);
            one.getNavigation().moveTo(player, 1.0D);
            names.add(one.getName().getString());
        }
        List<BlockPos> kept = ToolPiles.piles(level, band.id);
        ToolPiles.orphan(level, band.id);
        for (BlockPos pile : kept) {
            if (level.getBlockEntity(pile) instanceof dev.hominin.evolution.block.ToolPileBlockEntity heap) {
                heap.setOwner(player.getUUID());
            }
            ToolPiles.adopt(level, player.getUUID(), pile);
        }
        dev.hominin.evolution.world.Pois.bandJoined(player, band);
        Bands.remove(level, band.id);
        Cohesion.add(player, 8);
        player.sendSystemMessage(Component.literal(BandNames.capital(band.name) + " are one band with yours now: "
                + String.join(", ", names) + ". What they kept and what they knew is yours - and a band others chose "
                + "holds together (cohesion +8).").withStyle(ChatFormatting.GOLD));
    }

    /** Survivors nobody is waiting on any more - the world was closed on them - walk off. */
    private static void strays(ServerPlayer player, ServerLevel level) {
        for (BandMember one : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(96.0D),
                m -> m.getTags().contains(WAITING_TAG) && m.getLeader() == null && m.getBandId() == null)) {
            if (one.distanceToSqr(player) > 40.0D * 40.0D) {
                one.discard();
            } else {
                double dx = one.getX() - player.getX();
                double dz = one.getZ() - player.getZ();
                double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
                one.getNavigation().moveTo(one.getX() + dx / length * 48.0D, one.getY(), one.getZ() + dz / length * 48.0D,
                        1.0D);
            }
        }
    }

    /** Gone before answering: whoever was waiting walks on. */
    public static void logout(ServerPlayer player) {
        Arrival waiting = arrivals.remove(player.getUUID());
        if (waiting != null) {
            walkOn(player.serverLevel(), waiting, player);
        }
        merging.remove(player.getUUID());
    }

    private Refugees() {
    }
}
