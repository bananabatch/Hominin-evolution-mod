package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.ErectusWork;
import dev.hominin.evolution.band.Lines;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

/**
 * Erectus makes what you make, when the band needs it. In order: thatch bedding for anyone still sleeping in a nest;
 * thatch blocks and building branches for whatever the band is building; workable branches to make those from, and
 * a club for anyone without a proper weapon; twine for all of it; a cleaver at the knapping station; and a spear's
 * point hardened in the fire. Things made at the work station are made there, from what the maker carries and what
 * lies in the heaps beside it.
 */
public class ErectusCraftGoal extends Goal {
    private static final int WORK_TICKS = 60;
    private static final int GIVE_UP_TICKS = 400;
    private static final double REACH = 2.6D;
    /** Twine enough for a bed. */
    private static final int BED_TWINE = 10;

    private enum Job {
        BEDDING, THATCH_BLOCK, BUILDING_BRANCH, WORKABLE_BRANCH, CLUB, TWINE, CLEAVER, HARDEN, SCHONINGEN, STONE_TIPPED
    }

    private final BandMember member;
    @Nullable
    private Job job;
    /** Where the job is done - a station, a fire - or null for wherever they stand. */
    @Nullable
    private BlockPos at;
    /** Where the heaps it draws on lie: the work station. */
    @Nullable
    private BlockPos pool;
    private int ticks;
    private int working;
    private int nextTry;

    public ErectusCraftGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean busy() {
        return member.isSleeping() || member.isTurnedIn() || member.inDanger() || member.getTarget() != null
                || member.isUpATree() || member.isOnWatch();
    }

    @Override
    public boolean canUse() {
        if (member.tickCount < nextTry) {
            return false;
        }
        nextTry = member.tickCount + 200 + member.getRandom().nextInt(200);
        if (!ErectusWork.works(member) || busy()) {
            return false;
        }
        pick();
        return job != null;
    }

