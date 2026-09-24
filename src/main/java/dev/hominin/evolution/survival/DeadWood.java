package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;

import dev.hominin.evolution.ModBlocks;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Cracking a dead trunk for grubs. A decaying log pulled open by hand has beetle larvae in the
 * soft wood - up to three - and a little under half the time it comes apart into a hollow log. A dead
 * tree only has so much in it: two logs' worth within a few blocks of each other, branches and all,
 * and then it has given everything it had.
 */
public final class DeadWood extends SavedData {
    private static final String NAME = "hominin_evolution_dead_wood";
    /** Logs worth cracking in one dead tree - counted over everything within a few blocks, not per trunk. */
    private static final int PER_TREE = 2;
    private static final int TREE_REACH = 5;
    /** How often a cracked log comes apart into a hollow one; otherwise it stays as it was. */
    private static final float HOLLOW_CHANCE = 0.45F;
    private static final float EMPTY_CHANCE = 0.2F;

    /** Every log cracked, by position, and how many times (old saves counted per trunk foot). */
    private final Map<Long, Integer> cracked = new HashMap<>();

    private static DeadWood of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DeadWood::new, DeadWood::load), NAME);
    }

    private static DeadWood load(CompoundTag tag, HolderLookup.Provider registries) {
        DeadWood data = new DeadWood();
        long[] trees = tag.getLongArray("Trees");
        int[] counts = tag.getIntArray("Counts");
        for (int i = 0; i < Math.min(trees.length, counts.length); i++) {
            data.cracked.put(trees[i], counts[i]);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] trees = new long[cracked.size()];
        int[] counts = new int[cracked.size()];
        int i = 0;
        for (var entry : cracked.entrySet()) {
            trees[i] = entry.getKey();
            counts[i++] = entry.getValue();
        }
        tag.putLongArray("Trees", trees);
        tag.putIntArray("Counts", counts);
        return tag;
    }

    /** How many logs have been cracked within a few blocks of this one: the whole tree, branches and all. */
    private int crackedNear(BlockPos pos) {
        int count = 0;
        for (var entry : cracked.entrySet()) {
            BlockPos at = BlockPos.of(entry.getKey());
            if (Math.abs(at.getX() - pos.getX()) <= TREE_REACH && Math.abs(at.getZ() - pos.getZ()) <= TREE_REACH
                    && Math.abs(at.getY() - pos.getY()) <= TREE_REACH * 2) {
                count += entry.getValue();
            }
        }
        return count;
    }

    /** Pulls a decaying log open. Returns true if that is what the click did. */
    public static boolean crack(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.DECAYING_LOG.get())) {
            return false;
        }
        DeadWood data = of(level);
        int done = data.crackedNear(pos);
        if (done >= PER_TREE) {
            player.displayClientMessage(Component.literal(
                    "You have had everything this tree had to give. Find another dead one."), true);
            return true;
        }
        data.cracked.merge(pos.asLong(), 1, Integer::sum);
        data.setDirty();
        if (level.random.nextFloat() < HOLLOW_CHANCE) {
            BlockState hollow = ModBlocks.DECAYED_LOG.get().defaultBlockState();
            if (state.hasProperty(RotatedPillarBlock.AXIS)) {
                hollow = hollow.setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS));
            }
            level.setBlock(pos, hollow, 3);
        }
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, 16, 0.3D, 0.3D, 0.3D, 0.05D);
        player.swing(InteractionHand.MAIN_HAND, true);
        int grubs = level.random.nextFloat() < EMPTY_CHANCE ? 0 : 1 + level.random.nextInt(3);
        int left = PER_TREE - done - 1;
        String rest = left > 0 ? " There is one more log in this tree worth cracking." : " That was the last of it in this tree.";
        if (grubs == 0) {
            player.displayClientMessage(Component.literal("You pull the log open. Sawdust and nothing else." + rest), true);
            return true;
        }
        ItemStack found = new ItemStack(ModItems.GRUB.get(), grubs);
        if (!player.getInventory().add(found)) {
            player.drop(found, false);
        }
        player.displayClientMessage(Component.literal("You pull the soft wood apart: " + grubs + (grubs == 1 ? " fat grub" : " fat grubs")
                + " curled in the rot." + rest).withStyle(ChatFormatting.GOLD), true);
        return true;
    }
}
