package dev.hominin.evolution.band.goal;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Lines;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Opening a giant carcass. A ribcage that size is far too big to pull apart by hand, but a member with a hammerstone
 * (or anything that counts as one) cracks it open just as a player does, and everything in it spills out for the
 * band - meat, ribs, long bones and bones - whatever the hyenas have not had.
 */
public class GiantCarcassGoal extends WalkAndDoGoal {
    private static final int SEARCH = 24;

    public GiantCarcassGoal(BandMember member) {
        super(member, 60, 2.5D, 600, 20);
    }

    @Override
    protected boolean wants(ServerLevel level) {
        return member.countOf(stack -> stack.is(ModTags.Items.HAMMERSTONES)) > 0;
    }

    @Override
    @Nullable
    protected BlockPos find(ServerLevel level) {
        BlockPos origin = member.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH, -3, -SEARCH), origin.offset(SEARCH, 3, SEARCH))) {
            if (!level.getBlockState(pos).is(ModBlocks.GIANT_CARCASS.get())) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    @Override
    protected BlockPos standAt(BlockPos at) {
        return at;
    }

    @Override
    protected boolean act(ServerLevel level, BlockPos at) {
        if (!level.getBlockState(at).is(ModBlocks.GIANT_CARCASS.get())) {
            return true;
        }
        Lines.say(member, "carcass_cracked");
        // Broken open with the stone in hand: it drops what a player's strike would.
        level.destroyBlock(at, true, member);
        return true;
    }
}
