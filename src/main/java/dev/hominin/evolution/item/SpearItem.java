package dev.hominin.evolution.item;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.entity.ThrownSpear;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A spear: a hand weapon, and - held back and let go - a thrown one. Hold use to draw it back, release to throw.
 * Erectus throws overarm, hard and true: the shoulder that does it is the first of its kind. Earlier kinds can
 * throw a spear too, but it is a lob - slower, wilder, and it hits softer.
 */
public class SpearItem extends WoodenWeaponItem {
    /** Drawn back at least this long, or it is not a throw. */
    public static final int MIN_DRAW = 10;

    public SpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof ServerPlayer player) || getUseDuration(stack, entity) - timeLeft < MIN_DRAW) {
            return;
        }
        boolean overarm = dev.hominin.evolution.band.Bands.erectusOn(
                player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
        ThrownSpear spear = new ThrownSpear(level, player, stack.copyWithCount(1));
        spear.setThrowDamage(damageOf(stack) * (overarm ? 1.0F : 0.6F));
        spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, overarm ? 2.1F : 1.3F,
                overarm ? 1.0F : 4.5F);
        spear.pickup = player.getAbilities().instabuild ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
        level.addFreshEntity(spear);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, 1.0F, overarm ? 0.8F : 0.6F);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (!overarm) {
            player.displayClientMessage(Component.literal("A lob more than a throw - your shoulders are not built for it yet."),
                    true);
        }
    }

    /** What a thrown spear does: a fire-hardened point goes deeper. */
    public static float damageOf(ItemStack stack) {
        if (stack.is(ModItems.STONE_TIPPED_SPEAR.get())) {
            return 9.0F;
        }
        if (stack.is(ModItems.SCHONINGEN_SPEAR.get())) {
            // Balanced to be thrown: it hits far harder in flight than in the hand.
            return 8.0F;
        }
        return stack.is(ModItems.FIRE_HARDENED_SPEAR.get()) ? 7.0F : 5.0F;
    }

    public static boolean isSpear(ItemStack stack) {
        return stack.getItem() instanceof SpearItem;
    }
}
