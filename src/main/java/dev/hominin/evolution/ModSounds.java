package dev.hominin.evolution;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, HomininEvolutionMod.MODID);

    /** The player's pant hoot, the call that opens a threat display. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PANT_HOOT = register("pant_hoot");

    /** A band member taking up the call. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BAND_PANT_HOOT = register("band_pant_hoot");

    /** A branch struck against wood - the warning knock. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BRANCH_KNOCK = register("branch_knock");

    /**
     * The drawn-out call that summons a band. Fixed at 64 blocks so it always
     * carries the same distance no matter what volume it is played at.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> BAND_CALL = registerFixedRange("band_call", 64.0F);

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> registerFixedRange(String name, float range) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createFixedRangeEvent(id(name), range));
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, name);
    }

    private ModSounds() {
    }
}
