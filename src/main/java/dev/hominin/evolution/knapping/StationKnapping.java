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
    private static final int MULTITOOL_FINE_CHERT = 5;

    public static final List<KnappingChoice> OLDOWAN =
            List.of(KnappingChoice.FLAKE, KnappingChoice.CHOPPER, KnappingChoice.MULTITOOL, KnappingChoice.GRINDING_STONE);

    /** Acheulean: the Levallois technique - flakes and blades off a prepared core, and a finer hand axe. */
    public static final List<KnappingChoice> LEVALLOIS = List.of(KnappingChoice.LEVALLOIS_FLAKE,
            KnappingChoice.LEVALLOIS_BLADE, KnappingChoice.LEVALLOIS_HAND_AXE);
    /** Flakes a prepared core gives up: two where the old way got one. */
    private static final int LEVALLOIS_FLAKES = 2;

    public static void knap(ServerPlayer player, Container station, KnappingChoice choice, BlockPos pos) {
        if (station.getItem(KnappingStationBlockEntity.HAMMER).isEmpty()) {
            say(player, "Lay a hammerstone out first.");
            return;
        }
        if (station.getItem(KnappingStationBlockEntity.HAMMER).is(ModItems.OBSIDIAN_CHUNK.get())) {
            // Glass is no hammer: the first blow and it bursts.
            station.getItem(KnappingStationBlockEntity.HAMMER).shrink(1);
            if (station instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
                be.setChanged();
            }
            dev.hominin.evolution.item.ObsidianChunkItem.burst(player.serverLevel(), pos, player);
            return;
        }
        if (Acheulean.isAcheulean(choice)) {
            acheulean(player, station, choice, pos);
        } else if (LEVALLOIS.contains(choice)) {
            levallois(player, station, choice, pos);
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
        // A hammerstone laid out is a Levallois core waiting, not stone to be worked the old way.
        ItemStack first = firstStone(station, stack -> !stack.is(ModItems.HAMMERSTONE.get()));
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

    // ------------------------------------------------------------ Levallois

    private static void levallois(ServerPlayer player, Container station, KnappingChoice choice, BlockPos pos) {
        if (!Acheulean.canUseLevallois(player)) {
            say(player, "Your hands do not know this yet. (Homo heidelbergensis.)");
            return;
        }
        if (choice == KnappingChoice.LEVALLOIS_HAND_AXE) {
            levalloisHandAxe(player, station, pos);
            return;
        }
        ItemStack stone = firstStone(station, stack -> holdsAnEdge(stack) && !isHammerstone(stack));
        if (stone == null) {
            say(player, "Lay out stone that holds an edge - not limestone, and not a hammerstone.");
            return;
        }
        dev.hominin.evolution.item.StoneMaterial material = dev.hominin.evolution.item.StoneMaterial.ofStone(stone);
        take(station, stone.getItem(), 1);
        boolean flakes = choice == KnappingChoice.LEVALLOIS_FLAKE;
        ItemStack made = dev.hominin.evolution.item.StoneMaterial.stamp(new ItemStack(choice.result(),
                flakes ? LEVALLOIS_FLAKES : 1), material);
        if (!player.getInventory().add(made.copy())) {
            player.drop(made.copy(), false);
        }
        dev.hominin.evolution.EvolutionManager.incrementCriterion(player, "make_levallois_tool", 1);
        player.level().playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F, 1.3F);
        say(player, flakes ? "You shape the core, then strike: two thin, even flakes come away."
                : "You shape the core long, then strike: a blade comes away, straight as a stem.");
    }

    /**
     * The Levallois hand axe is taken down from a hammerstone of the stone it is to be: laid out among the stone,
     * beside the hammerstone you strike with. What it comes out as depends on your skill, as any hand axe does.
     */
    private static void levalloisHandAxe(ServerPlayer player, Container station, BlockPos pos) {
        if (station.getItem(KnappingStationBlockEntity.BOPPER).isEmpty()) {
            say(player, "The fine flakes need something softer than stone: lay a bone out.");
            return;
        }
        int slot = -1;
        for (int i = KnappingStationBlockEntity.STONES_START; i < KnappingStationBlockEntity.SIZE && slot < 0; i++) {
            if (isHammerstone(station.getItem(i))) {
                slot = i;
            }
        }
        if (slot < 0) {
            say(player, "A Levallois hand axe is struck from a prepared core: lay out a hammerstone of the stone you "
                    + "want it made of, beside the one you strike with.");
            return;
        }
        ItemStack core = station.getItem(slot);
        dev.hominin.evolution.item.StoneMaterial material = core.is(ModItems.OBSIDIAN_CHUNK.get())
                ? dev.hominin.evolution.item.StoneMaterial.OBSIDIAN : dev.hominin.evolution.item.StoneMaterial.of(core);
        if (material == dev.hominin.evolution.item.StoneMaterial.LIMESTONE) {
            say(player, "A limestone core crumbles before it takes a shape.");
            return;
        }
        ItemStack template = new ItemStack(rockOf(material));
        core.shrink(1);
        station.setChanged();
        Acheulean.make(player, KnappingChoice.LEVALLOIS_HAND_AXE, template, pos);
    }

    /** The loose stone a hammerstone is made of - quartzite, for one nobody marked. */
    public static Item rockOf(@Nullable dev.hominin.evolution.item.StoneMaterial material) {
        if (material == null) {
            return ModItems.GRANITE_ROCK.get();
        }
        return switch (material) {
            case CHERT -> ModItems.CHERT_ROCK.get();
            case FINE_CHERT -> ModItems.FINE_CHERT_ROCK.get();
            case OBSIDIAN -> ModItems.OBSIDIAN_ROCK.get();
            case BASALT -> ModItems.BASALT_ROCK.get();
            case LIMESTONE -> ModItems.LIMESTONE_ROCK.get();
            default -> ModItems.GRANITE_ROCK.get();
        };
    }

    /** A hammerstone - or, as a Levallois core, the obsidian chunk. */
    public static boolean isHammerstone(ItemStack stack) {
        return stack.is(ModItems.HAMMERSTONE.get()) || stack.is(ModItems.CHERT_HAMMERSTONE.get())
                || stack.is(ModItems.OBSIDIAN_CHUNK.get());
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
                } else if (count(station, ModItems.FINE_CHERT_ROCK.get()) >= MULTITOOL_FINE_CHERT) {
                    take(station, ModItems.FINE_CHERT_ROCK.get(), MULTITOOL_FINE_CHERT);
                    material = dev.hominin.evolution.item.StoneMaterial.FINE_CHERT;
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
        // A hammerstone laid out is a core for the Levallois hand axe, not stone to flake.
        return !stack.is(ModItems.LIMESTONE_ROCK.get()) && !stack.is(ModItems.HAMMERSTONE.get());
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
