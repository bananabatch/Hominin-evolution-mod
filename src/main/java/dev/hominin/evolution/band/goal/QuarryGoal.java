package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Wants;
import dev.hominin.evolution.block.LooseRockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Habilis goes looking for stone. A member picks up loose rocks and strikes faces off
 * deposits, choosing by taste: the stone it prefers first, obsidian above everything if
 * it is obsessed, and limestone only when there is nothing else - and even then rarely.
 */
public class QuarryGoal extends Goal {
    private static final int SEARCH_RADIUS = 16;
    private static final int STRIKE_TICKS = 60;
    private static final int GIVE_UP_TICKS = 400;
    private static final int MIN_COOLDOWN = 900;
    private static final int COOLDOWN_SPREAD = 1500;
    /** Carrying this many good stones is enough for now. */
    private static final int ENOUGH_STONE = 4;
    private static final float HAMMERSTONE_CHANCE = 0.15F;

    private final BandMember member;
    @Nullable
    private BlockPos target;
    private int ticks;
    private int striking;
    private int nextTry = -1;

    public QuarryGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (nextTry < 0) {
            nextTry = member.tickCount + member.getRandom().nextInt(COOLDOWN_SPREAD);
        }
        if (member.tickCount < nextTry || !CraftGoal.canCraft(member) || member.inDanger() || member.isHungry()
                || member.isUpATree() || member.getRandom().nextInt(20) != 0
                || !EventHooks.canEntityGrief(member.level(), member)) {
            return false;
        }
        boolean wantsObsidian = member.isObsessedWithObsidian() && member.count(ModItems.OBSIDIAN_ROCK.get()) == 0;
        if (goodStones() >= ENOUGH_STONE && !wantsObsidian) {
            nextTry = member.tickCount + MIN_COOLDOWN;
            return false;
        }
        target = findStone();
        if (target == null) {
            nextTry = member.tickCount + MIN_COOLDOWN;
        }
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < GIVE_UP_TICKS && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
        striking = 0;
        Block block = member.level().getBlockState(target).getBlock();
        if (block == ModBlocks.OBSIDIAN_ROCK.get() && member.isObsessedWithObsidian()) {
            Band.announceDiscovery(member, " has spotted obsidian, and can't think about anything else.");
        } else {
            Band.announce(member, " says they're going to find some good stone.");
        }
        walk();
    }

    @Override
    public void stop() {
        target = null;
        member.getNavigation().stop();
        nextTry = member.tickCount + MIN_COOLDOWN + member.getRandom().nextInt(COOLDOWN_SPREAD);
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (member.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) > 6.25D) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        BlockState state = member.level().getBlockState(target);
        if (state.getBlock() instanceof LooseRockBlock) {
            pickUp(state);
            return;
        }
        if (++striking % 20 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
            member.level().playSound(null, target, SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 0.8F, 0.9F);
        }
        if (striking >= STRIKE_TICKS) {
            strike(state);
        }
    }

    private void walk() {
        // An obsessive does not stroll towards obsidian.
        boolean obsidian = member.level().getBlockState(target).is(ModBlocks.OBSIDIAN_ROCK.get())
                && member.isObsessedWithObsidian();
        member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                obsidian ? 1.4D : 1.0D);
    }

    private void pickUp(BlockState state) {
        Level level = member.level();
        if (level instanceof ServerLevel server) {
            for (ItemStack drop : Block.getDrops(state, server, target, null)) {
                member.addToInventory(drop);
            }
        }
        level.destroyBlock(target, false, member);
        member.swing(InteractionHand.MAIN_HAND);
        afterGetting(state.getBlock().asItem());
        target = null;
    }

    /** Striking a face off a deposit. The deposit stays - a face can always be worked again. */
    private void strike(BlockState state) {
        member.level().playSound(null, target, SoundEvents.STONE_BREAK, SoundSource.NEUTRAL, 0.8F, 1.1F);
        Item stone;
        int count;
        if (state.is(ModBlocks.CHERT_DEPOSIT.get())) {
            stone = ModItems.CHERT_ROCK.get();
            count = 2;
        } else if (state.is(ModBlocks.QUARTZITE_DEPOSIT.get())) {
            stone = ModItems.GRANITE_ROCK.get();
            count = 3;
        } else {
            stone = ModItems.LIMESTONE_ROCK.get();
            count = 2;
        }
        member.addToInventory(new ItemStack(stone, count));
        if (stone != ModItems.LIMESTONE_ROCK.get() && member.getRandom().nextFloat() < HAMMERSTONE_CHANCE) {
            member.addToInventory(new ItemStack(ModItems.HAMMERSTONE.get()));
        }
        afterGetting(stone);
        target = null;
    }

    private void afterGetting(Item stone) {
        if (stone == ModItems.LIMESTONE_ROCK.get()) {
            Wants.complainAboutLimestone(member);
        } else if (stone == ModItems.OBSIDIAN_ROCK.get() && member.isObsessedWithObsidian()) {
            Band.announceDiscovery(member, " can't stop turning the obsidian over in their hands.");
        }
    }

    /** Where to go: the best-scoring stone in reach, by taste and by distance. */
    @Nullable
    private BlockPos findStone() {
        Level level = member.level();
        BlockPos origin = member.blockPosition();
        boolean hasHammer = hasHammer();
        Item preferred = member.preferredStone();
        BlockPos best = null;
        double bestScore = 0.0D;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -4, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 4, SEARCH_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            Item stone = stoneOf(state, hasHammer);
            if (stone == null) {
                continue;
            }
            double score;
            if (stone == ModItems.OBSIDIAN_ROCK.get()) {
                score = member.isObsessedWithObsidian() ? 100.0D : 8.0D;
            } else if (stone == ModItems.LIMESTONE_ROCK.get()) {
                // Only when nothing better turns up, and not often then.
                score = member.getRandom().nextInt(10) == 0 ? 0.5D : 0.0D;
            } else {
                score = stone == preferred ? 6.0D : 3.0D;
            }
            score /= 1.0D + Math.sqrt(pos.distSqr(origin)) / 6.0D;
            if (score > bestScore) {
                bestScore = score;
                best = pos.immutable();
            }
        }
        return best;
    }

    @Nullable
    private static Item stoneOf(BlockState state, boolean hasHammer) {
        if (state.getBlock() instanceof LooseRockBlock) {
            return state.getBlock().asItem();
        }
        if (!hasHammer) {
            return null;
        }
        if (state.is(ModBlocks.CHERT_DEPOSIT.get())) {
            return ModItems.CHERT_ROCK.get();
        }
        if (state.is(ModBlocks.QUARTZITE_DEPOSIT.get())) {
            return ModItems.GRANITE_ROCK.get();
        }
        if (state.is(ModBlocks.LIMESTONE_DEPOSIT.get())) {
            return ModItems.LIMESTONE_ROCK.get();
        }
        return null;
    }

    /** A hammerstone, or any stone at all to strike with. */
    private boolean hasHammer() {
        return member.count(ModItems.HAMMERSTONE.get()) > 0 || member.count(ModItems.CHERT_HAMMERSTONE.get()) > 0
                || goodStones() > 0;
    }

    private int goodStones() {
        int count = 0;
        var pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (Wants.isGoodStone(pack.getItem(slot))) {
                count += pack.getItem(slot).getCount();
            }
        }
        return count;
    }

    @SuppressWarnings("unused")
    private static boolean isHammer(ItemStack stack) {
        return stack.is(ModTags.Items.HAMMERSTONES);
    }
}
