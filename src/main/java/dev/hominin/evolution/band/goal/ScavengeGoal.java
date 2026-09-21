package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Habilis lived off other animals' kills. It could not catch much, but it could find what
 * something else had caught, get there first, and break open the bones nothing else could.
 *
 * <p>So a habilis band goes looking for carcasses on its own, strips them, and cracks the
 * long bones it finds with a stone for the marrow inside - and is pleased about it.
 */
public class ScavengeGoal extends Goal {
    private static final int SEARCH_RADIUS = 24;
    private static final int WORK_TICKS = 50;
    private static final int GIVE_UP_TICKS = 500;
    private static final int COOLDOWN = 600;

    private final BandMember member;
    @Nullable
    private BlockPos carcass;
    private int ticks;
    private int working;
    private int nextTry;

    public ScavengeGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry || !CraftGoal.canCraft(member) || member.inDanger() || member.isUpATree()
                || member.getRandom().nextInt(10) != 0) {
            return false;
        }
        // Marrow first, if there is a bone to hand and something to crack it with.
        if (crackBone()) {
            nextTry = member.tickCount + 100;
            return false;
        }
        carcass = findCarcass();
        if (carcass == null) {
            nextTry = member.tickCount + COOLDOWN / 2;
        }
        return carcass != null;
    }

    @Override
    public boolean canContinueToUse() {
        return carcass != null && ticks < GIVE_UP_TICKS && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
        working = 0;
        Band.announce(member, " has smelled a kill, and goes to find it.");
        walk();
    }

    @Override
    public void stop() {
        carcass = null;
        member.getNavigation().stop();
        nextTry = member.tickCount + COOLDOWN + member.getRandom().nextInt(COOLDOWN);
    }

    @Override
    public void tick() {
        ticks++;
        if (!member.level().getBlockState(carcass).is(ModBlocks.CARCASS.get())) {
            carcass = null;
            return;
        }
        member.getLookControl().setLookAt(carcass.getX() + 0.5D, carcass.getY(), carcass.getZ() + 0.5D);
        if (member.distanceToSqr(carcass.getX() + 0.5D, carcass.getY(), carcass.getZ() + 0.5D) > 5.0D) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        if (++working % 12 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
            member.level().playSound(null, carcass, SoundEvents.BONE_BLOCK_HIT, SoundSource.NEUTRAL, 0.8F, 0.9F);
        }
        if (working >= WORK_TICKS) {
            strip();
        }
    }

    private void walk() {
        member.getNavigation().moveTo(carcass.getX() + 0.5D, carcass.getY(), carcass.getZ() + 0.5D, 1.15D);
    }

    /** Everything that comes off it goes into the pack, and the band hears about the bones. */
    private void strip() {
        if (!(member.level() instanceof ServerLevel level)) {
            carcass = null;
            return;
        }
        BlockState state = level.getBlockState(carcass);
        boolean bone = false;
        for (ItemStack drop : Block.getDrops(state, level, carcass, null)) {
            bone |= isBone(drop);
            member.addToInventory(drop);
        }
        level.destroyBlock(carcass, false, member);
        if (bone) {
            Band.announceDiscovery(member, member.getRandom().nextBoolean()
                    ? ": \"Sweet, a bone!\"" : ": \"There is still marrow in this one!\"");
        }
        carcass = null;
    }

    /** A bone and a stone to break it with: marrow. */
    private boolean crackBone() {
        SimpleContainer pack = member.getInventory();
        int boneSlot = -1;
        boolean hasStone = member.count(ModItems.ROCK.get()) > 0 || member.count(ModItems.HAMMERSTONE.get()) > 0
                || member.count(ModItems.FLAKE.get()) > 0
                || member.getMainHandItem().is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE);
        for (int slot = 0; slot < pack.getContainerSize() && hasStone; slot++) {
            if (pack.getItem(slot).is(ModItems.LONG_BONE.get()) || pack.getItem(slot).is(Items.BONE)) {
                boneSlot = slot;
                break;
            }
        }
        if (boneSlot < 0) {
            return false;
        }
        boolean longBone = pack.getItem(boneSlot).is(ModItems.LONG_BONE.get());
        pack.getItem(boneSlot).shrink(1);
        int extra = member.knowsSkill(dev.hominin.evolution.mind.Skills.Skill.MARROW) && member.getRandom().nextInt(3) == 0 ? 1 : 0;
        member.addToInventory(new ItemStack(ModItems.BONE_MARROW.get(), (longBone ? 2 : 1) + extra));
        member.swing(InteractionHand.MAIN_HAND);
        member.level().playSound(null, member.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.NEUTRAL,
                0.8F, 1.1F);
        if (member.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, member.getX(), member.getY() + 0.8D, member.getZ(),
                    4, 0.2D, 0.1D, 0.2D, 0.0D);
        }
        if (longBone) {
            Band.announce(member, " cracks a long bone open with a stone and scoops out the marrow.");
        }
        return true;
    }

    private static boolean isBone(ItemStack stack) {
        return stack.is(ModItems.LONG_BONE.get()) || stack.is(ModItems.RIB.get()) || stack.is(Items.BONE);
    }

    @Nullable
    private BlockPos findCarcass() {
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -4, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 4, SEARCH_RADIUS))) {
            if (member.level().getBlockState(pos).is(ModBlocks.CARCASS.get())) {
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
