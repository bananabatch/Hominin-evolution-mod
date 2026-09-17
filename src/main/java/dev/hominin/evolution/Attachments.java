package dev.hominin.evolution;

import java.util.function.Supplier;

import dev.hominin.evolution.data.HeadTrauma;
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

    /**
     * Head injuries, attached to the struck mob rather than the striker: the
     * damage belongs to the skull that took it, and has to survive the mob
     * being unloaded and reloaded mid-hunt.
     */
    public static final Supplier<AttachmentType<HeadTrauma>> HEAD_TRAUMA = ATTACHMENT_TYPES.register(
            "head_trauma",
            () -> AttachmentType.builder(HeadTrauma::new)
                    .serialize(HeadTrauma.CODEC)
                    .build());

    /**
     * Whether a player is up a tree right now. Never saved: a player who logs out
     * mid-climb comes back standing, not clinging. Both sides keep a copy, because
     * leaf collision is checked on both and has to agree.
     */
    public static final Supplier<AttachmentType<Boolean>> CLIMBING = ATTACHMENT_TYPES.register(
            "climbing",
            () -> AttachmentType.builder(() -> Boolean.FALSE).build());

    private Attachments() {
    }
}
