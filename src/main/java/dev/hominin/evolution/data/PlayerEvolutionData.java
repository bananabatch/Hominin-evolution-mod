package dev.hominin.evolution.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.hominin.evolution.HomininEvolutionMod;
import net.minecraft.resources.ResourceLocation;

public class PlayerEvolutionData {
    public static final ResourceLocation DEFAULT_STAGE = ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "australopithecus");

    public static final Codec<PlayerEvolutionData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("stage", DEFAULT_STAGE).forGetter(PlayerEvolutionData::getStage),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("criterion_counters", Map.of()).forGetter(PlayerEvolutionData::getCriterionCounters),
            ResourceLocation.CODEC.listOf().optionalFieldOf("notified_ready_stages", List.of()).forGetter(d -> List.copyOf(d.notifiedReadyStages)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("foraged_biomes", List.of()).forGetter(d -> List.copyOf(d.foragedBiomes)),
            Codec.INT.optionalFieldOf("meat_killed", 0).forGetter(PlayerEvolutionData::getMeatKilled),
            Codec.INT.optionalFieldOf("meat_scavenged", 0).forGetter(PlayerEvolutionData::getMeatScavenged),
            Codec.INT.optionalFieldOf("foraged", 0).forGetter(PlayerEvolutionData::getForaged),
            ResourceLocation.CODEC.listOf().optionalFieldOf("unlocked_recipes", List.of()).forGetter(d -> List.copyOf(d.unlockedRecipes)),
            Codec.STRING.optionalFieldOf("clan_id").forGetter(d -> Optional.ofNullable(d.clanId)),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("learned_languages", Map.of()).forGetter(PlayerEvolutionData::getLearnedLanguages),
            ResourceLocation.CODEC.listOf().optionalFieldOf("crafted_oldowan_tools", List.of()).forGetter(d -> List.copyOf(d.craftedOldowanTools)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("water_source_biomes", List.of()).forGetter(d -> List.copyOf(d.waterSourceBiomes)),
            Codec.BOOL.optionalFieldOf("developer_mode", false).forGetter(PlayerEvolutionData::isDeveloperMode),
            Codec.FLOAT.optionalFieldOf("stage_start_walk_distance", 0.0F).forGetter(PlayerEvolutionData::getStageStartWalkDistance),
            Codec.INT.optionalFieldOf("distance_credits", 0).forGetter(PlayerEvolutionData::getDistanceCredits)
    ).apply(instance, PlayerEvolutionData::fromCodec));

    private ResourceLocation stage;
    private final Map<String, Integer> criterionCounters;
    private final Set<ResourceLocation> notifiedReadyStages;
    private final Set<ResourceLocation> foragedBiomes;
    private int meatKilled;
    private int meatScavenged;
    private int foraged;
    private final Set<ResourceLocation> unlockedRecipes;
    @Nullable
    private String clanId;
    private final Map<String, Float> learnedLanguages;
    private final Set<ResourceLocation> craftedOldowanTools;
    private final Set<ResourceLocation> waterSourceBiomes;
    /** Testing escape hatch: every gate and tool requirement waves this player through. */
    private boolean developerMode;
    /**
     * Snapshot of the player's lifetime walk distance when this stage began, so
     * distance credit measures ground covered *at this stage* - criterion counters
     * wipe on evolve, and a lifetime total would hand a fresh stage free progress.
     */
    private float stageStartWalkDistance;
    /** How many distance milestones have already paid out during this stage. */
    private int distanceCredits;
    private long lastCountedDay = -1;

    public PlayerEvolutionData() {
        this(DEFAULT_STAGE, new HashMap<>(), new HashSet<>(), new HashSet<>(), 0, 0, 0, new HashSet<>(), null, new HashMap<>(), new HashSet<>(),
                new HashSet<>(), false, 0.0F, 0);
    }

    private PlayerEvolutionData(ResourceLocation stage, Map<String, Integer> criterionCounters, Set<ResourceLocation> notifiedReadyStages,
            Set<ResourceLocation> foragedBiomes, int meatKilled, int meatScavenged, int foraged, Set<ResourceLocation> unlockedRecipes,
            @Nullable String clanId, Map<String, Float> learnedLanguages, Set<ResourceLocation> craftedOldowanTools,
            Set<ResourceLocation> waterSourceBiomes, boolean developerMode, float stageStartWalkDistance, int distanceCredits) {
        this.stage = stage;
        this.criterionCounters = criterionCounters;
        this.notifiedReadyStages = notifiedReadyStages;
        this.foragedBiomes = foragedBiomes;
        this.meatKilled = meatKilled;
        this.meatScavenged = meatScavenged;
        this.foraged = foraged;
        this.unlockedRecipes = unlockedRecipes;
        this.clanId = clanId;
        this.learnedLanguages = learnedLanguages;
        this.craftedOldowanTools = craftedOldowanTools;
        this.waterSourceBiomes = waterSourceBiomes;
        this.developerMode = developerMode;
        this.stageStartWalkDistance = stageStartWalkDistance;
        this.distanceCredits = distanceCredits;
    }

    private static PlayerEvolutionData fromCodec(ResourceLocation stage, Map<String, Integer> criterionCounters, List<ResourceLocation> notifiedReadyStages,
            List<ResourceLocation> foragedBiomes, int meatKilled, int meatScavenged, int foraged, List<ResourceLocation> unlockedRecipes,
            Optional<String> clanId, Map<String, Float> learnedLanguages, List<ResourceLocation> craftedOldowanTools,
            List<ResourceLocation> waterSourceBiomes, boolean developerMode, float stageStartWalkDistance, int distanceCredits) {
        return new PlayerEvolutionData(stage, new HashMap<>(criterionCounters), new HashSet<>(notifiedReadyStages), new HashSet<>(foragedBiomes),
                meatKilled, meatScavenged, foraged, new HashSet<>(unlockedRecipes), clanId.orElse(null), new HashMap<>(learnedLanguages),
                new HashSet<>(craftedOldowanTools), new HashSet<>(waterSourceBiomes), developerMode, stageStartWalkDistance, distanceCredits);
    }

    public ResourceLocation getStage() {
        return stage;
    }

    public void setStage(ResourceLocation stage) {
        this.stage = stage;
    }

    public Map<String, Integer> getCriterionCounters() {
        return criterionCounters;
    }

    public Set<ResourceLocation> getNotifiedReadyStages() {
        return notifiedReadyStages;
    }

    public Set<ResourceLocation> getForagedBiomes() {
        return foragedBiomes;
    }

    public int getMeatKilled() {
        return meatKilled;
    }

    public void addMeatKilled(int amount) {
        this.meatKilled += amount;
    }

    public int getMeatScavenged() {
        return meatScavenged;
    }

    public void addMeatScavenged(int amount) {
        this.meatScavenged += amount;
    }

    public int getForaged() {
        return foraged;
    }

    public void addForaged(int amount) {
        this.foraged += amount;
    }

    public Set<ResourceLocation> getUnlockedRecipes() {
        return unlockedRecipes;
    }

    @Nullable
    public String getClanId() {
        return clanId;
    }

    public void setClanId(@Nullable String clanId) {
        this.clanId = clanId;
    }

    public Map<String, Float> getLearnedLanguages() {
        return learnedLanguages;
    }

    public Set<ResourceLocation> getCraftedOldowanTools() {
        return craftedOldowanTools;
    }

    public Set<ResourceLocation> getWaterSourceBiomes() {
        return waterSourceBiomes;
    }

    public boolean isDeveloperMode() {
        return developerMode;
    }

    public void setDeveloperMode(boolean developerMode) {
        this.developerMode = developerMode;
    }

    public float getStageStartWalkDistance() {
        return stageStartWalkDistance;
    }

    public void setStageStartWalkDistance(float stageStartWalkDistance) {
        this.stageStartWalkDistance = stageStartWalkDistance;
    }

    public int getDistanceCredits() {
        return distanceCredits;
    }

    public void setDistanceCredits(int distanceCredits) {
        this.distanceCredits = distanceCredits;
    }

    public long getLastCountedDay() {
        return lastCountedDay;
    }

    public void setLastCountedDay(long lastCountedDay) {
        this.lastCountedDay = lastCountedDay;
    }
}
