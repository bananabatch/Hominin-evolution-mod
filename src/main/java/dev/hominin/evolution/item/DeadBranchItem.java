package dev.hominin.evolution.item;

import java.util.List;

import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A branch off a dead tree - snapped off with a chopper, grey and light and bored through by beetles. It is no weapon:
 * hit anything with it and it goes to pieces. What it is good for is what lives in it.
 *
 * <ul>
 * <li><b>Pick through it</b> (use): a grub or two, sometimes a beetle - once. After that it is only dead wood.</li>
 * <li><b>Break it open</b> (sneak-use): everything in it at once, more in hard times when the grubs have had longer at
 * it - and the branch is gone. Once picked through, there is little left to break open for.</li>
 * </ul>
 * It keeps: lay it on a pile with the bones and nothing comes for it in the night.
 */
public class DeadBranchItem extends Item {
    private static final String SEARCHED = "Searched";

    public DeadBranchItem(Properties properties) {
        super(properties);
    }

    public static boolean searched(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(SEARCHED);
    }

    private static void markSearched(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putBoolean(SEARCHED, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.success(stack);
        }
        boolean hard = dev.hominin.evolution.survival.Seasons.strained(level);
        if (player.isShiftKeyDown()) {
            // Broken open: the lot, and the branch with it.
            int grubs = searched(stack) ? level.random.nextInt(2) : (hard ? 2 + level.random.nextInt(3) : 1 + level.random.nextInt(2));
            int beetles = !searched(stack) && level.random.nextFloat() < 0.4F ? 1 : 0;
            ItemStack one = stack.split(1);
            give(server, grubs, beetles);
            level.playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.9F, 1.2F);
            player.displayClientMessage(Component.literal(grubs + beetles == 0 ? "You break it open. Nothing - it was "
                    + "already picked through."
                    : "You break the branch open: " + describe(grubs, beetles) + (hard ? " The hard times fattened them."
                            : "")).withStyle(ChatFormatting.GOLD), true);

            return InteractionResultHolder.consume(stack);
        }
        if (searched(stack)) {
            player.displayClientMessage(Component.literal("You have picked this one through already. Break it open "
                    + "(sneak) for whatever is left, or burn it."), true);
            return InteractionResultHolder.fail(stack);
        }
        ItemStack one = stack.getCount() > 1 ? stack.split(1) : stack;
        markSearched(one);
        int grubs = level.random.nextFloat() < 0.75F ? 1 + level.random.nextInt(2) : 0;
        int beetles = level.random.nextFloat() < 0.35F ? 1 : 0;
        give(server, grubs, beetles);
        if (one != stack && !player.getInventory().add(one)) {
            player.drop(one, false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.6F, 1.4F);
        player.displayClientMessage(Component.literal(grubs + beetles == 0 ? "You pick through the rotten wood. Nothing "
                + "in this one." : "You pick through the rotten wood: " + describe(grubs, beetles))
                .withStyle(ChatFormatting.GOLD), true);
        return InteractionResultHolder.consume(stack);
    }

    private static String describe(int grubs, int beetles) {
        String g = grubs == 0 ? "" : grubs + (grubs == 1 ? " grub" : " grubs");
        String b = beetles == 0 ? "" : (g.isEmpty() ? "" : " and ") + "a beetle";
        return g + b + ".";
    }

    private static void give(ServerPlayer player, int grubs, int beetles) {
        if (grubs > 0) {
            ItemStack found = new ItemStack(ModItems.GRUB.get(), grubs);
            if (!player.getInventory().add(found)) {
                player.drop(found, false);
            }
        }
        if (beetles > 0) {
            ItemStack found = new ItemStack(ModItems.BEETLE.get(), beetles);
            if (!player.getInventory().add(found)) {
                player.drop(found, false);
            }
        }
    }

    /** No weapon: it goes to pieces on whatever it hits. */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        attacker.level().playSound(null, attacker.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.0F, 1.3F);
        if (attacker instanceof Player player) {
            player.displayClientMessage(Component.literal("The dead branch goes to pieces."), true);
        }
        stack.shrink(1);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(searched(stack) ? "Picked through" : "Use: pick through it for grubs")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak-use (not at the ground): break it open").withStyle(ChatFormatting.DARK_GRAY));
    }
}
