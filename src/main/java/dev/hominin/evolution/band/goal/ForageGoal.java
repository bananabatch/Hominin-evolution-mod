package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Feeding itself: rooting through soil for insects, or stripping a berry bush - the
 * same ground a player forages on, with the same odds a bare-handed player gets.
 */
public class ForageGoal extends Goal {
    private static final int SEARCH_RADIUS = 10;
    private static final int WORK_TICKS = 60;
    private static final float SUCCESS_CHANCE = 0.45F;
    private static final int GIVE_UP_TICKS = 300;

    private final BandMember member;
    private BlockPos spot;
    private int ticks;
    private int working;

    public ForageGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Foraging beside the leader skips the dawdle: they asked for this.
        boolean together = member.isForagingTogether();
        if (member.isUpATree() || (member.hasFood() && !together)) {
            return false;
        }
        if (!together && (!member.isHungry() || member.getRandom().nextInt(40) != 0)) {
            return false;
        }
        spot = findSpot();
        return spot != null;
    }

    private BlockPos findSpot() {
        Level level = member.level();
        BlockPos origin = member.isForagingTogether() ? member.getForageAnchor() : member.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos pos = origin.offset(member.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS,
                    member.getRandom().nextInt(5) - 2,
                    member.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS);
            BlockState state = level.getBlockState(pos);
            if (isRipeBush(state)) {
                return pos;
            }
            if (state.is(ModTags.Blocks.FORAGING_GROUND) && level.getBlockState(pos.above()).getCollisionShape(
                    level, pos.above()).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isRipeBush(BlockState state) {
        return state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(SweetBerryBushBlock.AGE) >= 2;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && ticks < GIVE_UP_TICKS && (!member.hasFood() || member.isForagingTogether());
    }

    @Override
    public void start() {
        ticks = 0;
        working = 0;
        member.setHandTask(BandMember.HandTask.FORAGE);
        if (!member.isForagingTogether()) {
            dev.hominin.evolution.band.Band.announce(member, " says they're going to forage.");
        }
        member.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY() + 1, spot.getZ() + 0.5D, 1.0D);
    }

    @Override
    public void stop() {
        spot = null;
        member.setHandTask(BandMember.HandTask.NONE);
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D);
        if (member.distanceToSqr(spot.getX() + 0.5D, spot.getY() + 1.0D, spot.getZ() + 0.5D) > 4.0D) {
            if (ticks % 20 == 0) {
                member.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY() + 1, spot.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        member.getNavigation().stop();
        if (++working % 15 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
            member.level().playSound(null, spot, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 0.5F, 1.0F);
        }
        if (working >= WORK_TICKS) {
            finish();
        }
    }

    private void finish() {
        Level level = member.level();
        BlockState state = level.getBlockState(spot);
        if (isRipeBush(state)) {
            level.setBlock(spot, state.setValue(SweetBerryBushBlock.AGE, 1), 2);
            level.playSound(null, spot, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.NEUTRAL, 1.0F, 1.0F);
            member.addToInventory(new ItemStack(Items.SWEET_BERRIES, 1 + member.getRandom().nextInt(2)));
            dev.hominin.evolution.band.Band.contribute(member, "forage_biomes");
        } else if (member.getRandom().nextFloat() < (SUCCESS_CHANCE + member.foragingBonus())
                * dev.hominin.evolution.survival.Drought.forageMultiplier(level)
                * (CraftGoal.canCraft(member) ? 0.6F : 1.0F)) {
            Item[] insects = {ModItems.GRUB.get(), ModItems.BEETLE.get(), ModItems.EARTHWORM.get()};
            member.addToInventory(new ItemStack(insects[member.getRandom().nextInt(insects.length)]));
            dev.hominin.evolution.band.Band.contribute(member, "forage_biomes");
        }
        spot = null;
    }
}
