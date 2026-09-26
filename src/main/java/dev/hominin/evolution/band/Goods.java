package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.network.GoodsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Things to choose between - yours and your band's on one side, another band's on the other - for a counter offer,
 * a party's gifts and goods, a trade. Each thing remembers where it is (your pack, one of your band's, a pile, their
 * envoy's hands), so what is picked on the screen is taken from exactly there.
 */
public final class Goods {
    /** Where a thing lies. */
    public record Source(int where, @Nullable UUID holder, @Nullable BlockPos pos, int slot) {
        static final int PLAYER = 0;
        static final int MEMBER = 1;
        static final int PILE = 2;
    }

    public record Entry(ItemStack stack, String owner, Source source) {
    }

    /** Screens this is shown for. */
    public static final int COUNTER = 0;
    public static final int PARTY = 1;

    /** What the player has open: the lists in the order they were sent, so indices mean the same on both sides. */
    public record Session(int mode, UUID band, int intent, List<Entry> left, List<Entry> right) {
    }

    private static final Map<UUID, Session> sessions = new HashMap<>();

    @Nullable
    public static Session session(ServerPlayer player) {
        return sessions.get(player.getUUID());
    }

    public static void close(ServerPlayer player) {
        sessions.remove(player.getUUID());
    }

    // ------------------------------------------------------------ what there is

