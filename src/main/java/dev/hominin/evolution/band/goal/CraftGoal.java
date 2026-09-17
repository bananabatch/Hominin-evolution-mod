package dev.hominin.evolution.band.goal;

import java.util.EnumSet;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Habilis and later make their own tools, from what they carry, to meet their own needs.
 *
 * <p>Each member looks at what it lacks, in order: a worn stone edge wants grinding (or
 * a grinding stone made); no weapon wants a pointy stick or a spear; no cutting edge
 * wants a flake knapped; and a member who already cuts well works up to a chopper, and
 * having made one, to a multi tool. Nobody is told to - and the player hears about it.
 */
public class CraftGoal extends Goal {
    private static final int WORK_TICKS = 60;
    private static final int MIN_COOLDOWN = 2400;
    private static final int COOLDOWN_SPREAD = 3600;
    /** Grinding is worth doing once an edge has lost this share of its life. */
    private static final float WORN_FRACTION = 0.5F;
    private static final int GRIND_REPAIR = 16;

    private static final ResourceLocation ARDIPITHECUS = id("ardipithecus");
    private static final ResourceLocation AUSTRALOPITHECUS = id("australopithecus");

    private enum Plan {
        GRIND, GRINDING_STONE, SPEAR, POINTY_STICK, FLAKE, CHOPPER, MULTITOOL
    }

    private final BandMember member;
    @Nullable
    private Plan plan;
    private int working;
    private int nextTry = -1;

