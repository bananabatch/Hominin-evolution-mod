package dev.hominin.evolution.client;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModEffects;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Bleeding is one effect, its tier the amplifier - so the plain icon would be the same for a graze and a body opened
 * up. Each tier draws its own: one drop for an external wound, two for an internal one, a spreading pool for a
 * catastrophic one.
 */
public final class BleedingIcons {
    private static final ResourceLocation[] TIERS = {
            texture("bleeding"), texture("bleeding_internal"), texture("bleeding_catastrophic")};

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "textures/mob_effect/" + name + ".png");
    }

    private static ResourceLocation of(MobEffectInstance instance) {
        return TIERS[Math.max(0, Math.min(TIERS.length - 1, instance.getAmplifier()))];
    }

    public static void register(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override
            public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                    GuiGraphics graphics, int x, int y, int blitOffset) {
                graphics.blit(of(instance), x, y + 7, 0, 0, 18, 18, 18, 18);
                return true;
            }

            @Override
            public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics, int x, int y, float z,
                    float alpha) {
                graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
                graphics.blit(of(instance), x + 3, y + 3, 0, 0, 18, 18, 18, 18);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                return true;
            }
        }, ModEffects.BLEEDING);
    }

    private BleedingIcons() {
    }
}
