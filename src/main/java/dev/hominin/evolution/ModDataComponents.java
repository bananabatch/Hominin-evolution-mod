package dev.hominin.evolution;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HomininEvolutionMod.MODID);

    /** How well a knapped tool came out: 4 (crude) to 0 (flawless). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> QUALITY =
            COMPONENTS.registerComponentType("quality",
                    builder -> builder.persistent(Codec.intRange(0, 4)).networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {
    }
}
