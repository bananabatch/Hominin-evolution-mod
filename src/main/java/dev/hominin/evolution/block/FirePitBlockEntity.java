package dev.hominin.evolution.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlockEntities;
import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What is in a fire pit, and whether it is burning.
 *
 * <p>Unlit, it holds what you have put in it - sticks, branches, thatch - and, after a fire, what is left
 * of it: embers that stay warm for a day, then cold ash. A spark needs something to take it (thatch, or
 * warm embers) and enough to feed on (three things at least), and even then a drill does not always win:
 * the more thatch and the warmer the bed, the likelier. Lit, all of it is fire; whatever is added burns on
 * top, up to half an hour banked. Meat laid on it cooks.
 */
public class FirePitBlockEntity extends BlockEntity {
    /** At least this many things in it, or there is nothing for a fire to live on. */
    public static final int LEAST_TO_CATCH = 3;
    private static final int MOST_HELD = 24;
    private static final int MOST_BANKED = 36000;
    /** How long embers stay warm enough to help a new fire catch. */
    private static final int EMBERS_TICKS = 24000;
    private static final int COOKING_SLOTS = 4;

    /** What a fire pit burns, and how long each keeps it going. */
    public enum Fuel {
        STICK("stick", "sticks", 1200), BRANCH("branch", "branches", 3000), THATCH("thatch", "thatch", 600);

        final String one;
        final String many;
        public final int ticks;

        Fuel(String one, String many, int ticks) {
            this.one = one;
            this.many = many;
            this.ticks = ticks;
        }

        String count(int n) {
            return n + " " + (n == 1 ? one : many);
        }
    }

    private final int[] held = new int[Fuel.values().length];
    /** Thatch thrown on a burning fire smokes heavily for a while. */
    private long smokyUntil;
    private long burnsOutAt;
    private long embersUntil;
    private final NonNullList<ItemStack> cooking = NonNullList.withSize(COOKING_SLOTS, ItemStack.EMPTY);
    private final int[] cookingProgress = new int[COOKING_SLOTS];
    private final int[] cookingTime = new int[COOKING_SLOTS];

