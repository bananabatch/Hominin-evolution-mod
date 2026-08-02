package dev.hominin.evolution;

import java.util.function.Supplier;

import dev.hominin.evolution.data.PlayerEvolutionData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class Attachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HomininEvolutionMod.MODID);

    public static final Supplier<AttachmentType<PlayerEvolutionData>> PLAYER_EVOLUTION_DATA = ATTACHMENT_TYPES.register(
            "player_evolution_data",
            () -> AttachmentType.builder(PlayerEvolutionData::new)
                    .serialize(PlayerEvolutionData.CODEC)
                    .copyOnDeath()
                    .build());

    private Attachments() {
    }
}
