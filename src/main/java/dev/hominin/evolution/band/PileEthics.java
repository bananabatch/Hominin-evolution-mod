package dev.hominin.evolution.band;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.item.PileBundleItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What the band thinks of how you treat its pile. Everyone uses it; nobody owns it - least of all the one who
 * leads. So:
 *
 * <ul>
 * <li>Gather a pile up and walk about with it, with anything of theirs in it - or nothing of yours - and after
 * half a minute they want to know where it is going; every half minute after that it costs cohesion, until you
 * set it down.</li>
 * <li>Take something someone else laid down and that is fine, once. Hold two or more of the same thing from the
 * pile that are not yours, for a minute, and it is noticed - a point of cohesion a minute until one goes back.</li>
 * <li>Take from a pile you have never laid anything on, when others have, and somebody says so.</li>
 * </ul>
 */
public final class PileEthics {
    private static final int CHECK_TICKS = 20;
    /** How long you may walk about with a gathered pile before anyone minds. */
    private static final int BUNDLE_GRACE = 30 * 20;
    private static final int BUNDLE_EVERY = 30 * 20;
    private static final int BUNDLE_COST = 2;
    /** How long two of the same borrowed thing can stay in your hands. */
    private static final int HOARD_GRACE = 60 * 20;
    private static final int HOARD_EVERY = 60 * 20;

    /** Per player: how many of each thing they have taken off the pile that someone else laid down. */
    private static final Map<UUID, Map<Item, Integer>> borrowed = new HashMap<>();
    private static final Map<UUID, Integer> bundleTicks = new HashMap<>();
    private static final Map<UUID, Integer> hoardTicks = new HashMap<>();

    /** Something someone else laid down, taken off your band's pile. */
    public static void borrowed(ServerPlayer player, ItemStack stack) {
        borrowed.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).merge(stack.getItem(), stack.getCount(),
                Integer::sum);
    }

    /** Something laid back on a pile: whatever was borrowed of it is square again. */
    public static void returned(ServerPlayer player, ItemStack stack) {
        Map<Item, Integer> mine = borrowed.get(player.getUUID());
        if (mine == null) {
            return;
        }
        mine.computeIfPresent(stack.getItem(), (item, n) -> n - stack.getCount() > 0 ? n - stack.getCount() : null);
    }

    /** Taken from a pile you never laid anything on, when others have. */
    public static void freeloaded(ServerPlayer player) {
        BandMember saw = someoneWatching(player);
        if (saw != null) {
            say(player, saw, "You've never put a thing on that pile. Not one.");
        }
        Cohesion.addLimited(player, "pile_freeload", -1, 5 * 60 * 20L);
    }

    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_TICKS != 7 || player.isCreative()) {
            return;
        }
        tickBundle(player);
        tickHoard(player);
    }

    private static void tickBundle(ServerPlayer player) {
        boolean owing = false;
        boolean nothingOfYours = false;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof PileBundleItem && PileBundleItem.foreign(stack) > 0) {
                owing = true;
                nothingOfYours |= !PileBundleItem.anyOf(stack, player.getUUID());
            }
        }
        if (!owing) {
            bundleTicks.remove(player.getUUID());
            return;
        }
        int ticks = bundleTicks.merge(player.getUUID(), CHECK_TICKS, Integer::sum);
        if (ticks == BUNDLE_GRACE) {
            BandMember saw = someoneWatching(player);
            if (saw != null) {
                say(player, saw, "Where are you going with the pile? Half of that is ours.");
            }
            player.displayClientMessage(Component.literal("Set the pile down - use it on the ground.")
                    .withStyle(ChatFormatting.GOLD), true);
        } else if (ticks > BUNDLE_GRACE && (ticks - BUNDLE_GRACE) % BUNDLE_EVERY == 0) {
            BandMember saw = someoneWatching(player);
            if (saw != null) {
                say(player, saw, nothingOfYours ? "Not one thing in that pile is yours. Put it down."
                        : "Put it down. You don't get to walk off with everything.");
            }
            Cohesion.add(player, -(BUNDLE_COST + (nothingOfYours ? 1 : 0)), "walked off with the band's pile");
        }
    }

    private static void tickHoard(ServerPlayer player) {
        Map<Item, Integer> mine = borrowed.get(player.getUUID());
        if (mine == null || mine.isEmpty()) {
            hoardTicks.remove(player.getUUID());
            return;
        }
        // Whatever has gone - eaten, worn out, dropped - is not being hoarded any more.
        Item hoarded = null;
        for (var entry : List.copyOf(mine.entrySet())) {
            int held = player.getInventory().countItem(entry.getKey());
            int count = Math.min(entry.getValue(), held);
            if (count <= 0) {
                mine.remove(entry.getKey());
            } else {
                mine.put(entry.getKey(), count);
                if (count >= 2) {
                    hoarded = entry.getKey();
                }
            }
        }
        if (hoarded == null) {
            hoardTicks.remove(player.getUUID());
            return;
        }
        int ticks = hoardTicks.merge(player.getUUID(), CHECK_TICKS, Integer::sum);
        if (ticks >= HOARD_GRACE && (ticks - HOARD_GRACE) % HOARD_EVERY == 0) {
            BandMember saw = someoneWatching(player);
            String what = new ItemStack(hoarded).getHoverName().getString().toLowerCase();
            if (saw != null) {
                say(player, saw, "You've got " + mine.get(hoarded) + " " + what + (what.endsWith("s") ? "" : "s")
                        + " off the pile, and none of them yours. Put one back.");
            }
            Cohesion.add(player, -1, "kept more than your share from the pile");
        }
    }

    private static BandMember someoneWatching(ServerPlayer player) {
        for (BandMember member : Band.ownNear(player, 24.0D)) {
            if (!member.isBaby()) {
                return member;
            }
        }
        return null;
    }

    private static void say(ServerPlayer player, BandMember member, String line) {
        member.ensureName();
        player.sendSystemMessage(Component.literal("<" + member.getName().getString() + "> ")
                .withStyle(ChatFormatting.GOLD).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    public static void forget(UUID player) {
        borrowed.remove(player);
        bundleTicks.remove(player);
        hoardTicks.remove(player);
    }

    private PileEthics() {
    }
}
