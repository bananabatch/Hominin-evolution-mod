package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Pulling a branch out of a tree to have something to hit with. Done now and then by
 * an unarmed member, and straight away by one that has just been attacked and chose
 * to stand its ground.
 *
 * <p>Tearing through leaves turns up far more usable wood than a player breaking them
 * by accident, because this is the whole point of doing it - but it can still take a
 * few tries, and the sticks that come down with it are left on the ground.
 */
public class ArmSelfGoal extends Goal {
    private static final int SEARCH_RADIUS = 6;
    private static final int BREAK_TICKS = 20;
    private static final float BRANCH_CHANCE = 0.3F;
    private static final float STICK_CHANCE = 0.5F;
    private static final int MAX_TRIES = 4;
    /** Between unhurried attempts. Nobody strips trees for no reason. */
    private static final int CALM_COOLDOWN = 2400;
    private static final int GIVE_UP_TICKS = 400;

    private final BandMember member;
    private BlockPos leaves;
    private int ticks;
    private int breaking;
    private int tries;
    private int nextCalmAttempt;

    public ArmSelfGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.hasWeapon() || member.isBaby() || member.isUpATree() || !EventHooks.canEntityGrief(member.level(), member)) {
            return false;
        }
        boolean urgent = member.wantsWeaponUrgently();
        if (!urgent && (member.tickCount < nextCalmAttempt || member.getRandom().nextInt(200) != 0)) {
            return false;
        }
        leaves = findLeaves();
        if (leaves == null && !urgent) {
            nextCalmAttempt = member.tickCount + CALM_COOLDOWN / 4;
        }
        return leaves != null;
    }

    private BlockPos findLeaves() {
        Level level = member.level();
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
            if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return leaves != null && !member.hasWeapon() && tries < MAX_TRIES && ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        breaking = 0;
        tries = 0;
        moveToLeaves();
    }

    private void moveToLeaves() {
        member.getNavigation().moveTo(leaves.getX() + 0.5D, leaves.getY(), leaves.getZ() + 0.5D,
                member.wantsWeaponUrgently() ? 1.3D : 1.0D);
    }

    @Override
    public void stop() {
        leaves = null;
        member.getNavigation().stop();
        nextCalmAttempt = member.tickCount + CALM_COOLDOWN;
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(leaves.getX() + 0.5D, leaves.getY() + 0.5D, leaves.getZ() + 0.5D);
        double reach = member.distanceToSqr(leaves.getX() + 0.5D, leaves.getY() + 0.5D, leaves.getZ() + 0.5D);
        if (reach > 6.25D) {
            if (ticks % 20 == 0) {
                moveToLeaves();
            }
            return;
        }
        member.getNavigation().stop();
        if (++breaking % 5 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
        }
        if (breaking >= BREAK_TICKS) {
            tearDown();
        }
    }

    private void tearDown() {
        Level level = member.level();
        breaking = 0;
        tries++;
        if (!level.getBlockState(leaves).is(BlockTags.LEAVES)) {
            leaves = findLeaves();
            return;
        }
        level.destroyBlock(leaves, false, member);
        float roll = member.getRandom().nextFloat();
        if (roll < BRANCH_CHANCE) {
            member.addToInventory(new ItemStack(ModItems.LONG_BRANCH.get()));
        } else if (roll < BRANCH_CHANCE + STICK_CHANCE) {
            net.minecraft.world.level.block.Block.popResource(level, leaves, new ItemStack(Items.STICK));
        }
        leaves = member.hasWeapon() ? null : findLeaves();
    }
}
