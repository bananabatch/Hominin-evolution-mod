package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * River gravel, picked through for stone. Sneak-use a gravel block and you sift it: chert, often; now and then a piece
 * of <b>fine chert</b> - glassier than any seam gives, and found nowhere else; rarely obsidian that washed a long way;
 * and once in a long while a whole chunk of it. Each block of gravel is worth two goes, and then it is only gravel.
 * Your band picks through it too.
 */
public final class Gravel extends SavedData {
    private static final String NAME = "hominin_evolution_gravel";
    public static final int SEARCHES = 2;
    private static final long GAP_TICKS = 16L;

    /** Searches made, by block. */
    private final Map<Long, Integer> searched = new HashMap<>();
    private static final Map<UUID, Long> last = new HashMap<>();

    private static Gravel of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Gravel::new, Gravel::load), NAME);
    }

    private static Gravel load(CompoundTag tag, HolderLookup.Provider registries) {
        Gravel data = new Gravel();
        long[] at = tag.getLongArray("At");
        int[] times = tag.getIntArray("Times");
        for (int i = 0; i < Math.min(at.length, times.length); i++) {
            data.searched.put(at[i], times[i]);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] at = new long[searched.size()];
        int[] times = new int[searched.size()];
        int i = 0;
        for (var entry : searched.entrySet()) {
            at[i] = entry.getKey();
            times[i++] = entry.getValue();
        }
        tag.putLongArray("At", at);
        tag.putIntArray("Times", times);
        return tag;
    }

    public static boolean isGravel(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.GRAVEL);
    }

    /** Whether there is anything left to sift here. */
    public static boolean worthSearching(ServerLevel level, BlockPos pos) {
        return isGravel(level, pos) && of(level).searched.getOrDefault(pos.asLong(), 0) < SEARCHES;
    }

    /**
     * One go at a block of gravel: what comes out of it, or empty. Chert 40%, fine chert 12%, obsidian 5%, a chunk of
     * obsidian one time in a hundred and twenty; the rest is just gravel.
     */
    public static ItemStack sift(ServerLevel level, BlockPos pos, RandomSource random) {
        Gravel data = of(level);
        data.searched.merge(pos.asLong(), 1, Integer::sum);
        data.setDirty();
        level.playSound(null, pos, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 1.0F, 0.9F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState()),
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 10, 0.3D, 0.1D, 0.3D, 0.05D);
        float roll = random.nextFloat();
        if (roll < 1.0F / 120.0F) {
            return new ItemStack(ModItems.OBSIDIAN_CHUNK.get());
        }
        if (roll < 0.058F) {
            return new ItemStack(ModItems.OBSIDIAN_ROCK.get());
        }
        if (roll < 0.178F) {
            return new ItemStack(ModItems.FINE_CHERT_ROCK.get());
        }
        if (roll < 0.578F) {
            return new ItemStack(ModItems.CHERT_ROCK.get());
        }
        return ItemStack.EMPTY;
    }

    /** Sneak-use at gravel. Returns true if that is what the click did. */
    public static boolean search(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!isGravel(level, pos)) {
            return false;
        }
        long now = level.getGameTime();
        if (now - last.getOrDefault(player.getUUID(), -GAP_TICKS) < GAP_TICKS) {
            return true;
        }
        last.put(player.getUUID(), now);
        if (!worthSearching(level, pos)) {
            player.displayClientMessage(Component.literal("This gravel has been picked over. Try the next patch."), true);
            return true;
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        ItemStack found = sift(level, pos, player.getRandom());
        int left = SEARCHES - of(level).searched.getOrDefault(pos.asLong(), 0);
        if (found.isEmpty()) {
            player.displayClientMessage(Component.literal("You sift through it: nothing but gravel."
                    + (left > 0 ? "" : " That is all this patch has.")), true);
            return true;
        }
        String what = found.is(ModItems.OBSIDIAN_CHUNK.get()) ? "a whole chunk of obsidian!"
                : found.is(ModItems.OBSIDIAN_ROCK.get()) ? "a piece of obsidian, washed a long way."
                : found.is(ModItems.FINE_CHERT_ROCK.get()) ? "fine chert - glassier than any seam gives."
                : "a piece of chert.";
        if (!player.getInventory().add(found)) {
            player.drop(found, false);
        }
        player.displayClientMessage(Component.literal("You sift through the gravel: " + what)
                .withStyle(found.is(ModItems.CHERT_ROCK.get()) ? ChatFormatting.GRAY : ChatFormatting.GOLD), true);
        return true;
    }

    public static void forget(UUID player) {
        last.remove(player);
    }

    private Gravel() {
    }
}
