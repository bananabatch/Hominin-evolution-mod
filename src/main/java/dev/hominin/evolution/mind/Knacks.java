package dev.hominin.evolution.mind;

import java.util.List;
import java.util.Map;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Bands;
import dev.hominin.evolution.world.Pois;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The three knacks - skills that are about the one who has them, not something to show anybody:
 *
 * <ul>
 * <li><b>Clean eye</b> - run five animals down and you see them where others do not: whatever is standing in the
 * grass near you shows itself, and when one of a herd runs, you keep hold of it and two more.</li>
 * <li><b>Nomad</b> - move your band three times, each a long walk (250 blocks) from the last camp. Out adventuring,
 * far from camp, the country opens up to you: places worth knowing, the old tool deposits and lone camps, giant
 * carcasses, anything about to drop dead.</li>
 * <li><b>Jack of all trades</b> - three of: level 2 knapper, level 2 hunter, two places known, two skills taught,
 * a tool worth something made. From then on every new skill brings your knapping or your hunting up a level with
 * it, and both come easier.</li>
 * </ul>
 */
public final class Knacks {
    private static final String SKILL = dev.hominin.evolution.EvolutionManager.SKILL_PREFIX;
    private static final String RUN_DOWNS = SKILL + "run_downs";
    private static final String NOMAD_MOVES = SKILL + "nomad_moves";
    private static final String TAUGHT = SKILL + "jack_taught";
    private static final String TOOL = SKILL + "jack_tool";
    private static final int RUN_DOWNS_TO_LEARN = 5;
    private static final int NOMAD_DISTANCE = 250;
    private static final int NOMAD_MOVES_TO_LEARN = 3;
    private static final double ADVENTURING = 120.0D;

