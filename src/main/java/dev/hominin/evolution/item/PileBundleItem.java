package dev.hominin.evolution.item;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.band.PileEthics;
import dev.hominin.evolution.band.ToolPiles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A pile gathered up whole, to be put down somewhere else - everything in it, just as it lay: who laid each thing
 * down, and who it is for. Use it on the ground (your own) to set it down again.
 *
 * <p>Carry it long and the band starts to wonder where you are going with it - if there is anything of theirs in
 * it, or nothing of yours. See {@link PileEthics}.
 */
public class PileBundleItem extends Item {
    public PileBundleItem(Properties properties) {
        super(properties);
    }

    /** What the pile held, as the pile saved it. */
    public static CompoundTag contents(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag().getCompound("Pile");
    }

    public static ItemStack of(CompoundTag pile, int count, int foreign) {
        ItemStack stack = new ItemStack(dev.hominin.evolution.ModItems.PILE_BUNDLE.get());
        CompoundTag tag = new CompoundTag();
        tag.put("Pile", pile);
        tag.putInt("Count", count);
        tag.putInt("Foreign", foreign);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** How many things in it someone else laid down. */
    public static int foreign(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt("Foreign");
    }

    public static int count(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt("Count");
    }

    /** Whether the one carrying it laid anything in it at all. */
    public static boolean anyOf(ItemStack stack, UUID who) {
        for (Tag entry : contents(stack).getList("Meta", Tag.TAG_COMPOUND)) {
            CompoundTag meta = (CompoundTag) entry;
            if (meta.hasUUID("By") && meta.getUUID("By").equals(who)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockPos at = context.getClickedFace() == Direction.UP || !level.getBlockState(clicked).canBeReplaced()
                ? clicked.relative(context.getClickedFace()) : clicked;
        if (ToolPiles.setDown(player, at, context.getItemInHand())) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int count = count(stack);
        int foreign = foreign(stack);
        tooltip.add(Component.literal(count + (count == 1 ? " thing" : " things") + " gathered up from a pile")
                .withStyle(ChatFormatting.GRAY));
        var registries = context.registries();
        if (registries != null) {
            ListTag items = contents(stack).getList("Items", Tag.TAG_COMPOUND);
            int shown = 0;
            for (Tag entry : items) {
                if (shown++ >= 8) {
                    break;
                }
                ItemStack inside = ItemStack.parseOptional(registries, (CompoundTag) entry);
                if (!inside.isEmpty()) {
                    tooltip.add(Component.literal(" - " + inside.getHoverName().getString()
                            + (inside.getCount() > 1 ? " x" + inside.getCount() : "")).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        if (foreign > 0) {
            tooltip.add(Component.literal(foreign + " of them laid down by others - put it down soon, on your own ground.")
                    .withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.literal("Use it on the ground to set the pile down.").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Nullable
    public static UUID ownerOf(ItemStack stack) {
        CompoundTag pile = contents(stack);
        return pile.hasUUID("Owner") ? pile.getUUID("Owner") : null;
    }
}
