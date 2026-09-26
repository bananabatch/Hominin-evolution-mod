package dev.hominin.evolution.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hominin.evolution.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * The hunters of the plain are one another's rivals. Two of different kinds that meet - a clan of hyenas and a
 * sabre-tooth, a giant baboon and a giant hyena - may go for each other, and over a kill they very likely will: the
 * hyenas mob the cat off its carcass, the giant baboon runs the giant hyena off. A clan is a group, and never fights
 * itself; when one hyena of it goes in, the rest follow.
 */
public final class PredatorRivalry {
    private static final double MEET = 14.0D;
    private static final long COOLDOWN = 1200L;

    private static final Map<String, Long> quarrels = new HashMap<>();

    private static boolean rival(Mob mob) {
        return mob instanceof Crocuta || mob instanceof Pachycrocuta || mob instanceof Sabertooth
                || mob instanceof Homotherium || mob instanceof Dinopithecus;
    }

    /** Every three seconds, round each player: where things live, and where it can be seen. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 60 != 21 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<Mob> hunters = new ArrayList<>(level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0D),
                m -> m.isAlive() && rival(m)));
        long now = level.getGameTime();
        for (int i = 0; i < hunters.size(); i++) {
            for (int j = i + 1; j < hunters.size(); j++) {
                Mob a = hunters.get(i);
                Mob b = hunters.get(j);
                // Their own kind is their own group.
                if (a.getClass() == b.getClass() || a.distanceToSqr(b) > MEET * MEET
                        || a.getTarget() == b || b.getTarget() == a) {
                    continue;
                }
                String key = a.getUUID().compareTo(b.getUUID()) < 0 ? a.getUUID() + "|" + b.getUUID()
                        : b.getUUID() + "|" + a.getUUID();
                if (now < quarrels.getOrDefault(key, 0L)) {
                    continue;
                }
                boolean overAKill = carcassNear(level, a.blockPosition()) || carcassNear(level, b.blockPosition());
                if (level.random.nextFloat() >= (overAKill ? 0.45F : 0.05F)) {
                    quarrels.put(key, now + COOLDOWN / 2);
                    continue;
                }
                quarrels.put(key, now + COOLDOWN);
                if (quarrels.size() > 512) {
                    quarrels.values().removeIf(until -> until < now);
                }
                a.setTarget(b);
                b.setTarget(a);
                BlockPos at = a.blockPosition();
                int distance = (int) Math.sqrt(at.distSqr(player.blockPosition()));
                player.sendSystemMessage(Component.literal(article(a) + " and " + article(b).toLowerCase() + " go for "
                        + "each other" + (overAKill ? " over a kill" : "") + " - " + distance + " blocks "
                        + dev.hominin.evolution.mind.MentalMap.bearing(player, at) + ".")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static String article(Mob mob) {
        return mob instanceof Crocuta ? "A hyena clan" : "A " + mob.getName().getString().toLowerCase();
    }

    private static boolean carcassNear(ServerLevel level, BlockPos around) {
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-8, -3, -8), around.offset(8, 3, 8))) {
            var state = level.getBlockState(pos);
            if (state.is(ModBlocks.CARCASS.get()) || state.is(ModBlocks.GIANT_CARCASS.get())) {
                return true;
            }
        }
        return false;
    }

    private PredatorRivalry() {
    }
}
