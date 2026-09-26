package dev.hominin.evolution.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A spindle and a hearth board. Fire does not come at a click: hold use on what you want lit - a fire pit, a
 * campfire gone out, dry ground - and the spindle is rubbed back and forth between the palms for three seconds while
 * the dust smokes. Let go early and it is lost. Whether it then catches is the pit's business, as before.
 */
public class FireDrillItem extends Item {
    /** Three seconds of rubbing. */
    public static final int DRILL_TICKS = 60;
    /** Walk further than this from what you were drilling, and you have stopped. */
    private static final double REACH = 6.0D;

    /**
     * What each player is drilling, from the click that started it - one table a side, since in a single-player
     * world the client and the server share this class, and must not take each other's entries.
     */
    private static final Map<UUID, BlockHitResult> serverTargets = new HashMap<>();
    private static final Map<UUID, BlockHitResult> clientTargets = new HashMap<>();

    private static Map<UUID, BlockHitResult> targets(Level level) {
        return level.isClientSide() ? clientTargets : serverTargets;
    }

    public FireDrillItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRILL_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // The arms are the animation's; the item itself does nothing special in the hand.
        return UseAnim.NONE;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server && dev.hominin.evolution.band.Species.neverMakesFire(
                server.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            server.displayClientMessage(Component.literal("Your kind never learned to make fire. Find it burning, "
                    + "and carry it."), true);
            return InteractionResult.FAIL;
        }
        targets(context.getLevel()).put(player.getUUID(), new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), false));
        player.startUsingItem(context.getHand());
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(Component.literal("You set the spindle in the board and start to rub.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        BlockHitResult target = targets(level).get(entity.getUUID());
        if (target == null) {
            entity.releaseUsingItem();
            return;
        }
        Vec3 at = target.getLocation();
        if (entity.distanceToSqr(at) > REACH * REACH) {
            entity.releaseUsingItem();
            return;
        }
        int done = DRILL_TICKS - remaining;
        if (level.isClientSide()) {
            // The dust smokes more the longer it goes.
            if (done % Math.max(1, 6 - done / 12) == 0) {
                level.addParticle(ParticleTypes.SMOKE, at.x + (level.random.nextDouble() - 0.5D) * 0.2D, at.y + 0.05D,
                        at.z + (level.random.nextDouble() - 0.5D) * 0.2D, 0.0D, 0.02D, 0.0D);
            }
            return;
        }
        if (done % 6 == 0) {
            level.playSound(null, BlockPos.containing(at), SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.35F,
                    1.6F + level.random.nextFloat() * 0.3F);
        }
        if (done == DRILL_TICKS - 20) {
            level.playSound(null, BlockPos.containing(at), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        BlockHitResult target = targets(level).remove(entity.getUUID());
        if (target != null && entity instanceof ServerPlayer player) {
            light(player, stack, target);
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (targets(level).remove(entity.getUUID()) != null && entity instanceof ServerPlayer player
                && timeLeft > 0) {
            player.displayClientMessage(Component.literal("You stop before the dust catches.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Three seconds rubbed: now it catches - or does not - whatever it was held to. */
    private static void light(ServerPlayer player, ItemStack drill, BlockHitResult target) {
        BlockPos pos = target.getBlockPos();
        var level = player.serverLevel();
        if (level.getBlockEntity(pos) instanceof dev.hominin.evolution.block.FirePitBlockEntity pit) {
            pit.drillWith(player, drill);
            return;
        }
        if (dev.hominin.evolution.survival.Hearths.use(player, pos, drill)) {
            return;
        }
        dev.hominin.evolution.event.EvolutionEventHandler.workTheDrill(player, level, pos, target.getDirection());
    }

    public static void forget(UUID player) {
        serverTargets.remove(player);
    }
}
