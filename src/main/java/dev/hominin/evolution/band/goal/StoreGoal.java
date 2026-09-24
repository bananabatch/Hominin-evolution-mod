package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.band.ToolPiles;
import dev.hominin.evolution.build.Sites;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The band's store, used. Somebody hungry with nothing to eat goes to it and takes a few mouthfuls; with no food
 * in it, but a bone and a stone to break it with, they crack the bone for its marrow. Somebody carrying far more
 * than they can eat puts the rest by in it.
 */
public class StoreGoal extends Goal {
    private static final double RANGE = 40.0D;
    /** Carry this much food and the rest goes into the store... */
    private static final int SURPLUS = 16;
    /** ...down to this. */
    private static final int KEEP = 8;
    private static final int TAKE = 3;
    private static final int GIVE_UP_TICKS = 400;

    private enum Errand {
        EAT, MARROW, PUT_BY
    }

    private static final Predicate<ItemStack> FOOD = stack -> stack.has(DataComponents.FOOD);
    private static final Predicate<ItemStack> BONE = stack -> stack.is(ModItems.LONG_BONE.get()) || stack.is(Items.BONE);

    private final BandMember member;
    @Nullable
    private BlockPos target;
    @Nullable
    private Errand errand;
    private int ticks;
    private int cooldown;

