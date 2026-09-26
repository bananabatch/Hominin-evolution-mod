package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Feast;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.band.ToolPiles;
import dev.hominin.evolution.block.CookingSpitBlockEntity;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.food.Cooking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Getting ready for a feast, and then being at it. In the two days before, a member with food to spare brings it to
 * the feast place and lays it on a food pile there; one with raw meat hangs it on a rack over the fire; and if there
 * is no rack by the fire, one of them puts one up - two forked posts either side of a fire pit, a branch across. When
 * the feast begins, everyone comes to the fire (the eating itself is {@link Feast}'s).
 */
public class FeastGoal extends Goal {
    private static final double REACH = 2.4D;
    private static final int GIVE_UP_TICKS = 500;
    private static final int BUILD_TICKS = 120;
    /** Carry this much and it goes to the feast, all but one. */
    private static final int BRING = 3;

    private enum Errand {
        GATHER, BRING_FOOD, HANG_MEAT, BUILD_RACK
    }

    private final BandMember member;
    @Nullable
    private Errand errand;
    @Nullable
    private BlockPos target;
    private int ticks;
    private int working;
    private int cooldown;

    public FeastGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean busy() {
        return member.isSleeping() || member.isTurnedIn() || member.inDanger() || member.getTarget() != null
                || member.isUpATree() || member.isOnWatch() || member.isGuest() || member.isWild();
    }

    @Override
    public boolean canUse() {
        if (!(member.leaderPlayer() instanceof ServerPlayer leader) || busy()
                || !(member.level() instanceof ServerLevel level)) {
            return false;
        }
        BlockPos place = Feast.place(leader);
        if (place == null) {
            return false;
        }
        if (Feast.feasting(leader)) {
            // Everyone comes to the fire.
            if (member.distanceToSqr(place.getCenter()) > 5.0D * 5.0D) {
                errand = Errand.GATHER;
                target = place;
                return true;
            }
            return false;
        }
        if (!Feast.preparing(leader) || member.isBaby() || --cooldown > 0) {
            return false;
        }
        cooldown = 160 + member.getRandom().nextInt(200);
        UUID owner = leader.getUUID();
        ItemStack raw = member.findCarried(FeastGoal::rawMeat);
        if (raw != null) {
            BlockPos spit = spitWithRoom(level, place, raw);
            if (spit != null) {
                errand = Errand.HANG_MEAT;
                target = spit;
                return true;
            }
        }
        if (member.countOf(Feast.FEAST_FOOD) >= BRING) {
            BlockPos pile = foodPileSpot(level, owner, place);
            if (pile != null) {
                errand = Errand.BRING_FOOD;
                target = pile;
                return true;
            }
        }
        int racks = spits(level, place);
        int wanted = dev.hominin.evolution.band.Band.all(leader).size() >= 10 ? 2 : 1;
        if (racks < wanted && dev.hominin.evolution.band.Bands.erectusOn(member.getStage())
                && Feast.claimBuilder(leader, member)) {
            BlockPos spot = rackSpot(level, place);
            if (spot != null) {
                errand = Errand.BUILD_RACK;
                target = spot;
                return true;
            }
            Feast.releaseBuilder(leader, member);
        }
        return false;
    }

    private static boolean rawMeat(ItemStack stack) {
        return Feast.FEAST_FOOD.test(stack) && !Cooking.isCooked(stack)
                && (stack.is(dev.hominin.evolution.ModItems.MEAT_CHUNK.get())
                        || stack.is(dev.hominin.evolution.ModItems.RIB.get())
                        || stack.is(net.minecraft.tags.ItemTags.MEAT));
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || errand == null || ticks > GIVE_UP_TICKS || member.inDanger() || member.getTarget() != null) {
            return false;
        }
        if (errand == Errand.GATHER) {
            return Feast.feasting(member) && member.distanceToSqr(target.getCenter()) > 4.0D * 4.0D;
        }
        return Feast.preparing(member);
    }

    @Override
    public void start() {
        ticks = 0;
        working = 0;
        walk();
        if (errand == Errand.BUILD_RACK) {
            Lines.tell(member, "feast_rack");
        }
    }

    @Override
    public void stop() {
        if (errand == Errand.BUILD_RACK && member.leaderPlayer() instanceof ServerPlayer leader) {
            Feast.releaseBuilder(leader, member);
        }
        errand = null;
        target = null;
        member.getNavigation().stop();
    }

    private void walk() {
        if (target != null) {
            member.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                    errand == Errand.GATHER ? 1.1D : 1.0D);
        }
    }

    @Override
    public void tick() {
        ticks++;
        member.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        double reach = errand == Errand.GATHER ? 4.0D : REACH;
        if (member.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D) > reach * reach) {
            if (ticks % 20 == 0) {
                walk();
            }
            return;
        }
        member.getNavigation().stop();
        ServerLevel level = (ServerLevel) member.level();
        switch (errand) {
            case BRING_FOOD -> {
                bring(level);
                target = null;
            }
            case HANG_MEAT -> {
                hang(level);
                target = null;
            }
            case BUILD_RACK -> {
                if (++working % 15 == 0) {
                    member.swing(working % 30 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
                    level.playSound(null, target, SoundEvents.WOOD_HIT, SoundSource.NEUTRAL, 0.6F, 0.9F);
                }
                if (working >= BUILD_TICKS) {
                    if (member.leaderPlayer() instanceof ServerPlayer leader
                            && dev.hominin.evolution.build.Building.raiseRack(level, leader.getUUID(), target)) {
                        Lines.announce(member, "feast_rack_done");
                    }
                    target = null;
                }
            }
            default -> target = null;
        }
    }

    /** All but one mouthful onto a food pile by the fire. */
    private void bring(ServerLevel level) {
        UUID owner = member.getLeader();
        if (owner == null) {
            return;
        }
        boolean any = false;
        int keep = 1;
        var pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (!Feast.FEAST_FOOD.test(stack)) {
                continue;
            }
            int give = stack.getCount() - keep;
            keep = Math.max(0, keep - stack.getCount());
            if (give <= 0) {
                continue;
            }
            ItemStack heap = stack.split(give);
            int count = heap.getCount();
            if (!ToolPiles.layDown(level, target, owner, heap, member)) {
                stack.grow(heap.getCount());
                break;
            }
            stack.grow(heap.getCount());
            any |= count > heap.getCount();
        }
        if (any) {
            member.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, target, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 0.6F, 0.9F);
            Lines.tell(member, "feast_bring");
        }
    }

    private void hang(ServerLevel level) {
        if (!(level.getBlockEntity(target) instanceof CookingSpitBlockEntity spit)) {
            return;
        }
        ItemStack raw = member.findCarried(FeastGoal::rawMeat);
        if (raw == null) {
            return;
        }
        member.ensureName();
        if (spit.hangFor(member.getUUID(), member.getName().getString(), raw) > 0) {
            member.swing(InteractionHand.MAIN_HAND);
            Lines.tell(member, "feast_hang");
        }
    }

    // ------------------------------------------------------------ where

    /** A spit by the feast place with room for this. */
    @Nullable
    private static BlockPos spitWithRoom(ServerLevel level, BlockPos place, ItemStack stack) {
        int reach = Feast.FEAST_REACH;
        for (BlockPos pos : BlockPos.betweenClosed(place.offset(-reach, -2, -reach), place.offset(reach, 3, reach))) {
            if (level.getBlockEntity(pos) instanceof CookingSpitBlockEntity spit && spit.hasRoomFor(stack)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static int spits(ServerLevel level, BlockPos place) {
        int count = 0;
        int reach = Feast.FEAST_REACH;
        for (BlockPos pos : BlockPos.betweenClosed(place.offset(-reach, -2, -reach), place.offset(reach, 3, reach))) {
            if (level.getBlockState(pos).is(ModBlocks.COOKING_SPIT.get())) {
                count++;
            }
        }
        return count;
    }

    /** A food pile of the band's by the feast place with room, or clear ground for a new one there. */
    @Nullable
    private static BlockPos foodPileSpot(ServerLevel level, UUID owner, BlockPos place) {
        for (BlockPos pos : ToolPiles.piles(level, owner)) {
            if (pos.distSqr(place) <= 6 * 6 && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile
                    && pile.kind() == ToolPileBlockEntity.Kind.FOOD && !pile.isFull()) {
                return pos;
            }
        }
        for (int radius = 2; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos at = place.offset(dx, dy, dz);
                        if (ToolPiles.canPileAt(level, at)) {
                            return at;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Level, clear ground three wide by the feast place, for two posts and a fire pit between them. */
    @Nullable
    private static BlockPos rackSpot(ServerLevel level, BlockPos place) {
        for (int radius = 3; radius <= 7; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos at = place.offset(dx, dy, dz);
                        if (dev.hominin.evolution.build.Building.rackFits(level, at)) {
                            return at;
                        }
                    }
                }
            }
        }
        return null;
    }
}
