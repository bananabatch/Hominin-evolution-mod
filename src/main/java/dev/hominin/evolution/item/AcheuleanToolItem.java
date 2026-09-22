package dev.hominin.evolution.item;

import java.util.List;

import dev.hominin.evolution.ModDataComponents;
import dev.hominin.evolution.combat.Bleeding;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * A tool of the Acheulean industry: worked on both faces at a knapping station, and only as
 * good as the stone and the hands that made it. Its quality - tier 4, crude, to tier 0,
 * flawless - decides how long it lasts, how hard it hits, and how deep it cuts.
 */
public class AcheuleanToolItem extends Item {
    public static final String[] TIER_NAMES = {"Flawless", "Excellent", "Strong", "Rough", "Crude"};
    private static final ChatFormatting[] TIER_COLOURS = {ChatFormatting.LIGHT_PURPLE, ChatFormatting.AQUA,
            ChatFormatting.GREEN, ChatFormatting.YELLOW, ChatFormatting.GRAY};
    /** How long each tier lasts, as a share of the tool's base durability. */
    private static final float[] DURABILITY = {2.5F, 1.6F, 1.0F, 0.7F, 0.4F};

    private final int baseDurability;
    private final double baseDamage;
    private final double attackSpeed;

    public AcheuleanToolItem(int baseDurability, double baseDamage, double attackSpeed, Properties properties) {
        super(properties.durability(baseDurability));
        this.baseDurability = baseDurability;
        this.baseDamage = baseDamage;
        this.attackSpeed = attackSpeed;
    }

    public static int qualityOf(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.QUALITY.get(), 4);
    }

    /** A fresh tool of this quality: durability and edge to match. */
    public ItemStack make(int quality) {
        ItemStack stack = new ItemStack(this);
        stack.set(ModDataComponents.QUALITY.get(), quality);
        stack.set(DataComponents.MAX_DAMAGE, Math.max(8, Math.round(baseDurability * DURABILITY[quality])));
        // baseDamage is the crude tool's; each tier up adds 0.375, so a flawless one hits 1.5 harder.
        double damage = baseDamage + (4 - quality) * 0.375D;
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, damage - 1.0D,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed - 4.0D,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build());
        return stack;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
        int quality = qualityOf(stack);
        var random = attacker.getRandom();
        // Crude edges tear the skin; a good one opens something deeper; the best can end it.
        if (quality >= 3) {
            if (random.nextFloat() < 0.4F) {
                Bleeding.inflict(target, Bleeding.Tier.EXTERNAL);
            }
        } else if (quality == 2) {
            if (random.nextFloat() < 0.5F) {
                Bleeding.inflict(target, Bleeding.Tier.INTERNAL);
            }
        } else {
            boolean catastrophic = random.nextFloat() < (quality == 0 ? 0.3F : 0.15F);
            Bleeding.inflict(target, catastrophic ? Bleeding.Tier.CATASTROPHIC : Bleeding.Tier.INTERNAL);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int quality = qualityOf(stack);
        tooltip.add(Component.literal("Tier " + quality + " - " + TIER_NAMES[quality]).withStyle(TIER_COLOURS[quality]));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(dev.hominin.evolution.HomininEvolutionMod.MODID, path);
    }
}
