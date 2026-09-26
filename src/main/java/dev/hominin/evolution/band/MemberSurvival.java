package dev.hominin.evolution.band;

import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.combat.Bleeding;
import dev.hominin.evolution.food.Cooking;
import dev.hominin.evolution.food.Spoilage;
import dev.hominin.evolution.survival.Afflictions;
import dev.hominin.evolution.survival.Thirst;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;

/**
 * A band member's body kept the way a player's is: thirst and drinking, the catastrophic bleed that only water
 * stops, the sickness that turned meat brings on, and raw meat in a torn-open wound. What a player feels, they feel.
 *
 * <p>Everything here lives in the member's persistent data, so it is saved with them and needs nothing of the
 * member's own save.
 */
public final class MemberSurvival {
    private static final String THIRST = "HomininThirst";
    private static final String THIRST_CLOCK = "HomininThirstClock";
    private static final String BLEED_OUT_AT = "HomininBleedOutAt";
    private static final String BLEED_DRUNK = "HomininBleedDrunk";
    private static final String LAST_DRANK = "HomininLastDrank";
    private static final String ILL = "HomininIll";
    private static final String ILL_DRINK = "HomininIllDrink";
    private static final String ILL_MEAL = "HomininIllMeal";

    /** Below this they go looking for water; below the second they are parched and slow. */
    public static final int THIRSTY = 12;
    private static final int PARCHED = 6;
    /** The same clock as a bleeding-out player's: a minute, and twenty-four mouthfuls to live. */
    private static final int BLEED_OUT_TICKS = 60 * 20;
    private static final int WATER_TO_SURVIVE = 24;
    private static final long STEMMED_TICKS = 60L;
    private static final int LACERATION_TICKS = 24000;
    private static final int INFECTION_TICKS = 48000;
    /** Food illness, as for a player. */
    private static final int ILL_CAUGHT = 16;
    private static final int ILL_WORSE = 6;
    private static final int ILL_MOST = 24;
    private static final float COOKED_CHANCE = 0.3F;
    private static final long ILL_DRINK_GAP = 15 * 20L;
    private static final long ILL_MEAL_GAP = 20 * 20L;

    // ------------------------------------------------------------ thirst

    public static int thirst(BandMember member) {
        CompoundTag data = member.getPersistentData();
        return data.contains(THIRST) ? data.getInt(THIRST) : Thirst.MAX;
    }

    private static void setThirst(BandMember member, int value) {
        member.getPersistentData().putInt(THIRST, Math.max(0, Math.min(Thirst.MAX, value)));
    }

    public static boolean isThirsty(BandMember member) {
        return thirst(member) < THIRSTY;
    }

    /** Needs water right now, and badly: bleeding out, sick with bad meat, or parched. */
    public static boolean needsWaterNow(BandMember member) {
        return bleedingOut(member) || ill(member) || thirst(member) < PARCHED;
    }

    /** A mouthful: every drop of it goes on a bleed-out and on a sickness, as for a player. */
    public static void drink(BandMember member, int amount) {
        if (amount <= 0) {
            return;
        }
        setThirst(member, thirst(member) + amount);
        member.playSound(SoundEvents.GENERIC_DRINK, 0.6F, 0.9F + member.getRandom().nextFloat() * 0.2F);
        CompoundTag data = member.getPersistentData();
        long now = member.level().getGameTime();
        if (bleedingOut(member)) {
            data.putLong(LAST_DRANK, now);
            int drunk = data.getInt(BLEED_DRUNK) + amount;
            if (drunk >= WATER_TO_SURVIVE) {
                survive(member);
            } else {
                data.putInt(BLEED_DRUNK, drunk);
            }
        }
        if (ill(member) && now - data.getLong(ILL_DRINK) >= ILL_DRINK_GAP) {
            data.putLong(ILL_DRINK, now);
            recover(member, 2);
        }
    }

    /** How much each tick of life costs in water, in hundredths: heat and effort tell on them as on you. */
    private static int thirstCost(BandMember member) {
        int cost = 100;
        if (member.isSprinting() || member.getDeltaMovement().horizontalDistanceSqr() > 0.04D) {
            cost += 120;
        } else if (member.getDeltaMovement().horizontalDistanceSqr() > 0.0025D) {
            cost += 30;
        }
        Biome biome = member.level().getBiome(member.blockPosition()).value();
        float temperature = biome.getBaseTemperature();
        if (temperature >= 1.5F) {
            cost += 80;
        } else if (temperature >= 0.9F) {
            cost += 40;
        } else if (temperature < 0.3F) {
            cost -= 30;
        }
        if (member.level().isDay() && member.level().canSeeSky(member.blockPosition())) {
            cost += 20;
        }
        if (member.isInWaterOrRain()) {
            cost -= 40;
        }
        if (member.isPregnant()) {
            cost += 50;
        }
        return Math.max(20, cost);
    }

