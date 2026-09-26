package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.survival.Gravel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Short of stone and near gravel, a member squats and picks through it - the same two goes a patch that you get, and
 * what comes out of it is theirs: chert, fine chert, now and then obsidian.
 */
public class GravelGoal extends Goal {
    private static final int LOOK = 10;
    private static final int MOST_STONE = 3;

    private final BandMember member;
    @Nullable
    private BlockPos target;
    private int ticks;
    private int wait;

    public GravelGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.level().isClientSide() || member.isBaby() || member.inDanger() || member.isSleeping()
                || member.isHunting() || member.isOnExcursion() || --wait > 0) {
            return false;
        }
        wait = 300 + member.getRandom().nextInt(300);
        if (member.countCarried(s -> s.is(ModTags.Items.KNAPPABLE_STONE)) >= MOST_STONE) {
            return false;
        }
        target = find((ServerLevel) member.level());
        return target != null;
    }

    @Nullable
    private BlockPos find(ServerLevel level) {
        BlockPos at = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-LOOK, -3, -LOOK), at.offset(LOOK, 2, LOOK))) {
            if (Gravel.worthSearching(level, pos) && level.getBlockState(pos.above()).isAir()) {
                double d = pos.distSqr(at);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < 300 && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
    }

    @Override
    public void stop() {
        target = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (target == null) {
            return;
        }
        member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
        if (member.distanceToSqr(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D) > 6.25D) {
            if (ticks % 20 == 1) {
                member.getNavigation().moveTo(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        member.getNavigation().stop();
        if (ticks % 30 != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) member.level();
        if (!Gravel.worthSearching(level, target)) {
            target = null;
            return;
        }
        member.swing(InteractionHand.MAIN_HAND);
        ItemStack found = Gravel.sift(level, target, member.getRandom());
        if (!found.isEmpty()) {
            member.addToInventory(found);
            if (member.getRandom().nextInt(3) == 0) {
                dev.hominin.evolution.band.Lines.say(member, "gravel_found", "");
            }
        }
        if (!Gravel.worthSearching(level, target)) {
            target = null;
        }
    }
}
