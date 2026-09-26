package dev.hominin.evolution.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModDataComponents;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.combat.Bleeding;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * What a stone tool was knapped from. Every stone tool carries it, and it shows: each has a look
 * of its own. And it matters in the hand - basalt holds a better edge than rough stone, chert a
 * keener one, and obsidian keener still: sharp enough that a cut from it will sometimes open a
 * wound a tier deeper than the blow alone would have. Plain country rock is nothing in particular,
 * and a tool knapped from it keeps the plain look.
 */
public enum StoneMaterial {
    BASALT("Basalt", ChatFormatting.DARK_GRAY, 0.25F, "dense, dark lava stone - a tier behind chert"),
    QUARTZITE("Quartzite", ChatFormatting.GRAY, 0.0F, "coarse and tough - it holds up to a beating"),
    CHERT("Chert", ChatFormatting.GOLD, 0.5F, "fine-grained and waxy - it takes a keener edge"),
    LIMESTONE("Limestone", ChatFormatting.WHITE, 0.0F, "soft and chalky - it barely holds an edge"),
    OBSIDIAN("Obsidian", ChatFormatting.DARK_PURPLE, 1.5F, "volcanic glass - the sharpest edge there is"),
    /** Out of the river gravel and nowhere else: glassier than any seam gives. Nearly as keen as obsidian. */
    FINE_CHERT("Fine chert", ChatFormatting.YELLOW, 1.0F, "glassy chert out of the river gravel - nearly as keen as obsidian");

    private final String title;
    private final ChatFormatting colour;
    private final float damageBonus;
    private final String about;

    StoneMaterial(String title, ChatFormatting colour, float damageBonus, String about) {
        this.title = title;
        this.colour = colour;
        this.damageBonus = damageBonus;
        this.about = about;
    }

    public String title() {
        return title;
    }

    public float damageBonus() {
        return damageBonus;
    }

    /** The chance an obsidian cut deepens the wound it made by a tier. */
    public static final float OBSIDIAN_DEEPEN_CHANCE = 0.25F;

    // ------------------------------------------------------------ what a thing is made of

    /** What a piece of raw stone is. Null for anything that is not knapping stone. */
    @Nullable
    public static StoneMaterial ofStone(ItemStack stone) {
        if (stone.is(ModItems.OBSIDIAN_ROCK.get()) || stone.is(ModItems.OBSIDIAN_CHUNK.get())) {
            return OBSIDIAN;
        }
        if (stone.is(ModItems.FINE_CHERT_ROCK.get())) {
            return FINE_CHERT;
        }
        if (stone.is(ModItems.CHERT_ROCK.get()) || stone.is(ModItems.CHERT_HAMMERSTONE.get())) {
            return CHERT;
        }
        if (stone.is(ModItems.GRANITE_ROCK.get())) {
            return QUARTZITE;
        }
        if (stone.is(ModItems.LIMESTONE_ROCK.get())) {
            return LIMESTONE;
        }
        return stone.is(ModItems.BASALT_ROCK.get()) ? BASALT : null;
    }

    /** What a stone tool is made of: what it was stamped with, or what the item always is. */
    @Nullable
    public static StoneMaterial of(ItemStack tool) {
        Integer stored = tool.get(ModDataComponents.MATERIAL.get());
        if (stored != null && stored >= 0 && stored < values().length) {
            return values()[stored];
        }
        return tool.is(ModItems.CHERT_HAMMERSTONE.get()) ? CHERT : null;
    }

    public static boolean isStoneTool(ItemStack stack) {
        return stack.is(ModTags.Items.STONE_TOOLS) || stack.is(ModItems.GRINDING_ROCK.get());
    }

    /**
     * Marks a freshly made tool with the stone it came from. Returns the same stack - except a hammerstone of chert,
     * which is the chert hammerstone: one item, not a plain hammerstone that happens to be chert.
     */
    public static ItemStack stamp(ItemStack tool, @Nullable StoneMaterial material) {
        if (material == CHERT && tool.is(ModItems.HAMMERSTONE.get())) {
            return new ItemStack(ModItems.CHERT_HAMMERSTONE.get(), tool.getCount());
        }
        if (material != null && isStoneTool(tool) && !tool.is(ModItems.CHERT_HAMMERSTONE.get())) {
            tool.set(ModDataComponents.MATERIAL.get(), material.ordinal());
        }
        return tool;
    }