    // ------------------------------------------------------------ the catastrophic bleed

    public static boolean bleedingOut(BandMember member) {
        return member.getPersistentData().contains(BLEED_OUT_AT);
    }

    /** Drinking hard right now: the bleed holds while the water keeps going in. */
    public static boolean stemmed(LivingEntity entity) {
        if (!(entity instanceof BandMember member) || !bleedingOut(member)) {
            return false;
        }
        return member.level().getGameTime() - member.getPersistentData().getLong(LAST_DRANK) < STEMMED_TICKS;
    }

    private static void survive(BandMember member) {
        CompoundTag data = member.getPersistentData();
        data.remove(BLEED_OUT_AT);
        data.remove(BLEED_DRUNK);
        member.removeEffect(ModEffects.BLEEDING);
        Afflictions.afflict(member, Afflictions.Affliction.LACERATED, LACERATION_TICKS);
        Lines.say(member, "bleed_closed");
    }

    // ------------------------------------------------------------ food illness

    public static boolean ill(BandMember member) {
        return member.getPersistentData().getInt(ILL) > 0;
    }

    private static void recover(BandMember member, int points) {
        CompoundTag data = member.getPersistentData();
        int left = data.getInt(ILL) - points;
        if (left <= 0) {
            data.remove(ILL);
            member.removeEffect(ModEffects.FOOD_ILLNESS);
            member.removeEffect(MobEffects.HUNGER);
            Afflictions.relieve(member, Afflictions.Affliction.SICK);
        } else {
            data.putInt(ILL, left);
        }
    }

