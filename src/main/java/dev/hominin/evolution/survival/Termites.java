package dev.hominin.evolution.survival;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.block.TermiteMoundBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The life of termite mounds, kept per world.
 *
 * <p><b>Fished out.</b> A mound gives and gives, but not without end: fish it fifteen times and the
 * soldiers seal every hole, and the colony will not open up again for two days. Everyone's fishing counts
 * against the same mound - yours, your band's, and a Paranthropus troop's that is working the ground.
 *
 * <p><b>Super colonies.</b> Now and then, out in the homeland, one vast colony raises three great mounds
 * together. They never run dry, and every stick comes out heavier. Building beside one does the colony no
 * harm - it is the building that suffers: anything of wood or grass set down within thirty blocks of the
 * mounds is eaten. Thatch, planks, branches go; a log rots through, decaying, then decayed, then gone. The
 * nearer the mounds, the sooner. Anything holding things (a chest, a rack) is left alone.
 */
public final class Termites extends SavedData {
    private static final String NAME = "hominin_evolution_termites";
    /** Fishings before a mound seals itself up. */
    public static final int FISHINGS = 15;
    /** How long a fished-out mound stays shut: two days. */
    public static final long DRIED_TICKS = 48000L;
    /** A colony's reach: what is built this close, the termites get into. */
    public static final int COLONY_REACH = 30;
    /**
     * The chance, each ten seconds, that the termites finish a block beside the mounds (or rot a log a stage
     * further): about a day for thatch at the mounds' foot, two and a half out at the edge of their reach.
     */
    private static final float GNAW_CHANCE = 1.0F / 120.0F;
    /** At most this many blocks are watched at once. */
    private static final int MAX_GNAWED = 8192;
    /** How often a builder is reminded, at most: every five minutes. */
    private static final long WARN_TICKS = 6000L;

    public record Colony(BlockPos centre) {
    }

    private record Mound(int fished, long driedUntil) {
    }

    /** Super colonies by their centre. */
    private final Map<Long, Colony> colonies = new HashMap<>();
    /** Mounds that have been fished, by the column of their summit. */
    private final Map<Long, Mound> mounds = new HashMap<>();
    /** Blocks set down on a colony's ground that the termites are eating. */
    private final Set<Long> gnawed = new HashSet<>();
    /** When each builder was last told, so it is not every block. Not saved. */
    private final Map<UUID, Long> warned = new HashMap<>();
    private long lastDay = -1L;

    // ------------------------------------------------------------ worldgen hands colonies over here

    private record Generated(ResourceKey<Level> dimension, BlockPos centre) {
    }

    /** Filled from worldgen threads; emptied on the server thread. */
    private static final Queue<Generated> GENERATED = new ConcurrentLinkedQueue<>();

    /** A super colony has just been generated. Recorded properly on the next server tick. */
    public static void generated(ServerLevel level, BlockPos centre) {
        GENERATED.add(new Generated(level.dimension(), centre.immutable()));
    }

    // ------------------------------------------------------------ saving