    private static Map<String, Integer> counters(Player player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    // ------------------------------------------------------------ getting them

    /** One more animal run down. */
    public static void ranDown(ServerPlayer player) {
        if (counters(player).merge(RUN_DOWNS, 1, Integer::sum) >= RUN_DOWNS_TO_LEARN) {
            Skills.learn(player, Skills.Skill.CLEAN_EYE);
        }
    }

    /** The band settled somewhere new: a long way from the last camp counts towards nomad. */
    public static void relocated(ServerPlayer player, BlockPos from, BlockPos to) {
        if (Bands.horizontal(from, to) < (double) NOMAD_DISTANCE * NOMAD_DISTANCE) {
            return;
        }
        int moves = counters(player).merge(NOMAD_MOVES, 1, Integer::sum);
        if (moves < NOMAD_MOVES_TO_LEARN) {
            player.displayClientMessage(Component.literal("A long way from the last camp (" + moves + "/"
                    + NOMAD_MOVES_TO_LEARN + " such moves).").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Skills.learn(player, Skills.Skill.NOMAD);
    }

    /** Somebody of the band learned a skill from you. */
    public static void taught(ServerPlayer player) {
        counters(player).merge(TAUGHT, 1, Integer::sum);
    }

    /** How many of the five a jack of all trades needs, you have done. */
    public static int jackProgress(ServerPlayer player) {
        Map<String, Integer> counters = counters(player);
        int done = 0;
        if (dev.hominin.evolution.knapping.Acheulean.level(player) <= 2) {
            done++;
        }
        if (dev.hominin.evolution.hunt.Persistence.level(player) <= 2) {
            done++;
        }
        if (Pois.known(player).size() >= 2) {
            done++;
        }
        if (counters.getOrDefault(TAUGHT, 0) >= 2) {
            done++;
        }
        if (counters.getOrDefault(TOOL, 0) > 0) {
            done++;
        }
        return done;
    }

    /** A new skill, for a jack of all trades: knapping or hunting - whichever is behind - comes up a level with it. */
    public static void learnedAnother(ServerPlayer player, Skills.Skill skill) {
        if (skill == Skills.Skill.JACK || !Skills.knows(player, Skills.Skill.JACK)) {
            return;
        }
        Map<String, Integer> counters = counters(player);
        int knap = dev.hominin.evolution.knapping.Acheulean.level(player);
        int hunt = dev.hominin.evolution.hunt.Persistence.level(player);
        // Knapping runs 4 to 1, hunting 3 to 0: whichever is further from the best goes up.
        if (knap > 1 && knap - 1 >= hunt) {
            counters.put(dev.hominin.evolution.knapping.Acheulean.LEVEL, knap - 1);
            player.sendSystemMessage(Component.literal("Jack of all trades: your knapping comes on with it - level "
                    + (knap - 1) + ".").withStyle(ChatFormatting.GOLD));
        } else if (hunt > 0) {
            counters.put(dev.hominin.evolution.hunt.Persistence.LEVEL, hunt - 1);
            player.sendSystemMessage(Component.literal("Jack of all trades: your hunting comes on with it - level "
                    + (hunt - 1) + ".").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Every second. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 13 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (player.tickCount % 200 == 13 && !Skills.knows(player, Skills.Skill.JACK)) {
            for (ItemStack stack : player.getInventory().items) {
                if (stack.is(ModTags.Items.STONE_TOOLS) && dev.hominin.evolution.band.Trading.tierOf(stack,
                        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage()) >= 2) {
                    counters(player).put(TOOL, 1);
                    break;
                }
            }
            if (jackProgress(player) >= 3) {
                Skills.learn(player, Skills.Skill.JACK);
            }
        }
        if (player.tickCount % 60 == 13 && Skills.knows(player, Skills.Skill.CLEAN_EYE)) {
            cleanEye(player, level);
        }
        if (player.tickCount % 200 == 73 && Skills.knows(player, Skills.Skill.NOMAD) && adventuring(player)) {
            nomad(player, level);
        }
    }

    // ------------------------------------------------------------ clean eye

    /** Whatever stands in the grass near you shows itself. */
    private static void cleanEye(ServerPlayer player, ServerLevel level) {
        int shown = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(22.0D),
                m -> m.isAlive() && !(m instanceof BandMember) && (m instanceof Animal
                        || dev.hominin.evolution.hunt.PredatorLull.hunter(m)))) {
            if (inGrass(level, mob.blockPosition()) && !mob.hasEffect(MobEffects.GLOWING)) {
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 70, 0, false, false));
                if (++shown >= 6) {
                    return;
                }
            }
        }
    }

    private static boolean inGrass(ServerLevel level, BlockPos at) {
        for (BlockPos pos : new BlockPos[] {at, at.above()}) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** One of a herd broke and ran, and you have hold of it: with a clean eye, two more of them as well. */
    public static void trackMore(ServerPlayer player, LivingEntity quarry, int ticks) {
        if (!Skills.knows(player, Skills.Skill.CLEAN_EYE)) {
            return;
        }
        List<LivingEntity> herd = quarry.level().getEntitiesOfClass(LivingEntity.class,
                quarry.getBoundingBox().inflate(14.0D), e -> e != quarry && e.isAlive() && e.getType() == quarry.getType());
        herd.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(quarry)));
        for (int i = 0; i < Math.min(2, herd.size()); i++) {
            herd.get(i).addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        }
        if (!herd.isEmpty()) {
            player.displayClientMessage(Component.literal("A clean eye: you have " + (1 + Math.min(2, herd.size()))
                    + " of them in your head.").withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ------------------------------------------------------------ nomad

    private static boolean adventuring(ServerPlayer player) {
        return !dev.hominin.evolution.hunt.Predation.settled(player) || Bands.horizontal(
                dev.hominin.evolution.hunt.Predation.campOf(player), player.blockPosition()) > ADVENTURING * ADVENTURING;
    }

    /** Far from camp, the country opens up. */
    private static void nomad(ServerPlayer player, ServerLevel level) {
        boolean erectus = Bands.erectusOn(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        for (Pois.Poi poi : Pois.near(level, player.blockPosition(), 160, erectus)) {
            boolean worth = poi.kind().pressure >= 3 || poi.kind() == Pois.Kind.TOOLS || poi.kind() == Pois.Kind.CAMP;
            if (worth && !Pois.knows(player, poi.id())) {
                Pois.learn(player, poi);
                player.sendSystemMessage(Component.literal("A nomad's eye for country: " + poi.label() + ", "
                        + (int) Math.sqrt(Bands.horizontal(poi.pos(), player.blockPosition())) + " blocks off. (On your map.)")
                        .withStyle(ChatFormatting.AQUA));
                return;
            }
        }
        BlockPos kill = dev.hominin.evolution.hunt.Carcasses.nearestKill(level, player.blockPosition(), 96.0D, true);
        if (kill != null && level.getBlockState(kill).is(ModBlocks.GIANT_CARCASS.get())) {
            String key = "nomad_giant_" + kill.asLong();
            int day = (int) (level.getDayTime() / 24000L);
            if (counters(player).getOrDefault(key, -1) != day) {
                counters(player).put(key, day);
                player.sendSystemMessage(Component.literal("A nomad's eye: a giant's carcass, "
                        + (int) Math.sqrt(kill.distSqr(player.blockPosition())) + " blocks off - more meat than a band "
                        + "can carry.").withStyle(ChatFormatting.AQUA));
            }
        }
        // Anything about to drop dead.
        for (LivingEntity dying : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32.0D),
                e -> e.isAlive() && !(e instanceof Player) && !(e instanceof BandMember)
                        && e.getHealth() < e.getMaxHealth() * 0.25F)) {
            dying.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        }
    }

    private Knacks() {
    }
}
