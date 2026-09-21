package dev.hominin.evolution.item;

import java.util.UUID;

import dev.hominin.evolution.ModEntities;
import dev.hominin.evolution.band.WildBands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

/**
 * A spawn egg for a species that only ever exists as somebody else's band - Paranthropus,
 * say. It puts down a small wild troop of that species, rather than one lone hominin of
 * whatever kind the band-member egg would make.
 */
public class TroopSpawnEggItem extends DeferredSpawnEggItem {
    /** Null: whatever species the player using it is. */
    @javax.annotation.Nullable
    private final ResourceLocation stage;
    private final int size;

    /** A size of 0 means the species' own starting band size. */
    public TroopSpawnEggItem(@javax.annotation.Nullable ResourceLocation stage, int size, int background, int highlight,
            Properties properties) {
        super(ModEntities.BAND_MEMBER, background, highlight, properties);
        this.stage = stage;
        this.size = size;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        BlockPos at = state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(context.getClickedFace());
        ResourceLocation species = stage != null ? stage
                : context.getPlayer() != null
                        ? context.getPlayer().getData(dev.hominin.evolution.Attachments.PLAYER_EVOLUTION_DATA).getStage()
                        : ResourceLocation.fromNamespaceAndPath(dev.hominin.evolution.HomininEvolutionMod.MODID,
                                "australopithecus");
        int count = size > 0 ? size : Math.max(3, dev.hominin.evolution.band.BandSizes.of(species).start());
        if (WildBands.placeBand(level, at, species, count, UUID.randomUUID(), level.getRandom()) != null) {
            ItemStack stack = context.getItemInHand();
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResult.CONSUME;
    }
}
