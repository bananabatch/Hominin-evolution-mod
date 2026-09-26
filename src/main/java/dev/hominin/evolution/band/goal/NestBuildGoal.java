package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.block.Nests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Making the night's nest, an armful at a time. One per member per night, and not when
 * the band already has one close by - they share. Other bands leave theirs behind in
 * the morning, which is how you know one passed through.
 */
public class NestBuildGoal extends Goal {
    private static final int TICKS_PER_ARMFUL = 15;
    private static final int GIVE_UP_TICKS = 600;
    /** A nest this close is shared rather than built again. */
    private static final int SHARE_RADIUS = 6;

    private final BandMember member;
    private BlockPos[] cells;
    /** The foot of the trunk to climb, when the nest goes up a tree. */
    private BlockPos tree;
    private int placed;
    private int ticks;
    private long lastNestDay = -1L;

    public NestBuildGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Level level = member.level();
        long day = level.getDayTime() / 24000L;
        // From sunset, not full dark: a leader who goes straight to bed would otherwise sleep through it.
        long time = level.getDayTime() % 24000L;
        boolean evening = time >= 11500L && time < 23000L;
        if (!evening || day == lastNestDay || member.isBaby() || member.isUpATree()
                || member.getRandom().nextInt(40) != 0 || !EventHooks.canEntityGrief(level, member)) {
            return false;
        }
        boolean wildAtRest = member.isWild() && !member.isGuest();
        Player leader = member.leaderPlayer();
        boolean withLeader = leader != null && member.distanceToSqr(leader) < 24.0D * 24.0D;
        if (!wildAtRest && !withLeader) {
            return false;
        }
        if (withLeader && dev.hominin.evolution.band.ErectusWork.works(member)) {
            BlockPos bed = dev.hominin.evolution.band.ErectusWork.bedOf(member);
            if (bed != null && bed.distSqr(member.blockPosition()) < 32.0D * 32.0D) {
                // Erectus sleeps on thatch bedding of its own, if it is near enough to go back to: no nest tonight.
                lastNestDay = day;
                return false;
            }
            if (member.countOf(dev.hominin.evolution.band.ErectusWork.BEDDING) >= 2 && dev.hominin.evolution.band.ErectusWork.bedSite(member) != null) {
                // Bedding made and a place for it: they lay that instead (see BuildHelpGoal).
                return false;
            }
        }
        BlockPos origin = withLeader ? leader.blockPosition() : member.blockPosition();
        // A roof of their own - given to them, or their mate's: the nest goes in there, or there is one already.
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            dev.hominin.evolution.build.Sites.Site room = dev.hominin.evolution.build.Building.roomFor(member);
            if (room != null) {
                if (dev.hominin.evolution.build.Building.bedIn(server, room, member) != null) {
                    lastNestDay = day;
                    return false;
                }
                BlockPos floor = dev.hominin.evolution.build.Building.floorIn(server, room);
                if (floor != null) {
                    cells = new BlockPos[] {floor};
                    tree = null;
                    return true;
                }
            }
        }
        // A wild band shares one nest; your own band each make their own, close around you.
        if (!withLeader && Nests.nestNearby(level, origin, SHARE_RADIUS)) {
            lastNestDay = day;
            return false;
        }
        boolean mate = withLeader && member.isMateOf(leader.getUUID());
        if (mate && level instanceof net.minecraft.server.level.ServerLevel server && leaderHasNest(server, leader)) {
            // Your mate sleeps in yours.
            lastNestDay = day;
            return false;
        }
        // The climbers make theirs up a tree when there is one about; a mate makes theirs right beside you.
        tree = null;
        if (dev.hominin.evolution.climb.Climbing.climbsTrees(member.getStage())) {
            cells = Nests.treeSiteNear(level, origin, mate ? 6 : withLeader ? 12 : 8, member.getRandom());
            tree = cells != null ? Nests.trunkUnder(level, cells[0]) : null;
            if (tree == null) {
                cells = null;
            }
        }
        if (cells == null) {
            cells = Nests.siteNear(level, origin, mate ? 4 : withLeader ? 9 : 5, member.getRandom());
        }
        // Not in anyone's building - a store least of all.
        if (cells != null && level instanceof net.minecraft.server.level.ServerLevel server
                && dev.hominin.evolution.build.Building.anyBuilt(server, cells)) {
            cells = null;
        }
        return cells != null;
    }

    /** How far a climber clinging at the top of the trunk can reach to weave. */
    static final double TREE_REACH = 4.0D;

    /**
     * Gets a member to a trunk and up it, a tick at a time: walks to the foot of it, then clings and climbs. True
     * while it is clinging to the trunk; false while it is still on its way over.
     */
    static boolean climbTo(BandMember member, BlockPos trunk, int ticks) {
        double dx = member.getX() - (trunk.getX() + 0.5D);
        double dz = member.getZ() - (trunk.getZ() + 0.5D);
        if (dx * dx + dz * dz >= 2.25D) {
            if (member.isClimbingTree() && !member.onGround()) {
                // Knocked away from the trunk: let go.
                member.setClimbingTree(false);
            }
            if (ticks % 20 == 1 || member.getNavigation().isDone()) {
                member.getNavigation().moveTo(trunk.getX() + 0.5D, trunk.getY(), trunk.getZ() + 0.5D, 1.0D);
            }
            return false;
        }
        // Push into the trunk; while climbing, that is what lifts it.
        member.getNavigation().stop();
        member.setClimbingTree(true);
        member.getMoveControl().setWantedPosition(trunk.getX() + 0.5D, member.getY() + 2.0D, trunk.getZ() + 0.5D, 1.0D);
        return true;
    }

    /** Whether the leader has a finished nest or bed of their own close by. */
    private static boolean leaderHasNest(net.minecraft.server.level.ServerLevel level, Player leader) {
        BlockPos at = leader.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-12, -3, -12), at.offset(12, 3, 12))) {
            var state = level.getBlockState(pos);
            if ((state.is(ModBlocks.NEST.get()) && Nests.isComplete(level, pos) || state.is(ModBlocks.THATCH_BEDDING.get()))
                    && leader.getUUID().equals(dev.hominin.evolution.block.NestOwners.ownerOf(level, pos))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return cells != null && placed < cells.length && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        placed = 0;
        ticks = 0;
        lastNestDay = member.level().getDayTime() / 24000L;
        dev.hominin.evolution.band.Lines.tell(member, "nest_go");
    }

    @Override
    public void stop() {
        if (cells != null && placed >= cells.length && !member.isWild() && !member.isOnWatch()) {
            // The nest is made: that is the day done. No more wandering off.
            member.turnIn();
            dev.hominin.evolution.band.Lines.say(member, "turn_in");
        }
        cells = null;
        tree = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        BlockPos next = cells[placed];
        member.getLookControl().setLookAt(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D);
        if (tree != null) {
            // Up the trunk to the crown, and weave it from there, clinging on.
            if (member.distanceToSqr(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D) > TREE_REACH * TREE_REACH) {
                climbTo(member, tree, ticks);
                return;
            }
            member.getNavigation().stop();
            if (member.isClimbingTree()) {
                member.getMoveControl().setWantedPosition(tree.getX() + 0.5D, member.getY(), tree.getZ() + 0.5D, 1.0D);
            }
        } else if (member.distanceToSqr(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D) > 6.25D) {
            if (ticks % 20 == 0) {
                member.getNavigation().moveTo(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        member.getNavigation().stop();
        if (ticks % TICKS_PER_ARMFUL != 0) {
            return;
        }
        Level level = member.level();
        var state = ModBlocks.NEST.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,
                cells.length > 2 && cells[2].getX() != cells[0].getX() ? Direction.EAST : Direction.SOUTH);
        // Up a tree the leaves where it goes are bent over into it.
        if ((level.getBlockState(next).canBeReplaced()
                || tree != null && level.getBlockState(next).is(net.minecraft.tags.BlockTags.LEAVES))
                && state.canSurvive(level, next)) {
            level.setBlock(next, state, 3);
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                // Theirs: nobody else lies down in it.
                dev.hominin.evolution.block.NestOwners.set(server, next, member.getUUID());
            }
            level.playSound(null, next, SoundEvents.GRASS_PLACE, SoundSource.NEUTRAL, 0.8F, 0.9F);
            member.swing(InteractionHand.MAIN_HAND);
        }
        placed++;
    }
}
