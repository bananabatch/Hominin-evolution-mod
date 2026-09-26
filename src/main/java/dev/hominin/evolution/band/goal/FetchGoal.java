package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.List;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.FetchKind;
import dev.hominin.evolution.block.LooseRockBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Going to get something the player asked for, the honest way: pick it up if it is lying
 * nearby, tear it out of a tree, or prise a loose stone off the ground. If there is no
 * such thing around, the member says so and gives up rather than conjuring one.
 */
public class FetchGoal extends Goal {
    private static final int ITEM_SEARCH_RADIUS = 12;
    private static final int LEAF_SEARCH_RADIUS = 10;
    private static final int ROCK_SEARCH_RADIUS = 24;
    private static final int WORK_TICKS = 20;
    private static final int MAX_LEAF_TRIES = 8;
    private static final double HAND_OVER_DISTANCE_SQR = 9.0D;

    private final BandMember member;
    private ItemEntity lying;
    private BlockPos source;
    private int working;
    private int tries;
    private int ticks;

    public FetchGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return member.getFetchKind() != null && member.fetchPlayer() != null && !member.isBaby();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        lying = null;
        source = null;
        working = 0;
        tries = 0;
        ticks = 0;
    }

    @Override
    public void stop() {
        member.getNavigation().stop();
    }

    @Override
    public void tick() {
        ticks++;
        FetchKind kind = member.getFetchKind();
        Player player = member.fetchPlayer();
        if (kind == null || player == null) {
            return;
        }
        if (member.carries(kind)) {
            deliver(kind, player);
            return;
        }
        if (lying != null && lying.isAlive()) {
            pickUp();
            return;
        }
        if (ticks % 20 == 1) {
            lying = findLying(kind);
            if (lying != null) {
                return;
            }
        }
        if (kind.source() == FetchKind.Source.MADE && dev.hominin.evolution.band.ErectusWork.works(member)
                && member.level() instanceof net.minecraft.server.level.ServerLevel server) {
            // Not on them: the heaps by the work station, then.
            net.minecraft.server.level.ServerPlayer leader = dev.hominin.evolution.band.ErectusWork.leader(member);
            BlockPos station = leader == null ? null
                    : dev.hominin.evolution.band.ErectusWork.workStation(server, dev.hominin.evolution.band.ErectusWork.camp(leader));
            ItemStack found = dev.hominin.evolution.band.ErectusWork.takeFromHeaps(member, station, kind::matches);
            if (!found.isEmpty()) {
                member.addToInventory(found);
                return;
            }
        }
        if (kind.source() == FetchKind.Source.MADE) {
            // They either had it - handled above by carries() - or they did not.
            fail(kind, player);
            return;
        }
        if (source == null) {
            source = findSource(kind);
            if (source == null) {
                fail(kind, player);
                return;
            }
        }
        work(kind, player);
    }

    private void deliver(FetchKind kind, Player player) {
        member.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (member.distanceToSqr(player) > HAND_OVER_DISTANCE_SQR) {
            if (ticks % 20 == 0 || member.getNavigation().isDone()) {
                member.getNavigation().moveTo(player, 1.2D);
            }
            return;
        }
        member.getNavigation().stop();
        ItemStack given = member.takeOne(kind);
        if (!given.isEmpty()) {
            member.swing(InteractionHand.MAIN_HAND);
            player.displayClientMessage(Component.literal(member.getName().getString() + " hands you ")
                    .append(given.getHoverName()).append("."), true);
            if (!player.getInventory().add(given)) {
                player.drop(given, false);
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer taker) {
                dev.hominin.evolution.band.Mood.took(taker, 1);
            }
        }
        member.clearFetch();
    }

    private void pickUp() {
        member.getLookControl().setLookAt(lying, 30.0F, 30.0F);
        if (member.distanceToSqr(lying) > 2.25D) {
            if (ticks % 20 == 0 || member.getNavigation().isDone()) {
                member.getNavigation().moveTo(lying, 1.1D);
            }
            return;
        }
        ItemStack stack = lying.getItem().split(1);
        if (lying.getItem().isEmpty()) {
            lying.discard();
        }
        member.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
        member.getInventory().addItem(stack);
        lying = null;
    }

    private void work(FetchKind kind, Player player) {
        Level level = member.level();
        member.getLookControl().setLookAt(source.getX() + 0.5D, source.getY() + 0.5D, source.getZ() + 0.5D);
        if (member.distanceToSqr(source.getX() + 0.5D, source.getY() + 0.5D, source.getZ() + 0.5D) > 6.25D) {
            if (ticks % 20 == 0 || member.getNavigation().isDone()) {
                member.getNavigation().moveTo(source.getX() + 0.5D, source.getY(), source.getZ() + 0.5D, 1.1D);
            }
            if (ticks > 1600) {
                fail(kind, player);
            }
            return;
        }
        member.getNavigation().stop();
        if (++working % 5 == 0) {
            member.swing(InteractionHand.MAIN_HAND);
        }
        if (working < WORK_TICKS) {
            return;
        }
        working = 0;
        BlockState state = level.getBlockState(source);
        if (!EventHooks.canEntityGrief(level, member) || !stillASource(kind, state)) {
            source = null;
            return;
        }
        level.destroyBlock(source, false, member);
        if (kind.source() == FetchKind.Source.ROCKS) {
            member.getInventory().addItem(new ItemStack(state.getBlock().asItem()));
        } else if (member.getRandom().nextFloat() < kind.chancePerTry()) {
            member.getInventory().addItem(new ItemStack(kind.item()));
        } else if (++tries >= MAX_LEAF_TRIES) {
            fail(kind, player);
            return;
        }
        source = null;
    }

    private boolean stillASource(FetchKind kind, BlockState state) {
        if (kind.source() == FetchKind.Source.LEAVES) {
            return state.is(BlockTags.LEAVES);
        }
        return state.getBlock() instanceof LooseRockBlock
                && (kind == FetchKind.ANY_ROCK || state.getBlock().asItem() == kind.item());
    }

    private ItemEntity findLying(FetchKind kind) {
        List<ItemEntity> items = member.level().getEntitiesOfClass(ItemEntity.class,
                member.getBoundingBox().inflate(ITEM_SEARCH_RADIUS, 4.0D, ITEM_SEARCH_RADIUS),
                item -> item.isAlive() && kind.matches(item.getItem()));
        ItemEntity best = null;
        for (ItemEntity item : items) {
            if (best == null || member.distanceToSqr(item) < member.distanceToSqr(best)) {
                best = item;
            }
        }
        return best;
    }

    private BlockPos findSource(FetchKind kind) {
        int radius = kind.source() == FetchKind.Source.LEAVES ? LEAF_SEARCH_RADIUS : ROCK_SEARCH_RADIUS;
        Level level = member.level();
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -3, -radius), origin.offset(radius, 4, radius))) {
            if (stillASource(kind, level.getBlockState(pos))) {
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    private void fail(FetchKind kind, Player player) {
        // A tool is not somewhere to be looked for. Saying they searched and found none
        // would be a lie about what the problem is: they simply have not got one.
        String excuse = kind.source() == FetchKind.Source.MADE
                ? " hasn't got " + kind.label().toLowerCase() + " to give you."
                : " looked around, but couldn't find " + kind.label().toLowerCase() + " anywhere near here.";
        player.sendSystemMessage(Component.literal(member.getName().getString() + excuse)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        member.clearFetch();
    }
}