    private void pick() {
        job = null;
        at = null;
        ServerPlayer leader = ErectusWork.leader(member);
        if (leader == null || member.distanceToSqr(leader) > 48.0D * 48.0D) {
            return;
        }
        ServerLevel level = (ServerLevel) member.level();
        BlockPos camp = ErectusWork.camp(leader);
        BlockPos station = ErectusWork.workStation(level, camp);
        if (station == null) {
            station = ErectusWork.workStation(level, member.blockPosition());
        }
        pool = station;
        UUID owner = leader.getUUID();
        boolean needsBed = !ErectusWork.sleepsInBed(member) && member.countOf(ErectusWork.BEDDING) < 2;
        int blocksWanted = ErectusWork.wanted(level, owner, ModBlocks.THATCH_BLOCK.get());
        int branchesWanted = ErectusWork.wanted(level, owner, ModBlocks.BUILDING_BRANCH.get());
        if (station != null) {
            if (needsBed && has(ErectusWork.HIDE, 3) && has(ErectusWork.THATCH, 3) && has(ErectusWork.TWINE, BED_TWINE)) {
                set(Job.BEDDING, station);
                return;
            }
            if (blocksWanted > has(ErectusWork.THATCH_BLOCK) && has(ErectusWork.THATCH, 9) && has(ErectusWork.TWINE, 4)) {
                set(Job.THATCH_BLOCK, station);
                return;
            }
            if (branchesWanted > has(ErectusWork.BUILDING_BRANCH) && has(ErectusWork.WORKABLE_BRANCH, 1)
                    && has(ErectusWork.ROCK, 3)) {
                set(Job.BUILDING_BRANCH, station);
                return;
            }
            boolean weak = member.bestWeaponRank() < 3;
            if (weak && has(ErectusWork.WORKABLE_BRANCH, 2) && member.countOf(s -> s.is(ModTags.Items.HAMMERSTONES)) > 0) {
                set(Job.CLUB, station);
                return;
            }
            if ((branchesWanted > has(ErectusWork.BUILDING_BRANCH) || weak) && has(ErectusWork.WORKABLE_BRANCH) < 2
                    && has(ErectusWork.RAW_BRANCH, 1) && member.countOf(s -> s.is(ModTags.Items.HAND_AXE_TOOLS)) > 0) {
                set(Job.WORKABLE_BRANCH, station);
                return;
            }
        }
        // Twine for what is coming: a bed, a batch of thatch blocks.
        int twineWanted = (needsBed ? BED_TWINE : 0) + (blocksWanted > 0 ? 4 : 0);
        if (has(ErectusWork.TWINE) < twineWanted) {
            if (member.countOf(ErectusWork.THATCH) >= 2) {
                set(Job.TWINE, null);
                return;
            }
            if (station != null && has(ErectusWork.THATCH, 2)) {
                set(Job.TWINE, station);
                return;
            }
        }
        BlockPos knapping = ErectusWork.nearest(level, camp, ModBlocks.KNAPPING_STATION.get(), 24);
        if (knapping != null && member.countOf(s -> s.is(ModItems.CLEAVER.get())) == 0
                && member.countOf(dev.hominin.evolution.band.Wants::isGoodStone) >= 2
                && member.countOf(s -> s.is(ModItems.HAND_AXE.get())) > 0) {
            set(Job.CLEAVER, knapping);
            return;
        }
        // Shown how, a member without one sometimes makes the band's great weapon.
        if (member.knowsSkill(dev.hominin.evolution.mind.Skills.Skill.SUPER_WEAPONS) && member.getRandom().nextInt(3) == 0
                && member.bestWeaponRank() < BandMember.weaponRank(new ItemStack(ModItems.SCHONINGEN_SPEAR.get()))
                && (has(SHAFT, 1) || has(ErectusWork.WORKABLE_BRANCH, 1)
                        && member.countOf(s -> s.is(ModItems.CLEAVER.get())) > 0)) {
            int lineage = dev.hominin.evolution.stage.Lineage.of(leader);
            if (lineage == dev.hominin.evolution.stage.Lineage.NEANDERTHAL) {
                BlockPos fire = hearthNear(level, member.blockPosition());
                if (fire != null) {
                    set(Job.SCHONINGEN, fire);
                    return;
                }
            } else if (lineage == dev.hominin.evolution.stage.Lineage.SAPIENS && station != null
                    && has(ErectusWork.TWINE, 5) && (has(BLADE, 1) || member.countOf(dev.hominin.evolution.band.Wants::isGoodStone) > 0)) {
                set(Job.STONE_TIPPED, station);
                return;
            }
        }
        if (member.countOf(s -> s.is(ModItems.SHARPENED_SPEAR.get())) > 0) {
            BlockPos fire = hearthNear(level, member.blockPosition());
            if (fire != null) {
                set(Job.HARDEN, fire);
            }
        }
    }

    private static final java.util.function.Predicate<ItemStack> SHAFT = s -> s.is(ModItems.WORKABLE_SHAFT.get());
    private static final java.util.function.Predicate<ItemStack> BLADE = s -> s.is(ModItems.LEVALLOIS_BLADE.get());

    /** A shaft from the heaps - or trued on the spot from a workable branch, with a cleaver. */
    private boolean takeShaft() {
        if (use(SHAFT, 1)) {
            return true;
        }
        ItemStack cleaver = member.findCarried(s -> s.is(ModItems.CLEAVER.get()));
        if (cleaver != null && use(ErectusWork.WORKABLE_BRANCH, 1)) {
            wear(cleaver);
            return true;
        }
        return false;
    }

    private void set(Job job, @Nullable BlockPos at) {
        this.job = job;
        this.at = at;
    }

    private int has(java.util.function.Predicate<ItemStack> what) {
        return ErectusWork.available(member, pool, what);
    }

    private boolean has(java.util.function.Predicate<ItemStack> what, int count) {
        return has(what) >= count;
    }

