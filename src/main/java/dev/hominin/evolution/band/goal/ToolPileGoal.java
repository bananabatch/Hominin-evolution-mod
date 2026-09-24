package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.band.ToolPiles;
import dev.hominin.evolution.item.AcheuleanToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * The band's own tools. Somebody with nothing to work with goes to the pile and takes the best there is;
 * somebody carrying more than one puts the spare back - on the pile the first tools were laid on, or beside
 * it. The same for a wild band at its own camp.
 */
public class ToolPileGoal extends Goal {
    private static final double REACH = 2.2D;
    /** How far from the pile a member still thinks of going to it. */
    private static final double RANGE = 40.0D;
    private static final int GIVE_UP_TICKS = 300;

    private final BandMember member;
    @Nullable
    private BlockPos pile;
    private boolean taking;
    private int ticks;
    private int cooldown;

    public ToolPileGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** How good a tool is to have in hand: the more jobs it does, the better. */
    public static int rank(ItemStack stack) {
        if (!stack.is(ModTags.Items.STONE_TOOLS)) {
            return 0;
        }
        if (stack.getItem() instanceof AcheuleanToolItem) {
            return AcheuleanToolItem.isMultitool(stack) ? 7 : stack.is(ModItems.HAND_AXE.get()) ? 6 : 5;
        }
        if (stack.is(ModItems.OLDOWAN_MULTITOOL.get())) {
            return 5;
        }
        if (stack.is(ModItems.CHOPPER.get())) {
            return 4;
        }
        if (stack.is(ModItems.FLAKE.get()) || stack.is(ModItems.CHERT_HAMMERSTONE.get())) {
            return 3;
        }
        return 2;
    }

    private int toolsCarried() {
        int count = member.getMainHandItem().is(ModTags.Items.STONE_TOOLS) ? 1 : 0;
        if (member.getOffhandItem().is(ModTags.Items.STONE_TOOLS)) {
            count++;
        }
        var pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (pack.getItem(slot).is(ModTags.Items.STONE_TOOLS)) {
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
        cooldown = 100 + member.getRandom().nextInt(120);
        if (!(member.level() instanceof ServerLevel level) || member.isBaby() || member.isSleeping() || member.isTurnedIn()
                || member.inDanger() || member.getTarget() != null || member.isUpATree() || member.isGuest()) {
            return false;
        }
        UUID owner = ToolPiles.ownerOf(member);
        if (owner == null) {
            return false;
        }
        int carried = toolsCarried();
        if (carried == 0) {
            taking = true;
            // Only what they may have: nothing marked for their leader alone.
            pile = ToolPiles.pileWith(level, owner, member.blockPosition(), RANGE, s -> s.is(ModTags.Items.STONE_TOOLS),
                    ToolPiles.access(member));
        } else if (carried >= 2) {
            taking = false;
            pile = ToolPiles.storeWithin(level, owner, member.blockPosition(), RANGE);
        } else {
            pile = null;
        }
        return pile != null;
    }

    @Override
    public boolean canContinueToUse() {
        return pile != null && ticks < GIVE_UP_TICKS && !member.inDanger() && member.getTarget() == null;
    }

    @Override
    public void start() {
        ticks = 0;
        walk();
    }

    @Override
    public void stop() {
        pile = null;
        member.getNavigation().stop();
    }

    private void walk() {
        if (pile != null) {
            member.getNavigation().moveTo(pile.getX() + 0.5D, pile.getY(), pile.getZ() + 0.5D, 1.0D);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (pile == null) {
            return;
        }
        member.getLookControl().setLookAt(pile.getX() + 0.5D, pile.getY(), pile.getZ() + 0.5D);
        if (member.distanceToSqr(pile.getX() + 0.5D, pile.getY(), pile.getZ() + 0.5D) > REACH * REACH) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        member.swing(InteractionHand.MAIN_HAND);
        if (taking) {
            take();
        } else {
            putBack();
        }
        pile = null;
    }

    private void take() {
        ServerLevel level = (ServerLevel) member.level();
        ItemStack best = ItemStack.EMPTY;
        for (int want = 7; want >= 2 && best.isEmpty(); want--) {
            int wanted = want;
            best = ToolPiles.takeFrom(level, pile, s -> rank(s) == wanted, ToolPiles.access(member));
        }
        if (!best.isEmpty()) {
            member.addToInventory(best);
            Lines.tell(member, "pile_take", best.getHoverName().getString().toLowerCase());
        }
    }

    /** Everything but the best one goes back. */
    private void putBack() {
        ServerLevel level = (ServerLevel) member.level();
        UUID owner = ToolPiles.ownerOf(member);
        if (owner == null) {
            return;
        }
        var pack = member.getInventory();
        boolean handArmed = member.getMainHandItem().is(ModTags.Items.STONE_TOOLS);
        int keepSlot = -1;
        if (!handArmed) {
            int bestRank = 0;
            for (int slot = 0; slot < pack.getContainerSize(); slot++) {
                int rank = rank(pack.getItem(slot));
                if (rank > bestRank) {
                    bestRank = rank;
                    keepSlot = slot;
                }
            }
        }
        int returned = 0;
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (!stack.is(ModTags.Items.STONE_TOOLS)) {
                continue;
            }
            int keep = slot == keepSlot ? 1 : 0;
            while (stack.getCount() > keep) {
                ItemStack one = stack.split(1);
                if (!ToolPiles.putBack(level, owner, pile, one, member)) {
                    stack.grow(1);
                    break;
                }
                returned++;
            }
            if (stack.getCount() > keep) {
                break;
            }
        }
        pack.setChanged();
        if (returned > 0) {
            Lines.tell(member, "pile_put");
        }
    }
}
