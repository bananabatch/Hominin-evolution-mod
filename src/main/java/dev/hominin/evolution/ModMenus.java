package dev.hominin.evolution;

import dev.hominin.evolution.craft.WorkStationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, HomininEvolutionMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<WorkStationMenu>> WORK_STATION =
            MENUS.register("work_station", () -> IMenuTypeExtension.create(WorkStationMenu::new));

    private ModMenus() {
    }
}
