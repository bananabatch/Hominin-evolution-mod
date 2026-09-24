package dev.hominin.evolution.mind;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.BandNames;
import dev.hominin.evolution.band.Bands;
import dev.hominin.evolution.band.Presence;
import dev.hominin.evolution.band.Relations;
import dev.hominin.evolution.entity.TroopAnimal;
import dev.hominin.evolution.entity.TroopRelations;
import dev.hominin.evolution.network.MapActionPayload;
import dev.hominin.evolution.network.MapPayload;
import dev.hominin.evolution.network.WaypointPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The mental map: what the J screen's map shows, and how a mind fills it.
 *
 * <p>Always on it: your own ground (orange) and every band you know of and its ground (red), every
 * Paranthropus troop you know of - they move every day, and the map moves them - and every troop of
 * baboons, chimpanzees or bonobos that trusts you, where you last saw it.
 *
 * <p>Held on purpose, as many as there is room for: places. Stand somewhere and remember it - a seam of
 * stone, a lava pool, termite mounds, a hyena clan's haunt, water, or anywhere at all under a name of
 * your own. Noticing a stone deposit (sneak-use it) remembers it too, if there is room. Band members
 * remember places of their own; ask them, and what they know is on your map until the next day.
 *
 * <p>Following: any marker can be followed. A pointer at the top of the screen leads the way until
 * you get there.
 */
public final class MentalMap {
    /** Close enough to a followed place to have arrived. */
    private static final double ARRIVED = 12.0D;

    public static MindData mind(ServerPlayer player) {
        MindData mind = player.getData(Attachments.MIND);
        if (mind.slots() == 0) {
            rollSlots(player, mind);
        }
        return mind;
    }

    /** A new body: a new capacity. Two to four places below erectus, four to seven from it. */
    public static void rollSlots(ServerPlayer player, MindData mind) {
        RandomSource random = player.getRandom();
        mind.setSlots(erectusOn(player) ? 4 + random.nextInt(4) : 2 + random.nextInt(3));
    }

    public static void newBody(ServerPlayer player) {
        MindData mind = player.getData(Attachments.MIND);
        rollSlots(player, mind);
        player.sendSystemMessage(Component.literal("This mind can hold " + mind.slots() + " places at once.")
                .withStyle(ChatFormatting.GRAY));
    }

    private static boolean erectusOn(ServerPlayer player) {
        String era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath();
        return !era.startsWith("australopithecus") && !era.equals("ardipithecus") && !era.equals("homo_habilis")
                && !era.equals("homo_rudolfensis");
    }

    private static long today(ServerPlayer player) {
        return player.level().getDayTime() / 24000L;
    }

    // ------------------------------------------------------------ what is here

    /** What is notable where the player stands, as a memory - or null if nothing is. */
    @Nullable
    private static MindData.Memory noticeHere(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos here = player.blockPosition();
        long day = today(player);
        dev.hominin.evolution.survival.Termites.Colony colony =
                dev.hominin.evolution.survival.Termites.nearestColony(level, here, 32);
        if (colony != null) {
            return new MindData.Memory("termites", "The great termite colony", colony.centre(), day);
        }
        BlockPos lava = null;
        BlockPos deposit = null;
        String depositName = null;
        BlockPos mound = null;
        BlockPos water = null;
        for (BlockPos pos : BlockPos.betweenClosed(here.offset(-10, -4, -10), here.offset(10, 6, 10))) {
            BlockState state = level.getBlockState(pos);
            if (lava == null && level.getFluidState(pos).is(FluidTags.LAVA)) {
                lava = pos.immutable();
            } else if (deposit == null && depositName(state) != null) {
                deposit = pos.immutable();
                depositName = depositName(state);
            } else if (mound == null && state.is(ModBlocks.TERMITE_MOUND.get())) {
                mound = pos.immutable();
            } else if (water == null && level.getFluidState(pos).is(FluidTags.WATER)) {
                water = pos.immutable();
            }
        }
        if (lava != null) {
            return new MindData.Memory("lava", "Lava pool", lava, day);
        }
        if (deposit != null) {
            return new MindData.Memory("deposit", depositName, deposit, day);
        }
        var clans = level.getEntitiesOfClass(dev.hominin.evolution.entity.Crocuta.class, player.getBoundingBox().inflate(40.0D),
                Mob::isAlive);
        if (!clans.isEmpty()) {
            return new MindData.Memory("clan", "Hyena clan", clans.get(0).blockPosition(), day);
        }
        if (mound != null) {
            return new MindData.Memory("termites", "Termite mound", mound, day);
        }
        if (water != null) {
            return new MindData.Memory("water", "Water", water, day);
        }
        return null;
    }