    private static void vomit(BandMember member) {
        member.setHunger(member.getHunger() - 3);
        setThirst(member, thirst(member) - 2);
        member.playSound(SoundEvents.PLAYER_BURP, 0.9F, 0.5F);
        if (member.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SNEEZE, member.getX(), member.getEyeY() - 0.2D, member.getZ(), 10,
                    0.15D, 0.1D, 0.15D, 0.02D);
        }
    }

    /**
     * Something eaten - called as the last bite goes down. Turned meat makes them ill; eaten while ill, small helps
     * and big comes back up; raw meat in a wound only just closed turns it bad; a shell of water is drunk.
     */
    public static void ate(BandMember member, ItemStack food) {
        if (food.is(ModItems.WATER_EGGSHELL.get())) {
            drink(member, Thirst.DRINK_FROM_SHELL);
            return;
        }
        CompoundTag data = member.getPersistentData();
        if (Spoilage.isSpoiled(food) && !(Cooking.isCooked(food) && member.getRandom().nextFloat() >= COOKED_CHANCE)) {
            int left = data.getInt(ILL);
            data.putInt(ILL, Math.min(ILL_MOST, left == 0 ? ILL_CAUGHT : left + ILL_WORSE));
            if (left == 0) {
                Lines.say(member, "food_ill");
            }
        } else if (ill(member)) {
            FoodProperties properties = food.get(DataComponents.FOOD);
            int nutrition = properties != null ? properties.nutrition() : 0;
            long now = member.level().getGameTime();
            if (nutrition >= 5) {
                vomit(member);
            } else if (nutrition > 0 && nutrition <= 3 && now - data.getLong(ILL_MEAL) >= ILL_MEAL_GAP) {
                data.putLong(ILL_MEAL, now);
                recover(member, 2);
            }
        }
        if (isRawMeat(food) && Afflictions.current(member) == Afflictions.Affliction.LACERATED) {
            Afflictions.afflict(member, Afflictions.Affliction.INFECTED, INFECTION_TICKS);
            Lines.say(member, "wound_bad");
        }
    }

    public static boolean isRawMeat(ItemStack stack) {
        return !Cooking.isCooked(stack) && (stack.is(ModItems.MEAT_CHUNK.get()) || stack.is(Items.BEEF)
                || stack.is(Items.PORKCHOP) || stack.is(Items.MUTTON) || stack.is(Items.CHICKEN)
                || stack.is(Items.RABBIT));
    }

    // ------------------------------------------------------------ eggs

    /** An egg drunk out through a hole from a sharpened stick: a little food, a little water, and the shell kept. */
    private static boolean drainEgg(BandMember member) {
        if (member.count(Items.EGG) <= 0 || member.count(ModItems.SHARPENED_STICK.get()) <= 0
                && member.count(ModItems.POINTY_STICK.get()) <= 0) {
            return false;
        }
        if (member.takeOneOf(Items.EGG).isEmpty()) {
            return false;
        }
        member.setHunger(member.getHunger() + 2);
        drink(member, 2);
        member.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        member.addToInventory(new ItemStack(ModItems.EMPTY_EGGSHELL.get()));
        return true;
    }

    /** A shell of water carried: drunk when there is nothing better to hand. */
    private static boolean drinkShell(BandMember member) {
        if (member.takeOneOf(ModItems.WATER_EGGSHELL.get()).isEmpty()) {
            return false;
        }
        drink(member, Thirst.DRINK_FROM_SHELL);
        member.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        member.addToInventory(new ItemStack(ModItems.EMPTY_EGGSHELL.get()));
        return true;
    }

    /** At water with empty shells: fill them, as a player dips theirs. */
    public static void fillShells(BandMember member) {
        int empty = member.count(ModItems.EMPTY_EGGSHELL.get());
        for (int i = 0; i < empty; i++) {
            if (!member.takeOneOf(ModItems.EMPTY_EGGSHELL.get()).isEmpty()) {
                member.addToInventory(new ItemStack(ModItems.WATER_EGGSHELL.get()));
            }
        }
        if (empty > 0) {
            member.playSound(SoundEvents.BUCKET_FILL, 0.6F, 1.4F);
        }
    }

    /** Eggs, shells to carry water in, and the things a player carries for fire and digging: worth picking up. */
    public static boolean wantsToCarry(BandMember member, ItemStack stack) {
        if (stack.is(ModItems.FIRE_DRILL.get()) || stack.is(ModItems.DIGGING_STICK.get())) {
            return member.count(stack.getItem()) < 1;
        }
        if (stack.is(ModItems.TORCH.get()) || stack.is(ModItems.LIT_TORCH.get())) {
            return member.count(ModItems.TORCH.get()) + member.count(ModItems.LIT_TORCH.get()) < 2;
        }
        if (stack.is(Items.EGG)) {
            return member.count(Items.EGG) < 4;
        }
        if (stack.is(ModItems.EMPTY_EGGSHELL.get()) || stack.is(ModItems.WATER_EGGSHELL.get())) {
            return member.count(ModItems.EMPTY_EGGSHELL.get()) + member.count(ModItems.WATER_EGGSHELL.get()) < 3;
        }
        return false;
    }

    // ------------------------------------------------------------ torches

    private static final String LIGHT_AT = "HomininTorchLight";
    private static final String LAST_THROW = "HomininTorchThrown";
    private static final int LIGHT_LEVEL = 14;

    /** Night, and awake: a burning torch is worth having in hand. */
    public static boolean wantsTorchLight(BandMember member) {
        return !member.level().isDay() && !member.isSleeping();
    }

    private static boolean holdsLit(BandMember member) {
        return member.getOffhandItem().is(ModItems.LIT_TORCH.get()) || member.getMainHandItem().is(ModItems.LIT_TORCH.get());
    }

    /** The light that follows a held torch goes out: when it is put away, or they die, or leave. */
    public static void douse(BandMember member) {
        CompoundTag data = member.getPersistentData();
        if (data.contains(LIGHT_AT) && member.level() instanceof ServerLevel level) {
            BlockPos old = BlockPos.of(data.getLong(LIGHT_AT));
            if (level.isLoaded(old) && level.getBlockState(old).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                level.setBlock(old, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        data.remove(LIGHT_AT);
    }

    /**
     * Torches, carried as a player carries them: lit at a fire after dark, held where they light the ground, burning
     * down in the hand (faster in rain, out in water), stood in the ground by where they sleep - and thrown at
     * anything that hunts, which runs from fire.
     */
    private static void tickTorches(BandMember member, ServerLevel level) {
        // The light follows the torch, every other tick.
        if (member.tickCount % 2 == 0) {
            BlockPos want = holdsLit(member) ? lightSpot(member, level) : null;
            CompoundTag data = member.getPersistentData();
            BlockPos old = data.contains(LIGHT_AT) ? BlockPos.of(data.getLong(LIGHT_AT)) : null;
            if (want == null || !want.equals(old)) {
                douse(member);
                if (want != null) {
                    level.setBlock(want, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, LIGHT_LEVEL), 3);
                    data.putLong(LIGHT_AT, want.asLong());
                }
            }
        }
        // Burning down, wherever it is carried.
        if (member.tickCount % dev.hominin.evolution.item.LitTorchItem.BURN_EVERY == 0) {
            burn(member, member.getOffhandItem(), true);
            burn(member, member.getMainHandItem(), false);
            for (int slot = 0; slot < member.getInventory().getContainerSize(); slot++) {
                ItemStack stack = member.getInventory().getItem(slot);
                if (stack.is(ModItems.LIT_TORCH.get())) {
                    burn(member, stack, false);
                }
            }
        }
        if ((member.tickCount + member.getId()) % 10 != 3) {
            return;
        }
        // Thrown at something that hunts, close and coming.
        LivingEntity threat = member.getTarget() != null ? member.getTarget() : member.getLastHurtByMob();
        if (threat != null && threat.isAlive() && threat.getType().is(dev.hominin.evolution.ModTags.EntityTypes.PREDATORS)
                && member.distanceToSqr(threat) < 12.0D * 12.0D && member.distanceToSqr(threat) > 2.0D * 2.0D
                && level.getGameTime() - member.getPersistentData().getLong(LAST_THROW) > 60L) {
            ItemStack torch = member.findCarried(stack -> stack.is(ModItems.LIT_TORCH.get()));
            if (torch == null && member.getOffhandItem().is(ModItems.LIT_TORCH.get())) {
                torch = member.getOffhandItem();
            }
            if (torch != null) {
                throwAt(member, level, torch, threat);
                return;
            }
        }
        if (!wantsTorchLight(member) && !member.isSleeping()) {
            return;
        }
        // Lit off a fire close by, after dark.
        if (!holdsLit(member) && member.count(ModItems.LIT_TORCH.get()) == 0 && member.count(ModItems.TORCH.get()) > 0
                && fireWithin(level, member.blockPosition(), 3)) {
            member.takeOneOf(ModItems.TORCH.get());
            member.addToInventory(new ItemStack(ModItems.LIT_TORCH.get()));
            level.playSound(null, member.blockPosition(), SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.NEUTRAL,
                    0.6F, 1.4F);
            member.updateHands();
        }
        // Lying down for the night: stood in the ground beside them, to burn there.
        if (member.isSleeping() && holdsLit(member)) {
            setDown(member, level);
        }
    }

    private static void burn(BandMember member, ItemStack stack, boolean offhand) {
        if (!stack.is(ModItems.LIT_TORCH.get())) {
            return;
        }
        if (member.isInWater()) {
            stack.shrink(1);
            member.addToInventory(new ItemStack(ModItems.TORCH.get()));
            member.playSound(SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.2F);
            return;
        }
        int burn = member.isInWaterOrRain() ? 3 : 1;
        if (stack.getDamageValue() + burn >= stack.getMaxDamage()) {
            stack.shrink(1);
            member.playSound(SoundEvents.FIRE_EXTINGUISH, 0.4F, 1.0F);
            return;
        }
        stack.setDamageValue(stack.getDamageValue() + burn);
    }

    /** Where the light can go: at the head, else the feet - only ever into empty air. */
    @javax.annotation.Nullable
    private static BlockPos lightSpot(BandMember member, ServerLevel level) {
        BlockPos head = BlockPos.containing(member.getEyePosition());
        CompoundTag data = member.getPersistentData();
        BlockPos old = data.contains(LIGHT_AT) ? BlockPos.of(data.getLong(LIGHT_AT)) : null;
        for (BlockPos pos : new BlockPos[] {head, member.blockPosition(), head.above()}) {
            var state = level.getBlockState(pos);
            if (state.isAir() || pos.equals(old) && state.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean fireWithin(ServerLevel level, BlockPos around, int radius) {
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-radius, -2, -radius), around.offset(radius, 2, radius))) {
            if (dev.hominin.evolution.item.TorchItem.isFireSource(level.getBlockState(pos))) {
                return true;
            }
        }
        return false;
    }

    private static void throwAt(BandMember member, ServerLevel level, ItemStack torch, LivingEntity threat) {
        member.getPersistentData().putLong(LAST_THROW, level.getGameTime());
        var thrown = new dev.hominin.evolution.entity.ThrownTorch(level, member);
        thrown.setItem(torch.copyWithCount(1));
        double dx = threat.getX() - member.getX();
        double dz = threat.getZ() - member.getZ();
        double dy = threat.getY(0.4D) - thrown.getY();
        thrown.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.15D, dz, 1.1F, 4.0F);
        level.addFreshEntity(thrown);
        torch.shrink(1);
        member.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        level.playSound(null, member.blockPosition(), SoundEvents.SNOWBALL_THROW, net.minecraft.sounds.SoundSource.NEUTRAL,
                0.6F, 0.5F);
        Lines.say(member, "torch_thrown");
    }

    /** A burning torch stood in the ground beside a sleeper, where it burns on for twice what it had left. */
    private static void setDown(BandMember member, ServerLevel level) {
        ItemStack torch = member.getOffhandItem().is(ModItems.LIT_TORCH.get()) ? member.getOffhandItem()
                : member.getMainHandItem();
        var state = dev.hominin.evolution.ModBlocks.PLACED_TORCH.get().defaultBlockState();
        BlockPos centre = member.blockPosition();
        for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= 2; step++) {
                BlockPos at = centre.relative(side, step);
                if (level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty()
                        && state.canSurvive(level, at)) {
                    int left = torch.getMaxDamage() - torch.getDamageValue();
                    level.setBlock(at, state, 11);
                    level.scheduleTick(at, state.getBlock(),
                            Math.max(400, left * dev.hominin.evolution.item.LitTorchItem.BURN_EVERY * 2));
                    level.playSound(null, at, SoundEvents.WOOD_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.0F);
                    torch.shrink(1);
                    douse(member);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------ every tick

    public static void tick(BandMember member) {
        if (member.level().isClientSide() || !member.isAlive()) {
            return;
        }
        dev.hominin.evolution.survival.Hearths.tickMember(member);
        if (member.level() instanceof ServerLevel level) {
            tickTorches(member, level);
        }
        if (member.isBaby()) {
            // Children are nursed and carried: their minder drinks for them.
            return;
        }
        CompoundTag data = member.getPersistentData();
        // Thirst runs down on the same clock a player's does.
        int clock = data.getInt(THIRST_CLOCK) + thirstCost(member);
        if (clock >= Thirst.ticksPerPoint() * 100) {
            clock = 0;
            setThirst(member, thirst(member) - 1);
        }
        data.putInt(THIRST_CLOCK, clock);
        // A body opened up: the clock starts the moment the wound does.
        MobEffectInstance bleeding = member.getEffect(ModEffects.BLEEDING);
        long now = member.level().getGameTime();
        if (bleeding != null && bleeding.getAmplifier() >= Bleeding.Tier.CATASTROPHIC.ordinal() && !bleedingOut(member)) {
            data.putLong(BLEED_OUT_AT, now + BLEED_OUT_TICKS);
            data.putInt(BLEED_DRUNK, 0);
            Lines.say(member, "bleeding_out");
        }
        if (bleedingOut(member)) {
            if (stemmed(member)) {
                data.putLong(BLEED_OUT_AT, data.getLong(BLEED_OUT_AT) + 1);
            } else if (now >= data.getLong(BLEED_OUT_AT)) {
                data.remove(BLEED_OUT_AT);
                data.remove(BLEED_DRUNK);
                member.hurt(member.damageSources().source(Bleeding.BLED_OUT), Float.MAX_VALUE);
                return;
            }
        }
        if ((member.tickCount + member.getId()) % 20 != 7) {
            return;
        }
        // Once a second: how thirst, and sickness, sit on them.
        int thirst = thirst(member);
        if (thirst <= 0) {
            member.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
            member.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
            if (member.tickCount % 120 < 20 && member.getHealth() > 2.0F) {
                member.hurt(member.damageSources().dryOut(), 1.0F);
            }
        } else if (thirst < PARCHED) {
            member.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
        }
        if (ill(member)) {
            member.addEffect(new MobEffectInstance(ModEffects.FOOD_ILLNESS, 60, 0, false, false, true));
            member.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, false));
            Afflictions.afflict(member, Afflictions.Affliction.SICK, 100);
            if (member.tickCount % (20 * 25) < 20) {
                setThirst(member, thirst(member) - 1);
            }
            if (member.getRandom().nextInt(50) == 0) {
                vomit(member);
            }
            if (member.tickCount % (20 * 90) < 20) {
                recover(member, 1);
            }
        }
        // Thirsty with a shell of water, or an egg and something to pierce it: drink it where they stand. Bleeding
        // out, anything wet goes down at once.
        if (thirst < THIRSTY || bleedingOut(member)) {
            if (!drinkShell(member) && (thirst < THIRSTY || member.isHungry())) {
                drainEgg(member);
            }
        } else if (member.isHungry() && member.getRandom().nextInt(4) == 0) {
            drainEgg(member);
        }
    }

    private MemberSurvival() {
    }
}
