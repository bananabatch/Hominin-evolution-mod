package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The band starts its own piles - but asks you where first. When your people carry more stone tools than they need
 * and there is no tool pile to put them on, one of them comes to you: "We carry more than we need. Where do we keep
 * it?" Here, where you stand; by the fire; or not now. Wherever you say, they lay the spares down there, and that is
 * where the band keeps its tools.
 */
public final class PileAsk {
    public static final int ACTION = 64;
    private static final int HERE = 0;
    private static final int BY_THE_FIRE = 1;
    private static final int NOT_NOW = 2;
    private static final String NEXT = "pile_ask_next_minute";

    private static final Map<UUID, BlockPos> fires = new HashMap<>();

    /** Every two minutes: does anyone have spares with nowhere to put them? */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 2400 != 1234 || player.isSpectator()
                || !dev.hominin.evolution.hunt.Predation.settled(player)) {
            return;
        }
        var counters = player.getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
        int minute = (int) (player.level().getGameTime() / 1200L);
        if (minute < counters.getOrDefault(NEXT, 0) || hasToolPile(player)) {
            return;
        }
        BandMember asker = null;
        for (BandMember member : Band.ownNear(player, 16.0D)) {
            if (!member.isBaby() && spares(member).size() > 0) {
                asker = member;
                break;
            }
        }
        if (asker == null) {
            return;
        }
        counters.put(NEXT, minute + 10);
        asker.ensureName();
        asker.getLookControl().setLookAt(player, 30.0F, 30.0F);
        BlockPos fire = fireNear(player);
        List<String> labels = new ArrayList<>(List.of("Here, where I'm standing"));
        List<Integer> values = new ArrayList<>(List.of(HERE));
        if (fire != null) {
            fires.put(player.getUUID(), fire);
            labels.add("By the fire");
            values.add(BY_THE_FIRE);
        }
        labels.add("Not now");
        values.add(NOT_NOW);
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(asker.getId(), ACTION,
                asker.getName().getString() + ": \"We carry more than we need. Where do we keep it?\"", labels, values));
    }

    private static boolean hasToolPile(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : ToolPiles.piles(level, player.getUUID())) {
            if (!level.isLoaded(pos) || level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile
                    && pile.kind() == ToolPileBlockEntity.Kind.TOOLS) {
                return true;
            }
        }
        return false;
    }

    /** Stone tools a member carries past the one they would keep. */
    private static List<Integer> spares(BandMember member) {
        List<Integer> slots = new ArrayList<>();
        var inventory = member.getInventory();
        boolean keptOne = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModTags.Items.STONE_TOOLS)) {
                if (!keptOne) {
                    keptOne = true;
                } else {
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    @Nullable
    private static BlockPos fireNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        for (BlockPos pos : BlockPos.betweenClosed(camp.offset(-24, -4, -24), camp.offset(24, 4, 24))) {
            if (dev.hominin.evolution.survival.Hearths.isLitHearth(level.getBlockState(pos))
                    || level.getBlockState(pos).is(dev.hominin.evolution.ModBlocks.FIRE_PIT.get())) {
                return pos.immutable();
            }
        }
        return null;
    }

    public static void choose(ServerPlayer player, int entityId, int choice) {
        ServerLevel level = player.serverLevel();
        BlockPos fire = fires.remove(player.getUUID());
        if (!(level.getEntity(entityId) instanceof BandMember asker) || !asker.isLedBy(player) || choice == NOT_NOW) {
            return;
        }
        BlockPos near = choice == BY_THE_FIRE && fire != null ? fire : player.blockPosition();
        BlockPos at = spot(level, near, choice == BY_THE_FIRE ? 2 : 0);
        if (at == null) {
            player.displayClientMessage(Component.literal("There is no room to put anything down there."), true);
            return;
        }
        int laid = 0;
        List<BandMember> carriers = new ArrayList<>(List.of(asker));
        for (BandMember member : Band.ownNear(player, 16.0D)) {
            if (member != asker && !member.isBaby()) {
                carriers.add(member);
            }
        }
        for (BandMember member : carriers) {
            List<Integer> slots = spares(member);
            for (int i = slots.size() - 1; i >= 0; i--) {
                ItemStack tool = member.getInventory().removeItemNoUpdate(slots.get(i));
                if (tool.isEmpty()) {
                    continue;
                }
                if (ToolPiles.layDown(level, at, player.getUUID(), tool, member)) {
                    laid++;
                } else {
                    member.addToInventory(tool);
                }
            }
        }
        asker.getNavigation().moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 1.0D);
        player.sendSystemMessage(Component.literal(laid == 0 ? asker.getName().getString() + " looks at the spot, and "
                + "thinks better of it."
                : laid + (laid == 1 ? " tool goes down " : " tools go down ") + (choice == BY_THE_FIRE ? "by the fire" : "there")
                        + ". That is where the band keeps its tools now.").withStyle(ChatFormatting.AQUA));
    }

    /** Open ground at or near a spot - beside it, for a fire. */
    @Nullable
    private static BlockPos spot(ServerLevel level, BlockPos near, int least) {
        for (int ring = least; ring <= 4; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = near.getX() + dx;
                    int z = near.getZ() + dz;
                    BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (Math.abs(at.getY() - near.getY()) <= 2 && ToolPiles.canPileAt(level, at)) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    private PileAsk() {
    }
}