    public FirePitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIRE_PIT.get(), pos, state);
    }

    @Nullable
    public static Fuel fuelOf(ItemStack stack) {
        if (stack.is(Items.STICK) || stack.is(ModItems.SHARPENED_STICK.get()) || stack.is(ModItems.POINTY_STICK.get())
                || stack.is(ModItems.TERMITE_STICK.get())) {
            return Fuel.STICK;
        }
        if (ModItems.isLongBranch(stack) || stack.is(ModItems.BUILDING_BRANCH.get())) {
            return Fuel.BRANCH;
        }
        if (stack.is(ModItems.THATCH.get()) || stack.is(ModItems.NESTING_MATERIAL.get())) {
            return Fuel.THATCH;
        }
        return null;
    }

    /** Whether a click with this does anything here - so the client can swing an arm without guessing. */
    public static boolean handles(ItemStack stack, BlockState state) {
        // Not the drill: that is held to the pit for three seconds (FireDrillItem), and comes back here when done.
        return fuelOf(stack) != null || stack.is(ModItems.TORCH.get())
                || stack.is(ModItems.LIT_TORCH.get()) || (state.getValue(FirePitBlock.LIT) && !stack.isEmpty());
    }

    private boolean lit() {
        return getBlockState().getValue(FirePitBlock.LIT);
    }

    private int pieces() {
        int total = 0;
        for (int n : held) {
            total += n;
        }
        return total;
    }

    private boolean embersWarm() {
        return level != null && embersUntil > level.getGameTime();
    }

    private boolean ashes() {
        return getBlockState().getValue(FirePitBlock.ASHES);
    }

    // ------------------------------------------------------------ what a click does

    /** A right-click with something in hand. Returns true when it did something. */
    public boolean use(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        if (stack.is(ModItems.TORCH.get())) {
            if (lit()) {
                dev.hominin.evolution.item.TorchItem.lightOne(player, hand, stack);
            } else {
                say(player, "There is nothing burning here to light it from.");
            }
            return true;
        }
        if (stack.is(ModItems.LIT_TORCH.get()) && !lit()) {
            // A flame, not a spark: it catches far more easily, if there is anything to catch.
            tryToLight(player, 0.4F, "The torch");
            return true;
        }
        Fuel fuel = fuelOf(stack);
        if (fuel != null) {
            feed(player, stack, fuel);
            return true;
        }
        if (lit() && !stack.isEmpty()) {
            return cook(player, stack);
        }
        return false;
    }

    private void feed(ServerPlayer player, ItemStack stack, Fuel fuel) {
        long now = level.getGameTime();
        int count = player.isShiftKeyDown() ? Math.min(8, stack.getCount()) : 1;
        if (lit()) {
            long until = Math.min(Math.max(now, burnsOutAt) + (long) fuel.ticks * count, now + MOST_BANKED);
            burnsOutAt = until;
            if (fuel == Fuel.THATCH) {
                smokyUntil = Math.max(now, smokyUntil) + 200L * count;
            }
            take(player, stack, count);
            level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
            say(player, "The fire takes it. About " + Math.max(1, (until - now) / 1200) + " minutes of fire left.");
            changed();
            return;
        }
        count = Math.min(count, MOST_HELD - pieces());
        if (count <= 0) {
            say(player, "It is full. Light it.");
            return;
        }
        held[fuel.ordinal()] += count;
        take(player, stack, count);
        level.playSound(null, worldPosition, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.7F, 1.1F);
        setState(false, true, ashes());
        say(player, "In the pit: " + contents() + ". " + verdict());
    }

    private static void take(ServerPlayer player, ItemStack stack, int count) {
        if (!player.getAbilities().instabuild) {
            stack.shrink(count);
        }
    }

    /** Empty-handed: what is in it - and sneaking, the last thing put in comes back out. */
    public void emptyHanded(ServerPlayer player) {
        if (lit()) {
            long left = Math.max(0L, burnsOutAt - level.getGameTime());
            int cooking = (int) this.cooking.stream().filter(s -> !s.isEmpty()).count();
            say(player, "The fire pit is burning - about " + Math.max(1, left / 1200) + " minutes left on what is in it."
                    + (cooking > 0 ? " " + cooking + (cooking == 1 ? " piece" : " pieces") + " cooking." : "")
                    + " Feed it sticks, branches or thatch.");
            return;
        }
        if (player.isShiftKeyDown() && pieces() > 0) {
            for (int i = held.length - 1; i >= 0; i--) {
                if (held[i] > 0) {
                    held[i]--;
                    ItemStack back = new ItemStack(switch (Fuel.values()[i]) {
                        case STICK -> Items.STICK;
                        case BRANCH -> ModItems.LONG_BRANCH.get();
                        case THATCH -> ModItems.THATCH.get();
                    });
                    if (!player.getInventory().add(back)) {
                        player.drop(back, false);
                    }
                    break;
                }
            }
            setState(false, pieces() > 0, ashes());
        }
        say(player, pieces() == 0 && !ashes() ? "The fire pit is empty. Put thatch, sticks or branches in it."
                : "In the pit: " + contents() + ". " + verdict());
    }

    /** "3 sticks, 2 thatch and warm embers." */
    public String contents() {
        List<String> parts = new ArrayList<>();
        for (Fuel fuel : Fuel.values()) {
            if (held[fuel.ordinal()] > 0) {
                parts.add(fuel.count(held[fuel.ordinal()]));
            }
        }
        if (embersWarm()) {
            parts.add("warm embers");
        } else if (ashes()) {
            parts.add("cold ash");
        }
        if (parts.isEmpty()) {
            return "nothing";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    /** Whether a drill would light it now - and if not, why not. */
    private String verdict() {
        String why = whyNot();
        if (why != null) {
            return why;
        }
        return "A drill should catch it - about " + Math.round(chance(null, 0.0F) * 100.0F) + "% a try.";
    }

    @Nullable
    private String whyNot() {
        if (pieces() < LEAST_TO_CATCH) {
            return "Too little to catch - it needs " + LEAST_TO_CATCH + " things in it at least.";
        }
        if (held[Fuel.THATCH.ordinal()] == 0 && !embersWarm()) {
            return "Nothing in it to take the spark - add thatch, or light it on warm embers.";
        }
        return null;
    }

    private float chance(@Nullable ServerPlayer player, float bonus) {
        float chance = 0.3F + bonus + 0.1F * Math.min(3, held[Fuel.THATCH.ordinal()])
                + 0.04F * Math.min(5, held[Fuel.STICK.ordinal()]) + 0.03F * Math.min(3, held[Fuel.BRANCH.ordinal()]);
        if (embersWarm()) {
            chance += 0.3F;
        } else if (ashes()) {
            chance += 0.08F;
        }
        if (player != null && dev.hominin.evolution.mind.Skills.knows(player,
                dev.hominin.evolution.mind.Skills.Skill.FIRE)) {
            chance += 0.1F;
        }
        if (level != null && level.isRainingAt(worldPosition.above())) {
            chance *= 0.25F;
        }
        return Math.max(0.05F, Math.min(0.95F, chance));
    }

    // ------------------------------------------------------------ lighting

    /** Three seconds of the drill against it: one attempt at lighting it. */
    public void drillWith(ServerPlayer player, ItemStack drill) {
        drill(player, drill);
    }

    private void drill(ServerPlayer player, ItemStack drill) {
        if (dev.hominin.evolution.band.Species.neverMakesFire(
                player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            say(player, "Your kind never learned to make fire. Carry it here from one that is burning - a torch lit "
                    + "at a natural fire will catch it.");
            return;
        }
        if (lit()) {
            say(player, "It is already burning.");
            return;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        if (tryToLight(player, 0.0F, null)) {
            // Somebody who has done this before does not wreck the drill doing it.
            boolean skilled = dev.hominin.evolution.mind.Skills.knows(player, dev.hominin.evolution.mind.Skills.Skill.FIRE);
            if ((!skilled || player.getRandom().nextBoolean()) && !player.getAbilities().instabuild) {
                drill.shrink(1);
            }
            dev.hominin.evolution.mind.Skills.learn(player, dev.hominin.evolution.mind.Skills.Skill.FIRE);
            if (dev.hominin.evolution.EvolutionManager.isReadyForMilestone(player,
                    dev.hominin.evolution.stage.BuiltinMilestones.FIRE_TRANSFER)) {
                dev.hominin.evolution.EvolutionManager.attemptMilestone(player,
                        dev.hominin.evolution.stage.BuiltinMilestones.FIRE_TRANSFER);
            }
        } else if (player.getRandom().nextFloat() < 0.15F && !player.getAbilities().instabuild) {
            drill.shrink(1);
            say(player, "The drill wears through. Make another.");
        }
    }

    /** One attempt at lighting it: false when there was too little, or it did not catch this time. */
    private boolean tryToLight(ServerPlayer player, float bonus, @Nullable String with) {
        String why = whyNot();
        if (why != null) {
            say(player, "In the pit: " + contents() + ". " + why);
            return false;
        }
        float chance = chance(player, bonus);
        if (player.getRandom().nextFloat() >= chance) {
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.6F);
            ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.3D, worldPosition.getZ() + 0.5D, 6, 0.15D, 0.05D, 0.15D, 0.01D);
            say(player, (with == null ? "It smokes" : with + " smokes it") + ", but it will not catch. Try again. (About "
                    + Math.round(chance * 100.0F) + "% a try - thatch and warm embers help.)");
            return false;
        }
        light(player);
        return true;
    }

    private void light(ServerPlayer player) {
        long now = level.getGameTime();
        long burn = 0L;
        for (Fuel fuel : Fuel.values()) {
            burn += (long) fuel.ticks * held[fuel.ordinal()];
            held[fuel.ordinal()] = 0;
        }
        burnsOutAt = now + Math.min(burn, MOST_BANKED);
        embersUntil = 0L;
        setState(true, false, false);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
        dev.hominin.evolution.survival.Hearths.pitLit((ServerLevel) level, worldPosition);
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.TORCH);
        player.sendSystemMessage(Component.literal("The smoke thickens, and it catches. The fire pit burns - about "
                + Math.max(1, burn / 1200) + " minutes on what was in it. Feed it and it will keep.")
                .withStyle(ChatFormatting.GOLD));
    }

    private void goOut() {
        burnsOutAt = 0L;
        embersUntil = level.getGameTime() + EMBERS_TICKS;
        setState(false, pieces() > 0, true);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
        dev.hominin.evolution.survival.Hearths.pitOut((ServerLevel) level, worldPosition);
        for (ServerPlayer player : ((ServerLevel) level).players()) {
            if (player.blockPosition().closerThan(worldPosition, 24.0D)) {
                player.displayClientMessage(Component.literal("The fire pit burns down to embers. They will stay warm a "
                        + "day - relight it on them and it catches easily.").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    // ------------------------------------------------------------ cooking

    private boolean cook(ServerPlayer player, ItemStack stack) {
        Optional<RecipeHolder<CampfireCookingRecipe>> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack), level);
        if (recipe.isEmpty()) {
            return false;
        }
        for (int i = 0; i < COOKING_SLOTS; i++) {
            if (cooking.get(i).isEmpty()) {
                cooking.set(i, stack.copyWithCount(1));
                cookingTime[i] = recipe.get().value().getCookingTime();
                cookingProgress[i] = 0;
                take(player, stack, 1);
                level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.4F);
                changed();
                return true;
            }
        }
        say(player, "There is no room left over the fire.");
        return true;
    }

    public NonNullList<ItemStack> cooking() {
        return cooking;
    }

    // ------------------------------------------------------------ kept by the band

    public boolean isLit() {
        return lit();
    }

    /** Ticks of fire left on a burning pit; zero on a cold one. */
    public long ticksLeft() {
        return lit() && level != null ? Math.max(0L, burnsOutAt - level.getGameTime()) : 0L;
    }

    public int fuelPieces() {
        return pieces();
    }

    /** One of the band feeds it: more time on a burning fire, more to catch on a cold one. */
    public void stoke(@Nullable Fuel fuel, int count) {
        if (level == null || fuel == null || count <= 0) {
            return;
        }
        long now = level.getGameTime();
        if (lit()) {
            burnsOutAt = Math.min(Math.max(now, burnsOutAt) + (long) fuel.ticks * count, now + MOST_BANKED);
            if (fuel == Fuel.THATCH) {
                smokyUntil = Math.max(now, smokyUntil) + 200L * count;
            }
            level.playSound(null, worldPosition, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            held[fuel.ordinal()] += Math.max(0, Math.min(count, MOST_HELD - pieces()));
            setState(false, true, ashes());
            level.playSound(null, worldPosition, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.7F, 1.1F);
        }
        changed();
    }

    /** Lit by one of the band who knows how - if there is enough in it to catch. */
    public boolean kindle() {
        if (level == null || lit() || pieces() < LEAST_TO_CATCH) {
            return false;
        }
        long now = level.getGameTime();
        long burn = 0L;
        for (Fuel fuel : Fuel.values()) {
            burn += (long) fuel.ticks * held[fuel.ordinal()];
            held[fuel.ordinal()] = 0;
        }
        burnsOutAt = now + Math.min(burn, MOST_BANKED);
        embersUntil = 0L;
        setState(true, false, false);
        level.playSound(null, worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
        dev.hominin.evolution.survival.Hearths.pitLit((ServerLevel) level, worldPosition);
        changed();
        return true;
    }

    // ------------------------------------------------------------ over time

    /**
     * The smoke. Ticked with the block entity rather than drawn as the block's ambient effects, which only run within
     * a few dozen blocks of you: a column of smoke over a camp is seen from as far as the world is drawn - the way you
     * find other people's fires.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, FirePitBlockEntity pit) {
        if (state.getValue(FirePitBlock.LIT) && level.random.nextFloat() < 0.11F) {
            for (int i = 0; i < level.random.nextInt(2) + 2; i++) {
                net.minecraft.world.level.block.CampfireBlock.makeParticles(level, pos, false, false);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FirePitBlockEntity pit) {
        if (!state.getValue(FirePitBlock.LIT)) {
            return;
        }
        long now = level.getGameTime();
        // Rain on an open fire eats it eight times as fast - and a real downpour can put it out altogether.
        if (now % 20L == 0L && level.isRainingAt(pos.above())) {
            pit.burnsOutAt -= 140L;
            if (level.random.nextFloat() < (level.isThundering() ? 0.02F : 0.006F)) {
                pit.burnsOutAt = now;
            }
        }
        if (now >= pit.burnsOutAt) {
            pit.goOut();
            return;
        }
        if (now < pit.smokyUntil && now % 6L == 0L && level instanceof ServerLevel server) {
            // Green grass on a fire: a column of white smoke.
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5D,
                    pos.getY() + 0.5D, pos.getZ() + 0.5D, 1, 0.15D, 0.05D, 0.15D, 0.01D);
        }
        boolean changed = false;
        for (int i = 0; i < COOKING_SLOTS; i++) {
            ItemStack raw = pit.cooking.get(i);
            if (raw.isEmpty() || ++pit.cookingProgress[i] < pit.cookingTime[i]) {
                continue;
            }
            SingleRecipeInput input = new SingleRecipeInput(raw);
            ItemStack done = level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level)
                    .map(r -> r.value().assemble(input, level.registryAccess())).orElse(raw);
            dev.hominin.evolution.food.Spoilage.carry(raw, done);
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, done);
            pit.cooking.set(i, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) {
            pit.changed();
        }
        if (now % 1200L == 0L && level instanceof ServerLevel server) {
            dev.hominin.evolution.band.Presence.fireKept(server, pos);
        }
    }

    /** Broken up: whatever was in it, and anything cooking, is left on the ground. */
    public void spill() {
        if (level == null) {
            return;
        }
        Containers.dropContents(level, worldPosition, cooking);
        for (Fuel fuel : Fuel.values()) {
            int n = held[fuel.ordinal()];
            if (n > 0) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.3D,
                        worldPosition.getZ() + 0.5D, new ItemStack(switch (fuel) {
                            case STICK -> Items.STICK;
                            case BRANCH -> ModItems.LONG_BRANCH.get();
                            case THATCH -> ModItems.THATCH.get();
                        }, n));
            }
        }
        if (level instanceof ServerLevel server) {
            dev.hominin.evolution.survival.Hearths.pitOut(server, worldPosition);
        }
    }

    // ------------------------------------------------------------ state and saving

    private void setState(boolean lit, boolean fuelled, boolean ashes) {
        BlockState state = getBlockState().setValue(FirePitBlock.LIT, lit).setValue(FirePitBlock.FUELLED, fuelled)
                .setValue(FirePitBlock.ASHES, ashes).setValue(FirePitBlock.THATCH, !lit && held[Fuel.THATCH.ordinal()] > 0);
        level.setBlock(worldPosition, state, 3);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static void say(ServerPlayer player, String line) {
        player.displayClientMessage(Component.literal(line), true);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putIntArray("Held", held);
        tag.putLong("BurnsOutAt", burnsOutAt);
        tag.putLong("EmbersUntil", embersUntil);
        ContainerHelper.saveAllItems(tag, cooking, true, registries);
        tag.putIntArray("CookingProgress", cookingProgress);
        tag.putIntArray("CookingTime", cookingTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] saved = tag.getIntArray("Held");
        for (int i = 0; i < held.length; i++) {
            held[i] = i < saved.length ? saved[i] : 0;
        }
        burnsOutAt = tag.getLong("BurnsOutAt");
        embersUntil = tag.getLong("EmbersUntil");
        for (int i = 0; i < COOKING_SLOTS; i++) {
            cooking.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, cooking, registries);
        int[] progress = tag.getIntArray("CookingProgress");
        int[] time = tag.getIntArray("CookingTime");
        for (int i = 0; i < COOKING_SLOTS; i++) {
            cookingProgress[i] = i < progress.length ? progress[i] : 0;
            cookingTime[i] = i < time.length ? time[i] : 600;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, cooking, true, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