    public static Termites of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Termites::new, Termites::load), NAME);
    }

    private static Termites load(CompoundTag tag, HolderLookup.Provider registries) {
        Termites data = new Termites();
        for (Tag entry : tag.getList("Colonies", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            BlockPos centre = BlockPos.of(c.getLong("Centre"));
            data.colonies.put(centre.asLong(), new Colony(centre));
        }
        for (Tag entry : tag.getList("Mounds", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) entry;
            data.mounds.put(m.getLong("Summit"), new Mound(m.getInt("Fished"), m.getLong("DriedUntil")));
        }
        for (long pos : tag.getLongArray("Gnawed")) {
            data.gnawed.add(pos);
        }
        data.lastDay = tag.getLong("LastDay");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Colony colony : colonies.values()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Centre", colony.centre().asLong());
            list.add(c);
        }
        tag.put("Colonies", list);
        ListTag moundList = new ListTag();
        for (var entry : mounds.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putLong("Summit", entry.getKey());
            m.putInt("Fished", entry.getValue().fished());
            m.putLong("DriedUntil", entry.getValue().driedUntil());
            moundList.add(m);
        }
        tag.put("Mounds", moundList);
        tag.putLongArray("Gnawed", gnawed.stream().mapToLong(Long::longValue).toArray());
        tag.putLong("LastDay", lastDay);
        return tag;
    }

    // ------------------------------------------------------------ fishing

    public enum Catch {
        /** An ordinary mound, with termites in it. */
        MOUND,
        /** That was the fifteenth: the mound has sealed itself. */
        LAST,
        /** A super colony: more than a stick can hold. */
        COLONY,
        /** Fished out: nothing comes up until it recovers. */
        DRIED
    }

    public static boolean isMound(BlockState state) {
        return state.is(ModBlocks.TERMITE_MOUND.get());
    }

    /** Whether the mound this block belongs to is shut, fished out. */
    public static boolean isDried(ServerLevel level, BlockPos pos) {
        if (!isMound(level.getBlockState(pos)) || colonyOf(level, pos) != null) {
            return false;
        }
        Termites data = of(level);
        long now = level.getGameTime();
        for (var entry : data.mounds.entrySet()) {
            if (entry.getValue().driedUntil() > now) {
                BlockPos summit = BlockPos.of(entry.getKey());
                if (Math.abs(summit.getX() - pos.getX()) <= 5 && Math.abs(summit.getZ() - pos.getZ()) <= 5) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Days (rounded up) until a fished-out mound here opens again. */
    public static int daysUntilOpen(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        long until = now;
        for (var entry : of(level).mounds.entrySet()) {
            BlockPos summit = BlockPos.of(entry.getKey());
            if (Math.abs(summit.getX() - pos.getX()) <= 5 && Math.abs(summit.getZ() - pos.getZ()) <= 5) {
                until = Math.max(until, entry.getValue().driedUntil());
            }
        }
        return (int) Math.max(1, (until - now + 23999L) / 24000L);
    }

    /** One stick drawn out of this mound. Decayed logs and anything else that is not a mound always give. */
    public static Catch fish(ServerLevel level, BlockPos pos) {
        if (!isMound(level.getBlockState(pos))) {
            return Catch.MOUND;
        }
        if (colonyOf(level, pos) != null) {
            return Catch.COLONY;
        }
        if (isDried(level, pos)) {
            return Catch.DRIED;
        }
        Termites data = of(level);
        long summit = summitOf(level, pos);
        Mound mound = data.mounds.getOrDefault(summit, new Mound(0, 0L));
        int fished = mound.fished() + 1;
        data.setDirty();
        if (fished >= FISHINGS) {
            data.mounds.put(summit, new Mound(0, level.getGameTime() + DRIED_TICKS));
            return Catch.LAST;
        }
        data.mounds.put(summit, new Mound(fished, mound.driedUntil()));
        return Catch.MOUND;
    }

    /**
     * The column of the top of the mound this block is part of: climb the mound, always to the highest
     * column in reach, until there is nowhere higher. Ties go the same way whichever side you start from,
     * so every block of one mound comes out at the same summit.
     */
    private static long summitOf(ServerLevel level, BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        int best = topOf(level, x, z, pos.getY());
        for (int step = 0; step < 10; step++) {
            int bx = x;
            int bz = z;
            int bh = best;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int h = topOf(level, x + dx, z + dz, pos.getY());
                    int cx = x + dx;
                    int cz = z + dz;
                    if (h > bh || (h == bh && (cx < bx || (cx == bx && cz < bz)))) {
                        bh = h;
                        bx = cx;
                        bz = cz;
                    }
                }
            }
            if (bx == x && bz == z) {
                break;
            }
            x = bx;
            z = bz;
            best = bh;
        }
        return new BlockPos(x, 0, z).asLong();
    }

    private static int topOf(ServerLevel level, int x, int z, int aroundY) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, aroundY + 14, z);
        for (int y = aroundY + 14; y >= aroundY - 6; y--) {
            at.setY(y);
            BlockState state = level.getBlockState(at);
            if (isMound(state) || state.is(Blocks.PACKED_MUD) || state.is(Blocks.TERRACOTTA)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------ super colonies

    /** The living super colony this block belongs to, if any. */
    @Nullable
    public static Colony colonyOf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isMound(state) || !state.getValue(TermiteMoundBlock.COLONY)) {
            return null;
        }
        return nearestColony(level, pos, 24);
    }

    @Nullable
    public static Colony nearestColony(ServerLevel level, BlockPos pos, int within) {
        Colony best = null;
        double bestDistance = (double) within * within;
        for (Colony colony : of(level).colonies.values()) {
            double dx = colony.centre().getX() - pos.getX();
            double dz = colony.centre().getZ() - pos.getZ();
            double distance = dx * dx + dz * dz;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = colony;
            }
        }
        return best;
    }

    /** Every living super colony in this world. */
    public static List<Colony> colonies(ServerLevel level) {
        return new ArrayList<>(of(level).colonies.values());
    }

    /** Wood and grass: what termites eat. Not anything holding things - that is left to its owner. */
    public static boolean isTermiteFood(BlockState state) {
        if (state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                || state.is(ModBlocks.THATCH_BLOCK.get()) || state.is(ModBlocks.THATCH_BEDDING.get())
                || state.is(ModBlocks.BUILDING_BRANCH.get()) || state.is(ModBlocks.TOOL_RACK_BAR.get())
                || state.is(ModBlocks.COOKING_SPIT.get()) || state.is(Blocks.HAY_BLOCK);
    }

    /** A block set down: if it is on a colony's ground and made of something they eat, the termites find it. */
    public static void builtNear(ServerLevel level, BlockPos pos, BlockState placed, @Nullable ServerPlayer builder) {
        if (!isTermiteFood(placed) || nearestColony(level, pos, COLONY_REACH) == null) {
            return;
        }
        Termites data = of(level);
        if (data.gnawed.size() < MAX_GNAWED && data.gnawed.add(pos.asLong())) {
            data.setDirty();
        }
        if (builder != null) {
            long now = level.getGameTime();
            Long last = data.warned.get(builder.getUUID());
            if (last == null || now - last >= WARN_TICKS) {
                data.warned.put(builder.getUUID(), now);
                builder.displayClientMessage(Component.literal("The great mounds are close. Their termites will eat "
                        + "anything of wood or grass built here.").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /**
     * The termites at work on what was built beside their mounds: a log rots a stage (decaying, then decayed,
     * then gone), and anything else is simply eaten away. Nearer the mounds, the sooner.
     */
    private static void gnaw(ServerLevel level, Termites data) {
        RandomSource random = level.getRandom();
        for (Iterator<Long> it = data.gnawed.iterator(); it.hasNext(); ) {
            BlockPos pos = BlockPos.of(it.next());
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            Colony colony = nearestColony(level, pos, COLONY_REACH);
            if (colony == null || !isTermiteFood(state)) {
                // Taken down, replaced, or the ground is no colony's: nothing left to eat here.
                it.remove();
                data.setDirty();
                continue;
            }
            double dx = colony.centre().getX() - pos.getX();
            double dz = colony.centre().getZ() - pos.getZ();
            float closeness = 1.0F - (float) Math.min(1.0D, Math.sqrt(dx * dx + dz * dz) / COLONY_REACH);
            if (random.nextFloat() >= GNAW_CHANCE * (0.4F + 0.6F * closeness)) {
                continue;
            }
            data.setDirty();
            if (state.is(ModBlocks.DECAYED_LOG.get()) || !state.is(BlockTags.LOGS)) {
                level.destroyBlock(pos, false);
                it.remove();
            } else {
                BlockState rotted = (state.is(ModBlocks.DECAYING_LOG.get()) ? ModBlocks.DECAYED_LOG.get()
                        : ModBlocks.DECAYING_LOG.get()).defaultBlockState();
                if (state.hasProperty(RotatedPillarBlock.AXIS)) {
                    rotted = rotted.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
                }
                level.setBlock(pos, rotted, 3);
                level.levelEvent(2001, pos, Block.getId(state));
            }
        }
    }

    // ------------------------------------------------------------ once in a while

    /** Per level: colonies from worldgen recorded, what is built beside them eaten, and mounds forgotten. */
    public static void tick(ServerLevel level) {
        if (!GENERATED.isEmpty()) {
            Termites data = null;
            for (Iterator<Generated> it = GENERATED.iterator(); it.hasNext(); ) {
                Generated generated = it.next();
                if (generated.dimension().equals(level.dimension())) {
                    if (data == null) {
                        data = of(level);
                    }
                    data.colonies.putIfAbsent(generated.centre().asLong(), new Colony(generated.centre()));
                    data.setDirty();
                    it.remove();
                }
            }
        }
        if (level.getGameTime() % 200L != 40L) {
            return;
        }
        Termites data = of(level);
        if (!data.gnawed.isEmpty()) {
            gnaw(level, data);
        }
        long day = level.getDayTime() / 24000L;
        if (data.lastDay == day) {
            return;
        }
        data.lastDay = day;
        data.setDirty();
        long now = level.getGameTime();
        // Mounds that have recovered, and nobody has touched since, are forgotten.
        data.mounds.values().removeIf(m -> m.fished() == 0 && m.driedUntil() < now);
        paranthropusForage(level);
    }

    /** A Paranthropus troop camped by a mound works it too, and fishes it out the faster. */
    private static void paranthropusForage(ServerLevel level) {
        for (var member : level.getEntities(dev.hominin.evolution.ModEntities.BAND_MEMBER.get(),
                m -> m.isAlive() && m.isWild() && dev.hominin.evolution.band.Paranthropus.is(m) && m.isAlpha())) {
            BlockPos origin = member.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12, -3, -12), origin.offset(12, 6, 12))) {
                if (isMound(level.getBlockState(pos))) {
                    BlockPos found = pos.immutable();
                    for (int i = 0; i < 5; i++) {
                        fish(level, found);
                    }
                    break;
                }
            }
        }
    }

    private Termites() {
    }
}
