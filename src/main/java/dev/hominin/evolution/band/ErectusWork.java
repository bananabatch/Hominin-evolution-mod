package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.build.Blueprint;
import dev.hominin.evolution.build.Footprint;
import dev.hominin.evolution.build.Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * What an erectus band does for itself, the way you would: gathers thatch and hunts for hides, twists twine, makes
 * thatch bedding and sleeps on it rather than in a nest, makes what goes into a build, and puts it up. This is the
 * shared part - who works, what the band has to work with, what it is short of, and what it is building.
 *
 * <p>A member carries little (seven things), so materials are pooled: laid in heaps beside the work station, and
 * whoever is making something draws on those heaps as well as on what they carry.
 *
 * <p>The band works on at most two of your builds at once - the two you marked out first. Those are its projects.
 */
public final class ErectusWork {
    public static final int MAX_PROJECTS = 2;
    /** How far from the work station a heap still counts as being to hand. */
    public static final double HEAP_REACH = 12.0D;
    /** How far from the leader's camp a member's bed still counts as theirs to go back to. */
    private static final int BED_RANGE = 24;

    public static final Predicate<ItemStack> THATCH = s -> s.is(ModItems.THATCH.get());
    public static final Predicate<ItemStack> TWINE = s -> s.is(ModItems.TWINE.get());
    public static final Predicate<ItemStack> HIDE = s -> s.is(ModItems.HIDE.get());
    public static final Predicate<ItemStack> BEDDING = s -> s.is(ModItems.THATCH_BEDDING.get());
    public static final Predicate<ItemStack> WORKABLE_BRANCH = s -> s.is(ModItems.WORKABLE_BRANCH.get());
    public static final Predicate<ItemStack> RAW_BRANCH = s -> s.is(ModItems.LONG_BRANCH.get())
            || s.is(net.minecraft.tags.ItemTags.LOGS);
    public static final Predicate<ItemStack> BUILDING_BRANCH = s -> s.is(ModItems.BUILDING_BRANCH.get());
    public static final Predicate<ItemStack> THATCH_BLOCK = s -> s.is(ModItems.THATCH_BLOCK.get());
    public static final Predicate<ItemStack> ROCK = s -> s.is(ModTags.Items.ROCKS) || s.is(ModTags.Items.KNAPPABLE_STONE);
    /** What gets pooled in the heaps by the work station. */
    public static final Predicate<ItemStack> MATERIAL = THATCH.or(TWINE).or(HIDE).or(WORKABLE_BRANCH);

    // ------------------------------------------------------------ who

    /** Erectus and later, grown, of your own band: they do what you can do at erectus. */
    public static boolean works(BandMember member) {
        return !member.isBaby() && !member.isWild() && Bands.erectusOn(member.getStage())
                && member.leaderPlayer() instanceof ServerPlayer && member.level() instanceof ServerLevel;
    }

    @Nullable
    public static ServerPlayer leader(BandMember member) {
        return member.leaderPlayer() instanceof ServerPlayer player ? player : null;
    }

    /** Where the band lives: its camp, for a leader who has ground - wherever the leader is, otherwise. */
    public static BlockPos camp(ServerPlayer leader) {
        return dev.hominin.evolution.hunt.Predation.settled(leader) ? dev.hominin.evolution.hunt.Predation.campOf(leader)
                : leader.blockPosition();
    }

    // ------------------------------------------------------------ where

