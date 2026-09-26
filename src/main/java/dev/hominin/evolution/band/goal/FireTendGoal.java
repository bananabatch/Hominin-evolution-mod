package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.ErectusWork;
import dev.hominin.evolution.band.Feast;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.band.ToolPiles;
import dev.hominin.evolution.block.FirePitBlockEntity;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Keeping the fire. From erectus on, a member who sees a fire pit by the camp burning low goes and feeds it: sticks,
 * branches or thatch they carry, or out of the band's piles, or - with the camp settled, or a feast coming - dead wood
 * picked up on the way. One who knows how to make fire lights a laid pit at dusk, and any pit under a rack while a
 * feast is being got ready.
 */
public class FireTendGoal extends Goal {
    private static final double REACH = 2.2D;
    private static final int GIVE_UP_TICKS = 400;
    /** Burning with less than this left, it wants feeding: two minutes. */
    private static final long LOW = 2400L;
    private static final int LOOK = 12;

    private enum Errand {
        FEED, LIGHT
    }

    private final BandMember member;
    @Nullable
    private BlockPos target;
    @Nullable
    private Errand errand;
    private int ticks;
    private int nextTry;

    public FireTendGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry) {
            return false;
        }
        nextTry = member.tickCount + 300 + member.getRandom().nextInt(300);
        if (!ErectusWork.works(member) || member.isSleeping() || member.isTurnedIn() || member.inDanger()
                || member.getTarget() != null || member.isUpATree() || member.isOnWatch()) {
            return false;
        }
        ServerPlayer leader = ErectusWork.leader(member);
        if (leader == null || !(member.level() instanceof ServerLevel level)) {
            return false;
        }
        boolean feast = Feast.preparing(leader) || Feast.feasting(leader);
        BlockPos centre = feast && Feast.place(leader) != null ? Feast.place(leader) : ErectusWork.camp(leader);
        if (member.distanceToSqr(centre.getCenter()) > 40.0D * 40.0D) {
            return false;
        }
        boolean dusk = !level.isDay() || level.getDayTime() % 24000L > 11500L;
        boolean lights = dev.hominin.evolution.band.Species.makesFire(member.getStage());
        BlockPos best = null;
        Errand bestErrand = null;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-LOOK, -3, -LOOK), centre.offset(LOOK, 3, LOOK))) {
            if (!level.getBlockState(pos).is(ModBlocks.FIRE_PIT.get())
                    || !(level.getBlockEntity(pos) instanceof FirePitBlockEntity pit)) {
                continue;
            }
            Errand want = null;
            boolean underRack = level.getBlockState(pos.above()).is(ModBlocks.COOKING_SPIT.get());
            if (pit.isLit()) {
                if (pit.ticksLeft() < LOW) {
                    want = Errand.FEED;
                }
            } else if (pit.fuelPieces() < FirePitBlockEntity.LEAST_TO_CATCH) {
                // Laid ready for lighting: only the pits that are wanted burning.
                if (feast && underRack || dusk && lights) {
                    want = Errand.FEED;
                }
            } else if (lights && (dusk || feast && underRack)) {
                want = Errand.LIGHT;
            }
            if (want != null && (best == null || pos.distSqr(member.blockPosition()) < best.distSqr(member.blockPosition()))) {
                best = pos.immutable();
                bestErrand = want;
            }
        }
        if (best == null || bestErrand == Errand.FEED && !hasFuel(level, leader.getUUID(), centre, feast)) {
            return false;
        }
        target = best;
        errand = bestErrand;
        return true;
    }

    /** Anything to feed it with: carried, in the band's piles, or dead wood to be had. */
    private boolean hasFuel(ServerLevel level, UUID owner, BlockPos centre, boolean feast) {
        return member.findCarried(s -> FirePitBlockEntity.fuelOf(s) != null) != null || pileWithFuel(level, owner, centre) != null
                || feast || dev.hominin.evolution.hunt.Predation.settled(ErectusWork.leader(member));
    }

    @Nullable
    private ToolPileBlockEntity pileWithFuel(ServerLevel level, UUID owner, BlockPos centre) {
        var access = ToolPiles.access(member);
        for (BlockPos pos : ToolPiles.piles(level, owner)) {
            if (pos.distSqr(centre) <= 24 * 24 && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile
                    && pile.total(s -> FirePitBlockEntity.fuelOf(s) != null, access) > 0) {
                return pile;
            }
        }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < GIVE_UP_TICKS && !member.inDanger() && member.getTarget() == null;
    }

    @Override
    public void start() {
        ticks = 0;
        walk();
    }

    @Override
    public void stop() {
        target = null;
        errand = null;
        member.getNavigation().stop();
    }

    private void walk() {
        if (target != null) {
            member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
        }
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.3D, target.getZ() + 0.5D);
        if (member.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) > REACH * REACH) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        ServerLevel level = (ServerLevel) member.level();
        if (level.getBlockEntity(target) instanceof FirePitBlockEntity pit) {
            member.swing(InteractionHand.MAIN_HAND);
            if (errand == Errand.LIGHT) {
                if (pit.kindle()) {
                    Lines.tell(member, "fire_light");
                }
            } else {
                feed(level, pit);
            }
        }
        target = null;
    }

    private void feed(ServerLevel level, FirePitBlockEntity pit) {
        ItemStack fuel = member.takeFirst(s -> FirePitBlockEntity.fuelOf(s) != null);
        ServerPlayer leader = ErectusWork.leader(member);
        if (fuel.isEmpty() && leader != null) {
            boolean feast = Feast.preparing(leader) || Feast.feasting(leader);
            BlockPos centre = feast && Feast.place(leader) != null ? Feast.place(leader) : ErectusWork.camp(leader);
            ToolPileBlockEntity pile = pileWithFuel(level, leader.getUUID(), centre);
            if (pile != null) {
                fuel = pile.takeSome(s -> FirePitBlockEntity.fuelOf(s) != null, 2, ToolPiles.access(member));
            }
        }
        if (!fuel.isEmpty()) {
            FirePitBlockEntity.Fuel kind = FirePitBlockEntity.fuelOf(fuel);
            pit.stoke(kind, fuel.getCount());
        } else {
            // Dead wood picked up on the way over: a couple of sticks' worth.
            pit.stoke(FirePitBlockEntity.Fuel.STICK, 2);
        }
        Lines.tell(member, "fire_feed");
    }
}
