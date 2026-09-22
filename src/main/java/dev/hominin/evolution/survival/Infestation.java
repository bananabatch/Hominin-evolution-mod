package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Why primates spend hours a day with their hands in each other's hair.
 *
 * <p>Grooming is usually read as a social thing, and it is - but it is social because it
 * is useful. A body nobody attends to fills up with ticks, and a body full of ticks does
 * not mend. That is the whole bargain: you cannot reach your own back, so the only way to
 * stay healthy is for somebody else to want to help you, which means the health of every
 * individual runs through the relationships of the group.
 *
 * <p>Which is also why a tick is worth eating. Picking one off and putting it in your
 * mouth is not a joke - it is a small, free meal, and it is the direct payment for doing
 * somebody a favour.
 */
public final class Infestation {
    /** Two a day at most. Six takes three days of nobody touching you. */
    private static final int TICKS_PER_BITE = 12000;
    /** A nest is bedding, not a bed: sleeping in one risks a tick nothing has to bite for. */
    private static final float NEST_SLEEP_RISK = 0.01F;
    private static final int NEST_SLEEP_CHECK_TICKS = 200;

    /** Past this many, nothing you do heals. */
    public static final int CRIPPLING = 6;

    /** The worst it gets, so a neglected body is bad but not unrecoverable. */
    private static final int MAX = 10;

    /** How long the infestation affliction is re-asserted for while it stands. */
    private static final int AFFLICTION_TICKS = 200;

    private static final Map<UUID, Integer> burden = new HashMap<>();
    private static final Map<UUID, Long> lastBite = new HashMap<>();

    public static int of(ServerPlayer player) {
        return burden.getOrDefault(player.getUUID(), 0);
    }

    public static void set(ServerPlayer player, int value) {
        burden.put(player.getUUID(), Math.max(0, Math.min(MAX, value)));
    }

    /** How it reads on the stats screen. */
    public static String describe(ServerPlayer player) {
        int ticks = of(player);
        if (ticks == 0) {
            return "Clean";
        }
        if (ticks < 3) {
            return "Itchy (" + ticks + ")";
        }
        return ticks < CRIPPLING ? "Crawling (" + ticks + ")" : "Infested (" + ticks + ")";
    }

    /**
     * Picking them up, slowly, forever. Nothing stops this happening - it is the cost of
     * having a body - and the only cure is somebody else's hands.
     */
    public static void tick(ServerPlayer player) {
        tickNestSleep(player);
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        if (now - lastBite.getOrDefault(id, now) >= TICKS_PER_BITE) {
            lastBite.put(id, now);
            int worse = of(player) + 1;
            set(player, worse);
            if (worse == CRIPPLING) {
                player.sendSystemMessage(Component.literal(
                        "You are crawling with ticks. Nothing is going to heal until somebody picks them off.")
                        .withStyle(ChatFormatting.RED));
            }
        } else {
            lastBite.putIfAbsent(id, now);
        }
        // Re-asserted rather than set once, so it lapses on its own the moment somebody
        // grooms the count back down - and so anything worse still overrides it.
        if (of(player) >= CRIPPLING) {
            Afflictions.afflict(player, Afflictions.Affliction.INFESTED, AFFLICTION_TICKS);
        }
    }

    /**
     * A nest is leaves and twigs on the ground, and everything living in the ground has a
     * chance to get on you. Thatch bedding does not carry this risk.
     */
    private static void tickNestSleep(ServerPlayer player) {
        if (player.tickCount % NEST_SLEEP_CHECK_TICKS != 0 || !player.isSleeping() || of(player) >= MAX) {
            return;
        }
        var sleepingPos = player.getSleepingPos();
        if (sleepingPos.isEmpty()
                || !(player.level().getBlockState(sleepingPos.get()).getBlock()
                        instanceof dev.hominin.evolution.block.NestBlock)
                || player.getRandom().nextFloat() >= NEST_SLEEP_RISK) {
            return;
        }
        set(player, of(player) + 1);
        player.displayClientMessage(Component.literal("Something in the nest bit you in the night.")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * Somebody went through your hair. Two come off, and if that takes you back under
     * the line the affliction goes with them.
     */
    public static void groomed(ServerPlayer player, int removed) {
        int before = of(player);
        set(player, before - removed);
        if (before >= CRIPPLING && of(player) < CRIPPLING) {
            Afflictions.relieve(player, Afflictions.Affliction.INFESTED);
            player.sendSystemMessage(Component.literal("That is most of them off. You can feel it.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** What you get for doing somebody else the favour: something small to eat. */
    public static ItemStack pickedOff(int count) {
        return new ItemStack(ModItems.TICK.get(), count);
    }

    public static void forget(UUID player) {
        burden.remove(player);
        lastBite.remove(player);
    }

    private Infestation() {
    }
}