    public StoreGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0 || member.isWild() || member.isBaby() || member.getLeader() == null || member.isSleeping()
                || member.inDanger() || !(member.level() instanceof ServerLevel level)) {
            return false;
        }
        cooldown = 80 + member.getRandom().nextInt(80);
        UUID owner = member.getLeader();
        BlockPos at = member.blockPosition();
        if (member.isHungry() && !member.hasFood()) {
            target = storePileWith(level, owner, at, FOOD, ToolPiles.access(member));
            if (target != null) {
                errand = Errand.EAT;
                return true;
            }
            if (hasStone() && dev.hominin.evolution.band.Species.cracksMarrow(member.getStage())) {
                target = storePileWith(level, owner, at, BONE, ToolPiles.access(member));
                if (target != null) {
                    errand = Errand.MARROW;
                    return true;
                }
            }
            return false;
        }
        if (foodCarried() >= SURPLUS) {
            target = storeSpot(level, owner, at);
            if (target != null) {
                errand = Errand.PUT_BY;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && ticks < GIVE_UP_TICKS && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
        walk();
    }

    @Override
    public void stop() {
        target = null;
        errand = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        if (member.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) > 4.0D) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        member.swing(InteractionHand.MAIN_HAND);
        ServerLevel level = (ServerLevel) member.level();
        switch (errand) {
            case EAT -> {
                ItemStack food = takeFrom(level, target, FOOD, TAKE, ToolPiles.access(member));
                if (!food.isEmpty()) {
                    member.addToInventory(food);
                    Lines.tell(member, "store_take");
                }
            }
            case MARROW -> {
                ItemStack bone = takeFrom(level, target, BONE, 1, ToolPiles.access(member));
                if (!bone.isEmpty()) {
                    crack(level, bone);
                }
            }
            case PUT_BY -> putBy(level);
            default -> {
            }
        }
        target = null;
    }

    private void walk() {
        member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.0D);
    }

    private boolean hasStone() {
        return member.count(ModItems.ROCK.get()) > 0 || member.count(ModItems.HAMMERSTONE.get()) > 0
                || member.count(ModItems.FLAKE.get()) > 0
                || member.getMainHandItem().is(dev.hominin.evolution.ModTags.Items.KNAPPABLE_STONE)
                || member.getMainHandItem().is(dev.hominin.evolution.ModTags.Items.HAMMERSTONES);
    }

    private int foodCarried() {
        int n = FOOD.test(member.getOffhandItem()) ? member.getOffhandItem().getCount() : 0;
        for (int slot = 0; slot < member.getInventory().getContainerSize(); slot++) {
            ItemStack stack = member.getInventory().getItem(slot);
            if (FOOD.test(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** A bone and a stone: marrow. */
    private void crack(ServerLevel level, ItemStack bone) {
        boolean longBone = bone.is(ModItems.LONG_BONE.get());
        int extra = member.knowsSkill(dev.hominin.evolution.mind.Skills.Skill.MARROW) && member.getRandom().nextInt(3) == 0
                ? 1 : 0;
        member.addToInventory(new ItemStack(ModItems.BONE_MARROW.get(), (longBone ? 2 : 1) + extra));
        level.playSound(null, member.blockPosition(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.NEUTRAL, 0.8F, 1.1F);
        level.sendParticles(ParticleTypes.CRIT, member.getX(), member.getY() + 0.8D, member.getZ(), 4, 0.2D, 0.1D, 0.2D,
                0.0D);
        Lines.tell(member, "store_marrow");
    }

    /** Everything over what they keep, into the store. */
    private void putBy(ServerLevel level) {
        int over = foodCarried() - KEEP;
        boolean any = false;
        for (int slot = 0; slot < member.getInventory().getContainerSize() && over > 0; slot++) {
            ItemStack stack = member.getInventory().getItem(slot);
            if (!FOOD.test(stack)) {
                continue;
            }
            ItemStack heap = stack.split(Math.min(over, stack.getCount()));
            int count = heap.getCount();
            BlockPos spot = level.getBlockEntity(target) instanceof dev.hominin.evolution.block.ToolPileBlockEntity pile
                    && !pile.isFull() ? target : ToolPiles.storeSpot(level, target);
            if (spot == null || !ToolPiles.layDown(level, spot, member.getLeader(), heap, member)) {
                stack.grow(count);
                break;
            }
            // Whatever would not fit goes back in the pack.
            stack.grow(heap.getCount());
            over -= count - heap.getCount();
            any |= count > heap.getCount();
            target = spot;
        }
        if (any) {
            level.playSound(null, target, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 0.6F, 0.9F);
            Lines.tell(member, "store_put");
        }
    }

    /** The nearest pile in one of the band's stores that has something wanted in it. */
    @Nullable
    private static BlockPos storePileWith(ServerLevel level, UUID owner, BlockPos near, Predicate<ItemStack> wanted,
            dev.hominin.evolution.block.ToolPileBlockEntity.Access access) {
        BlockPos best = null;
        for (BlockPos pos : ToolPiles.piles(level, owner)) {
            // Any of the band's piles: food only ever lies in a store, but a bone can be put by anywhere.
            if (pos.distSqr(near) > RANGE * RANGE || !level.isLoaded(pos)
                    || !(level.getBlockEntity(pos) instanceof dev.hominin.evolution.block.ToolPileBlockEntity pile)
                    || pile.total(wanted, access) == 0) {
                continue;
            }
            if (best == null || pos.distSqr(near) < best.distSqr(near)) {
                best = pos;
            }
        }
        return best;
    }

    /** Where to put food by: a pile in the store with room on it, or clear floor in a store. */
    @Nullable
    private static BlockPos storeSpot(ServerLevel level, UUID owner, BlockPos near) {
        for (BlockPos pos : ToolPiles.piles(level, owner)) {
            if (pos.distSqr(near) <= RANGE * RANGE && level.isLoaded(pos) && Sites.isStore(level, pos)
                    && level.getBlockEntity(pos) instanceof dev.hominin.evolution.block.ToolPileBlockEntity pile
                    && !pile.isFull()) {
                return pos;
            }
        }
        for (Sites.Site site : Sites.ownedBy(level, owner)) {
            if (site.built() && site.use() == Sites.Use.STORE && site.origin().distSqr(near) <= RANGE * RANGE
                    && site.footprint() != null) {
                for (BlockPos pos : site.footprint().inside()) {
                    if (pos.getY() == site.origin().getY()) {
                        BlockPos spot = ToolPiles.storeSpot(level, pos);
                        if (spot != null) {
                            return spot;
                        }
                        break;
                    }
                }
            }
        }
        return null;
    }

    private static ItemStack takeFrom(ServerLevel level, BlockPos pos, Predicate<ItemStack> wanted, int max,
            dev.hominin.evolution.block.ToolPileBlockEntity.Access access) {
        if (!(level.getBlockEntity(pos) instanceof dev.hominin.evolution.block.ToolPileBlockEntity pile)) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = pile.takeSome(wanted, max, access);
        if (pile.isEmpty()) {
            level.removeBlock(pos, false);
        }
        return taken;
    }
}