    @Nullable
    public static String depositName(BlockState state) {
        if (state.is(ModBlocks.CHERT_DEPOSIT.get())) {
            return "Chert outcrop";
        }
        if (state.is(ModBlocks.QUARTZITE_DEPOSIT.get())) {
            return "Quartzite outcrop";
        }
        if (state.is(ModBlocks.LIMESTONE_DEPOSIT.get())) {
            return "Limestone outcrop";
        }
        if (state.is(ModBlocks.BASALT_DEPOSIT.get())) {
            return "Basalt outcrop";
        }
        return null;
    }

    // ------------------------------------------------------------ holding it

    /** Keep this place in mind, under what it is - or under the player's own name for it. */
    public static void rememberHere(ServerPlayer player, String name) {
        MindData mind = mind(player);
        MindData.Memory noticed = noticeHere(player);
        String label = name == null ? "" : net.minecraft.util.StringUtil.filterText(name).trim();
        MindData.Memory memory;
        if (!label.isEmpty()) {
            memory = new MindData.Memory(noticed == null ? "custom" : noticed.kind(), label,
                    noticed == null ? player.blockPosition() : noticed.pos(), today(player));
        } else if (noticed != null) {
            memory = noticed;
        } else {
            player.displayClientMessage(Component.literal("Nothing here stands out. Give it a name to remember it by."), true);
            return;
        }
        remember(player, mind, memory, true);
    }

    /** Something noticed in passing - a deposit struck, say - kept if there is room, and not kept twice. */
    public static void noticed(ServerPlayer player, String kind, String label, BlockPos pos) {
        remember(player, mind(player), new MindData.Memory(kind, label, pos, today(player)), false);
    }

