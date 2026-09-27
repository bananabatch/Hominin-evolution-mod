package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Feast;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.band.MemberSurvival;
import dev.hominin.evolution.band.ToolPiles;
import dev.hominin.evolution.block.CookingSpitBlockEntity;
import dev.hominin.evolution.block.FirePitBlockEntity;
import dev.hominin.evolution.food.Cooking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Cooking for themselves. A member carrying raw meat, with a fire going near - a lit fire pit, a campfire, or a rack
 * over one - takes the meat to it, lays it on or hangs it up, and waits by it: what rolls off the fire is picked up,
 * what hangs cooked on the rack is taken back down. Not while a feast is being got ready - that has its own ways.
 */
public class CookGoal extends Goal {
    private static final int SEARCH = 16;
    private static final int GIVE_UP_TICKS = 1500;

    private final BandMember member;
    @Nullable
    private BlockPos fire;
    private boolean laid;
    private int ticks;

    public CookGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (member.isBaby() || member.isSleeping() || member.getTarget() != null || member.inDanger()
                || member.getRandom().nextInt(120) != 0 || member.countOf(MemberSurvival::isRawMeat) <= 0
                || !(member.level() instanceof ServerLevel level) || Feast.preparing(member)) {
            return false;
        }
        fire = findFire(level, member.blockPosition());
        return fire != null;
    }

    @Override
    public boolean canContinueToUse() {
        return fire != null && ticks < GIVE_UP_TICKS && member.getTarget() == null && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
        laid = false;
        walkTo(fire);
    }

    @Override
    public void stop() {
        fire = null;
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        if (!(member.level() instanceof ServerLevel level)) {
            return;
        }
        if (!laid) {
            member.getLookControl().setLookAt(fire.getX() + 0.5D, fire.getY() + 0.5D, fire.getZ() + 0.5D);
            if (member.distanceToSqr(fire.getX() + 0.5D, fire.getY() + 0.5D, fire.getZ() + 0.5D) > 3.0D * 3.0D) {
                if (ticks % 20 == 0) {
                    walkTo(fire);
                }
                return;
            }
            member.getNavigation().stop();
            laid = layOn(level);
            if (!laid) {
                fire = null;
                return;
            }
            member.swing(InteractionHand.MAIN_HAND);
            Lines.say(member, "cooked");
            return;
        }
        // Waiting by the fire: take down what is done, pick up what has rolled off.
        if (ticks % 10 != 0) {
            return;
        }
        if (level.getBlockEntity(fire) instanceof CookingSpitBlockEntity spit) {
            boolean waiting = false;
            var access = ToolPiles.access(member);
            for (int slot = 0; slot < spit.slots(); slot++) {
                ItemStack on = spit.at(slot);
                if (on.isEmpty() || !member.getUUID().equals(spit.layerOf(slot))) {
                    continue;
                }
                if (Cooking.isCooked(on)) {
                    ItemStack down = spit.takeSlot(slot, access);
                    if (!down.isEmpty()) {
                        member.addToInventory(down);
                        member.swing(InteractionHand.MAIN_HAND);
                    }
                } else {
                    waiting = true;
                }
            }
            if (!waiting) {
                fire = null;
            }
            return;
        }
        List<ItemEntity> done = level.getEntitiesOfClass(ItemEntity.class, new AABB(fire).inflate(3.0D),
                item -> Cooking.isCooked(item.getItem()));
        if (!done.isEmpty()) {
            ItemEntity nearest = done.get(0);
            if (BandMember.burningAt(level, nearest.blockPosition())
                    || BandMember.burningAt(level, nearest.blockPosition().below())) {
                // Rolled into the fire: raked out with a stick from the edge, not fetched by walking into it.
                member.swing(InteractionHand.MAIN_HAND);
                member.addToInventory(nearest.getItem().copy());
                nearest.discard();
                return;
            }
            member.getNavigation().moveTo(nearest, 1.0D);
            return;
        }
        if (!stillCooking(level)) {
            fire = null;
        }
    }

    private void walkTo(BlockPos pos) {
        member.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 1.0D);
    }

    /** Everything raw they carry onto the fire, as much as it takes. */
    private boolean layOn(ServerLevel level) {
        boolean any = false;
        ItemStack raw;
        while ((raw = member.findCarried(MemberSurvival::isRawMeat)) != null && !raw.isEmpty()) {
            boolean put;
            if (level.getBlockEntity(fire) instanceof CookingSpitBlockEntity spit) {
                member.ensureName();
                put = spit.hangFor(member.getUUID(), member.getName().getString(), raw) > 0;
            } else if (level.getBlockEntity(fire) instanceof FirePitBlockEntity pit) {
                put = pit.cookFor(raw);
            } else if (level.getBlockEntity(fire) instanceof CampfireBlockEntity campfire) {
                var recipe = campfire.getCookableRecipe(raw);
                put = recipe.isPresent() && campfire.placeFood(member, raw, recipe.get().value().getCookingTime());
            } else {
                put = false;
            }
            if (!put) {
                break;
            }
            any = true;
        }
        return any;
    }

    private boolean stillCooking(ServerLevel level) {
        if (level.getBlockEntity(fire) instanceof FirePitBlockEntity pit) {
            return pit.isLit() && pit.cooking().stream().anyMatch(stack -> !stack.isEmpty());
        }
        if (level.getBlockEntity(fire) instanceof CampfireBlockEntity campfire) {
            return campfire.getItems().stream().anyMatch(stack -> !stack.isEmpty());
        }
        return false;
    }

    /** A rack over a fire with room, a lit pit, or a lit campfire - the nearest. */
    @Nullable
    private static BlockPos findFire(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH, -3, -SEARCH), origin.offset(SEARCH, 3, SEARCH))) {
            var entity = level.getBlockEntity(pos);
            boolean fire = entity instanceof CookingSpitBlockEntity && CookingSpitBlockEntity.fireBelow(level, pos)
                    || entity instanceof FirePitBlockEntity pit && pit.isLit()
                    || entity instanceof CampfireBlockEntity && net.minecraft.world.level.block.CampfireBlock.isLitCampfire(
                            level.getBlockState(pos));
            if (!fire) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }
}
