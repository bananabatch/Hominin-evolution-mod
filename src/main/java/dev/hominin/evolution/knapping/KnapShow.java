package dev.hominin.evolution.knapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.block.KnappingStationBlockEntity;
import dev.hominin.evolution.network.BodyAnimationPayload;
import dev.hominin.evolution.network.KnapHandsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Knapping, seen. Working stone is no longer a click and a result: it is done with the hands, and takes as long as a
 * few blows take.
 *
 * <p><b>In the hands:</b> the stone held in the right hand, the hammerstone in the left brought down on it three
 * times, stone dust and chips coming off each blow; then what was made is in the hand, lifted and looked over, and the
 * hands go back to what they held.
 *
 * <p><b>At the station:</b> the stone to be worked lies out on the mat; where the work wants a bone, one hand takes
 * the bone up and taps the stone with it, then lays it back; then both hands take the hammerstone and bring it down on
 * the stone; and there on the mat, and then in the hand, is the tool.
 *
 * <p>Nothing is spent until the last blow: the stone is worked, and paid for, when it breaks - so a knapping cut
 * short (walking off, dying, leaving) costs nothing. What is shown in the hands is only shown: the real inventory is
 * never touched until the tool is made.
 */
public final class KnapShow {
    private enum Kind {
        HAND, STATION
    }

    private static final class Session {
        final Kind kind;
        final KnappingChoice choice;
        @Nullable
        final BlockPos station;
        final ItemStack stone;
        final ItemStack bone;
        final ItemStack hammer;
        final long start;
        /** When the stone breaks and the tool is made. */
        final int resolveAt;
        final int endAt;
        final boolean withBone;
        ItemStack made = ItemStack.EMPTY;
        /** What is being shown in the hands, if anything - sent again now and then, so a real change cannot undo it. */
        @Nullable
        ItemStack shownMain;
        ItemStack shownOff = ItemStack.EMPTY;

        Session(Kind kind, KnappingChoice choice, @Nullable BlockPos station, ItemStack stone, ItemStack bone,
                ItemStack hammer, long start, boolean withBone) {
            this.kind = kind;
            this.choice = choice;
            this.station = station;
            this.stone = stone;
            this.bone = bone;
            this.hammer = hammer;
            this.start = start;
            this.withBone = withBone;
            if (kind == Kind.HAND) {
                resolveAt = HAND_RESOLVE;
            } else {
                resolveAt = (withBone ? SMASH_AFTER_BONE : LOOK) + SMASH_LENGTH;
            }
            endAt = resolveAt + INSPECT;
        }

        int smashStart() {
            return withBone ? SMASH_AFTER_BONE : LOOK;
        }
    }

    private static final int HAND_RESOLVE = 32;
    private static final int[] HAND_STRIKES = {8, 18, 28};
    /** A moment to see the stone laid out before the work starts. */
    private static final int LOOK = 8;
    private static final int BONE_LENGTH = 30;
    private static final int[] BONE_TAPS = {10, 18, 26};
    private static final int SET_DOWN = 6;
    private static final int SMASH_AFTER_BONE = LOOK + BONE_LENGTH + SET_DOWN;
    private static final int SMASH_LENGTH = 30;
    private static final int[] SMASH_BLOWS = {10, 24};
    private static final int INSPECT = 30;
    /** Walking further than this from the station leaves the work undone. */
    private static final double STATION_REACH = 5.0D;

    private static final Map<UUID, Session> sessions = dev.hominin.evolution.ServerState.track(new HashMap<>());

