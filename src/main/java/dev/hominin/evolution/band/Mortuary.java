package dev.hominin.evolution.band;

import java.util.List;
import java.util.Map;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.survival.Kuru;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * What a band does with its dead.
 *
 * <p>Eating hominin flesh is food, and some peoples made it the right way to treat the
 * dead - kept with the living, eaten together. That is a choice a band can make from erectus
 * on, when there is enough of a mind to hold a rule. Without the rule, eating one of your own
 * kind shakes the band a little. With it, nobody minds - but the rule binds: every one of your
 * band who dies must be eaten, by you with the band, within a day, or the band feels the
 * betrayal. And whoever eats the brain gambles on kuru.
 */
public final class Mortuary {
    /** The norm outlives any one body: it is your people's, not yours. */
    public static final String NORM = EvolutionManager.SKILL_PREFIX + "norm_eat_dead";
    /** When the owed funeral feast is due by, in minutes of game time; zero when nothing is owed. */
    private static final String DUE = "funeral_due_minute";
    private static final long FEAST_WINDOW_TICKS = 24000L;
    /** The band minds once per sitting, not once per mouthful: ten minutes between. */
    private static final String LAST_DISAPPROVAL = "flesh_disapproval_minute";
    private static final int DISAPPROVAL_GAP_MINUTES = 10;
    private static final double WITH_THE_BAND = 16.0D;

    public static boolean isHomininFlesh(ItemStack stack) {
        return stack.is(ModItems.HOMININ_MEAT.get()) || stack.is(ModItems.HOMININ_BRAIN.get());
    }

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    public static boolean hasNorm(ServerPlayer player) {
        return counters(player).getOrDefault(NORM, 0) > 0;
    }

    /** From erectus: a mind big enough to hold a rule about the dead. */
    public static boolean canAdopt(ServerPlayer player) {
        String era = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage().getPath();
        return !era.startsWith("australopithecus") && !era.equals("ardipithecus") && !era.equals("homo_habilis")
                && !era.equals("homo_rudolfensis");
    }

    /** "Our dead stay with us." */
    public static void adopt(ServerPlayer player) {
        if (hasNorm(player)) {
            player.displayClientMessage(Component.literal("It is already the way of your people."), true);
            return;
        }
        if (!canAdopt(player)) {
            player.displayClientMessage(Component.literal(
                    "Your band cannot hold a rule like that yet. (Erectus and later.)"), true);
            return;
        }
        counters(player).put(NORM, 1);
        EvolutionManager.incrementCriterion(player, "adopt_norm", 1);
        player.sendSystemMessage(Component.literal("It is decided: your dead stay with you. From now on, when one of "
                + "the band dies, you eat of them together within a day - every time.").withStyle(ChatFormatting.DARK_RED));
        player.sendSystemMessage(Component.literal("(Butcher the body with a stone tool. Whoever eats the brain risks "
                + "kuru, which cannot be cured.)").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** One of the band has died. Under the norm, that is a feast owed. */
    public static void memberDied(ServerPlayer player) {
        if (!hasNorm(player)) {
            return;
        }
        long due = (player.level().getGameTime() + FEAST_WINDOW_TICKS) / 1200L;
        counters(player).put(DUE, (int) due);
        player.sendSystemMessage(Component.literal("The band looks to you. They are waiting to eat of the dead with you, "
                + "before the day is out.").withStyle(ChatFormatting.GOLD));
    }

    /** The player has eaten hominin meat or a brain. */
    public static void ate(ServerPlayer player, ItemStack eaten) {
        List<BandMember> band = Band.ownNear(player, WITH_THE_BAND);
        boolean brain = eaten.is(ModItems.HOMININ_BRAIN.get());
        if (hasNorm(player)) {
            if (counters(player).getOrDefault(DUE, 0) > 0 && !band.isEmpty()) {
                counters(player).remove(DUE);
                Cohesion.add(player, 3);
                player.sendSystemMessage(Component.literal("You eat of the dead with the band, as is right. They stay "
                        + "with you now.").withStyle(ChatFormatting.GOLD));
                // Somebody at the feast takes the brain if you did not.
                if (!brain && player.getRandom().nextFloat() < 0.5F) {
                    BandMember taker = band.get(player.getRandom().nextInt(band.size()));
                    if (player.getRandom().nextFloat() < Kuru.CHANCE) {
                        taker.contractKuru();
                    }
                }
            }
        } else {
            // No rule says this is right, and everyone saw.
            int minute = (int) (player.level().getGameTime() / 1200L);
            int last = counters(player).getOrDefault(LAST_DISAPPROVAL, -DISAPPROVAL_GAP_MINUTES);
            if (!band.isEmpty() && minute - last >= DISAPPROVAL_GAP_MINUTES) {
                counters(player).put(LAST_DISAPPROVAL, minute);
                Cohesion.add(player, -1, "given our dead their due");
                player.displayClientMessage(Component.literal("The band watches you eat it, and something in them pulls "
                        + "back. (Cohesion -1)").withStyle(ChatFormatting.GRAY), true);
            }
        }
        if (brain) {
            // Nothing to feel yet - but something to taste. The trembling starts tomorrow.
            boolean caught = Kuru.exposed(player);
            player.sendSystemMessage(Component.literal(caught
                    ? "The brain tastes strange. Sweet, and wrong somehow."
                    : Kuru.has(player) ? "It makes no difference now." : "It tastes the way a brain should.")
                    .withStyle(caught ? ChatFormatting.DARK_PURPLE : ChatFormatting.DARK_GRAY));
        }
    }

    /** Every few seconds: a feast left too long is a betrayal. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 100 != 50) {
            return;
        }
        int due = counters(player).getOrDefault(DUE, 0);
        if (due <= 0 || player.level().getGameTime() / 1200L <= due) {
            return;
        }
        counters(player).remove(DUE);
        Cohesion.add(player, -8, "kept to our way with the dead");
        for (BandMember member : Band.all(player)) {
            member.addBond(-1);
        }
        player.sendSystemMessage(Component.literal("The day is gone and the dead were left to the hyenas. That is not "
                + "what your people do, and the band will not forget it. (Cohesion -8, bond -1 with everyone)")
                .withStyle(ChatFormatting.DARK_RED));
    }

    private Mortuary() {
    }
}
