package dev.hominin.evolution.block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Whose nest this is. A nest belongs to whoever wove it, and nobody else lies down in it: not another member of
 * the band, and not you - unless you are their mate, or close enough to them (bond 4 and up) that they do not
 * mind. Mates share. A wild band shares one nest between all of it, the way chimpanzees do not but early
 * hominins on the ground may well have.
 *
 * <p>A nest whose maker is nowhere about - left behind by a band passing through, or by someone long dead - is
 * anybody's.
 */
public final class NestOwners extends SavedData {
    private static final String NAME = "hominin_evolution_nest_owners";
    /** How close a bond has to be before someone lets you into their nest. */
    public static final int SHARE_BOND = 4;

    private final Map<Long, UUID> owners = new HashMap<>();

    private static NestOwners of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(NestOwners::new, NestOwners::load), NAME);
    }

    public static void set(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        NestOwners data = of(level);
        if (owner == null) {
            if (data.owners.remove(pos.asLong()) != null) {
                data.setDirty();
            }
            return;
        }
        data.owners.put(pos.asLong(), owner);
        data.setDirty();
    }

    /** Whoever made any part of the nest this block belongs to. */
    @Nullable
    public static UUID ownerOf(ServerLevel level, BlockPos pos) {
        NestOwners data = of(level);
        UUID owner = data.owners.get(pos.asLong());
        if (owner != null) {
            return owner;
        }
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 0, 2))) {
            if (level.getBlockState(near).is(ModBlocks.NEST.get())) {
                UUID other = data.owners.get(near.asLong());
                if (other != null) {
                    return other;
                }
            }
        }
        return null;
    }

    /** The member who made it, if they are about - or null for a player's nest, or one nobody is about to claim. */
    @Nullable
    public static BandMember maker(ServerLevel level, @Nullable UUID owner) {
        return owner != null && level.getEntity(owner) instanceof BandMember member && member.isAlive() ? member : null;
    }

    /**
     * Whether a band member may lie down here: their own nest, their mate's, a nest their own wild band shares,
     * or one nobody is about to claim.
     */
    public static boolean mayUse(BandMember member, BlockPos pos) {
        if (!(member.level() instanceof ServerLevel level)) {
            return true;
        }
        // A room decides first: their own, or their mate's - and never a store, or anyone else's.
        Boolean room = dev.hominin.evolution.build.Building.mayRest(member, pos);
        if (room != null) {
            return room;
        }
        UUID owner = ownerOf(level, pos);
        // Children sleep curled up with whoever's nest it is.
        if (owner == null || owner.equals(member.getUUID()) || member.isMateOf(owner) || member.isBaby()) {
            return true;
        }
        BandMember maker = maker(level, owner);
        if (maker != null) {
            // A wild band shares its nest; your own band each keep to their own.
            return member.isWild() && member.getBandId() != null && member.getBandId().equals(maker.getBandId());
        }
        Player player = level.getPlayerByUUID(owner);
        if (player != null) {
            return false;
        }
        return true;
    }

    /** Whether a player may lie down here. Null if they may; otherwise the maker who will not have it. */
    public static Refusal refusal(ServerLevel level, Player player, BlockPos pos) {
        String room = dev.hominin.evolution.build.Building.refusal(level, player, pos);
        if (room != null) {
            return new Refusal(null, room);
        }
        UUID owner = ownerOf(level, pos);
        if (owner == null || owner.equals(player.getUUID())) {
            return Refusal.NONE;
        }
        BandMember maker = maker(level, owner);
        if (maker == null) {
            Player other = level.getPlayerByUUID(owner);
            return other != null ? new Refusal(null, other.getName().getString() + " made this nest for themselves.")
                    : Refusal.NONE;
        }
        if (maker.isMateOf(player.getUUID())) {
            return Refusal.NONE;
        }
        maker.ensureName();
        if (maker.isLedBy(player)) {
            if (maker.getBond() >= SHARE_BOND) {
                return Refusal.NONE;
            }
            return new Refusal(maker, "That's my nest. Make your own. (Bond " + maker.getBond() + " - they let you in "
                    + "from " + SHARE_BOND + ", or if you are their mate.)");
        }
        return new Refusal(maker, "This is ours. Get out of it.");
    }

    public record Refusal(@Nullable BandMember maker, @Nullable String line) {
        static final Refusal NONE = new Refusal(null, null);

        public boolean refused() {
            return line != null;
        }
    }

    private static NestOwners load(CompoundTag tag, HolderLookup.Provider registries) {
        NestOwners data = new NestOwners();
        for (Tag entry : tag.getList("Owners", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) entry;
            data.owners.put(o.getLong("Pos"), o.getUUID("Owner"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : owners.entrySet()) {
            CompoundTag o = new CompoundTag();
            o.putLong("Pos", entry.getKey());
            o.putUUID("Owner", entry.getValue());
            list.add(o);
        }
        tag.put("Owners", list);
        return tag;
    }

    private NestOwners() {
    }
}