    public CraftGoal(BandMember member) {
        this.member = member;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, path);
    }

    /** Only from habilis on: before that, hitting stones together is as far as it goes. */
    public static boolean canCraft(BandMember member) {
        ResourceLocation stage = member.getStage();
        return !member.isBaby() && !ARDIPITHECUS.equals(stage) && !AUSTRALOPITHECUS.equals(stage);
    }

    @Override
    public boolean canUse() {
        if (nextTry < 0) {
            nextTry = member.tickCount + MIN_COOLDOWN / 2 + member.getRandom().nextInt(COOLDOWN_SPREAD);
        }
        if (member.tickCount < nextTry || !canCraft(member) || member.getTarget() != null || member.isUpATree()
                || member.getRandom().nextInt(20) != 0) {
            return false;
        }
        plan = choosePlan();
        if (plan == null) {
            nextTry = member.tickCount + MIN_COOLDOWN / 2;
        }
        return plan != null;
    }

    @Override
    public boolean canContinueToUse() {
        return plan != null && working < WORK_TICKS && member.getTarget() == null;
    }

    @Override
    public void start() {
        working = 0;
        member.getNavigation().stop();
    }

    @Override
    public void stop() {
        plan = null;
        nextTry = member.tickCount + MIN_COOLDOWN + member.getRandom().nextInt(COOLDOWN_SPREAD);
    }

    @Override
    public void tick() {
        member.getNavigation().stop();
        if (++working % 12 == 0) {
            member.swing(working % 24 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            SoundEvent sound = plan == Plan.POINTY_STICK || plan == Plan.SPEAR ? SoundEvents.WOOD_HIT
                    : plan == Plan.GRIND ? SoundEvents.GRINDSTONE_USE : SoundEvents.STONE_HIT;
            member.level().playSound(null, member.blockPosition(), sound, SoundSource.NEUTRAL, 0.6F,
                    0.8F + member.getRandom().nextFloat() * 0.3F);
        }
        if (working >= WORK_TICKS && plan != null) {
            finish(plan);
            plan = null;
        }
    }

    // ------------------------------------------------------------ deciding

    @Nullable
    private Plan choosePlan() {
        ItemStack worn = wornTool();
        if (worn != null) {
            if (has(s -> s.is(ModItems.GRINDING_ROCK.get()))) {
                return Plan.GRIND;
            }
            if (has(s -> s.is(ModItems.LIMESTONE_ROCK.get())) && stones() >= 2) {
                return Plan.GRINDING_STONE;
            }
        }
        boolean flake = has(s -> s.is(ModTags.Items.FLAKES));
        if (!member.hasWeapon() || weakWeapon()) {
            if (flake && has(s -> s.is(ModItems.LONG_BRANCH.get()))) {
                return Plan.SPEAR;
            }
            if (flake && has(s -> s.is(Items.STICK) || s.is(ModItems.SHARPENED_STICK.get()))) {
                return Plan.POINTY_STICK;
            }
        }
        if (!flake && stones() >= 2) {
            return Plan.FLAKE;
        }
        if (flake && !has(s -> s.is(ModItems.CHOPPER.get())) && !member.hasMadeChopper() && stones() >= 2) {
            return Plan.CHOPPER;
        }
        if (member.hasMadeChopper() && !has(s -> s.is(ModItems.OLDOWAN_MULTITOOL.get()))
                && (count(ModItems.CHERT_ROCK.get()) >= 3 || count(ModItems.OBSIDIAN_ROCK.get()) >= 2)
                && stones() >= 4) {
            return Plan.MULTITOOL;
        }
        return null;
    }

    /** A pointy stick or better is a proper weapon; a bare branch or a gnawed stick is not. */
    private boolean weakWeapon() {
        ItemStack held = member.getMainHandItem();
        return held.is(ModItems.LONG_BRANCH.get()) || held.is(ModItems.SHARPENED_STICK.get());
    }

    // ------------------------------------------------------------ making

    private void finish(Plan plan) {
        switch (plan) {
            case GRIND -> {
                ItemStack worn = wornTool();
                ItemStack stone = find(s -> s.is(ModItems.GRINDING_ROCK.get()));
                if (worn != null && stone != null) {
                    worn.setDamageValue(Math.max(0, worn.getDamageValue() - GRIND_REPAIR));
                    wear(stone);
                    say(" grinds a fresh edge back onto their " + worn.getHoverName().getString() + ".");
                }
            }
            case GRINDING_STONE -> {
                if (take(s -> s.is(ModItems.LIMESTONE_ROCK.get()))) {
                    make(ModItems.GRINDING_ROCK.get(), " shapes a flat, rough stone for grinding edges.");
                }
            }
            case SPEAR -> {
                if (take(s -> s.is(ModItems.LONG_BRANCH.get()) || member.getMainHandItem().is(ModItems.LONG_BRANCH.get()))) {
                    wear(find(s -> s.is(ModTags.Items.FLAKES)));
                    make(ModItems.SHARPENED_SPEAR.get(), " whittles a branch into a Sharpened Spear with a flake.");
                }
            }
            case POINTY_STICK -> {
                if (take(s -> s.is(Items.STICK) || s.is(ModItems.SHARPENED_STICK.get()))) {
                    wear(find(s -> s.is(ModTags.Items.FLAKES)));
                    make(ModItems.POINTY_STICK.get(), " works a flake along a stick and makes a Pointy Stick.");
                }
            }
            case FLAKE -> {
                if (takeStone()) {
                    make(ModItems.FLAKE.get(), " strikes a sharp Flake off a stone.");
                }
            }
            case CHOPPER -> {
                if (takeStone()) {
                    member.markMadeChopper();
                    make(ModItems.CHOPPER.get(), " batters a stone down into a Chopper.");
                }
            }
            case MULTITOOL -> {
                Item stone = count(ModItems.OBSIDIAN_ROCK.get()) >= 2 ? ModItems.OBSIDIAN_ROCK.get() : ModItems.CHERT_ROCK.get();
                int cost = stone == ModItems.OBSIDIAN_ROCK.get() ? 2 : 3;
                for (int i = 0; i < cost; i++) {
                    take(s -> s.is(stone));
                }
                make(ModItems.OLDOWAN_MULTITOOL.get(), " works a stone on both faces into an Oldowan Multitool!");
            }
        }
    }

    private void make(Item item, String announcement) {
        member.addToInventory(new ItemStack(item));
        Band.announceDiscovery(member, announcement);
    }

    private void say(String rest) {
        Band.announceDiscovery(member, rest);
    }

    private void wear(@Nullable ItemStack tool) {
        if (tool != null && tool.isDamageableItem()) {
            tool.setDamageValue(tool.getDamageValue() + 1);
            if (tool.getDamageValue() >= tool.getMaxDamage()) {
                tool.shrink(1);
            }
        }
    }

    // ------------------------------------------------------------ what it carries

    @Nullable
    private ItemStack wornTool() {
        ItemStack held = member.getMainHandItem();
        if (isWorn(held)) {
            return held;
        }
        return find(this::isWorn);
    }

    private boolean isWorn(ItemStack stack) {
        return stack.is(ModTags.Items.STONE_TOOLS) && stack.isDamageableItem()
                && !stack.is(ModItems.GRINDING_ROCK.get())
                && stack.getDamageValue() >= stack.getMaxDamage() * WORN_FRACTION;
    }

    private boolean has(Predicate<ItemStack> test) {
        return test.test(member.getMainHandItem()) || find(test) != null;
    }

    @Nullable
    private ItemStack find(Predicate<ItemStack> test) {
        SimpleContainer pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (!stack.isEmpty() && test.test(stack)) {
                return stack;
            }
        }
        return test.test(member.getMainHandItem()) && !member.getMainHandItem().isEmpty()
                ? member.getMainHandItem() : null;
    }

    private boolean take(Predicate<ItemStack> test) {
        ItemStack stack = find(test);
        if (stack == null) {
            return false;
        }
        stack.shrink(1);
        return true;
    }

    private int count(Item item) {
        int count = 0;
        SimpleContainer pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (pack.getItem(slot).is(item)) {
                count += pack.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static boolean isStone(ItemStack stack) {
        return stack.is(ModItems.ROCK.get()) || stack.is(ModTags.Items.KNAPPABLE_STONE);
    }

    private int stones() {
        int count = 0;
        SimpleContainer pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            if (isStone(pack.getItem(slot))) {
                count += pack.getItem(slot).getCount();
            }
        }
        return count;
    }

    /** Uses up one stone, keeping the last as the hammer. Soft limestone goes first. */
    private boolean takeStone() {
        return take(s -> s.is(ModItems.LIMESTONE_ROCK.get()) || s.is(ModItems.ROCK.get())) || take(CraftGoal::isStone);
    }
}
