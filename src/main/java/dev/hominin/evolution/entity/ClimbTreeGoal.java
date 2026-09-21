package dev.hominin.evolution.entity;

import java.util.EnumSet;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Tree-sleeping primates: baboons are ground monkeys that know exactly where the nearest tree is. They go up
 * to sleep - a troop spends the night in the branches, out of reach of the cats - and
 * they go up when something frightens them. Now and then by day one simply goes up
 * to look around.
 */
public class ClimbTreeGoal<T extends PathfinderMob & TreeClimber> extends Goal {
    private static final int SEARCH = 14;

    @Nullable
    private BlockPos trunk;
    private int canopyTop;
    private int ticks;
    private int stay;
    private boolean descending;

    private final T mob;
    private final BooleanSupplier blocked;
    private final BooleanSupplier frightened;

    public ClimbTreeGoal(T mob, BooleanSupplier blocked, BooleanSupplier frightened) {
        this.mob = mob;
        this.blocked = blocked;
        this.frightened = frightened;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean night() {
        return !mob.level().isDay();
    }

    @Override
    public boolean canUse() {
        if (blocked.getAsBoolean()) {
            return false;
        }
        // Asleep in the branches after dark, up it goes when frightened, and once in a
        // long while by day just for the view.
        boolean wants = night() ? mob.getRandom().nextInt(40) == 0
                : frightened.getAsBoolean() ? mob.getRandom().nextInt(4) == 0
                : mob.getRandom().nextInt(2400) == 0;
        if (!wants) {
            return false;
        }
        trunk = findTrunk();
        return trunk != null;
    }

    @Override
    public boolean canContinueToUse() {
        return trunk != null && !blocked.getAsBoolean() && ticks < 20 * 60 * 10 && !(descending && mob.onGround() && ticks > 40);
    }

    @Override
    public void start() {
        ticks = 0;
        descending = false;
        // At night it stays until morning; by day, a minute or so.
        stay = night() ? Integer.MAX_VALUE : 600 + mob.getRandom().nextInt(900);
        canopyTop = trunk.getY();
        while (canopyTop < trunk.getY() + 20) {
            var state = mob.level().getBlockState(new BlockPos(trunk.getX(), canopyTop, trunk.getZ()));
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                break;
            }
            canopyTop++;
        }
        mob.getNavigation().moveTo(trunk.getX() + 0.5D, trunk.getY(), trunk.getZ() + 0.5D, frightened.getAsBoolean() ? 1.5D : 1.0D);
    }

    @Override
    public void stop() {
        mob.setClimbing(false);
        trunk = null;
    }

    @Override
    public void tick() {
        // This goal ticks every tick, and on the in-between ticks the game does not ask
        // canContinueToUse first - so once the trunk is let go of, it has to stop here.
        if (trunk == null) {
            mob.setClimbing(false);
            return;
        }
        ticks++;
        double dx = mob.getX() - (trunk.getX() + 0.5D);
        double dz = mob.getZ() - (trunk.getZ() + 0.5D);
        boolean atTrunk = dx * dx + dz * dz < 2.25D;
        if (descending) {
            // Down the way it came: holding the trunk slows the drop to a climb.
            mob.setClimbing(true);
            mob.getMoveControl().setWantedPosition(trunk.getX() + 0.5D, mob.getY() - 1.0D, trunk.getZ() + 0.5D, 0.8D);
            if (mob.onGround() && mob.getY() <= trunk.getY() + 0.5D) {
                mob.setClimbing(false);
                trunk = null;
            }
            return;
        }
        if (mob.getY() >= canopyTop) {
            // On top of the canopy. Let go of the trunk and sit in the branches.
            mob.setClimbing(false);
            mob.getNavigation().stop();
            boolean morning = stay == Integer.MAX_VALUE && !night();
            if (morning || (stay != Integer.MAX_VALUE && --stay <= 0)) {
                descending = true;
            }
            return;
        }
        if (!atTrunk) {
            mob.setClimbing(false);
            if (ticks % 20 == 0 && mob.getNavigation().isDone()) {
                mob.getNavigation().moveTo(trunk.getX() + 0.5D, trunk.getY(), trunk.getZ() + 0.5D, 1.0D);
            }
            if (ticks > 300) {
                trunk = null;
            }
            return;
        }
        mob.getNavigation().stop();
        mob.setClimbing(true);
        mob.getMoveControl().setWantedPosition(trunk.getX() + 0.5D, mob.getY() + 2.0D, trunk.getZ() + 0.5D, 1.0D);
    }

    @Nullable
    private BlockPos findTrunk() {
        BlockPos origin = mob.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SEARCH, -2, -SEARCH), origin.offset(SEARCH, 2, SEARCH))) {
            if (mob.level().getBlockState(pos).is(BlockTags.LOGS)
                    && !mob.level().getBlockState(pos.below()).is(BlockTags.LOGS)
                    && mob.level().getBlockState(pos.above()).is(BlockTags.LOGS)) {
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
