package dev.hominin.evolution.band.goal;

import java.util.EnumSet;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.block.LooseRockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Hitting stones together with nobody showing them how. A member picks up a couple of
 * loose rocks and bangs them against each other until something with an edge comes
 * off - a Lomekwian core, the oldest stone tool there is. From habilis on, what comes
 * off is more often a proper flake or a chopper.
 */
public class TinkerGoal extends Goal {
    private static final int SEARCH_RADIUS = 10;
    private static final int KNAP_TICKS = 60;
    private static final int GIVE_UP_TICKS = 500;
    /** Between tries, whatever the outcome. Tool-making is an occasional thing. */
    private static final int MIN_COOLDOWN = 12000;
    private static final int COOLDOWN_SPREAD = 12000;
    /** Most blind attempts come to nothing. */
    private static final float EARLY_SUCCESS = 0.35F;

    private static final ResourceLocation ARDIPITHECUS =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "ardipithecus");
    private static final ResourceLocation AUSTRALOPITHECUS =
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "australopithecus");

    private final BandMember member;
    private BlockPos rock;
    private int ticks;
    private int knapping;
    private int nextTry = -1;

    public TinkerGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (nextTry < 0) {
            nextTry = member.tickCount + MIN_COOLDOWN / 2 + member.getRandom().nextInt(COOLDOWN_SPREAD);
        }
        if (member.tickCount < nextTry || member.isBaby() || member.isUpATree() || member.isHungry()
                || member.getRandom().nextInt(20) != 0) {
            return false;
        }
        // Blind knapping is a rare accident, not a pastime: never on the first day, and at most
        // one core a day for the whole band.
        if (!CraftGoal.canCraft(member) && !Band.mayMakeLomekwian(member)) {
            nextTry = member.tickCount + MIN_COOLDOWN;
            return false;
        }
        if (stones() >= 2) {
            rock = null;
            // From habilis on, what to make of the stones is the craft goal's decision.
            return !CraftGoal.canCraft(member);
        }
        rock = findLooseRock();
        if (rock == null) {
            nextTry = member.tickCount + MIN_COOLDOWN / 3;
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return ticks < GIVE_UP_TICKS && (rock != null || (stones() >= 2 && !CraftGoal.canCraft(member)));
    }

    @Override
    public void start() {
        ticks = 0;
        knapping = 0;
        if (rock != null) {
            dev.hominin.evolution.band.Lines.tell(member, "tinker_go");
        }
    }

    @Override
    public void stop() {
        rock = null;
        member.getNavigation().stop();
        nextTry = member.tickCount + MIN_COOLDOWN + member.getRandom().nextInt(COOLDOWN_SPREAD);
    }

    @Override
    public void tick() {
        ticks++;
        if (rock != null) {
            gatherRock();
            return;
        }
        member.getNavigation().stop();
        if (++knapping % 10 == 0) {
            member.swing(knapping % 20 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            member.level().playSound(null, member.blockPosition(), SoundEvents.STONE_HIT, SoundSource.NEUTRAL,
                    0.7F, 0.8F + member.getRandom().nextFloat() * 0.3F);
        }
        if (knapping >= KNAP_TICKS) {
            finishKnapping();
        }
    }

    private void gatherRock() {
        Level level = member.level();
        BlockState state = level.getBlockState(rock);
        if (!(state.getBlock() instanceof LooseRockBlock)) {
            rock = stones() >= 2 ? null : findLooseRock();
            return;
        }
        member.getLookControl().setLookAt(rock.getX() + 0.5D, rock.getY(), rock.getZ() + 0.5D);
        if (member.distanceToSqr(rock.getX() + 0.5D, rock.getY(), rock.getZ() + 0.5D) > 4.0D) {
            if (ticks % 20 == 1) {
                member.getNavigation().moveTo(rock.getX() + 0.5D, rock.getY(), rock.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        if (!EventHooks.canEntityGrief(level, member)) {
            rock = null;
            return;
        }
        Item stone = state.getBlock().asItem();
        level.destroyBlock(rock, false, member);
        member.swing(InteractionHand.MAIN_HAND);
        member.getInventory().addItem(new ItemStack(stone, 2));
        rock = stones() >= 2 ? null : findLooseRock();
    }

    private void finishKnapping() {
        consumeStone();
        var random = member.getRandom();
        ResourceLocation stage = member.getStage();
        boolean early = CraftGoal.isPreOldowan(stage);
        Item made;
        if (early) {
            if (random.nextFloat() >= EARLY_SUCCESS) {
                // Usually the stone just breaks into nothing worth keeping.
                knapping = 0;
                ticks = GIVE_UP_TICKS;
                return;
            }
            made = ModItems.LOMEKWIAN_TOOL.get();
            Band.lomekwianMade(member);
        } else {
            float roll = random.nextFloat();
            made = roll < 0.5F ? ModItems.FLAKE.get() : roll < 0.75F ? ModItems.CHOPPER.get() : ModItems.LOMEKWIAN_TOOL.get();
        }
        ItemStack tool = new ItemStack(made);
        member.addToInventory(tool.copy());
        dev.hominin.evolution.band.Lines.announce(member, "tinker_made", tool.getHoverName().getString());
        knapping = 0;
        ticks = GIVE_UP_TICKS;
    }

    private int stones() {
        int count = 0;
        SimpleContainer pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (isStone(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void consumeStone() {
        SimpleContainer pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (isStone(pack.getItem(slot))) {
                pack.getItem(slot).shrink(1);
                return;
            }
        }
    }

    private static boolean isStone(ItemStack stack) {
        return stack.is(ModItems.ROCK.get()) || stack.is(ModTags.Items.KNAPPABLE_STONE);
    }

    private BlockPos findLooseRock() {
        Level level = member.level();
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, 3, SEARCH_RADIUS))) {
            if (level.getBlockState(pos).getBlock() instanceof LooseRockBlock) {
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
