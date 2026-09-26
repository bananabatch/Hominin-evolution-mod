package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.ToolPiles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Keeping stock. Somebody carrying more good stone than they will knap soon puts the rest by, in a heap beside the
 * band's tool pile - from erectus on, beside the knapping station, where it will be worked. Somebody carrying more
 * food than they will eat puts that by too - beside the work station, or the pile - where it keeps, off the ground.
 */
public class StockGoal extends Goal {
    private static final double REACH = 2.2D;
    private static final int GIVE_UP_TICKS = 300;
    /** Stone kept in hand for the next tool; food kept for the next meal. */
    private static final int KEEP_ROCKS = 1;
    private static final int KEEP_FOOD = 2;
    private static final int SPARE_ROCKS = 3;
    private static final int SPARE_FOOD = 5;

    private static final Predicate<ItemStack> ROCKS = stack -> stack.is(ModTags.Items.KNAPPABLE_STONE)
            || stack.is(ModTags.Items.ROCKS);

    private final BandMember member;
    @Nullable
    private BlockPos spot;
    private boolean rocks;
    /** Erectus: thatch, twine, hides and workable branches, pooled by the work station. */
    private boolean materials;
    private int ticks;
    private int cooldown;

    public StockGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private Predicate<ItemStack> kind() {
        return materials ? dev.hominin.evolution.band.ErectusWork.MATERIAL : rocks ? ROCKS : BandMember::edible;
    }

    private int carried(Predicate<ItemStack> what) {
        int count = 0;
        var pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (what.test(pack.getItem(slot))) {
                count += pack.getItem(slot).getCount();
            }
        }
        return count;
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) {
            return false;
        }
        cooldown = 300 + member.getRandom().nextInt(300);
        if (!(member.level() instanceof ServerLevel level) || member.isBaby() || member.isSleeping() || member.isTurnedIn()
                || member.inDanger() || member.getTarget() != null || member.isUpATree() || member.isGuest()) {
            return false;
        }
        UUID owner = ToolPiles.ownerOf(member);
        if (owner == null) {
            return false;
        }
        materials = false;
        if (dev.hominin.evolution.band.ErectusWork.works(member) && (carried(dev.hominin.evolution.band.ErectusWork.THATCH) >= 6 || carried(dev.hominin.evolution.band.ErectusWork.TWINE) >= 4
                || carried(dev.hominin.evolution.band.ErectusWork.HIDE) >= 1 || carried(dev.hominin.evolution.band.ErectusWork.WORKABLE_BRANCH) >= 2)) {
            rocks = false;
            materials = true;
        } else if (carried(ROCKS) >= SPARE_ROCKS) {
            rocks = true;
        } else if (carried(BandMember::edible) >= SPARE_FOOD && !member.isHungry()) {
            rocks = false;
        } else {
            return false;
        }
        BlockPos anchor = ToolPiles.stockAnchor(level, owner, member, rocks);
        spot = anchor == null ? null : ToolPiles.stockSpot(level, anchor, kind());
        return spot != null;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && ticks < GIVE_UP_TICKS && !member.inDanger() && member.getTarget() == null;
    }

    @Override
    public void start() {
        ticks = 0;
        walk();
    }

    @Override
    public void stop() {
        spot = null;
        member.getNavigation().stop();
    }

    private void walk() {
        if (spot != null) {
            member.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 1.0D);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (spot == null) {
            return;
        }
        member.getLookControl().setLookAt(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        if (member.distanceToSqr(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D) > REACH * REACH) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        member.swing(InteractionHand.MAIN_HAND);
        layBy((ServerLevel) member.level());
        spot = null;
    }

    /** All but what they keep for themselves goes on the heap. */
    private void layBy(ServerLevel level) {
        UUID owner = ToolPiles.ownerOf(member);
        if (owner == null) {
            return;
        }
        Predicate<ItemStack> what = kind();
        int keep = materials ? 0 : rocks ? KEEP_ROCKS : KEEP_FOOD;
        var pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (!what.test(stack)) {
                continue;
            }
            int give = stack.getCount() - keep;
            keep = Math.max(0, keep - stack.getCount());
            if (give <= 0) {
                continue;
            }
            ItemStack heap = stack.split(give);
            if (!ToolPiles.layDown(level, spot, owner, heap, member)) {
                stack.grow(heap.getCount());
                return;
            }
            if (!heap.isEmpty()) {
                // The heap is full: whatever did not fit comes back.
                stack.grow(heap.getCount());
            }
        }
    }
}