    /** What you carry, and what your band near you carries. */
    public static List<Entry> yours(ServerPlayer player) {
        List<Entry> list = new ArrayList<>();
        var items = player.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            // The guide in your head is not something to hand over.
            if (!stack.isEmpty() && !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())
                    .getNamespace().equals("patchouli")) {
                list.add(new Entry(stack.copy(), "you", new Source(Source.PLAYER, player.getUUID(), null, slot)));
            }
        }
        for (BandMember member : Band.ownNear(player, 16.0D)) {
            member.ensureName();
            var pack = member.getInventory();
            for (int slot = 0; slot < pack.getContainerSize(); slot++) {
                ItemStack stack = pack.getItem(slot);
                if (!stack.isEmpty()) {
                    list.add(new Entry(stack.copy(), member.getName().getString(),
                            new Source(Source.MEMBER, member.getUUID(), null, slot)));
                }
            }
        }
        return list;
    }

    /** What a band has to hand: its envoy's pack, and whatever lies on its piles that anyone can see. */
    public static List<Entry> theirs(ServerLevel level, Bands.Record band, @Nullable BandMember envoy) {
        List<Entry> list = new ArrayList<>();
        if (envoy != null) {
            envoy.ensureName();
            var pack = envoy.getInventory();
            for (int slot = 0; slot < pack.getContainerSize(); slot++) {
                ItemStack stack = pack.getItem(slot);
                if (!stack.isEmpty()) {
                    list.add(new Entry(stack.copy(), envoy.getName().getString(),
                            new Source(Source.MEMBER, envoy.getUUID(), null, slot)));
                }
            }
        }
        for (BlockPos pos : ToolPiles.piles(level, band.id)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile) {
                for (int slot = 0; slot < ToolPileBlockEntity.MAX; slot++) {
                    ItemStack stack = pile.at(slot);
                    if (!stack.isEmpty()) {
                        list.add(new Entry(stack.copy(), "their " + pile.kind().label().toLowerCase(),
                                new Source(Source.PILE, band.id, pos, slot)));
                    }
                }
            }
        }
        if (list.isEmpty()) {
            // Nothing to be seen from here: what a band of their kind usually keeps.
            list.add(new Entry(new ItemStack(dev.hominin.evolution.ModItems.MEAT_CHUNK.get(), 6), "their camp",
                    new Source(Source.PILE, band.id, null, -1)));
            if (ToolPiles.usesStone(band.species)) {
                list.add(new Entry(ToolPiles.tool(level.random, band.species, 0.2F), "their camp",
                        new Source(Source.PILE, band.id, null, -1)));
                list.add(new Entry(new ItemStack(dev.hominin.evolution.ModItems.CHERT_ROCK.get(), 4), "their camp",
                        new Source(Source.PILE, band.id, null, -1)));
            }
        }
        return list;
    }

    /**
     * Takes so many of an entry from where it lies - fewer if it has gone since. Things "at their camp" (nothing to
     * be seen) are made as they were described.
     */
    public static ItemStack take(ServerLevel level, @Nullable ServerPlayer player, Entry entry, int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        Source source = entry.source();
        ItemStack at = switch (source.where()) {
            case Source.PLAYER -> player == null ? ItemStack.EMPTY : player.getInventory().items.get(source.slot());
            case Source.MEMBER -> level.getEntity(source.holder()) instanceof BandMember member
                    ? member.getInventory().getItem(source.slot()) : ItemStack.EMPTY;
            default -> source.pos() != null && level.isLoaded(source.pos())
                    && level.getBlockEntity(source.pos()) instanceof ToolPileBlockEntity pile ? pile.at(source.slot())
                    : ItemStack.EMPTY;
        };
        if (source.where() == Source.PILE && source.slot() < 0) {
            return entry.stack().copyWithCount(Math.min(count, entry.stack().getCount()));
        }
        if (at.isEmpty() || !ItemStack.isSameItemSameComponents(at, entry.stack())) {
            return ItemStack.EMPTY;
        }
        if (source.where() == Source.PILE && level.getBlockEntity(source.pos()) instanceof ToolPileBlockEntity pile) {
            ItemStack taken = pile.takeFromSlot(source.slot(), Math.min(count, at.getCount()));
            if (pile.isEmpty()) {
                level.removeBlock(source.pos(), false);
            }
            return taken;
        }
        return at.split(Math.min(count, at.getCount()));
    }

    /** What these picks are worth, to a band of this kind. */
    public static int worth(List<Entry> entries, List<Integer> counts, net.minecraft.resources.ResourceLocation stage) {
        int worth = 0;
        for (int i = 0; i < entries.size() && i < counts.size(); i++) {
            int count = Math.min(counts.get(i), entries.get(i).stack().getCount());
            if (count > 0) {
                worth += Trading.valueOf(entries.get(i).stack(), stage) * count;
            }
        }
        return worth;
    }

    // ------------------------------------------------------------ the screen

    public static void open(ServerPlayer player, int mode, Bands.Record band, int intent, String title, String detail,
            List<Entry> left, List<Entry> right, int maxParty, List<String> options) {
        sessions.put(player.getUUID(), new Session(mode, band.id, intent, left, right));
        List<ItemStack> leftStacks = new ArrayList<>();
        List<String> leftOwners = new ArrayList<>();
        for (Entry entry : left) {
            leftStacks.add(entry.stack());
            leftOwners.add(entry.owner());
        }
        List<ItemStack> rightStacks = new ArrayList<>();
        List<String> rightOwners = new ArrayList<>();
        for (Entry entry : right) {
            rightStacks.add(entry.stack());
            rightOwners.add(entry.owner());
        }
        PacketDistributor.sendToPlayer(player, new GoodsPayload(mode, band.id.toString(), intent, title, detail,
                leftStacks, leftOwners, rightStacks, rightOwners, maxParty, options));
    }

    /** The player's picks, back from the screen. */
    public static void chosen(ServerPlayer player, int mode, int partySize, int option, List<Integer> leftCounts,
            List<Integer> rightCounts) {
        Session session = sessions.remove(player.getUUID());
        if (session == null || session.mode() != mode) {
            return;
        }
        Bands.Record band = Bands.get(player.serverLevel(), session.band());
        if (band == null) {
            return;
        }
        if (mode == COUNTER) {
            Claims.counter(player, band, session.right(), rightCounts);
        } else {
            Parties.send(player, band, session.intent(), partySize, option, session.left(), leftCounts, session.right(),
                    rightCounts);
        }
    }

    private Goods() {
    }
}