    public static boolean busy(ServerPlayer player) {
        return sessions.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------ starting

    /** Knapping in the hands, chosen from the knapping screen: checked now, worked over the next second and a half. */
    public static void beginHand(ServerPlayer player, @Nullable KnappingChoice choice) {
        if (choice == null || busy(player) || Acheulean.isAcheulean(choice)) {
            return;
        }
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (!off.is(ModTags.Items.HAMMERSTONES) || !Knapping.choicesFor(main).contains(choice)) {
            return;
        }
        Session s = new Session(Kind.HAND, choice, null, main.copyWithCount(1), ItemStack.EMPTY, off.copyWithCount(1),
                player.level().getGameTime(), false);
        sessions.put(player.getUUID(), s);
        animate(player, "knap_hand_strike");
    }

    /**
     * Knapping at a station, chosen from its screen. The screen closes so the work can be watched; what the work wants
     * laid out is checked now, and again when the stone breaks.
     */
    public static void beginStation(ServerPlayer player, KnappingStationBlockEntity station, KnappingChoice choice) {
        if (busy(player)) {
            return;
        }
        var items = station.items();
        ItemStack hammer = items.getItem(KnappingStationBlockEntity.HAMMER);
        if (hammer.isEmpty() || hammer.is(ModItems.OBSIDIAN_CHUNK.get())) {
            // No hammer, or glass for one: the work itself says so (and the glass bursts).
            StationKnapping.knap(player, items, choice, station.getBlockPos());
            return;
        }
        boolean acheulean = Acheulean.isAcheulean(choice);
        boolean levallois = StationKnapping.LEVALLOIS.contains(choice);
        if (acheulean && !Acheulean.canUse(player) || levallois && !Acheulean.canUseLevallois(player)
                || acheulean && items.getItem(KnappingStationBlockEntity.BOPPER).isEmpty()) {
            StationKnapping.knap(player, items, choice, station.getBlockPos());
            return;
        }
        ItemStack stone = ItemStack.EMPTY;
        for (int slot = KnappingStationBlockEntity.STONES_START; slot < KnappingStationBlockEntity.SIZE; slot++) {
            ItemStack laid = items.getItem(slot);
            if (!laid.isEmpty() && (acheulean ? !laid.is(ModItems.HAMMERSTONE.get()) : true)) {
                stone = laid.copyWithCount(1);
                break;
            }
        }
        if (stone.isEmpty()) {
            StationKnapping.knap(player, items, choice, station.getBlockPos());
            return;
        }
        ItemStack bone = items.getItem(KnappingStationBlockEntity.BOPPER).copyWithCount(1);
        // The bone is for the Acheulean and the Levallois - the fine work. The Oldowan is the hammer alone.
        boolean withBone = (acheulean || levallois) && !bone.isEmpty();
        Session s = new Session(Kind.STATION, choice, station.getBlockPos(), stone, bone, hammer.copyWithCount(1),
                player.level().getGameTime(), withBone);
        sessions.put(player.getUUID(), s);
        player.closeContainer();
        station.setDisplay(stone, false);
    }

    // ------------------------------------------------------------ the work, a tick at a time

    public static void tick(ServerPlayer player) {
        Session s = sessions.get(player.getUUID());
        if (s == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int t = (int) (level.getGameTime() - s.start);
        if (s.shownMain != null && t % 4 == 0) {
            send(player, s.shownMain, s.shownOff);
        }
        if (s.kind == Kind.STATION && (s.station == null || !level.isLoaded(s.station)
                || !(level.getBlockEntity(s.station) instanceof KnappingStationBlockEntity)
                || player.distanceToSqr(Vec3.atCenterOf(s.station)) > STATION_REACH * STATION_REACH)) {
            player.displayClientMessage(Component.literal("You leave the stone where it lies."), true);
            finish(player, s);
            return;
        }
        if (t < s.resolveAt) {
            if (s.kind == Kind.HAND) {
                handWork(player, level, s, t);
            } else {
                stationWork(player, level, s, t);
            }
            return;
        }
        if (t == s.resolveAt) {
            resolve(player, s);
            if (s.made.isEmpty()) {
                finish(player, s);
                return;
            }
            // The tool, in the hand, looked over.
            show(player, s.made, s.kind == Kind.HAND ? player.getOffhandItem() : ItemStack.EMPTY);
            animate(player, "knap_inspect");
            return;
        }
        if (t >= s.endAt) {
            finish(player, s);
        }
    }

    private static void handWork(ServerPlayer player, ServerLevel level, Session s, int t) {
        for (int strike : HAND_STRIKES) {
            if (t == strike) {
                Vec3 hands = handsOf(player);
                chips(level, hands, s.stone);
                level.playSound(null, player.blockPosition(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F,
                        0.9F + player.getRandom().nextFloat() * 0.3F);
            }
        }
    }

    private static void stationWork(ServerPlayer player, ServerLevel level, Session s, int t) {
        Vec3 onMat = Vec3.atCenterOf(s.station).add(0.0D, -0.35D, 0.0D);
        if (s.withBone) {
            if (t == LOOK) {
                // One hand takes the bone up off the mat.
                show(player, s.bone, ItemStack.EMPTY);
                station(player).ifPresent(be -> be.lift(false, true));
                animate(player, "station_bop");
            }
            for (int tap : BONE_TAPS) {
                if (t == LOOK + tap) {
                    level.sendParticles(ParticleTypes.SMOKE, onMat.x, onMat.y + 0.1D, onMat.z, 2, 0.04D, 0.02D, 0.04D,
                            0.003D);
                    level.playSound(null, s.station, SoundEvents.BONE_BLOCK_HIT, SoundSource.PLAYERS, 0.5F, 1.6F);
                }
            }
            if (t == LOOK + BONE_LENGTH) {
                // And lays it back.
                show(player, ItemStack.EMPTY, ItemStack.EMPTY);
                station(player).ifPresent(be -> be.lift(false, false));
                animate(player, "station_set_down");
            }
        }
        int smash = s.smashStart();
        if (t == smash) {
            // Both hands on the hammerstone.
            show(player, s.hammer, ItemStack.EMPTY);
            station(player).ifPresent(be -> be.lift(true, false));
            animate(player, "station_smash");
        }
        for (int blow : SMASH_BLOWS) {
            if (t == smash + blow) {
                chips(level, onMat.add(0.0D, 0.1D, 0.0D), s.stone);
                level.playSound(null, s.station, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F,
                        0.8F + player.getRandom().nextFloat() * 0.2F);
            }
        }
    }

    /** The stone breaks, and the tool is made - by the same work as ever, which checks and pays for it all. */
    private static void resolve(ServerPlayer player, Session s) {
        List<ItemStack> before = snapshot(player);
        if (s.kind == Kind.HAND) {
            ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!ItemStack.isSameItem(main, s.stone) || !player.getOffhandItem().is(ModTags.Items.HAMMERSTONES)) {
                player.displayClientMessage(Component.literal("The stone is not in your hands any more."), true);
                return;
            }
            Knapping.resolve(player, s.choice);
        } else {
            var be = station(player);
            if (be.isEmpty()) {
                return;
            }
            be.get().lift(false, false);
            StationKnapping.knap(player, be.get().items(), s.choice, s.station);
        }
        s.made = gained(before, snapshot(player));
        if (!s.made.isEmpty() && s.kind == Kind.STATION) {
            station(player).ifPresent(be -> be.setDisplay(s.made, true));
        }
    }

    private static void finish(ServerPlayer player, Session s) {
        sessions.remove(player.getUUID());
        restore(player);
        if (s.kind == Kind.STATION) {
            station(player, s).ifPresent(be -> {
                be.lift(false, false);
                be.setDisplay(ItemStack.EMPTY, false);
            });
        }
    }

    /** Leaving or dying mid-work: nothing was spent, and nothing is shown any more. */
    public static void forget(ServerPlayer player) {
        Session s = sessions.get(player.getUUID());
        if (s != null) {
            finish(player, s);
        }
    }

    // ------------------------------------------------------------ what is seen

    private static void animate(ServerPlayer player, String name) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new BodyAnimationPayload(player.getId(), name));
    }

