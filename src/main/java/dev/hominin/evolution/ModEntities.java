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

    /** The giant baboon: the one animal out here that answers a threat display. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Dinopithecus>> DINOPITHECUS =
            ENTITY_TYPES.register("dinopithecus", () -> EntityType.Builder.<dev.hominin.evolution.entity.Dinopithecus>of(
                    dev.hominin.evolution.entity.Dinopithecus::new, MobCategory.CREATURE)
                    .sized(1.0F, 1.7F).clientTrackingRange(10).build("dinopithecus"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Pachycrocuta>> PACHYCROCUTA =
            ENTITY_TYPES.register("pachycrocuta", () -> EntityType.Builder.<dev.hominin.evolution.entity.Pachycrocuta>of(
                    dev.hominin.evolution.entity.Pachycrocuta::new, MobCategory.CREATURE)
                    .sized(1.1F, 1.4F).clientTrackingRange(10).build("pachycrocuta"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Sabertooth>> SABERTOOTH =
            ENTITY_TYPES.register("sabertooth", () -> EntityType.Builder.<dev.hominin.evolution.entity.Sabertooth>of(
                    dev.hominin.evolution.entity.Sabertooth::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.4F).clientTrackingRange(10).build("sabertooth"));

    /** Scimitar cat: the daylight pursuit hunter of the open plain. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Homotherium>> HOMOTHERIUM =
            ENTITY_TYPES.register("homotherium", () -> EntityType.Builder.<dev.hominin.evolution.entity.Homotherium>of(
                    dev.hominin.evolution.entity.Homotherium::new, MobCategory.CREATURE)
                    .sized(1.2F, 1.5F).clientTrackingRange(10).build("homotherium"));

    /** The bird that takes children out of the open. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.CrownedEagle>> CROWNED_EAGLE =
            ENTITY_TYPES.register("crowned_eagle", () -> EntityType.Builder.<dev.hominin.evolution.entity.CrownedEagle>of(
                    dev.hominin.evolution.entity.CrownedEagle::new, MobCategory.CREATURE)
                    .sized(1.0F, 0.9F).clientTrackingRange(12).build("crowned_eagle"));

    private ModEntities() {
    }
}
