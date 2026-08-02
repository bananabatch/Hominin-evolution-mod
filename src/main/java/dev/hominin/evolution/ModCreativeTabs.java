package dev.hominin.evolution;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HomininEvolutionMod.MODID);

    public static final Supplier<CreativeModeTab> HOMININ_TAB = CREATIVE_MODE_TABS.register("hominin_evolution",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hominin_evolution"))
                    .icon(() -> new ItemStack(ModItems.FLAKE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.FLAKE.get());
                        output.accept(ModItems.CHOPPER.get());
                        output.accept(ModItems.HAMMERSTONE.get());
                        output.accept(ModItems.GRUB.get());
                        output.accept(ModItems.BEETLE.get());
                        output.accept(ModItems.EARTHWORM.get());
                        output.accept(ModItems.CARCASS.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
