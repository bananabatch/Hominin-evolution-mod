package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.ErectusWork;
import dev.hominin.evolution.band.Lines;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Cutting grass for thatch, the way you do: low, with an edge, a handful at a time - about half the handfuls worth
 * keeping. Only while the band is short of it (for bedding, twine, thatch blocks), only in the day, and only with
 * something that cuts.
 */
public class GatherThatchGoal extends Goal {
    private static final int GIVE_UP_TICKS = 500;
    private static final int CUTS = 10;
    private static final float KEEP_CHANCE = 0.55F;
    private static final int CARRY = 16;

    private final BandMember member;
    @Nullable
    private BlockPos target;
    private int ticks;
    private int cuts;
    private int nextTry;

    public GatherThatchGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry) {
            return false;
        }
        nextTry = member.tickCount + 300 + member.getRandom().nextInt(300);
        if (!ErectusWork.works(member) || !member.level().isDay() || member.isTurnedIn() || member.inDanger()
                || member.getTarget() != null || member.isUpATree() || member.isOnWatch()
                || member.countOf(ErectusWork.THATCH) >= CARRY
                || member.countOf(s -> s.is(ModTags.Items.CUTTING_EDGE)) == 0
                || !EventHooks.canEntityGrief(member.level(), member)) {
            return false;
        }
        ServerPlayer leader = ErectusWork.leader(member);
        if (leader == null || member.distanceToSqr(leader) > 40.0D * 40.0D || !ErectusWork.wantsThatch(leader)) {
            return false;
        }
        target = grassNear(member.blockPosition(), 10);
        return target != null;
    }

    @Nullable
    private BlockPos grassNear(BlockPos around, int radius) {
        ServerLevel level = (ServerLevel) member.level();
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-radius, -3, -radius), around.offset(radius, 3, radius))) {
            if (level.getBlockState(pos).is(ModTags.Blocks.THATCH_SOURCE)
                    && dev.hominin.evolution.build.Sites.cellAt(level, pos) == null
                    && (best == null || pos.distSqr(around) < best.distSqr(around))) {
                best = pos.immutable();
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < GIVE_UP_TICKS && cuts < CUTS && member.getTarget() == null
                && !member.inDanger() && member.countOf(ErectusWork.THATCH) < CARRY;
    }

    @Override
    public void start() {
        ticks = 0;
        cuts = 0;
        Lines.announce(member, "cut_thatch");
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
        ServerLevel level = (ServerLevel) member.level();
        if (!level.getBlockState(target).is(ModTags.Blocks.THATCH_SOURCE)) {
            target = grassNear(target, 5);
            return;
        }
        member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.3D, target.getZ() + 0.5D);
        if (member.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) > 2.6D * 2.6D) {
            if (ticks % 20 == 0) {
                member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        member.getNavigation().stop();
        if (ticks % 15 != 0) {
            return;
        }
        member.swing(InteractionHand.MAIN_HAND);
        level.destroyBlock(target, false, member);
        cuts++;
        if (member.getRandom().nextFloat() < KEEP_CHANCE) {
            member.addToInventory(new ItemStack(ModItems.THATCH.get()));
        }
        target = grassNear(target, 5);
    }
}
