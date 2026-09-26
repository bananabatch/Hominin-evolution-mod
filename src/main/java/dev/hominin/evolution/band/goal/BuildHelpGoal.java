package dev.hominin.evolution.band.goal;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.ErectusWork;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.build.Blueprint;
import dev.hominin.evolution.build.Building;
import dev.hominin.evolution.build.Footprint;
import dev.hominin.evolution.build.Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Helping with the build. Whoever carries what a ghost wants - thatch blocks, building branches - sets it in, lowest
 * first so nothing hangs in the air; the heaps beside the build are drawn on too. Anyone with a spare hide stretches
 * it over raw thatch so the weather does not take it apart. And a member with bedding made lays their own bed.
 * Only for the band's projects: the first two builds you marked out.
 */
public class BuildHelpGoal extends Goal {
    private static final int GIVE_UP_TICKS = 500;
    private static final double REACH = 3.2D;

    private enum Job {
        PLACE, CURE, BED
    }

    private final BandMember member;
    @Nullable
    private Job job;
    @Nullable
    private BlockPos target;
    @Nullable
    private Sites.Site site;
    @Nullable
    private BlockPos[] bed;
    private int ticks;
    private int nextTry;

    public BuildHelpGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry) {
            return false;
        }
        nextTry = member.tickCount + 100 + member.getRandom().nextInt(100);
        if (!ErectusWork.works(member) || member.isSleeping() || member.isTurnedIn() || member.inDanger()
                || member.getTarget() != null || member.isUpATree() || member.isOnWatch()) {
            return false;
        }
        pick();
        return job != null;
    }

    private void pick() {
        job = null;
        ServerPlayer leader = ErectusWork.leader(member);
        if (leader == null || member.distanceToSqr(leader) > 48.0D * 48.0D) {
            return;
        }
        ServerLevel level = (ServerLevel) member.level();
        // Their own bed first: bedding made, and nowhere to lie on it yet.
        if (member.countOf(ErectusWork.BEDDING) >= 2 && !ErectusWork.sleepsInBed(member)) {
            bed = ErectusWork.bedSite(member);
            if (bed != null) {
                job = Job.BED;
                target = bed[0];
                return;
            }
        }
        for (Sites.Site project : ErectusWork.projects(level, leader.getUUID())) {
            if (project.origin().distSqr(member.blockPosition()) > 48.0D * 48.0D) {
                continue;
            }
            BlockPos cell = nextCell(level, project);
            if (cell != null) {
                job = Job.PLACE;
                site = project;
                target = cell;
                return;
            }
        }
        BlockPos station = ErectusWork.workStation(level, ErectusWork.camp(leader));
        if (ErectusWork.sleepsInBed(member) && ErectusWork.available(member, station, ErectusWork.HIDE) > 0) {
            BlockPos raw = ErectusWork.rawThatch(level, leader.getUUID()).stream()
                    .filter(pos -> pos.distSqr(member.blockPosition()) < 40.0D * 40.0D)
                    .min(Comparator.comparingDouble(pos -> pos.distSqr(member.blockPosition()))).orElse(null);
            if (raw != null) {
                job = Job.CURE;
                target = raw;
            }
        }
    }

    /** The lowest empty cell of the build that this member has something for - carried, or in a heap beside it. */
    @Nullable
    private BlockPos nextCell(ServerLevel level, Sites.Site project) {
        Footprint footprint = project.footprint();
        if (footprint == null) {
            return null;
        }
        BlockPos best = null;
        for (Map.Entry<BlockPos, Blueprint.Cell> entry : footprint.cells().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos) || footprint.filled(level, pos) || !Building.canSetByHand(level, project, pos)) {
                continue;
            }
            Block block = entry.getValue().block();
            if (ErectusWork.available(member, project.origin(), s -> block.equals(ErectusWork.blockOf(s))) == 0) {
                continue;
            }
            if (best == null || pos.getY() < best.getY()
                    || pos.getY() == best.getY() && pos.distSqr(member.blockPosition()) < best.distSqr(member.blockPosition())) {
                best = pos;
            }
        }
        return best;
    }

    /**
     * Somewhere to stand that is not part of the build: on the ground beside the cell, outside its walls - walking
     * into a ghost cell is walking into the block about to go there, and the pathing jumps at it.
     */
    private BlockPos standBeside(BlockPos cell) {
        ServerLevel level = (ServerLevel) member.level();
        Footprint footprint = site == null ? null : site.footprint();
        BlockPos best = cell;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = cell.getX() + dx;
                int z = cell.getZ() + dz;
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos spot = new BlockPos(x, y, z);
                if (Math.abs(y - cell.getY()) > 3 || footprint != null && (footprint.cells().containsKey(spot)
                        || footprint.isInside(spot)) || !level.getFluidState(spot).isEmpty()) {
                    continue;
                }
                double distance = spot.distSqr(member.blockPosition());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = spot;
                }
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return job != null && target != null && ticks < GIVE_UP_TICKS && member.getTarget() == null && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        job = null;
        target = null;
        site = null;
        bed = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (target == null || job == null) {
            return;
        }
        member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (member.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) > REACH * REACH) {
            if (ticks % 20 == 0) {
                BlockPos stand = job == Job.PLACE ? standBeside(target) : target;
                member.getNavigation().moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        if (job == Job.PLACE && site != null && site.footprint() != null
                && site.footprint().cells().containsKey(member.blockPosition())) {
            // Standing in the ghost itself: step out of it first, or the block would go in on top of them.
            BlockPos stand = standBeside(target);
            member.getNavigation().moveTo(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 1.0D);
            return;
        }
        member.getNavigation().stop();
        if (ticks % 15 != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) member.level();
        member.swing(InteractionHand.MAIN_HAND);
        switch (job) {
            case BED -> {
                if (bed != null && ErectusWork.layBed(member, bed)) {
                    Lines.announce(member, "bed_laid");
                }
                target = null;
            }
            case CURE -> {
                ServerPlayer leader = ErectusWork.leader(member);
                BlockPos station = leader == null ? null : ErectusWork.workStation(level, ErectusWork.camp(leader));
                var state = level.getBlockState(target);
                if (state.getBlock() instanceof dev.hominin.evolution.block.ThatchBlock
                        && !state.getValue(dev.hominin.evolution.block.ThatchBlock.CURED)
                        && ErectusWork.consume(member, station, ErectusWork.HIDE, 1)) {
                    level.setBlock(target, state.setValue(dev.hominin.evolution.block.ThatchBlock.CURED, true), 3);
                    level.playSound(null, target, net.minecraft.sounds.SoundEvents.WOOL_PLACE,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.9F);
                    Lines.announce(member, "build_cure");
                }
                target = null;
            }
            case PLACE -> {
                Footprint footprint = site == null ? null : site.footprint();
                Blueprint.Cell cell = footprint == null ? null : footprint.cells().get(target);
                if (cell == null || footprint.filled(level, target)) {
                    target = site == null ? null : nextCell(level, site);
                    return;
                }
                Block block = cell.block();
                if (ErectusWork.consume(member, site.origin(), s -> block.equals(ErectusWork.blockOf(s)), 1)) {
                    if (Building.setByMember(level, member, site, target)) {
                        Lines.announce(member, "build_place", block.getName().getString().toLowerCase(), site.name());
                    } else {
                        // Could not go in after all: back in the pack.
                        member.addToInventory(new ItemStack(block.asItem()));
                    }
                }
                // Keep going while there is more they can set in.
                target = nextCell(level, site);
            }
        }
    }
}