    /** Marks something as made of this stone whatever it is - the point of a spear, say, which is no stone tool. */
    public static ItemStack mark(ItemStack stack, @Nullable StoneMaterial material) {
        if (material != null) {
            stack.set(ModDataComponents.MATERIAL.get(), material.ordinal());
        }
        return stack;
    }

    /** Any plain hammerstone stamped chert, from before there was only the one, becomes the chert hammerstone. */
    public static void tidyHammerstones(net.minecraft.server.level.ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.HAMMERSTONE.get()) && of(stack) == CHERT) {
                inventory.setItem(slot, new ItemStack(ModItems.CHERT_HAMMERSTONE.get(), stack.getCount()));
            }
        }
    }

    /** Marks a tool made from this piece of stone. */
    public static ItemStack stampFrom(ItemStack tool, ItemStack stone) {
        return stamp(tool, ofStone(stone));
    }

    // ------------------------------------------------------------ the stone a player is working

    /** What the player last struck: handed to whatever comes out of it by another route. */
    private static final Map<UUID, StoneMaterial> struck = new HashMap<>();

    public static void struckBy(UUID player, @Nullable StoneMaterial material) {
        if (material == null) {
            struck.remove(player);
        } else {
            struck.put(player, material);
        }
    }

    @Nullable
    public static StoneMaterial lastStruck(UUID player) {
        return struck.get(player);
    }

    // ------------------------------------------------------------ in the hand

    private record Deepen(LivingEntity target, long at) {
    }

    private static final List<Deepen> deepening = new ArrayList<>();

    /** A keener edge lands harder; obsidian may open the wound further once the blow is done. */
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !(event.getSource().getDirectEntity() instanceof LivingEntity attacker)
                || event.getSource().getEntity() != attacker) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        StoneMaterial material = isStoneTool(weapon) ? of(weapon) : null;
        if (material == null) {
            return;
        }
        if (material.damageBonus > 0.0F) {
            event.setAmount(event.getAmount() + material.damageBonus);
        }
        if (material == OBSIDIAN && target.getRandom().nextFloat() < OBSIDIAN_DEEPEN_CHANCE && deepening.size() < 256) {
            // After the blow has done what it does: the weapon's own cut lands first.
            deepening.add(new Deepen(target, target.level().getGameTime() + 1));
        }
    }

    /** Once a tick: obsidian cuts opening one tier deeper than the blow that made them. */
    public static void tick(ServerLevel level) {
        if (deepening.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        deepening.removeIf(d -> {
            if (d.target().level() != level || now < d.at()) {
                return d.target().isRemoved();
            }
            LivingEntity target = d.target();
            if (target.isAlive()) {
                var bleeding = target.getEffect(dev.hominin.evolution.ModEffects.BLEEDING);
                int next = bleeding == null ? 0 : bleeding.getAmplifier() + 1;
                if (next < Bleeding.Tier.values().length) {
                    Bleeding.inflict(target, Bleeding.Tier.values()[next]);
                }
            }
            return true;
        });
    }

    /** The tooltip line: what it is made of, and what that means. */
    public static void describe(ItemStack stack, List<Component> tooltip) {
        if (stack.is(ModItems.STONE_TIPPED_SPEAR.get())) {
            StoneMaterial point = of(stack);
            if (point != null) {
                tooltip.add(Math.min(1, tooltip.size()), Component.literal(point.title + " point - " + point.about)
                        .withStyle(point.colour));
            }
            return;
        }
        StoneMaterial material = isStoneTool(stack) ? of(stack) : null;
        if (material == null) {
            return;
        }
        String effect = material == OBSIDIAN ? " (+1.5 damage; cuts can open a tier deeper)"
                : material == FINE_CHERT ? " (+1 damage)"
                : material == CHERT ? " (+0.5 damage)" : material == BASALT ? " (+0.25 damage)" : "";
        tooltip.add(Math.min(1, tooltip.size()), Component.literal(material.title + " - " + material.about + effect)
                .withStyle(material.colour));
    }
}
