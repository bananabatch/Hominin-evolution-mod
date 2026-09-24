package dev.hominin.evolution;

import java.util.function.Supplier;

import dev.hominin.evolution.data.Ancestors;
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
     * The names your line carries out of the deep past - one from each band you left behind.
     * Kept apart from the rest of a player's data because it outlives every part of it: the
     * species changes, the body changes, the counters are wiped, and these do not.
     */
    public static final Supplier<AttachmentType<Ancestors>> ANCESTORS = ATTACHMENT_TYPES.register(
            "ancestors",
            () -> AttachmentType.builder(() -> new Ancestors())
                    .serialize(Ancestors.CODEC)
                    .copyOnDeath()
                    .build());

    /** The mental map: places held in mind, what the band told you, your band's name, where you are heading. */
    public static final Supplier<AttachmentType<dev.hominin.evolution.mind.MindData>> MIND = ATTACHMENT_TYPES.register(
            "mind",
            () -> AttachmentType.builder(() -> new dev.hominin.evolution.mind.MindData())
                    .serialize(dev.hominin.evolution.mind.MindData.CODEC)
                    .copyOnDeath()
                    .build());

    /** Which tips a player has seen, and whether they want them. Kept through death and evolving. */
    public static final Supplier<AttachmentType<dev.hominin.evolution.guide.TipsData>> TIPS = ATTACHMENT_TYPES.register(
            "tips",
            () -> AttachmentType.builder(() -> new dev.hominin.evolution.guide.TipsData())
                    .serialize(dev.hominin.evolution.guide.TipsData.CODEC)
                    .copyOnDeath()
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
