package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * After a predator has come for it, a member with a plain stick sits down, once it is safe,
 * and gnaws a point onto it - the way the player can, and the way Fongoli chimps do.
 */
public class SharpenStickGoal extends Goal {
    private static final int WORK_TICKS = 80;
    /** A sharpened stick, in the member's order of weapons. Anything that good or better will do. */
    private static final int SHARPENED_STICK_RANK = 1;

    private final BandMember member;
    private int working;

    public SharpenStickGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!member.wantsToSharpen() || member.isBaby() || member.inDanger() || member.isUpATree()) {
            return false;
        }
        if (member.bestWeaponRank() >= SHARPENED_STICK_RANK) {
            member.sharpened();
            return false;
        }
        return hasStick() && member.getRandom().nextInt(10) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return working < WORK_TICKS && !member.inDanger() && hasStick();
    }

    @Override
    public void start() {
        working = 0;
        member.getNavigation().stop();
        member.setHandTask(BandMember.HandTask.FISH);
    }

    @Override
    public void stop() {
        member.setHandTask(BandMember.HandTask.NONE);
    }

    @Override
    public void tick() {
        member.getNavigation().stop();
        if (++working % 16 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
            member.level().playSound(null, member.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.4F,
                    1.3F + member.getRandom().nextFloat() * 0.2F);
        }
        if (working >= WORK_TICKS && takeStick()) {
            member.sharpened();
            member.setHandTask(BandMember.HandTask.NONE);
            member.addToInventory(new ItemStack(ModItems.SHARPENED_STICK.get()));
            dev.hominin.evolution.band.Lines.announce(member, "sharpen_stick");
        }
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
