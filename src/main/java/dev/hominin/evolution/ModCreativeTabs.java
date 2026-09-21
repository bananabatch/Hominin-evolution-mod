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
                        output.accept(ModItems.CHERT_ROCK.get());
                        output.accept(ModItems.GRANITE_ROCK.get());
                        output.accept(ModItems.LIMESTONE_ROCK.get());
                        output.accept(ModItems.OBSIDIAN_ROCK.get());
                        output.accept(ModItems.CHERT_DEPOSIT.get());
                        output.accept(ModItems.QUARTZITE_DEPOSIT.get());
                        output.accept(ModItems.LIMESTONE_DEPOSIT.get());
                        output.accept(ModItems.ROCK.get());
                        output.accept(ModItems.TICK.get());
                        output.accept(ModItems.THATCH.get());
                        output.accept(ModItems.TWINE.get());
                        output.accept(ModItems.FIRE_DRILL.get());
                        output.accept(ModItems.GRINDING_ROCK.get());
                        output.accept(ModItems.FLAKE.get());
                        output.accept(ModItems.CHOPPER.get());
                        output.accept(ModItems.HAMMERSTONE.get());
                        output.accept(ModItems.RIB.get());
                        output.accept(ModItems.CARCASS.get());
                        output.accept(ModItems.EMPTY_EGGSHELL.get());
                        output.accept(ModItems.WATER_EGGSHELL.get());
                        output.accept(ModItems.CHERT_HAMMERSTONE.get());
                        output.accept(ModItems.LOMEKWIAN_TOOL.get());
                        output.accept(ModItems.OLDOWAN_MULTITOOL.get());
                        output.accept(ModItems.DIGGING_STICK.get());
                        output.accept(ModItems.LONG_BRANCH.get());
                        output.accept(ModItems.SHARPENED_STICK.get());
                        output.accept(ModItems.POINTY_STICK.get());
                        output.accept(ModItems.SHARPENED_SPEAR.get());
                        output.accept(ModItems.FIRE_HARDENED_SPEAR.get());
                        output.accept(ModItems.WOODEN_CLUB.get());
                        output.accept(ModItems.TERMITE_MOUND.get());
                        output.accept(ModItems.DECAYING_LOG.get());
                        output.accept(ModItems.DECAYED_LOG.get());
                        output.accept(ModItems.TERMITE_STICK.get());
                        output.accept(ModItems.NESTING_MATERIAL.get());
                        output.accept(ModItems.NEST.get());
                        output.accept(ModItems.BAND_MEMBER_SPAWN_EGG.get());
                        output.accept(ModItems.BABOON_SPAWN_EGG.get());
                        output.accept(ModItems.DINOPITHECUS_SPAWN_EGG.get());
                        output.accept(ModItems.PACHYCROCUTA_SPAWN_EGG.get());
                        output.accept(ModItems.SABERTOOTH_SPAWN_EGG.get());
                        output.accept(ModItems.HOMOTHERIUM_SPAWN_EGG.get());
                        output.accept(ModItems.CROWNED_EAGLE_SPAWN_EGG.get());
                        output.accept(ModItems.MEAT_CHUNK.get());
                        output.accept(ModItems.GRUB.get());
                        output.accept(ModItems.BEETLE.get());
                        output.accept(ModItems.EARTHWORM.get());
                        output.accept(ModItems.LONG_BONE.get());
                        output.accept(ModItems.BONE_MARROW.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
