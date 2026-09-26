package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.item.AcheuleanToolItem;
import dev.hominin.evolution.item.StoneMaterial;
import dev.hominin.evolution.network.PilePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A pile, looked over: left-click it (or look at it and press the work key) and see everything in it - what each
 * thing is, what stone, how good and how worn, who laid it down, and who laid the pile down first. Take what you
 * may. Anything you laid down yourself you can mark: for everyone, for you alone, or for those close to you (bond
 * 4 and up) - your band leaves alone what is not for them.
 */
public final class PileMenu {
    public static final int OPEN = 0;
    public static final int TAKE = 1;
    public static final int MARK = 2;
    public static final int TAKE_ALL = 3;
    public static final int RELOCATE = 4;

    /** Bits in each row's code, over the mark in the low two bits. */
    public static final int YOURS = 4;
    public static final int MAY_TAKE = 8;
    /** The slot each row stands for, above the flags: racks have gaps in them, piles do not. */
    public static final int SLOT_SHIFT = 5;

    private static final double REACH = 8.0D;

    public static void handle(ServerPlayer player, BlockPos pos, int action, int slot) {
        ServerLevel level = player.serverLevel();
        if (level.isLoaded(pos) && player.distanceToSqr(Vec3.atCenterOf(pos)) <= REACH * REACH
                && level.getBlockEntity(pos) instanceof dev.hominin.evolution.block.Holding holding
                && !(holding instanceof ToolPileBlockEntity)) {
            handleHolding(player, pos, holding, action, slot);
            return;
        }
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH
                || !level.getBlockState(pos).is(ModBlocks.TOOL_PILE.get())
                || !(level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile)) {
            close(player, pos);
            return;
        }
        ToolPileBlockEntity.Access access = ToolPiles.access(player);
        UUID owner = pile.owner();
        switch (action) {
            case TAKE -> {
                UUID layer = pile.layerOf(slot);
                ItemStack taken = pile.takeSlot(slot, access);
                if (!taken.isEmpty()) {
                    ToolPiles.taking(player, pile, layer, taken);
                    give(player, taken);
                    level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.6F, 1.2F);
                    ToolPiles.theft(player, level, owner);
                }
            }
            case TAKE_ALL -> {
                boolean any = false;
                for (int i = ToolPileBlockEntity.MAX - 1; i >= 0; i--) {
                    UUID layer = pile.layerOf(i);
                    ItemStack taken = pile.takeSlot(i, access);
                    if (!taken.isEmpty()) {
                        ToolPiles.taking(player, pile, layer, taken);
                        give(player, taken);
                        any = true;
                    }
                }
                if (any) {
                    level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.7F, 1.0F);
                    ToolPiles.theft(player, level, owner);
                }
            }
            case RELOCATE -> {
                ItemStack bundle = ToolPiles.gather(player, pos);
                if (!bundle.isEmpty()) {
                    give(player, bundle);
                    close(player, pos);
                    return;
                }
            }
            case MARK -> {
                if (player.getUUID().equals(pile.layerOf(slot))) {
                    int mark = (pile.markOf(slot) + 1) % 3;
                    pile.setMark(slot, mark);
                    player.displayClientMessage(Component.literal(pile.at(slot).getHoverName().getString() + ": "
                            + markText(mark) + ".").withStyle(ChatFormatting.GRAY), true);
                }
            }
            default -> {
            }
        }
        if (pile.isEmpty()) {
            level.removeBlock(pos, false);
            close(player, pos);
            return;
        }
        send(player, pos, pile);
    }

    public static String markText(int mark) {
        return switch (mark) {
            case ToolPileBlockEntity.FOR_ME -> "for you alone";
            case ToolPileBlockEntity.FOR_CLOSE -> "for those close to you (bond 4+)";
            default -> "for everyone";
        };
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void close(ServerPlayer player, BlockPos pos) {
        PacketDistributor.sendToPlayer(player, new PilePayload(pos, List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    private static void send(ServerPlayer player, BlockPos pos, ToolPileBlockEntity pile) {
        ServerLevel level = player.serverLevel();
        UUID owner = pile.owner();
        String title;
        if (owner == null) {
            title = "An old deposit";
        } else if (owner.equals(player.getUUID())) {
            title = dev.hominin.evolution.build.Sites.isStore(level, pos) ? "Your band's store" : "Your band's pile";
        } else {
            Bands.Record band = Bands.get(level, owner);
            title = band != null && band.name != null && !band.name.isEmpty() ? "The pile of " + band.name
                    : "Somebody else's pile";
        }
        String first;
        if (player.getUUID().equals(pile.firstBy())) {
            first = "You laid this pile down first.";
        } else if (!pile.firstName().isEmpty()) {
            first = "First laid down by " + pile.firstName() + ".";
        } else {
            first = owner == null ? "Nobody remembers who left these here." : "Laid down by their people.";
        }
        List<ItemStack> stacks = new ArrayList<>();
        List<String> details = new ArrayList<>();
        List<String> by = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        ToolPileBlockEntity.Access access = ToolPiles.access(player);
        for (int slot = 0; slot < ToolPileBlockEntity.MAX; slot++) {
            ItemStack stack = pile.at(slot);
            if (stack.isEmpty()) {
                break;
            }
            stacks.add(stack.copy());
            details.add(details(stack));
            UUID layer = pile.layerOf(slot);
            by.add(player.getUUID().equals(layer) ? "you" : pile.layerNameOf(slot).isEmpty() ? "nobody you know"
                    : pile.layerNameOf(slot));
            int code = pile.markOf(slot) | slot << SLOT_SHIFT;
            if (player.getUUID().equals(layer)) {
                code |= YOURS;
            }
            if (pile.mayTake(slot, access)) {
                code |= MAY_TAKE;
            }
            codes.add(code);
        }
        boolean movable = player.getUUID().equals(owner) || owner == null
                && dev.hominin.evolution.hunt.Predation.onOwnGround(player, pos);
        PacketDistributor.sendToPlayer(player, new PilePayload(pos, List.of(title, first, movable ? "1" : "0"), stacks,
                details, by, codes));
    }

    // ------------------------------------------------------------ racks

    /** A rack looked over: take what you may, and say who what you put there is for. */
    private static void handleHolding(ServerPlayer player, BlockPos pos, dev.hominin.evolution.block.Holding holding,
            int action, int slot) {
        ServerLevel level = player.serverLevel();
        ToolPileBlockEntity.Access access = ToolPiles.access(player);
        switch (action) {
            case TAKE -> {
                ItemStack taken = holding.takeSlot(slot, access);
                if (!taken.isEmpty()) {
                    give(player, taken);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
                }
            }
            case TAKE_ALL -> {
                boolean any = false;
                for (int i = 0; i < holding.slots(); i++) {
                    ItemStack taken = holding.takeSlot(i, access);
                    if (!taken.isEmpty()) {
                        give(player, taken);
                        any = true;
                    }
                }
                if (any) {
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 0.9F);
                }
            }
            case MARK -> {
                if (player.getUUID().equals(holding.layerOf(slot))) {
                    int mark = (holding.markOf(slot) + 1) % 3;
                    holding.setMark(slot, mark);
                    player.displayClientMessage(Component.literal(holding.at(slot).getHoverName().getString() + ": "
                            + markText(mark) + ".").withStyle(ChatFormatting.GRAY), true);
                }
            }
            default -> {
            }
        }
        List<ItemStack> stacks = new ArrayList<>();
        List<String> details = new ArrayList<>();
        List<String> by = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < holding.slots(); i++) {
            ItemStack stack = holding.at(i);
            if (stack.isEmpty()) {
                continue;
            }
            stacks.add(stack.copy());
            String extra = holding.extra(i);
            String detail = details(stack);
            details.add(extra.isEmpty() ? detail : detail.isEmpty() ? extra : detail + " - " + extra);
            UUID layer = holding.layerOf(i);
            by.add(player.getUUID().equals(layer) ? "you" : holding.layerNameOf(i).isEmpty() ? "nobody you know"
                    : holding.layerNameOf(i));
            int code = holding.markOf(i) | i << SLOT_SHIFT;
            if (player.getUUID().equals(layer)) {
                code |= YOURS;
            }
            if (holding.mayTake(i, access)) {
                code |= MAY_TAKE;
            }
            codes.add(code);
        }
        if (stacks.isEmpty()) {
            close(player, pos);
            if (action == OPEN) {
                player.displayClientMessage(Component.literal(holding.holdingName() + ", with nothing on it."), true);
            }
            return;
        }
        String first = player.getUUID().equals(holding.firstBy()) ? "You put the first thing on it."
                : !holding.firstName().isEmpty() ? "First used by " + holding.firstName() + "."
                : "Nobody remembers who used it first.";
        PacketDistributor.sendToPlayer(player, new PilePayload(pos, List.of(holding.holdingName(), first, "0", "rack"),
                stacks, details, by, codes));
    }

    /** "Obsidian - Tier 1, Excellent - 80% left". */
    static String details(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        StoneMaterial material = StoneMaterial.isStoneTool(stack) ? StoneMaterial.of(stack) : null;
        if (material != null) {
            parts.add(material.title());
        } else if (StoneMaterial.isStoneTool(stack)) {
            parts.add("Stone");
        }
        if (stack.getItem() instanceof AcheuleanToolItem) {
            int quality = AcheuleanToolItem.qualityOf(stack);
            parts.add("Tier " + quality + ", " + AcheuleanToolItem.TIER_NAMES[Math.max(0, Math.min(4, quality))]);
        }
        if (stack.isDamageableItem()) {
            int left = Math.round(100.0F * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
            parts.add(left >= 100 ? "Unused" : left + "% left");
        }
        if (ToolPiles.isBone(stack)) {
            parts.add("Marrow still in it");
        } else if (stack.has(DataComponents.FOOD)) {
            parts.add("Food");
        }
        return parts.isEmpty() ? "" : String.join(" - ", parts);
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        handle(player, pos, OPEN, -1);
    }

    private PileMenu() {
    }
}
