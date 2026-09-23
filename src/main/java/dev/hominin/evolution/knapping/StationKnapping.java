package dev.hominin.evolution.knapping;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.block.KnappingStationBlockEntity;
import dev.hominin.evolution.tool.ToolUse;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Knapping from what is laid out at a station, rather than from what is in your hands.
 *
 * <p>Oldowan work needs only the hammerstone. Acheulean work needs the bone as well - the
 * soft hammer that takes the fine flakes off both faces - and erectus hands. The stone used
 * is the first laid out, so what you put first is what you work.
 */
public final class StationKnapping {
    /** Oldowan multi tool: 8 chert, 2 obsidian, or one chert hammerstone. */
    private static final int MULTITOOL_CHERT = 8;
    private static final int MULTITOOL_OBSIDIAN = 2;
    private static final int MULTITOOL_BASALT = 10;

    public static final List<KnappingChoice> OLDOWAN =
            List.of(KnappingChoice.FLAKE, KnappingChoice.CHOPPER, KnappingChoice.MULTITOOL, KnappingChoice.GRINDING_STONE);

    public static void knap(ServerPlayer player, Container station, KnappingChoice choice, BlockPos pos) {
        if (station.getItem(KnappingStationBlockEntity.HAMMER).isEmpty()) {
            say(player, "Lay a hammerstone out first.");
            return;
        }
        if (Acheulean.isAcheulean(choice)) {
            acheulean(player, station, choice, pos);
        } else if (OLDOWAN.contains(choice)) {
            oldowan(player, station, choice, pos);
        }
    }

    // ------------------------------------------------------------ Acheulean

    private static void acheulean(ServerPlayer player, Container station, KnappingChoice choice, BlockPos pos) {
        if (!Acheulean.canUse(player)) {
            say(player, "Your hands do not know this yet. (Homo erectus.)");
            return;
        }
        if (station.getItem(KnappingStationBlockEntity.BOPPER).isEmpty()) {
            say(player, "The fine flakes need something softer than stone: lay a bone out.");
            return;
        }
        ItemStack first = firstStone(station, stack -> true);
        if (first == null) {
            say(player, "There is no stone laid out.");
            return;
        }
        int cost = Acheulean.STONE_COST;
        Item kind = first.getItem();
        if (count(station, kind) < cost) {
            say(player, "That takes " + cost + " " + first.getHoverName().getString().toLowerCase()
                    + " - the first stone laid out is the one worked.");
            return;
        }
        ItemStack template = first.copyWithCount(1);
        take(station, kind, cost);
        Acheulean.make(player, choice, template, pos);
    }

    // ------------------------------------------------------------ Oldowan

    private static void oldowan(ServerPlayer player, Container station, KnappingChoice choice, BlockPos pos) {
        Item result;
        dev.hominin.evolution.item.StoneMaterial material = null;
        switch (choice) {
            case FLAKE, CHOPPER -> {
                ItemStack stone = firstStone(station, StationKnapping::holdsAnEdge);
                if (stone == null) {
                    say(player, "Limestone will not take an edge. Lay out something harder.");
                    return;
                }
                material = dev.hominin.evolution.item.StoneMaterial.ofStone(stone);
                take(station, stone.getItem(), 1);
                result = choice == KnappingChoice.FLAKE ? ModItems.FLAKE.get() : ModItems.CHOPPER.get();
            }
            case GRINDING_STONE -> {
                ItemStack stone = firstStone(station, s -> s.is(ModItems.LIMESTONE_ROCK.get()));
                if (stone == null) {
                    stone = firstStone(station, s -> true);
                }
                if (stone == null) {
                    say(player, "There is no stone laid out.");
                    return;
                }
                material = dev.hominin.evolution.item.StoneMaterial.ofStone(stone);
                take(station, stone.getItem(), 1);
                result = ModItems.GRINDING_ROCK.get();
            }
            case MULTITOOL -> {
                if (count(station, ModItems.CHERT_HAMMERSTONE.get()) >= 1) {
                    take(station, ModItems.CHERT_HAMMERSTONE.get(), 1);
                    material = dev.hominin.evolution.item.StoneMaterial.CHERT;
                } else if (count(station, ModItems.OBSIDIAN_ROCK.get()) >= MULTITOOL_OBSIDIAN) {
                    take(station, ModItems.OBSIDIAN_ROCK.get(), MULTITOOL_OBSIDIAN);
                    material = dev.hominin.evolution.item.StoneMaterial.OBSIDIAN;
                } else if (count(station, ModItems.CHERT_ROCK.get()) >= MULTITOOL_CHERT) {
                    take(station, ModItems.CHERT_ROCK.get(), MULTITOOL_CHERT);
                    material = dev.hominin.evolution.item.StoneMaterial.CHERT;
                } else if (count(station, ModItems.BASALT_ROCK.get()) >= MULTITOOL_BASALT) {
                    take(station, ModItems.BASALT_ROCK.get(), MULTITOOL_BASALT);
                    material = dev.hominin.evolution.item.StoneMaterial.BASALT;
                } else {
                    say(player, "A multi tool takes 8 chert, 10 basalt, 2 obsidian, or a chert hammerstone.");
                    return;
                }
                result = ModItems.OLDOWAN_MULTITOOL.get();
            }
            default -> {
                return;
            }
        }
        ItemStack made = dev.hominin.evolution.item.StoneMaterial.stamp(new ItemStack(result), material);
        if (!player.getInventory().add(made)) {
            player.drop(made, false);
        }
        ToolUse.creditOldowanTool(player, result);
        player.level().playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F, 1.0F);
        say(player, "The stone comes away clean: " + made.getHoverName().getString() + ".");
    }

    private static boolean holdsAnEdge(ItemStack stack) {
        return !stack.is(ModItems.LIMESTONE_ROCK.get());
    }

    // ------------------------------------------------------------ the stone laid out

    @Nullable
    private static ItemStack firstStone(Container station, Predicate<ItemStack> wanted) {
        for (int slot = KnappingStationBlockEntity.STONES_START; slot < KnappingStationBlockEntity.SIZE; slot++) {
            ItemStack stack = station.getItem(slot);
            if (!stack.isEmpty() && wanted.test(stack)) {
                return stack;
            }
        }
        return null;
    }

    private static int count(Container station, Item kind) {
        int count = 0;
        for (int slot = KnappingStationBlockEntity.STONES_START; slot < KnappingStationBlockEntity.SIZE; slot++) {
            ItemStack stack = station.getItem(slot);
            if (stack.is(kind)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void take(Container station, Item kind, int amount) {
        for (int slot = KnappingStationBlockEntity.STONES_START; slot < KnappingStationBlockEntity.SIZE && amount > 0;
                slot++) {
            ItemStack stack = station.getItem(slot);
            if (stack.is(kind)) {
                int used = Math.min(amount, stack.getCount());
                stack.shrink(used);
                amount -= used;
            }
        }
        station.setChanged();
    }

    private static void say(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    private StationKnapping() {
    }
}