    private static void remember(ServerPlayer player, MindData mind, MindData.Memory memory, boolean asked) {
        for (MindData.Memory held : mind.memories()) {
            if (held.kind().equals(memory.kind()) && held.pos().distSqr(memory.pos()) < 24.0D * 24.0D) {
                if (asked) {
                    player.displayClientMessage(Component.literal("You already hold this place in mind: " + held.label() + "."), true);
                }
                return;
            }
        }
        if (mind.memories().size() >= mind.slots()) {
            if (asked) {
                player.displayClientMessage(Component.literal("Your mind is full (" + mind.slots() + " places). "
                        + "Let something go first - J, Map.").withStyle(ChatFormatting.GOLD), true);
            }
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.MIND_FULL);
            return;
        }
        mind.memories().add(memory);
        player.sendSystemMessage(Component.literal("You fix it in your mind: " + memory.label() + ". ("
                + mind.memories().size() + "/" + mind.slots() + " places held)").withStyle(ChatFormatting.AQUA));
    }

    public static void forget(ServerPlayer player, int index) {
        MindData mind = mind(player);
        if (index >= 0 && index < mind.memories().size()) {
            MindData.Memory gone = mind.memories().remove(index);
            player.displayClientMessage(Component.literal("You let it go: " + gone.label() + "."), true);
        }
    }

    // ------------------------------------------------------------ asking the band

    /** "What do you remember?" Everything the band near you has in mind, on your map until tomorrow. */
    public static void askAround(ServerPlayer player) {
        MindData mind = mind(player);
        long tomorrow = today(player) + 1;
        mind.told().removeIf(m -> m.day() <= today(player));
        List<String> heard = new ArrayList<>();
        for (BandMember member : Band.ownNear(player, 24.0D)) {
            if (member.isBaby()) {
                continue;
            }
            for (MindData.Memory memory : member.memories()) {
                boolean dup = mind.told().stream().anyMatch(t -> t.kind().equals(memory.kind())
                        && t.pos().distSqr(memory.pos()) < 16.0D * 16.0D);
                if (dup) {
                    continue;
                }
                member.ensureName();
                mind.told().add(new MindData.Memory(memory.kind(), memory.label() + " (" + member.getName().getString() + ")",
                        memory.pos(), tomorrow));
                heard.add(member.getName().getString() + ": " + memory.label().toLowerCase() + ", "
                        + (int) Math.sqrt(memory.pos().distSqr(player.blockPosition())) + " blocks "
                        + bearing(player, memory.pos()));
            }
        }
        if (heard.isEmpty()) {
            player.displayClientMessage(Component.literal("Nobody near remembers anywhere you do not already know."), true);
            return;
        }
        player.sendSystemMessage(Component.literal("They tell you what they remember - on your map for today:")
                .withStyle(ChatFormatting.AQUA));
        for (String line : heard.subList(0, Math.min(8, heard.size()))) {
            player.sendSystemMessage(Component.literal("  " + line).withStyle(ChatFormatting.GRAY));
        }
    }

    /** A band member, going about, notices something worth remembering. Called now and then. */
    public static void memberNotices(BandMember member) {
        if (!(member.level() instanceof ServerLevel level) || member.isBaby()) {
            return;
        }
        RandomSource random = member.getRandom();
        BlockPos origin = member.blockPosition();
        MindData.Memory found = null;
        long day = level.getDayTime() / 24000L;
        for (int i = 0; i < 40 && found == null; i++) {
            BlockPos pos = origin.offset(random.nextInt(33) - 16, random.nextInt(9) - 4, random.nextInt(33) - 16);
            BlockState state = level.getBlockState(pos);
            String deposit = depositName(state);
            if (deposit != null) {
                found = new MindData.Memory("deposit", deposit, pos.immutable(), day);
            } else if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                found = new MindData.Memory("lava", "Lava pool", pos.immutable(), day);
            } else if (state.is(ModBlocks.TERMITE_MOUND.get())) {
                boolean great = state.getValue(dev.hominin.evolution.block.TermiteMoundBlock.COLONY);
                found = new MindData.Memory("termites", great ? "The great termite colony" : "Termite mound", pos.immutable(), day);
            }
        }
        if (found == null) {
            var clans = level.getEntitiesOfClass(dev.hominin.evolution.entity.Crocuta.class,
                    member.getBoundingBox().inflate(24.0D), Mob::isAlive);
            if (!clans.isEmpty()) {
                found = new MindData.Memory("clan", "Hyena clan", clans.get(0).blockPosition(), day);
            }
        }
        if (found != null) {
            member.remember(found);
        }
    }

    // ------------------------------------------------------------ following

    public static void lead(ServerPlayer player, BlockPos where, String label, String band) {
        MindData mind = mind(player);
        mind.setWaypoint(where, label, band);
        sync(player);
        player.displayClientMessage(Component.literal("You set off for " + label + " - " + (int) Math.sqrt(
                Bands.horizontal(where, player.blockPosition())) + " blocks " + bearing(player, where) + ".")
                .withStyle(ChatFormatting.AQUA), true);
    }

    public static void stop(ServerPlayer player) {
        mind(player).setWaypoint(null, "", "");
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        MindData mind = player.getData(Attachments.MIND);
        BlockPos at = mind.waypoint();
        PacketDistributor.sendToPlayer(player, at == null ? new WaypointPayload(false, 0, 0, "")
                : new WaypointPayload(true, at.getX(), at.getZ(), mind.waypointLabel()));
    }

    /** Every two seconds: a followed band moves with its people; arriving ends it; friendly troops are marked. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 40 != 29) {
            return;
        }
        MindData mind = player.getData(Attachments.MIND);
        BlockPos at = mind.waypoint();
        if (at != null) {
            if (!mind.waypointBand().isEmpty()) {
                try {
                    Bands.Record band = Bands.get(player.serverLevel(), java.util.UUID.fromString(mind.waypointBand()));
                    if (band != null) {
                        BlockPos now = Relations.whereIs(player.serverLevel(), band);
                        if (now.distSqr(at) > 16.0D) {
                            mind.setWaypoint(now, mind.waypointLabel(), mind.waypointBand());
                            at = now;
                            sync(player);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    mind.setWaypoint(at, mind.waypointLabel(), "");
                }
            }
            if (Bands.horizontal(at, player.blockPosition()) < ARRIVED * ARRIVED) {
                player.displayClientMessage(Component.literal("You are there: " + mind.waypointLabel() + ".")
                        .withStyle(ChatFormatting.AQUA), true);
                stop(player);
            }
        }
        if (player.tickCount % 200 == 29) {
            markTroops(player, mind);
        }
    }

    /** Troops that trust you are remembered where you last saw them. */
    private static void markTroops(ServerPlayer player, MindData mind) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0D),
                m -> m.isAlive() && m instanceof TroopAnimal)) {
            java.util.UUID troop = ((TroopAnimal) mob).getTroop();
            if (troop == null || !TroopRelations.isTrusted(player, troop)) {
                continue;
            }
            String kind = mob instanceof dev.hominin.evolution.entity.Baboon ? "Baboon troop"
                    : mob instanceof dev.hominin.evolution.entity.Chimpanzee ? "Chimpanzee community" : "Bonobo troop";
            mind.troops().removeIf(m -> m.label().equals(troop.toString()));
            mind.troops().add(new MindData.Memory(kind, troop.toString(), mob.blockPosition(), today(player)));
        }
    }

    // ------------------------------------------------------------ the map screen

    public static void act(ServerPlayer player, int action, int index, String text) {
        switch (action) {
            case MapActionPayload.OPEN -> send(player);
            case MapActionPayload.REMEMBER -> {
                rememberHere(player, text);
                send(player);
            }
            case MapActionPayload.FORGET -> {
                forget(player, index);
                send(player);
            }
            case MapActionPayload.LEAD -> leadMarker(player, index, text);
            case MapActionPayload.STOP -> stop(player);
            case MapActionPayload.ASK -> {
                askAround(player);
                send(player);
            }
            case MapActionPayload.PACK_UP -> {
                dev.hominin.evolution.hunt.Predation.packUp(player);
                send(player);
            }
            case MapActionPayload.SETTLE -> {
                dev.hominin.evolution.hunt.Predation.settleHere(player);
                send(player);
            }
            default -> {
            }
        }
    }

    /** Following something picked on the map: a band by id, else the coordinates it was sent with. */
    private static void leadMarker(ServerPlayer player, int index, String target) {
        if (target.startsWith("band:")) {
            try {
                Bands.Record band = Bands.get(player.serverLevel(), java.util.UUID.fromString(target.substring(5)));
                if (band != null) {
                    Relations.lead(player, band);
                }
            } catch (IllegalArgumentException ignored) {
                // Not a band after all.
            }
            return;
        }
        if (target.startsWith("at:")) {
            String[] parts = target.substring(3).split(",", 3);
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int z = Integer.parseInt(parts[1]);
                    BlockPos where = new BlockPos(x, player.getBlockY(), z);
                    lead(player, where, parts[2], "");
                } catch (NumberFormatException ignored) {
                    // A malformed marker leads nowhere.
                }
            }
        }
    }

    private static String at(BlockPos pos, String label) {
        return "at:" + pos.getX() + "," + pos.getZ() + "," + label;
    }

    public static void send(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MindData mind = mind(player);
        mind.told().removeIf(m -> m.day() <= today(player));
        List<MapPayload.Ground> grounds = new ArrayList<>();
        List<MapPayload.Marker> markers = new ArrayList<>();
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        int ownRadius = Bands.radiusFor(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        String ownName = Relations.ownName(player);
        boolean settled = dev.hominin.evolution.hunt.Predation.settled(player);
        int presence = Presence.get(player);
        if (settled) {
            var land = dev.hominin.evolution.world.Land.ofPlayer(player);
            grounds.add(new MapPayload.Ground(camp.getX(), camp.getZ(), ownRadius, 0, BandNames.capital(ownName),
                    "Your ground - presence " + presence + "/50, " + Presence.label(presence) + "|Cohesion "
                            + dev.hominin.evolution.band.Cohesion.get(player) + "/" + dev.hominin.evolution.band.Cohesion.MAX
                            + "|Pressure " + land.total() + "/10 - "
                            + dev.hominin.evolution.world.Land.label(land.total()) + "|" + String.join(", ", land.describe(player))
                            + "|Desperation " + dev.hominin.evolution.band.Claims.ownDesperation(player) + "/5"));
            markers.add(new MapPayload.Marker(camp.getX(), camp.getZ(), MapPayload.CAMP, "Your camp - presence " + presence
                    + "/50", -1, at(camp, "your camp")));
        }
        for (Bands.Record band : Bands.all(level)) {
            if (!band.knownTo(player.getUUID())) {
                continue;
            }
            int standing = Relations.standing(player, band);
            BlockPos where = band.nomadic() ? Relations.whereIs(level, band) : band.home;
            String label = BandNames.capital(band.name) + " (" + (band.nomadic() ? "Paranthropus"
                    : dev.hominin.evolution.band.Relations.speciesName(band.species)) + ") - standing " + standing + "/50";
            if (!band.nomadic()) {
                label += ", presence " + band.presence + "/50";
                String pressure = level.hasChunk(band.home.getX() >> 4, band.home.getZ() >> 4)
                        ? "|Pressure " + dev.hominin.evolution.world.Land.of(level, band.home, band.radius()).total() + "/10"
                        : "";
                grounds.add(new MapPayload.Ground(band.home.getX(), band.home.getZ(), band.radius(), 1,
                        BandNames.capital(band.name), Relations.speciesName(band.species) + "|Standing " + standing
                                + "/50 - " + Relations.tier(standing)
                                + "|Presence " + band.presence + "/50 - " + band.strength() + "|Cohesion " + band.cohesion
                                + "/50 - " + band.temper() + "|Desperation " + band.desperation + "/5 - "
                                + dev.hominin.evolution.band.Claims.desperationLabel(band.desperation) + pressure));
            }
            markers.add(new MapPayload.Marker(where.getX(), where.getZ(), band.nomadic() ? MapPayload.PARANTHROPUS : MapPayload.BAND,
                    label, -1, "band:" + band.id));
        }
        for (dev.hominin.evolution.entity.ChimpRanges.Range range
                : dev.hominin.evolution.entity.ChimpRanges.knownTo(level, player.getUUID())) {
            boolean trusted = dev.hominin.evolution.entity.TroopRelations.isTrusted(player, range.community());
            grounds.add(new MapPayload.Ground(range.home().getX(), range.home().getZ(),
                    dev.hominin.evolution.entity.ChimpRanges.RADIUS, 2, "Chimpanzee community",
                    trusted ? "They know you: you may pass." : "Territorial - strangers are warned, then driven off."));
        }
        for (MindData.Memory troop : mind.troops()) {
            markers.add(new MapPayload.Marker(troop.pos().getX(), troop.pos().getZ(), MapPayload.TROOP,
                    troop.kind() + " (trusts you)", -1, at(troop.pos(), troop.kind())));
        }
        for (int i = 0; i < mind.memories().size(); i++) {
            MindData.Memory memory = mind.memories().get(i);
            markers.add(new MapPayload.Marker(memory.pos().getX(), memory.pos().getZ(), kindOf(memory.kind()), memory.label(), i,
                    at(memory.pos(), memory.label())));
        }
        for (MindData.Memory memory : mind.told()) {
            markers.add(new MapPayload.Marker(memory.pos().getX(), memory.pos().getZ(), MapPayload.TOLD, memory.label(), -1,
                    at(memory.pos(), memory.label())));
        }
        // The places the band knows: not held in any one head, and never forgotten while the band lives.
        for (dev.hominin.evolution.world.Pois.Poi poi : dev.hominin.evolution.world.Pois.known(player)) {
            markers.add(new MapPayload.Marker(poi.pos().getX(), poi.pos().getZ(), MapPayload.PLACE + poi.kind().ordinal(),
                    poi.label() + " (the band knows it)", -1, at(poi.pos(), poi.label())));
        }
        BlockPos store = dev.hominin.evolution.band.ToolPiles.store(level, player.getUUID());
        if (store != null) {
            int tools = dev.hominin.evolution.band.ToolPiles.toolsIn(level, player.getUUID());
            markers.add(new MapPayload.Marker(store.getX(), store.getZ(), MapPayload.TOOL_STORE, "Your band's tools"
                    + (tools > 0 ? " - " + tools + " lying there" : ""), -1, at(store, "your band's tools")));
        }
        // What you have built, and what you have marked out to.
        for (dev.hominin.evolution.build.Sites.Site site : dev.hominin.evolution.build.Sites.ownedBy(level,
                player.getUUID())) {
            markers.add(new MapPayload.Marker(site.origin().getX(), site.origin().getZ(), MapPayload.STRUCTURE,
                    site.label(), -1, at(site.origin(), site.name())));
        }
        BlockPos waypoint = mind.waypoint();
        if (waypoint != null) {
            markers.add(new MapPayload.Marker(waypoint.getX(), waypoint.getZ(), MapPayload.WAYPOINT,
                    "Heading for: " + mind.waypointLabel(), -1, ""));
        }
        PacketDistributor.sendToPlayer(player, new MapPayload(player.getBlockX(), player.getBlockZ(), mind.slots(),
                presence, ownName, settled, grounds, markers));
    }

    private static int kindOf(String kind) {
        return switch (kind) {
            case "deposit" -> MapPayload.DEPOSIT;
            case "lava" -> MapPayload.LAVA;
            case "termites" -> MapPayload.TERMITES;
            case "clan" -> MapPayload.CLAN;
            case "water" -> MapPayload.WATER;
            default -> MapPayload.CUSTOM;
        };
    }

    // ------------------------------------------------------------ rain on lava

    /**
     * While it rains, lava that meets the rain cools fast at its edges - into glass. Now and then, near
     * surface lava by a player, a piece of obsidian is left lying at the edge.
     */
    public static void rainOnLava(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.tickCount % 600 != 311 || !level.isRaining()) {
            return;
        }
        RandomSource random = player.getRandom();
        List<BlockPos> pools = new ArrayList<>();
        for (MindData.Memory memory : player.getData(Attachments.MIND).memories()) {
            if (memory.kind().equals("lava")) {
                pools.add(memory.pos());
            }
        }
        for (int i = 0; i < 16; i++) {
            int x = player.getBlockX() + random.nextInt(81) - 40;
            int z = player.getBlockZ() + random.nextInt(81) - 40;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            if (level.getFluidState(top).is(FluidTags.LAVA)) {
                pools.add(top);
            }
        }
        for (BlockPos pool : pools) {
            if (!level.hasChunk(pool.getX() >> 4, pool.getZ() >> 4) || !level.canSeeSky(pool.above())
                    || random.nextInt(3) != 0) {
                continue;
            }
            int glass = 0;
            for (BlockPos near : BlockPos.betweenClosed(pool.offset(-8, -3, -8), pool.offset(8, 3, 8))) {
                if (level.getBlockState(near).is(ModBlocks.OBSIDIAN_ROCK.get())) {
                    glass++;
                }
            }
            if (glass >= 4) {
                continue;
            }
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = pool.getX() + random.nextInt(9) - 4;
                int z = pool.getZ() + random.nextInt(9) - 4;
                BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                BlockState obsidian = ModBlocks.OBSIDIAN_ROCK.get().defaultBlockState();
                if (level.getBlockState(spot).canBeReplaced() && level.getFluidState(spot).isEmpty()
                        && obsidian.canSurvive(level, spot) && besideLava(level, spot)) {
                    level.setBlock(spot, obsidian, 3);
                    break;
                }
            }
        }
    }

    private static boolean besideLava(ServerLevel level, BlockPos spot) {
        for (BlockPos near : BlockPos.betweenClosed(spot.offset(-2, -1, -2), spot.offset(2, 0, 2))) {
            if (level.getFluidState(near).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    public static String bearing(ServerPlayer player, BlockPos to) {
        double dx = to.getX() - player.getX();
        double dz = to.getZ() - player.getZ();
        String ns = Math.abs(dz) < Math.abs(dx) / 2.0D ? "" : dz < 0 ? "north" : "south";
        String ew = Math.abs(dx) < Math.abs(dz) / 2.0D ? "" : dx < 0 ? "west" : "east";
        String both = ns + ew;
        return "to the " + (ns.isEmpty() || ew.isEmpty() ? both : ns + "-" + ew);
    }

    private MentalMap() {
    }
}
