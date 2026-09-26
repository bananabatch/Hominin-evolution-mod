package dev.hominin.evolution.hunt;

import java.util.Map;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.band.Presence;
import dev.hominin.evolution.band.ToolPiles;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.guide.Alerts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Food left out overnight is food for whatever comes by. A food pile of the band's that is not under a roof - not in a
 * store, not in a hut - may be got into in the night: hyenas, or baboons at first light. You know which by what is
 * left: tracks and scattered scraps. Bones are left alone (a hyena would crack them, but not off a heap it has to
 * sniff out). A fire burning near the pile keeps them off, and a band with real presence on its ground is left be.
 */
public final class PileRaids {
    private static final String NIGHT = "pile_raid_night";
    /** At this presence and up, nothing dares. */
    private static final int SAFE = Presence.STRONG;
    private static final int FIRE_REACH = 8;

    /** Every half a minute: once a night, the piles are tried. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 600 != 317 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long time = level.getDayTime() % 24000L;
        // Deep in the night - or, if you slept through it, the morning after: sleeping skips the night's hours, not
        // what came in them.
        int day = (int) (level.getDayTime() / 24000L);
        int night;
        if (time >= 17000L) {
            night = day;
        } else if (time < 3000L) {
            night = day - 1;
        } else {
            return;
        }
        Map<String, Integer> counters = player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        if (counters.getOrDefault(NIGHT, -1) >= night) {
            return;
        }
        counters.put(NIGHT, night);
        int presence = Presence.get(player);
        if (presence >= SAFE) {
            return;
        }
        // Presence 0-5: they come nearly every night. Better known on your ground, less often.
        float chance = presence <= Presence.WEAK ? 0.75F : 0.25F + 0.4F * (SAFE - presence) / (float) (SAFE - Presence.WEAK);
        for (BlockPos pos : ToolPiles.piles(level, player.getUUID())) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile)
                    || pile.kind() != ToolPileBlockEntity.Kind.FOOD
                    || dev.hominin.evolution.build.Sites.roomAt(level, pos) != null || fireNear(level, pos)
                    || level.random.nextFloat() >= chance) {
                continue;
            }
            int there = pile.total(PileRaids::takeable);
            if (there <= 0) {
                continue;
            }
            int taken = Math.max(1, there * (3 + level.random.nextInt(4)) / 10);
            int got = 0;
            while (got < taken) {
                ItemStack one = pile.takeSome(PileRaids::takeable, taken - got);
                if (one.isEmpty()) {
                    break;
                }
                got += one.getCount();
            }
            if (pile.isEmpty()) {
                level.removeBlock(pos, false);
            }
            if (got <= 0) {
                continue;
            }
            boolean baboons = (time > 22500L || time < 3000L) && level.random.nextBoolean();
            int distance = (int) Math.sqrt(pos.distSqr(player.blockPosition()));
            Alerts.urgent(player, Alerts.Kind.WARNING, Component.literal((baboons
                    ? "At first light baboons got into your food pile"
                    : "In the night hyenas got into your food pile") + " (" + distance + " blocks "
                    + dev.hominin.evolution.mind.MentalMap.bearing(player, pos) + ") - " + got + " gone. "
                    + (baboons ? "Hand-prints and scraps everywhere." : "Tracks all round it, and the bones left.")
                    + " Keep food under a roof, or a fire by it.").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Anything eaten - but not the bones. */
    private static boolean takeable(ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.FOOD) && !ToolPiles.isBone(stack);
    }

    private static boolean fireNear(ServerLevel level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-FIRE_REACH, -2, -FIRE_REACH),
                pos.offset(FIRE_REACH, 2, FIRE_REACH))) {
            if (dev.hominin.evolution.survival.Hearths.isLitHearth(level.getBlockState(near))) {
                return true;
            }
        }
        return false;
    }

    private PileRaids() {
    }
}
