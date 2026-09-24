package dev.hominin.evolution.client;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.network.PlaceToolPayload;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sneak-use at the ground with a stone tool in hand: the server decides whether that lays it down (on your own
 * ground) or searches the ground as before (anywhere else). Either way the item's own use is not wanted - a
 * hammerstone should not fly off after it has been put down.
 */
public final class ToolPlacing {
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        boolean pile = state.is(ModBlocks.TOOL_PILE.get());
        // Stone tools and bones anywhere; food and the rest only on the floor of your own store.
        if (!dev.hominin.evolution.band.ToolPiles.layable(event.getItemStack()) && !(dev.hominin.evolution.band.ToolPiles.storable(
                event.getItemStack()) && dev.hominin.evolution.build.SiteView.inOwnStore(pile ? event.getPos()
                        : event.getPos().above()))) {
            return;
        }
        if (!pile) {
            // Only bare ground: a deposit, a mound, a station keep their own sneak-use.
            if (event.getFace() != Direction.UP || state.hasBlockEntity()
                    || state.is(ModTags.Blocks.WORKABLE_STONE_DEPOSIT)
                    || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(HomininEvolutionMod.MODID)) {
                return;
            }
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        PacketDistributor.sendToServer(new PlaceToolPayload(event.getPos()));
    }

    private static long lastOpened;

    /**
     * Hitting a pile does not scatter it: it opens its menu - what is in it, who laid it, and who it is for. Taking
     * everything from it is how a pile goes.
     */
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || !event.getLevel().getBlockState(event.getPos()).is(ModBlocks.TOOL_PILE.get())) {
            return;
        }
        event.setCanceled(true);
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        long now = net.minecraft.Util.getMillis();
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START && now - lastOpened > 400L
                && net.minecraft.client.Minecraft.getInstance().screen == null) {
            lastOpened = now;
            PileScreen.request(event.getPos());
        }
    }

    private ToolPlacing() {
    }
}
