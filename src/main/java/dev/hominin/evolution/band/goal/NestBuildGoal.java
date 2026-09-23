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
        BlockPos origin = withLeader ? leader.blockPosition() : member.blockPosition();
        // A wild band shares one nest; your own band each make their own, close around you.
        if (!withLeader && Nests.nestNearby(level, origin, SHARE_RADIUS)) {
            lastNestDay = day;
            return false;
        }
        cells = Nests.siteNear(level, origin, withLeader ? 9 : 5, member.getRandom());
        return cells != null;
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
        cells = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        BlockPos next = cells[placed];
        member.getLookControl().setLookAt(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D);
        if (member.distanceToSqr(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D) > 6.25D) {
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
        if (level.getBlockState(next).canBeReplaced() && state.canSurvive(level, next)) {
            level.setBlock(next, state, 3);
            level.playSound(null, next, SoundEvents.GRASS_PLACE, SoundSource.NEUTRAL, 0.8F, 0.9F);
            member.swing(InteractionHand.MAIN_HAND);
        }
        placed++;
    }
}