    /** What everyone - the knapper included - sees in the knapper's hands. Only seen: the inventory is untouched. */
    private static void show(ServerPlayer player, ItemStack main, ItemStack off) {
        Session s = sessions.get(player.getUUID());
        if (s != null) {
            s.shownMain = main.copy();
            s.shownOff = off.copy();
        }
        send(player, main, off);
    }

    private static void send(ServerPlayer player, ItemStack main, ItemStack off) {
        // Everyone else sees it held. The knapper is told apart, to draw it only: the equipment packet would put it
        // in their selected slot - a second tool in the hotbar beside the real one, and one creative mode keeps.
        others(player, main, off);
        PacketDistributor.sendToPlayer(player, new KnapHandsPayload(true, main.copy(), off.copy()));
    }

    private static void others(ServerPlayer player, ItemStack main, ItemStack off) {
        player.serverLevel().getChunkSource().broadcast(player, new ClientboundSetEquipmentPacket(player.getId(),
                List.of(Pair.of(EquipmentSlot.MAINHAND, main.copy()), Pair.of(EquipmentSlot.OFFHAND, off.copy()))));
    }

    /** The hands as they really are again. */
    private static void restore(ServerPlayer player) {
        others(player, player.getMainHandItem(), player.getOffhandItem());
        PacketDistributor.sendToPlayer(player, new KnapHandsPayload(false, ItemStack.EMPTY, ItemStack.EMPTY));
        player.inventoryMenu.sendAllDataToRemote();
    }

