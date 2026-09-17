package dev.hominin.evolution.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.hominin.evolution.block.NestBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/**
 * Vanilla lays every sleeper at mattress height. In a nest that would float them above
 * it, so a nest lays them on its floor instead.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "setPosToBed", at = @At("HEAD"), cancellable = true)
    private void hominin$lieInNest(BlockPos pos, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().getBlockState(pos).getBlock() instanceof NestBlock) {
            self.setPos(pos.getX() + 0.5D, pos.getY() + NestBlock.SLEEP_HEIGHT, pos.getZ() + 0.5D);
            ci.cancel();
        }
    }
}
