package dev.hominin.evolution.stage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Where the line splits. Heidelbergensis is the last common ancestor: some of its descendants stay in Africa and
 * become us, and some go north and become the Neanderthals. Evolving into heidelbergensis, you choose which - and
 * that decides where your people wake up, what they are working towards, and which great weapon they come to.
 *
 * <p>The choice is kept under the skill prefix, so it survives the evolving that follows it.
 */
public final class Lineage {
    /** ChoicesPayload action. */
    public static final int ACTION = 62;
    public static final int NONE = 0;
    public static final int SAPIENS = 1;
    public static final int NEANDERTHAL = 2;

    private static final String KEY = EvolutionManager.SKILL_PREFIX + "lineage";
    private static final ResourceLocation HEIDELBERGENSIS =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "homo_heidelbergensis");
    /** Asked again this often, for as long as nothing has been picked. */
    private static final int ASK_AGAIN_TICKS = 30 * 20;

    /** Who is waiting to evolve until they choose, and into what. Null: already that kind, only unchosen. */
    private record Waiting(@Nullable ResourceLocation into, long askedAt) {
    }

    private static final Map<UUID, Waiting> waiting = new HashMap<>();
    /** Chosen just now: the next evolve goes through without asking. */
    private static final Map<UUID, Boolean> justChose = new HashMap<>();

    /** Whether evolving into this stage means choosing a path. */
    public static boolean branches(ResourceLocation stage) {
        return stage.equals(HEIDELBERGENSIS);
    }

    public static int of(ServerPlayer player) {
        return of(player.getData(Attachments.PLAYER_EVOLUTION_DATA));
    }

    public static int of(dev.hominin.evolution.data.PlayerEvolutionData data) {
        return data.getCriterionCounters().getOrDefault(KEY, NONE);
    }

    /** The stage file for this path, beside the stage's own: {@code homo_heidelbergensis/sapiens}. */
    @Nullable
    public static String suffix(int lineage) {
        return switch (lineage) {
            case SAPIENS -> "sapiens";
            case NEANDERTHAL -> "neanderthal";
            default -> null;
        };
    }

    public static String people(int lineage) {
        return lineage == NEANDERTHAL ? "the Neanderthals" : lineage == SAPIENS ? "Homo sapiens" : "";
    }

    /** Where they wake: for now a bearing and a name; one day the country itself. */
    public static String region(int lineage) {
        return lineage == NEANDERTHAL ? "North Africa" : lineage == SAPIENS ? "East Africa" : "";
    }

    /** Bearing, in radians the way the arrival ring measures them: east is 0, north is -pi/2. */
    public static double bearing(int lineage) {
        return lineage == NEANDERTHAL ? -Math.PI / 2.0D : 0.0D;
    }

    /**
     * Called by {@link EvolutionManager#become} before anything changes. Returns true when the evolve has to wait
     * for the choice - it will go through once one is made.
     */
    public static boolean holdFor(ServerPlayer player, ResourceLocation into) {
        if (!branches(into) || justChose.remove(player.getUUID()) != null) {
            return false;
        }
        waiting.put(player.getUUID(), new Waiting(into, player.level().getGameTime()));
        ask(player);
        return true;
    }

    private static void ask(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Your line is about to split. Some of your descendants will stay in "
                + "Africa, and some will go north. Choose which you follow.").withStyle(ChatFormatting.LIGHT_PURPLE));
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(-1, ACTION, "Which way do your people go?",
                List.of("Towards Homo sapiens - East Africa", "Towards the Neanderthals - North Africa"),
                List.of(SAPIENS, NEANDERTHAL)));
    }

    public static void choose(ServerPlayer player, int value) {
        if (value != SAPIENS && value != NEANDERTHAL) {
            return;
        }
        Waiting was = waiting.remove(player.getUUID());
        if (was == null) {
            return;
        }
        player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(KEY, value);
        player.sendSystemMessage(Component.literal(value == SAPIENS
                ? "Your people stay. The long road to Homo sapiens starts in East Africa."
                : "Your people go north. The long road to the Neanderthals starts in North Africa.")
                .withStyle(ChatFormatting.GOLD));
        if (was.into() != null) {
            justChose.put(player.getUUID(), true);
            EvolutionManager.become(player, was.into());
        } else {
            // Already heidelbergensis: only the goals change.
            StageSync.sync(player);
        }
    }

    /** Every second: ask again anyone who closed the question, and anyone who is heidelbergensis without a path. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 7) {
            return;
        }
        long now = player.level().getGameTime();
        Waiting was = waiting.get(player.getUUID());
        if (was == null) {
            var data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
            if (branches(data.getStage()) && of(data) == NONE && !CutsceneGuard.isProtected(player)) {
                waiting.put(player.getUUID(), new Waiting(null, now));
                ask(player);
            }
            return;
        }
        if (now - was.askedAt() >= ASK_AGAIN_TICKS) {
            waiting.put(player.getUUID(), new Waiting(was.into(), now));
            ask(player);
        }
    }

    /** Chosen already - in an intermission, with everyone else: the next evolve goes straight through. */
    public static void chosen(ServerPlayer player, int lineage) {
        if (lineage == SAPIENS || lineage == NEANDERTHAL) {
            player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters().put(KEY, lineage);
        }
        waiting.remove(player.getUUID());
        justChose.put(player.getUUID(), true);
    }

    public static void forget(ServerPlayer player) {
        waiting.remove(player.getUUID());
        justChose.remove(player.getUUID());
    }

    private Lineage() {
    }
}