    private static Vec3 handsOf(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.ZERO : flat.normalize();
        return player.getEyePosition().add(flat.scale(0.45D)).subtract(0.0D, 0.65D, 0.0D);
    }

    /** A blow: a small puff of stone dust, and a few chips of the stone. */
    private static void chips(ServerLevel level, Vec3 at, ItemStack stone) {
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.05D, 0.03D, 0.05D, 0.004D);
        if (!stone.isEmpty()) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stone), at.x, at.y, at.z, 4, 0.05D, 0.03D,
                    0.05D, 0.06D);
        }
    }

    private static java.util.Optional<KnappingStationBlockEntity> station(ServerPlayer player) {
        Session s = sessions.get(player.getUUID());
        return s == null ? java.util.Optional.empty() : station(player, s);
    }

    private static java.util.Optional<KnappingStationBlockEntity> station(ServerPlayer player, Session s) {
        if (s.station == null || !player.serverLevel().isLoaded(s.station)) {
            return java.util.Optional.empty();
        }
        return player.serverLevel().getBlockEntity(s.station) instanceof KnappingStationBlockEntity be
                ? java.util.Optional.of(be) : java.util.Optional.empty();
    }

    // ------------------------------------------------------------ what came out of it

    private static List<ItemStack> snapshot(ServerPlayer player) {
        List<ItemStack> all = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            all.add(inventory.getItem(slot).copy());
        }
        return all;
    }

    /** The thing the work put into the inventory: a slot that has more of something than it had. Tools first. */
    private static ItemStack gained(List<ItemStack> before, List<ItemStack> after) {
        ItemStack best = ItemStack.EMPTY;
        for (int slot = 0; slot < Math.min(before.size(), after.size()); slot++) {
            ItemStack was = before.get(slot);
            ItemStack now = after.get(slot);
            if (now.isEmpty()) {
                continue;
            }
            boolean grew = was.isEmpty() || !ItemStack.isSameItemSameComponents(was, now) || now.getCount() > was.getCount();
            if (!grew) {
                continue;
            }
            if (best.isEmpty() || dev.hominin.evolution.item.StoneMaterial.isStoneTool(now)
                    && !dev.hominin.evolution.item.StoneMaterial.isStoneTool(best)) {
                best = now.copyWithCount(1);
            }
        }
        return best;
    }

    private KnapShow() {
    }
}
