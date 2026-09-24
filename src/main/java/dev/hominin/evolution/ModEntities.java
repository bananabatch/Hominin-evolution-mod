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

    /** A burning torch, thrown: flies like a snowball, lands still burning. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.ThrownTorch>> THROWN_TORCH =
            ENTITY_TYPES.register("thrown_torch", () -> EntityType.Builder.<dev.hominin.evolution.entity.ThrownTorch>of(
                    dev.hominin.evolution.entity.ThrownTorch::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("thrown_torch"));

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

    /** The relatives who stayed in the trees. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Chimpanzee>> CHIMPANZEE =
            ENTITY_TYPES.register("chimpanzee", () -> EntityType.Builder.<dev.hominin.evolution.entity.Chimpanzee>of(
                    dev.hominin.evolution.entity.Chimpanzee::new, MobCategory.CREATURE)
                    .sized(0.8F, 1.3F).clientTrackingRange(10).build("chimpanzee"));

    /** The peaceable ape: where a troop lives, nothing hunts. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Bonobo>> BONOBO =
            ENTITY_TYPES.register("bonobo", () -> EntityType.Builder.<dev.hominin.evolution.entity.Bonobo>of(
                    dev.hominin.evolution.entity.Bonobo::new, MobCategory.CREATURE)
                    .sized(0.7F, 1.15F).clientTrackingRange(10).build("bonobo"));

    /** What waits at the water. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Crocodile>> CROCODILE =
            ENTITY_TYPES.register("crocodile", () -> EntityType.Builder.<dev.hominin.evolution.entity.Crocodile>of(
                    dev.hominin.evolution.entity.Crocodile::new, MobCategory.CREATURE)
                    .sized(1.4F, 0.6F).clientTrackingRange(10).build("crocodile"));

    /** The giant baboon: the one animal out here that answers a threat display. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Dinopithecus>> DINOPITHECUS =
            ENTITY_TYPES.register("dinopithecus", () -> EntityType.Builder.<dev.hominin.evolution.entity.Dinopithecus>of(
                    dev.hominin.evolution.entity.Dinopithecus::new, MobCategory.CREATURE)
                    .sized(1.0F, 1.7F).clientTrackingRange(10).build("dinopithecus"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Pachycrocuta>> PACHYCROCUTA =
            ENTITY_TYPES.register("pachycrocuta", () -> EntityType.Builder.<dev.hominin.evolution.entity.Pachycrocuta>of(
                    dev.hominin.evolution.entity.Pachycrocuta::new, MobCategory.CREATURE)
                    .sized(1.5F, 1.8F).clientTrackingRange(12).build("pachycrocuta"));

    /** The clan hyena: the spotted hyena's early line, and the thing hominins fought over carcasses with. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Crocuta>> CROCUTA =
            ENTITY_TYPES.register("crocuta", () -> EntityType.Builder.<dev.hominin.evolution.entity.Crocuta>of(
                    dev.hominin.evolution.entity.Crocuta::new, MobCategory.CREATURE)
                    .sized(0.9F, 1.1F).clientTrackingRange(10).build("crocuta"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Sabertooth>> SABERTOOTH =
            ENTITY_TYPES.register("sabertooth", () -> EntityType.Builder.<dev.hominin.evolution.entity.Sabertooth>of(
                    dev.hominin.evolution.entity.Sabertooth::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.4F).clientTrackingRange(10).build("sabertooth"));

    /** Scimitar cat: the daylight pursuit hunter of the open plain. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Homotherium>> HOMOTHERIUM =
            ENTITY_TYPES.register("homotherium", () -> EntityType.Builder.<dev.hominin.evolution.entity.Homotherium>of(
                    dev.hominin.evolution.entity.Homotherium::new, MobCategory.CREATURE)
                    .sized(1.2F, 1.5F).clientTrackingRange(10).build("homotherium"));

    /** The giant buffalo: megafauna, and what an erectus hunt is measured against. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Pelorovis>> PELOROVIS =
            ENTITY_TYPES.register("pelorovis", () -> EntityType.Builder.<dev.hominin.evolution.entity.Pelorovis>of(
                    dev.hominin.evolution.entity.Pelorovis::new, MobCategory.CREATURE)
                    .sized(1.9F, 1.9F).clientTrackingRange(10).build("pelorovis"));

    /** The later megafauna: the first mammoth, the giant hartebeest, and the antelope with the voice. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.Mammuthus>> MAMMUTHUS =
            ENTITY_TYPES.register("mammuthus", () -> EntityType.Builder.<dev.hominin.evolution.entity.Mammuthus>of(
                    dev.hominin.evolution.entity.Mammuthus::new, MobCategory.CREATURE)
                    .sized(2.9F, 4.5F).clientTrackingRange(12).build("mammuthus"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Megalotragus>> MEGALOTRAGUS =
            ENTITY_TYPES.register("megalotragus", () -> EntityType.Builder.<dev.hominin.evolution.entity.Megalotragus>of(
                    dev.hominin.evolution.entity.Megalotragus::new, MobCategory.CREATURE)
                    .sized(1.7F, 2.5F).clientTrackingRange(10).build("megalotragus"));

    public static final Supplier<EntityType<dev.hominin.evolution.entity.Rusingoryx>> RUSINGORYX =
            ENTITY_TYPES.register("rusingoryx", () -> EntityType.Builder.<dev.hominin.evolution.entity.Rusingoryx>of(
                    dev.hominin.evolution.entity.Rusingoryx::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.8F).clientTrackingRange(10).build("rusingoryx"));

    /** The bird that takes children out of the open. */
    public static final Supplier<EntityType<dev.hominin.evolution.entity.CrownedEagle>> CROWNED_EAGLE =
            ENTITY_TYPES.register("crowned_eagle", () -> EntityType.Builder.<dev.hominin.evolution.entity.CrownedEagle>of(
                    dev.hominin.evolution.entity.CrownedEagle::new, MobCategory.CREATURE)
                    .sized(1.0F, 0.9F).clientTrackingRange(12).build("crowned_eagle"));

    private ModEntities() {
    }
}
