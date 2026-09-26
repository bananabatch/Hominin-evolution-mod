package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.mind.Skills;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Just shown how, and wanting to try it. A member who has learned something that is done with the hands goes and
 * does it, straight away: fishes the nearest mound, works the nearest fire pit, grooms whoever is nearest, cracks a
 * bone. Things only told - what to do in front of a troop, the long view - are not something to try out.
 */
public class PractiseGoal extends Goal {
    private static final int WORK_TICKS = 80;
    private static final int GIVE_UP_TICKS = 400;

    private final BandMember member;
    @Nullable
    private Skills.Skill skill;
    @Nullable
    private BlockPos at;
    @Nullable
    private BandMember partner;
    private int ticks;
    private int working;

    public PractiseGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        skill = member.practising();
        if (skill == null || member.inDanger() || member.getTarget() != null || member.isSleeping()) {
            return false;
        }
        ServerLevel level = (ServerLevel) member.level();
        at = null;
        partner = null;
        switch (skill) {
            case TERMITE_FISHING -> at = nearest(level, s -> s.is(ModTags.Blocks.TERMITE_SOURCE));
            case FIRE -> at = nearest(level, s -> s.is(ModBlocks.FIRE_PIT.get()));
            case GROOMING -> {
                for (BandMember other : dev.hominin.evolution.band.Band.near(member, 12.0D)) {
                    if (other != member && !other.isBaby() && (partner == null
                            || other.distanceToSqr(member) < partner.distanceToSqr(member))) {
                        partner = other;
                    }
                }
            }
            default -> {
            }
        }
        return true;
    }

    @Nullable
    private BlockPos nearest(ServerLevel level, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> what) {
        BlockPos best = null;
        BlockPos from = member.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(from.offset(-16, -3, -16), from.offset(16, 3, 16))) {
            if (what.test(level.getBlockState(pos)) && (best == null || pos.distSqr(from) < best.distSqr(from))) {
                best = pos.immutable();
            }
        }
        return best;
    }

    @Override
    public boolean canContinueToUse() {
        return skill != null && ticks < GIVE_UP_TICKS && !member.inDanger() && member.getTarget() == null;
    }

    @Override
    public void start() {
        ticks = 0;
        working = 0;
        Lines.say(member, "practise_start");
    }

    @Override
    public void stop() {
        member.stopPractising();
        member.getNavigation().stop();
        skill = null;
    }

    @Override
    public void tick() {
        ticks++;
        double tx = at != null ? at.getX() + 0.5D : partner != null ? partner.getX() : member.getX();
        double ty = at != null ? at.getY() + 0.5D : partner != null ? partner.getEyeY() : member.getEyeY();
        double tz = at != null ? at.getZ() + 0.5D : partner != null ? partner.getZ() : member.getZ();
        if (at != null || partner != null) {
            member.getLookControl().setLookAt(tx, ty, tz);
            if (member.distanceToSqr(tx, member.getY(), tz) > 2.4D * 2.4D) {
                if (ticks % 20 == 0) {
                    member.getNavigation().moveTo(tx, ty, tz, 1.0D);
                }
                return;
            }
            member.getNavigation().stop();
        }
        if (++working % 10 == 0) {
            member.swing(working % 20 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            member.level().playSound(null, member.blockPosition(), soundFor(skill), SoundSource.NEUTRAL, 0.5F,
                    0.9F + member.getRandom().nextFloat() * 0.3F);
        }
        if (working >= WORK_TICKS) {
            finish((ServerLevel) member.level());
            skill = null;
        }
    }

    private static SoundEvent soundFor(Skills.Skill skill) {
        return switch (skill) {
            case TERMITE_FISHING -> SoundEvents.ROOTED_DIRT_BREAK;
            case FIRE -> SoundEvents.WOOD_HIT;
            case LOMEKWIAN, MARROW -> SoundEvents.STONE_HIT;
            case GROOMING -> SoundEvents.WOOL_HIT;
            default -> SoundEvents.GRASS_HIT;
        };
    }

    /** How the first try went. */
    private void finish(ServerLevel level) {
        switch (skill) {
            case TERMITE_FISHING -> {
                if (at != null) {
                    member.addToInventory(new ItemStack(ModItems.GRUB.get()));
                }
            }
            case FIRE -> {
                if (at != null && level.getBlockEntity(at) instanceof dev.hominin.evolution.block.FirePitBlockEntity pit
                        && !pit.isLit()) {
                    pit.kindle();
                }
            }
            case GROOMING -> {
                if (partner != null) {
                    member.addAffinity(partner, 1);
                    partner.addAffinity(member, 1);
                }
            }
            case MARROW -> {
                if (!member.takeFirst(s -> s.is(ModItems.LONG_BONE.get()) || s.is(Items.BONE)).isEmpty()) {
                    member.addToInventory(new ItemStack(ModItems.BONE_MARROW.get()));
                }
            }
            default -> {
            }
        }
        Lines.say(member, "practise_done");
    }
}
