package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A member with a stick and a termite mound in reach goes fishing: works the stick into
 * the mound and draws it out loaded, the same as the player does. Eating the termites
 * leaves the stick, so a member with one stick can keep itself fed at a mound.
 */
public class TermiteFishGoal extends Goal {
    private static final int SEARCH_RADIUS = 12;
    private static final int WORK_TICKS = 60;
    private static final int GIVE_UP_TICKS = 300;
    private static final int COOLDOWN = 1200;

    private final BandMember member;
    @Nullable
    private BlockPos mound;
    private int ticks;
    private int working;
    private int nextTry;

    public TermiteFishGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry || member.isBaby() || member.isUpATree() || member.inDanger()
                || member.getTarget() != null || !hasStick()) {
            return false;
        }
        // Hungry, or just peckish and passing a mound.
        // Somebody who has been shown how goes and does it whenever they are near a mound.
        int odds = member.knowsSkill(dev.hominin.evolution.mind.Skills.Skill.TERMITE_FISHING) ? 4 : 1;
        boolean wants = member.isHungry() ? member.getRandom().nextInt(20 / odds) == 0
                : member.getHunger() < BandMember.MAX_HUNGER - 2 && member.getRandom().nextInt(400 / odds) == 0;
        if (!wants) {
            return false;
        }
        mound = findMound();
        if (mound == null) {
            nextTry = member.tickCount + COOLDOWN / 4;
        }
        return mound != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mound != null && ticks < GIVE_UP_TICKS && !member.inDanger() && hasStick();
    }

    @Override
    public void start() {
        ticks = 0;
        working = 0;
        member.setHandTask(BandMember.HandTask.FISH);
        dev.hominin.evolution.band.Lines.tell(member, "termite_go");
        walk();
    }

    @Override
    public void stop() {
        mound = null;
        member.setHandTask(BandMember.HandTask.NONE);
        member.getNavigation().stop();
        nextTry = member.tickCount + COOLDOWN;
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(mound.getX() + 0.5D, mound.getY() + 0.5D, mound.getZ() + 0.5D);
        if (member.distanceToSqr(mound.getX() + 0.5D, mound.getY() + 0.5D, mound.getZ() + 0.5D) > 6.25D) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        if (++working % 20 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
            member.level().playSound(null, mound, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 0.5F, 1.3F);
        }
        if (working >= WORK_TICKS) {
            if (member.level().getBlockState(mound).is(ModTags.Blocks.TERMITE_SOURCE) && takeStick()) {
                member.addToInventory(new ItemStack(ModItems.TERMITE_STICK.get()));
            }
            mound = null;
        }
    }

    private void walk() {
        member.getNavigation().moveTo(mound.getX() + 0.5D, mound.getY(), mound.getZ() + 0.5D, 1.0D);
    }

    @Nullable
    private BlockPos findMound() {
        Level level = member.level();
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
            if (level.getBlockState(pos).is(ModTags.Blocks.TERMITE_SOURCE)) {
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    private boolean hasStick() {
        if (member.getMainHandItem().is(Items.STICK)) {
            return true;
        }
        SimpleContainer pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (pack.getItem(slot).is(Items.STICK)) {
                return true;
            }
        }
        return false;
    }

    private boolean takeStick() {
        if (member.getMainHandItem().is(Items.STICK)) {
            member.getMainHandItem().shrink(1);
            return true;
        }
        SimpleContainer pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (pack.getItem(slot).is(Items.STICK)) {
                pack.getItem(slot).shrink(1);
                return true;
            }
        }
        return false;
    }
}
