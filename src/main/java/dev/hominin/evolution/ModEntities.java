package dev.hominin.evolution;

import java.util.function.Supplier;

import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.entity.ThrownObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, HomininEvolutionMod.MODID);

    /** Sized and tracked like a snowball, which is what it behaves like in flight. */
    public static final Supplier<EntityType<ThrownObject>> THROWN_OBJECT = ENTITY_TYPES.register("thrown_object",
            () -> EntityType.Builder.<ThrownObject>of(ThrownObject::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("thrown_object"));

    /** A little shorter than a player, as the hominins it stands for were. */
    public static final Supplier<EntityType<BandMember>> BAND_MEMBER = ENTITY_TYPES.register("band_member",
            () -> EntityType.Builder.<BandMember>of(BandMember::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.6F)
                    .eyeHeight(1.45F)
                    .clientTrackingRange(10)
                    .build("band_member"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Baboon>> BABOON = ENTITY_TYPES.register(
            "baboon", () -> EntityType.Builder.<dev.hominin.evolution.entity.Baboon>of(
                    dev.hominin.evolution.entity.Baboon::new, MobCategory.CREATURE)
                    .sized(0.7F, 1.0F).clientTrackingRange(10).build("baboon"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Pachycrocuta>> PACHYCROCUTA =
            ENTITY_TYPES.register("pachycrocuta", () -> EntityType.Builder.<dev.hominin.evolution.entity.Pachycrocuta>of(
                    dev.hominin.evolution.entity.Pachycrocuta::new, MobCategory.CREATURE)
                    .sized(1.1F, 1.4F).clientTrackingRange(10).build("pachycrocuta"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Sabertooth>> SABERTOOTH =
            ENTITY_TYPES.register("sabertooth", () -> EntityType.Builder.<dev.hominin.evolution.entity.Sabertooth>of(
                    dev.hominin.evolution.entity.Sabertooth::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.4F).clientTrackingRange(10).build("sabertooth"));

    private ModEntities() {
    }
}
