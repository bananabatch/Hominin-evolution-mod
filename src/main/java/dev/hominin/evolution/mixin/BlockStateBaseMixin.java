package dev.hominin.evolution.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.hominin.evolution.climb.Climbing;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lets a climbing player move through leaves.
 *
 * <p>This is the one place every entity-aware collision query ends up - movement,
 * the ground check, and the server's "moved into a block" check all go through it -
 * so client and server agree about the canopy as long as they agree about who is
 * climbing. A player who is not climbing gets the normal full leaf block, which is
 * what lets them stand on the canopy once they stop.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void hominin$passThroughLeaves(BlockGetter level, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        // Cheapest checks first: this runs for every block near every moving entity.
        if (context instanceof EntityCollisionContext entityContext) {
            net.minecraft.world.entity.Entity entity = entityContext.getEntity();
            if (entity instanceof Player player) {
                if (((BlockBehaviour.BlockStateBase) (Object) this).is(BlockTags.LEAVES) && Climbing.isClimbing(player)) {
                    cir.setReturnValue(Shapes.empty());
                }
            } else if (entity instanceof dev.hominin.evolution.band.BandMember member
                    && ((BlockBehaviour.BlockStateBase) (Object) this).is(BlockTags.LEAVES)
                    && member.phasesThroughLeaves()) {
                // Band members go through the canopy the same way, while climbing and dropping out of it.
                cir.setReturnValue(Shapes.empty());
            }
        }
    }
}