    /** The nearest block of this kind - a work station, a knapping station - within reach of here. */
    @Nullable
    public static BlockPos nearest(ServerLevel level, BlockPos around, Block block, int radius) {
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-radius, -4, -radius), around.offset(radius, 4, radius))) {
            if (level.getBlockState(pos).is(block) && (best == null || pos.distSqr(around) < best.distSqr(around))) {
                best = pos.immutable();
            }
        }
        return best;
    }

    @Nullable
    public static BlockPos workStation(ServerLevel level, BlockPos around) {
        return nearest(level, around, ModBlocks.WORK_STATION.get(), 24);
    }

    /** The owner's heaps and piles near a place. */
    public static List<BlockPos> heaps(ServerLevel level, UUID owner, @Nullable BlockPos near) {
        List<BlockPos> list = new ArrayList<>();
        if (near == null) {
            return list;
        }
        for (BlockPos pos : ToolPiles.piles(level, owner)) {
            if (level.isLoaded(pos) && pos.distSqr(near) <= HEAP_REACH * HEAP_REACH
                    && level.getBlockEntity(pos) instanceof ToolPileBlockEntity) {
                list.add(pos);
            }
        }
        return list;
    }

    // ------------------------------------------------------------ what there is to work with

    /** What this member carries of it, and what lies in the band's heaps near {@code near}. */
    public static int available(BandMember member, @Nullable BlockPos near, Predicate<ItemStack> what) {
        int count = member.countOf(what);
        UUID owner = ToolPiles.ownerOf(member);
        if (owner != null && member.level() instanceof ServerLevel level) {
            for (BlockPos pos : heaps(level, owner, near)) {
                if (level.getBlockEntity(pos) instanceof ToolPileBlockEntity heap) {
                    count += heap.total(what);
                }
            }
        }
        return count;
    }

    /** Uses up so many: out of their own hands first, then off the heaps. False, and nothing used, if short. */
    public static boolean consume(BandMember member, @Nullable BlockPos near, Predicate<ItemStack> what, int count) {
        if (available(member, near, what) < count) {
            return false;
        }
        int left = count;
        while (left > 0 && !member.takeFirst(what).isEmpty()) {
            left--;
        }
        UUID owner = ToolPiles.ownerOf(member);
        if (left > 0 && owner != null && member.level() instanceof ServerLevel level) {
            for (BlockPos pos : heaps(level, owner, near)) {
                if (left <= 0) {
                    break;
                }
                if (level.getBlockEntity(pos) instanceof ToolPileBlockEntity heap) {
                    left -= heap.takeCount(what, left);
                    if (heap.isEmpty()) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        }
        return left <= 0;
    }

    /** One off the band's heaps near here, as it lies - or empty. */
    public static ItemStack takeFromHeaps(BandMember member, @Nullable BlockPos near, Predicate<ItemStack> what) {
        UUID owner = ToolPiles.ownerOf(member);
        if (owner == null || !(member.level() instanceof ServerLevel level)) {
            return ItemStack.EMPTY;
        }
        for (BlockPos pos : heaps(level, owner, near)) {
            if (level.getBlockEntity(pos) instanceof ToolPileBlockEntity heap) {
                ItemStack one = heap.takeOne(what);
                if (heap.isEmpty()) {
                    level.removeBlock(pos, false);
                }
                if (!one.isEmpty()) {
                    return one;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------ projects

    /** The builds the band is working on: the owner's first two marked out and not yet standing. */
    public static List<Sites.Site> projects(ServerLevel level, UUID owner) {
        return Sites.ownedBy(level, owner).stream().filter(site -> !site.built() && !site.proposed()
                && dev.hominin.evolution.build.Building.shelter(site))
                .sorted(Comparator.comparingInt(Sites.Site::id)).limit(MAX_PROJECTS).toList();
    }

    /** What is still to go into a build, block by block - only what is loaded, so nothing is guessed at. */
    public static Map<Block, Integer> missing(ServerLevel level, Sites.Site site) {
        Map<Block, Integer> counts = new LinkedHashMap<>();
        Footprint footprint = site.footprint();
        if (footprint == null) {
            return counts;
        }
        for (Map.Entry<BlockPos, Blueprint.Cell> entry : footprint.cells().entrySet()) {
            if (level.isLoaded(entry.getKey()) && !footprint.filled(level, entry.getKey())) {
                counts.merge(entry.getValue().block(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** How many of this block the band's projects still want. */
    public static int wanted(ServerLevel level, UUID owner, Block block) {
        int count = 0;
        for (Sites.Site site : projects(level, owner)) {
            count += missing(level, site).getOrDefault(block, 0);
        }
        return count;
    }

    /** The block a carried thing would set into a ghost, if it is one. */
    @Nullable
    public static Block blockOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item ? item.getBlock() : null;
    }

    /** "Small hut 12/30 - still wants 6 thatch blocks, 4 building branches", for the journal. */
    public static List<String> describeProjects(ServerLevel level, UUID owner) {
        List<String> lines = new ArrayList<>();
        for (Sites.Site site : projects(level, owner)) {
            List<String> parts = new ArrayList<>();
            missing(level, site).forEach((block, n) -> {
                String name = block.getName().getString().toLowerCase();
                parts.add(n + " " + name + (n == 1 || name.endsWith("s") ? "" : name.endsWith("h") ? "es" : "s"));
            });
            lines.add("The band is building the " + site.name() + " (" + site.placed() + " in place)"
                    + (parts.isEmpty() ? "." : " - it still wants " + String.join(", ", parts) + "."));
        }
        return lines;
    }

    // ------------------------------------------------------------ beds

    private record Found(BlockPos pos, long at) {
    }

    private static final Map<UUID, Found> beds = new HashMap<>();

    /** This member's own thatch bedding near camp, if they have one - looked for at most every ten seconds. */
    @Nullable
    public static BlockPos bedOf(BandMember member) {
        ServerPlayer leader = leader(member);
        if (leader == null || !(member.level() instanceof ServerLevel level)) {
            return null;
        }
        long now = level.getGameTime();
        Found found = beds.get(member.getUUID());
        if (found != null && now - found.at < 200L) {
            return found.pos != null && level.getBlockState(found.pos).is(ModBlocks.THATCH_BEDDING.get()) ? found.pos : null;
        }
        BlockPos bed = null;
        BlockPos camp = camp(leader);
        for (BlockPos pos : BlockPos.betweenClosed(camp.offset(-BED_RANGE, -4, -BED_RANGE), camp.offset(BED_RANGE, 4, BED_RANGE))) {
            if (level.getBlockState(pos).is(ModBlocks.THATCH_BEDDING.get())
                    && member.getUUID().equals(dev.hominin.evolution.block.NestOwners.ownerOf(level, pos))) {
                bed = pos.immutable();
                break;
            }
        }
        if (beds.size() > 512) {
            beds.clear();
        }
        beds.put(member.getUUID(), new Found(bed, now));
        return bed;
    }

    /** A bed was laid or lost: look again next time. */
    public static void forgetBed(BandMember member) {
        beds.remove(member.getUUID());
    }

    /** Whether this one sleeps in a bed of their own - or their leader's, as a mate does. */
    public static boolean sleepsInBed(BandMember member) {
        ServerPlayer leader = leader(member);
        return bedOf(member) != null || leader != null && member.isMateOf(leader.getUUID());
    }

    /** Where a member's bed goes: two cells side by side, in a room of their own if they have one, else by camp. */
    @Nullable
    public static BlockPos[] bedSite(BandMember member) {
        ServerPlayer leader = leader(member);
        if (leader == null || !(member.level() instanceof ServerLevel level)) {
            return null;
        }
        Sites.Site room = dev.hominin.evolution.build.Building.roomFor(member);
        if (room != null && room.footprint() != null) {
            BlockPos floor = dev.hominin.evolution.build.Building.floorIn(level, room);
            if (floor != null) {
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos next = floor.relative(side);
                    if (room.footprint().isInside(next) && bedFits(level, next)) {
                        return new BlockPos[] {floor, next};
                    }
                }
            }
        }
        BlockPos[] cells = dev.hominin.evolution.block.Nests.siteNear(level, camp(leader), 9, member.getRandom());
        if (cells == null || dev.hominin.evolution.build.Building.anyBuilt(level, cells)) {
            return null;
        }
        // Along the nest's length: head to foot.
        BlockPos[] two = {cells[0], cells[dev.hominin.evolution.block.Nests.WIDTH]};
        return bedFits(level, two[0]) && bedFits(level, two[1]) ? two : null;
    }

    private static boolean bedFits(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
                && ModBlocks.THATCH_BEDDING.get().defaultBlockState().canSurvive(level, pos);
    }

    /** Two bedding laid side by side - a bed - and theirs. */
    public static boolean layBed(BandMember member, BlockPos[] two) {
        if (!(member.level() instanceof ServerLevel level) || member.countOf(BEDDING) < 2 || two.length < 2
                || !bedFits(level, two[0]) || !bedFits(level, two[1])) {
            return false;
        }
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.getNearest(two[1].getX() - two[0].getX(), 0,
                two[1].getZ() - two[0].getZ());
        if (!facing.getAxis().isHorizontal()) {
            facing = net.minecraft.core.Direction.NORTH;
        }
        for (BlockPos pos : two) {
            member.takeFirst(BEDDING);
            var state = Block.updateFromNeighbourShapes(ModBlocks.THATCH_BEDDING.get().defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing), level, pos);
            level.setBlock(pos, state, 3);
            dev.hominin.evolution.block.NestOwners.set(level, pos, member.getUUID());
        }
        level.playSound(null, two[0], net.minecraft.sounds.SoundEvents.WOOL_PLACE, net.minecraft.sounds.SoundSource.NEUTRAL,
                0.9F, 0.9F);
        forgetBed(member);
        return true;
    }

    /** Grown members of the band still sleeping in nests. */
    public static int bedless(ServerPlayer leader) {
        int count = 0;
        for (BandMember member : Band.all(leader)) {
            if (works(member) && !sleepsInBed(member)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------ what the band is short of

    private record Short(boolean hide, boolean thatch, long at) {
    }

    private static final Map<UUID, Short> shortOf = new HashMap<>();

    private static Short shortages(ServerPlayer leader) {
        long now = leader.level().getGameTime();
        Short known = shortOf.get(leader.getUUID());
        if (known != null && now - known.at < 200L) {
            return known;
        }
        ServerLevel level = leader.serverLevel();
        BlockPos station = workStation(level, camp(leader));
        int beds = bedless(leader);
        int hides = 0;
        int thatch = 0;
        int twine = 0;
        for (BandMember member : Band.all(leader)) {
            hides += member.countOf(HIDE);
            thatch += member.countOf(THATCH);
            twine += member.countOf(TWINE);
        }
        for (BlockPos pos : heaps(level, leader.getUUID(), station)) {
            if (level.getBlockEntity(pos) instanceof ToolPileBlockEntity heap) {
                hides += heap.total(HIDE);
                thatch += heap.total(THATCH);
                twine += heap.total(TWINE);
            }
        }
        int rawThatch = rawThatch(level, leader.getUUID()).size();
        int blocks = wanted(level, leader.getUUID(), ModBlocks.THATCH_BLOCK.get());
        // A bed is three hides, three thatch and ten twine (two thatch each); four thatch blocks, nine thatch and four
        // twine. Twine already made counts as the thatch it took.
        int hideWanted = Math.min(6, beds * 3) + Math.min(4, rawThatch);
        int thatchWanted = Math.min(2, beds) * 23 + (blocks + 3) / 4 * 17 - twine * 2;
        Short now2 = new Short(hides < hideWanted, thatch < Math.min(48, thatchWanted), now);
        shortOf.put(leader.getUUID(), now2);
        return now2;
    }

    public static boolean wantsHide(ServerPlayer leader) {
        return shortages(leader).hide();
    }

    public static boolean wantsThatch(ServerPlayer leader) {
        return shortages(leader).thatch();
    }

    /**
     * Raw thatch in the owner's builds - finished or not - that a hide would cure: the top of each stack only. A cured
     * roof keeps the wall under it from rotting, so hide goes on top, where it shows and where it does its work.
     */
    public static List<BlockPos> rawThatch(ServerLevel level, UUID owner) {
        List<BlockPos> raw = new ArrayList<>();
        for (Sites.Site site : Sites.ownedBy(level, owner)) {
            Footprint footprint = site.footprint();
            if (footprint == null || site.proposed()) {
                continue;
            }
            for (BlockPos pos : footprint.cells().keySet()) {
                if (level.isLoaded(pos)) {
                    var state = level.getBlockState(pos);
                    if (state.getBlock() instanceof dev.hominin.evolution.block.ThatchBlock
                            && !state.getValue(dev.hominin.evolution.block.ThatchBlock.CURED)
                            && !(level.getBlockState(pos.above()).getBlock() instanceof dev.hominin.evolution.block.ThatchBlock)) {
                        raw.add(pos.immutable());
                    }
                }
            }
        }
        return raw;
    }

    private ErectusWork() {
    }
}
