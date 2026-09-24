package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.mind.Skills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Paranthropus boisei: the other hominin. Huge jaws, a crest on the skull to anchor them,
 * and a diet of whatever is tough enough that nothing else bothers with it.
 *
 * <p>They were our neighbours for a million years, and this is what that was like. They
 * strip the ground for {@link #FORAGE_RADIUS} blocks, so foraging near them is slim, and in
 * a drought you may have to drive them off. But they know the country: they call when a
 * predator is about before it ever reaches you, and they will walk you to good stone or to
 * obsidian. You can trade with them - and, Homo being Homo, now and then get the better
 * of them. Knap in front of them and they watch, and learn.
 *
 * <p>They are gone by the time of Homo antecessor.
 */
public final class Paranthropus {
    public static final ResourceLocation STAGE =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "paranthropus_boisei");
    /** How much ground a troop strips of anything worth eating. */
    public static final double FORAGE_RADIUS = 60.0D;
    /** Other primates pick over a smaller patch, but they pick it over thoroughly. */
    public static final double PRIMATE_FORAGE_RADIUS = 35.0D;
    private static final double WARN_RADIUS = 128.0D;
    private static final int CHECK_TICKS = 1800;
    private static final float SPAWN_CHANCE = 0.3F;
    private static final double CROWDING_RADIUS = 220.0D;
    private static final int GUIDE_SEARCH = 72;
    /** A troop will show you the way once a day. */
    private static final long GUIDE_COOLDOWN = 24000L;
    /** How often a lowball offer gets past them. */
    private static final float LOWBALL_CHANCE = 0.4F;

    private static final Map<UUID, Long> lastGuided = new HashMap<>();
    /** One alarm per player per minute: a pair of cats is one alarm, not two. */
    private static final Map<UUID, Long> lastWarned = new HashMap<>();
    private static final long WARN_GAP = 1200L;

    public static boolean is(BandMember member) {
        return STAGE.equals(member.getStage());
    }

    /** Whether Paranthropus still lives in this player's era: from Australopithecus until antecessor. */
    public static boolean stillAround(ResourceLocation era) {
        return switch (era.getPath()) {
            case "australopithecus", "australopithecus_anamensis", "homo_habilis", "homo_rudolfensis",
                    "homo_erectus", "homo_ergaster" -> true;
            default -> false;
        };
    }

    private static ResourceLocation eraOf(Player player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage();
    }

    public static List<BandMember> near(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(radius),
                m -> m.isAlive() && is(m));
    }

    // ------------------------------------------------------------ spawning

    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_TICKS != 900 || player.isSpectator()
                || !stillAround(eraOf(player)) || !near(player, CROWDING_RADIUS).isEmpty()
                || player.getRandom().nextFloat() >= SPAWN_CHANCE) {
            return;
        }
        // One troop in any stretch of country: the registry remembers the ones nobody is near.
        boolean known = Bands.all(player.serverLevel()).stream().anyMatch(b -> b.nomadic()
                && Bands.horizontal(b.home, player.blockPosition()) < CROWDING_RADIUS * CROWDING_RADIUS);
        if (known) {
            return;
        }
        WildBands.spawnNear(player, 64, 150, STAGE, false);
    }

    // ------------------------------------------------------------ competition

    /**
     * How much of the usual luck foraging has here, with whoever else is picking the same
     * ground. Paranthropus clean out a wide stretch, and a drought makes it worse. Baboons
     * and apes work a smaller patch.
     */
    public static float forageShare(ServerPlayer player) {
        float share = 1.0F;
        if (!near(player, FORAGE_RADIUS).isEmpty()) {
            share *= dev.hominin.evolution.survival.Seasons.strained(player.level()) ? 0.15F : 0.3F;
        }
        boolean primates = !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(PRIMATE_FORAGE_RADIUS),
                m -> m.isAlive() && (m instanceof dev.hominin.evolution.entity.Baboon
                        || m instanceof dev.hominin.evolution.entity.Chimpanzee
                        || m instanceof dev.hominin.evolution.entity.Bonobo
                        || m instanceof dev.hominin.evolution.entity.Dinopithecus)).isEmpty();
        if (primates) {
            share *= 0.35F;
        }
        return share;
    }

    /** Which other primates are working the ground near the player, by name - or null if none. */
    @Nullable
    public static String primatesNear(ServerPlayer player) {
        java.util.Set<String> kinds = new java.util.LinkedHashSet<>();
        for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(PRIMATE_FORAGE_RADIUS), net.minecraft.world.entity.Mob::isAlive)) {
            if (mob instanceof dev.hominin.evolution.entity.Baboon) {
                kinds.add("baboons");
            } else if (mob instanceof dev.hominin.evolution.entity.Chimpanzee) {
                kinds.add("chimpanzees");
            } else if (mob instanceof dev.hominin.evolution.entity.Bonobo) {
                kinds.add("bonobos");
            } else if (mob instanceof dev.hominin.evolution.entity.Dinopithecus) {
                kinds.add("giant baboons");
            }
        }
        return kinds.isEmpty() ? null : String.join(" and ", kinds);
    }

    /** Why the ground came up empty, if somebody else is to blame. */
    @Nullable
    public static String whyEmpty(ServerPlayer player) {
        if (!near(player, FORAGE_RADIUS).isEmpty()) {
            return "Paranthropus have been through here. The ground is picked clean. (Drive them off with a display.)";
        }
        return forageShare(player) < 1.0F ? "Other primates have already picked this ground over." : null;
    }

    /**
     * One of them hit by a player - a branch, a thrown stone. Paranthropus do not fight Homo
     * over it; the troop scatters, and goes to forage somewhere you are not.
     */
    public static void struck(BandMember member, ServerPlayer by) {
        if (!is(member) || !member.isAlive()) {
            return;
        }
        List<BandMember> troop = member.level().getEntitiesOfClass(BandMember.class,
                member.getBoundingBox().inflate(24.0D), m -> m.isAlive() && is(m)
                        && m.getBandId() != null && m.getBandId().equals(member.getBandId()));
        if (!troop.contains(member)) {
            troop.add(member);
        }
        driveOff(by, troop);
    }

    /** Anything that makes a display at them - and they are no match for it. */
    public static int scareNear(ServerPlayer player, double radius) {
        List<BandMember> troop = near(player, radius);
        if (troop.isEmpty()) {
            return 0;
        }
        return driveOff(player, troop);
    }

    private static int driveOff(ServerPlayer player, List<BandMember> troop) {
        // Driven off: they take their foraging somewhere well away from you.
        BlockPos from = troop.get(0).blockPosition();
        double dx = from.getX() - player.getX();
        double dz = from.getZ() - player.getZ();
        double length = Math.max(1.0D, Math.sqrt(dx * dx + dz * dz));
        BlockPos away = player.serverLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                from.offset((int) (dx / length * 100.0D), 0, (int) (dz / length * 100.0D)));
        for (BandMember member : troop) {
            dev.hominin.evolution.combat.Scare.scare(member, player.position(), 10 * 20);
            member.leaveTowards(away);
        }
        player.displayClientMessage(Component.literal("The Paranthropus break and go, taking their foraging elsewhere.")
                .withStyle(ChatFormatting.GOLD), true);
        return troop.size();
    }

    // ------------------------------------------------------------ neighbours

    /**
     * A predator has come into the country. If a Paranthropus troop is within earshot, they
     * saw it first, and they are screaming about it.
     */
    public static void warn(ServerPlayer player, BlockPos danger) {
        List<BandMember> troop = near(player, WARN_RADIUS);
        if (troop.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        if (now - lastWarned.getOrDefault(player.getUUID(), -WARN_GAP) < WARN_GAP) {
            return;
        }
        lastWarned.put(player.getUUID(), now);
        BandMember caller = troop.get(0);
        player.serverLevel().playSound(null, caller.blockPosition(), ModSounds.BAND_CALL.get(), SoundSource.NEUTRAL,
                3.0F, 1.4F);
        player.sendSystemMessage(Component.literal("Paranthropus are shrieking alarm calls. Something is coming in, "
                + WildBands.bearingFrom(player, danger) + ".").withStyle(ChatFormatting.GOLD));
    }

    /** Somebody made a stone tool in front of them. They watch the hands. */
    public static void watched(ServerPlayer player) {
        boolean anyLearned = false;
        for (BandMember member : near(player, 16.0D)) {
            if (!member.knowsSkill(Skills.Skill.LOMEKWIAN) && member.hasLineOfSight(player)) {
                member.learnSkill(Skills.Skill.LOMEKWIAN);
                anyLearned = true;
            }
        }
        if (anyLearned) {
            player.displayClientMessage(Component.literal(
                    "The Paranthropus watch your hands the whole time. They have seen how it is done.")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /**
     * Whether this one may knap at all. The ones who have watched you do it will, now and
     * then. The rest only pick it up on their own once Homo habilis is making tools around
     * them, and even then rarely.
     */
    public static boolean mayCraft(BandMember member) {
        if (member.knowsSkill(Skills.Skill.LOMEKWIAN)) {
            return member.getRandom().nextInt(3) == 0;
        }
        Player nearest = member.level().getNearestPlayer(member, 96.0D);
        if (nearest == null) {
            return false;
        }
        String era = eraOf(nearest).getPath();
        boolean homoAbout = !era.startsWith("australopithecus") && !era.equals("ardipithecus");
        return homoAbout && member.getRandom().nextInt(12) == 0;
    }

    /** They get the worst of a deal now and then: a smaller brain, and not much use for flakes. */
    public static boolean fallsForLowball(BandMember member, int offerTier, int wantedTier) {
        return is(member) && wantedTier - offerTier <= 2 && member.getRandom().nextFloat() < LOWBALL_CHANCE;
    }

    // ------------------------------------------------------------ guiding

    /** "Show me good stone" / "Show me obsidian". */
    public static void guide(ServerPlayer player, BandMember member, boolean obsidian) {
        member.ensureName();
        UUID band = member.getBandId() != null ? member.getBandId() : member.getUUID();
        long now = player.level().getGameTime();
        if (now - lastGuided.getOrDefault(band, -GUIDE_COOLDOWN) < GUIDE_COOLDOWN) {
            say(player, member.getName().getString() + " has walked you about enough for one day.");
            return;
        }
        BlockPos target = find(player.serverLevel(), member.blockPosition(), obsidian);
        if (target == null) {
            say(player, member.getName().getString() + " looks about, and shrugs. There is none near here.");
            return;
        }
        lastGuided.put(band, now);
        member.startGuiding(player, target);
        say(player, member.getName().getString() + " grunts and sets off. Follow.");
    }

    @Nullable
    private static BlockPos find(ServerLevel level, BlockPos around, boolean obsidian) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -GUIDE_SEARCH; dx <= GUIDE_SEARCH; dx++) {
            for (int dz = -GUIDE_SEARCH; dz <= GUIDE_SEARCH; dz++) {
                int x = around.getX() + dx;
                int z = around.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                for (int y = top - 6; y <= top + 1; y++) {
                    pos.set(x, y, z);
                    if (!valuable(level.getBlockState(pos), obsidian)) {
                        continue;
                    }
                    double distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                    break;
                }
            }
        }
        return best;
    }

    private static boolean valuable(BlockState state, boolean obsidian) {
        if (obsidian) {
            return state.is(ModBlocks.OBSIDIAN_ROCK.get()) || state.is(Blocks.OBSIDIAN);
        }
        return state.is(ModBlocks.CHERT_DEPOSIT.get()) || state.is(ModBlocks.QUARTZITE_DEPOSIT.get())
                || state.is(ModBlocks.CHERT_ROCK.get()) || state.is(ModBlocks.BASALT_ROCK.get());
    }

    private static void say(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    private Paranthropus() {
    }
}