    @Nullable
    private static BlockPos hearthNear(ServerLevel level, BlockPos around) {
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-16, -3, -16), around.offset(16, 3, 16))) {
            if (dev.hominin.evolution.survival.Hearths.isLitHearth(level.getBlockState(pos))) {
                return pos.immutable();
            }
        }
        return null;
    }

    @Override
    public boolean canContinueToUse() {
        return job != null && ticks < GIVE_UP_TICKS && member.getTarget() == null && !member.inDanger();
    }

    @Override
    public void start() {
        ticks = 0;
        working = 0;
        walk();
    }

    @Override
    public void stop() {
        job = null;
        member.getNavigation().stop();
    }

    private void walk() {
        if (at != null) {
            member.getNavigation().moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 1.0D);
        }
    }

    @Override
    public void tick() {
        ticks++;
        if (at != null) {
            member.getLookControl().setLookAt(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D);
            if (member.distanceToSqr(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D) > REACH * REACH) {
                if (ticks % 20 == 0) {
                    walk();
                }
                return;
            }
        }
        member.getNavigation().stop();
        if (++working % 12 == 0) {
            member.swing(working % 24 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            SoundEvent sound = switch (job) {
                case BEDDING, TWINE, THATCH_BLOCK -> SoundEvents.GRASS_HIT;
                case CLEAVER -> SoundEvents.STONE_HIT;
                case HARDEN, SCHONINGEN -> SoundEvents.FIRE_AMBIENT;
                default -> SoundEvents.WOOD_HIT;
            };
            member.level().playSound(null, member.blockPosition(), sound, SoundSource.NEUTRAL, 0.6F,
                    0.8F + member.getRandom().nextFloat() * 0.3F);
        }
        if (working >= WORK_TICKS && job != null) {
            finish(job);
            job = null;
        }
    }

    // ------------------------------------------------------------ making it

    private void finish(Job job) {
        switch (job) {
            case BEDDING -> {
                if (has(ErectusWork.HIDE, 3) && has(ErectusWork.THATCH, 3) && has(ErectusWork.TWINE, BED_TWINE)
                        && use(ErectusWork.HIDE, 3) && use(ErectusWork.THATCH, 3) && use(ErectusWork.TWINE, BED_TWINE)) {
                    member.addToInventory(new ItemStack(ModItems.THATCH_BEDDING.get(), 2));
                    Lines.announce(member, "craft_bedding");
                }
            }
            case THATCH_BLOCK -> {
                if (has(ErectusWork.THATCH, 9) && has(ErectusWork.TWINE, 4)
                        && use(ErectusWork.THATCH, 9) && use(ErectusWork.TWINE, 4)) {
                    member.addToInventory(new ItemStack(ModItems.THATCH_BLOCK.get(), 4));
                    Lines.announce(member, "craft_thatch_block");
                }
            }
            case BUILDING_BRANCH -> {
                if (has(ErectusWork.WORKABLE_BRANCH, 1) && has(ErectusWork.ROCK, 3)
                        && use(ErectusWork.WORKABLE_BRANCH, 1) && use(ErectusWork.ROCK, 3)) {
                    member.addToInventory(new ItemStack(ModItems.BUILDING_BRANCH.get(), 2));
                    Lines.announce(member, "craft_building_branch");
                }
            }
            case WORKABLE_BRANCH -> {
                boolean log = has(s -> s.is(net.minecraft.tags.ItemTags.LOGS)) > 0;
                ItemStack axe = member.findCarried(s -> s.is(ModTags.Items.HAND_AXE_TOOLS));
                if (axe != null && use(log ? s -> s.is(net.minecraft.tags.ItemTags.LOGS) : ErectusWork.RAW_BRANCH, 1)) {
                    wear(axe);
                    member.addToInventory(new ItemStack(ModItems.WORKABLE_BRANCH.get(), log ? 2 : 1));
                    Lines.announce(member, "craft_workable_branch");
                }
            }
            case CLUB -> {
                ItemStack hammer = member.findCarried(s -> s.is(ModTags.Items.HAMMERSTONES));
                if (hammer != null && use(ErectusWork.WORKABLE_BRANCH, 2)) {
                    wear(hammer);
                    member.addToInventory(new ItemStack(ModItems.WOODEN_CLUB.get()));
                    Lines.announce(member, "craft_club");
                }
            }
            case TWINE -> {
                int made = 0;
                while (made < 4 && has(ErectusWork.THATCH, 2) && use(ErectusWork.THATCH, 2)) {
                    member.addToInventory(new ItemStack(ModItems.TWINE.get()));
                    made++;
                }
                if (made > 0) {
                    Lines.announce(member, "craft_twine");
                }
            }
            case CLEAVER -> {
                ItemStack stone = member.findCarried(dev.hominin.evolution.band.Wants::isGoodStone);
                if (stone == null) {
                    return;
                }
                ItemStack kind = stone.copyWithCount(1);
                if (member.countOf(s -> s.is(kind.getItem())) < 2) {
                    return;
                }
                member.takeFirst(s -> s.is(kind.getItem()));
                member.takeFirst(s -> s.is(kind.getItem()));
                int quality = dev.hominin.evolution.band.Species.capQuality(member.getStage(),
                        dev.hominin.evolution.knapping.Acheulean.rollQuality(member.getKnapLevel(), kind, member.getRandom()));
                member.addToInventory(dev.hominin.evolution.item.StoneMaterial.stampFrom(
                        ((dev.hominin.evolution.item.AcheuleanToolItem) ModItems.CLEAVER.get()).make(quality), kind));
                member.practiseKnapping();
                Lines.announce(member, "craft_cleaver",
                        dev.hominin.evolution.item.AcheuleanToolItem.TIER_NAMES[quality].toLowerCase(), quality);
            }
            case HARDEN -> {
                if (!member.takeFirst(s -> s.is(ModItems.SHARPENED_SPEAR.get())).isEmpty()) {
                    member.addToInventory(new ItemStack(ModItems.FIRE_HARDENED_SPEAR.get()));
                    Lines.announce(member, "craft_hardened");
                }
            }
            case SCHONINGEN -> {
                if (takeShaft()) {
                    member.addToInventory(new ItemStack(ModItems.SCHONINGEN_SPEAR.get()));
                    Lines.announce(member, "craft_schoningen");
                }
            }
            case STONE_TIPPED -> {
                boolean blade = has(BLADE, 1);
                if (has(ErectusWork.TWINE, 5) && (blade || member.countOf(dev.hominin.evolution.band.Wants::isGoodStone) > 0)
                        && takeShaft()) {
                    dev.hominin.evolution.item.StoneMaterial point;
                    if (blade) {
                        ItemStack carried = member.findCarried(BLADE);
                        point = carried != null ? dev.hominin.evolution.item.StoneMaterial.of(carried)
                                : dev.hominin.evolution.item.StoneMaterial.CHERT;
                        use(BLADE, 1);
                    } else {
                        // Everyone knaps the Levallois way: a blade off a good stone, there and then.
                        point = dev.hominin.evolution.item.StoneMaterial.ofStone(
                                member.takeFirst(dev.hominin.evolution.band.Wants::isGoodStone));
                    }
                    use(ErectusWork.TWINE, 5);
                    member.addToInventory(dev.hominin.evolution.item.StoneMaterial.mark(
                            new ItemStack(ModItems.STONE_TIPPED_SPEAR.get()), point));
                    Lines.announce(member, "craft_stone_tipped");
                }
            }
        }
    }

    private boolean use(java.util.function.Predicate<ItemStack> what, int count) {
        return ErectusWork.consume(member, pool, what, count);
    }

    private void wear(ItemStack tool) {
        if (tool.isDamageableItem()) {
            tool.setDamageValue(tool.getDamageValue() + 1);
            if (tool.getDamageValue() >= tool.getMaxDamage()) {
                tool.shrink(1);
            }
        }
    }
}
