package dev.hominin.evolution.mind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * What a mind holds about the country: the mental map.
 *
 * <p>A few places, kept on purpose - a seam of good stone, a lava pool, where a hyena clan lives, somewhere
 * you named yourself - as many as this mind has room for: two to four below erectus, four to seven from
 * erectus, rolled with each new body. Things the band told you, kept for the day. The name you gave your
 * own band. And where you are walking to, if you are following something.
 *
 * <p>Other bands, Paranthropus troops and friendly troops are not memories: they are people you know, and
 * the map always shows them (see {@link MentalMap}).
 */
public final class MindData {
    /** One remembered place. {@code day} is when it was remembered - or, for something told, when it lapses. */
    public record Memory(String kind, String label, BlockPos pos, long day) {
        public static final Codec<Memory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("kind").forGetter(Memory::kind),
                Codec.STRING.fieldOf("label").forGetter(Memory::label),
                BlockPos.CODEC.fieldOf("pos").forGetter(Memory::pos),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Memory::day)
        ).apply(instance, Memory::new));

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Kind", kind);
            tag.putString("Label", label);
            tag.putLong("Pos", pos.asLong());
            tag.putLong("Day", day);
            return tag;
        }

        public static Memory fromTag(CompoundTag tag) {
            return new Memory(tag.getString("Kind"), tag.getString("Label"), BlockPos.of(tag.getLong("Pos")), tag.getLong("Day"));
        }

        public static ListTag listTag(List<Memory> memories) {
            ListTag list = new ListTag();
            memories.forEach(m -> list.add(m.toTag()));
            return list;
        }

        public static List<Memory> fromList(ListTag list) {
            List<Memory> memories = new ArrayList<>();
            for (Tag tag : list) {
                memories.add(fromTag((CompoundTag) tag));
            }
            return memories;
        }
    }

    public static final Codec<MindData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("slots", 0).forGetter(d -> d.slots),
            Memory.CODEC.listOf().optionalFieldOf("memories", List.of()).forGetter(d -> List.copyOf(d.memories)),
            Memory.CODEC.listOf().optionalFieldOf("told", List.of()).forGetter(d -> List.copyOf(d.told)),
            Memory.CODEC.listOf().optionalFieldOf("troops", List.of()).forGetter(d -> List.copyOf(d.troops)),
            Codec.STRING.optionalFieldOf("band_name", "").forGetter(d -> d.bandName),
            Codec.BOOL.optionalFieldOf("name_pending", false).forGetter(d -> d.namePending),
            BlockPos.CODEC.optionalFieldOf("waypoint").forGetter(d -> Optional.ofNullable(d.waypoint)),
            Codec.STRING.optionalFieldOf("waypoint_label", "").forGetter(d -> d.waypointLabel),
            Codec.STRING.optionalFieldOf("waypoint_band", "").forGetter(d -> d.waypointBand),
            Codec.STRING.optionalFieldOf("body_name", "").forGetter(d -> d.bodyName)
    ).apply(instance, MindData::new));

    private int slots;
    private final List<Memory> memories = new ArrayList<>();
    private final List<Memory> told = new ArrayList<>();
    private final List<Memory> troops = new ArrayList<>();
    private String bandName = "";
    private boolean namePending;
    @Nullable
    private BlockPos waypoint;
    private String waypointLabel = "";
    private String waypointBand = "";
    /** The name of the one you are now - the member you woke as, or swapped into. Empty for the first. */
    private String bodyName = "";

    public MindData() {
    }

    private MindData(int slots, List<Memory> memories, List<Memory> told, List<Memory> troops, String bandName,
            boolean namePending, Optional<BlockPos> waypoint, String waypointLabel, String waypointBand, String bodyName) {
        this.slots = slots;
        this.memories.addAll(memories);
        this.told.addAll(told);
        this.troops.addAll(troops);
        this.bandName = bandName;
        this.namePending = namePending;
        this.waypoint = waypoint.orElse(null);
        this.waypointLabel = waypointLabel;
        this.waypointBand = waypointBand;
        this.bodyName = bodyName;
    }

    public String bodyName() {
        return bodyName;
    }

    public void setBodyName(String bodyName) {
        this.bodyName = bodyName;
    }

    /** A clean mind: nothing held, nothing told, nobody followed. The band's name and the body's stay. */
    public void wipe() {
        memories.clear();
        told.clear();
        troops.clear();
        waypoint = null;
        waypointLabel = "";
        waypointBand = "";
    }

    public int slots() {
        return slots;
    }

    public void setSlots(int slots) {
        this.slots = slots;
        while (memories.size() > slots && !memories.isEmpty()) {
            memories.remove(0);
        }
    }

    public List<Memory> memories() {
        return memories;
    }

    public List<Memory> told() {
        return told;
    }

    public List<Memory> troops() {
        return troops;
    }

    public String bandName() {
        return bandName;
    }

    public void setBandName(String bandName) {
        this.bandName = bandName;
    }

    public boolean namePending() {
        return namePending;
    }

    public void setNamePending(boolean namePending) {
        this.namePending = namePending;
    }

    @Nullable
    public BlockPos waypoint() {
        return waypoint;
    }

    public String waypointLabel() {
        return waypointLabel;
    }

    public String waypointBand() {
        return waypointBand;
    }

    public void setWaypoint(@Nullable BlockPos waypoint, String label, String band) {
        this.waypoint = waypoint;
        this.waypointLabel = label;
        this.waypointBand = band;
    }
}
