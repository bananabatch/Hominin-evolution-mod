package dev.hominin.evolution.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A whole chunk of volcanic glass, the size of a fist - rare, from an obsidian seam most often, now and then out of a
 * scatter of obsidian or the river gravel.
 *
 * <ul>
 * <li>It is the <b>core for an obsidian Levallois hand axe</b>: laid out among the stone at a knapping station, where
 * the technique wants a hammerstone of the stone the axe is to be, the chunk is that core.</li>
 * <li>It is <b>no hammerstone</b>. Strike with it - in the hammer's place at a station, in your off hand, at a rock
 * face - and it bursts: obsidian pieces everywhere, and sometimes a flake with an edge on it.</li>
 * <li><b>Thrown</b>, it bursts on whatever it hits - into a cloud of glass that opens the worst wound there is.</li>
 * <li><b>Broken down</b> (use it twice, quickly): eight pieces of obsidian, to knap as you like.</li>
 * </ul>
 */
public class ObsidianChunkItem extends Item {
    public static final int PIECES = 8;
    /** A second use this soon after the first breaks it down: a double use, never an accident. */
    private static final long DOUBLE_USE_TICKS = 12L;
    private static final Map<UUID, Long> firstUse = new HashMap<>();
    private static final Map<UUID, Long> CLIENT_USES = new HashMap<>();
    /** Ten seconds in hand before it can be thrown: nobody throws the rarest stone there is by accident. */
    private static final long HOLD_BEFORE_THROW = 200L;
    /** Since when, and in which slot, each player has had one in hand. */
    private static final Map<UUID, long[]> heldSince = new HashMap<>();

    public ObsidianChunkItem(Properties properties) {
        super(properties);
    }

    public static boolean is(ItemStack stack) {
        return stack.is(ModItems.OBSIDIAN_CHUNK.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            // Sneak-use is the throw (ThreatDisplay).
            return InteractionResultHolder.pass(stack);
        }
        // Each side keeps its own count, so both agree on which use is the second. The first use is held (like a
        // bow drawn) until you let go - so holding the button down never counts as using it twice.
        Map<UUID, Long> uses = level.isClientSide() ? CLIENT_USES : firstUse;
        long now = level.getGameTime();
        Long first = uses.get(player.getUUID());
        if (first == null || now - first > DOUBLE_USE_TICKS) {
            uses.put(player.getUUID(), now);
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.literal("Use again, quickly, to break the chunk down.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        uses.remove(player.getUUID());
        if (!level.isClientSide()) {
            stack.shrink(1);
            give(player, new ItemStack(ModItems.OBSIDIAN_ROCK.get(), PIECES));
            level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
            player.displayClientMessage(Component.literal("You break the chunk down, carefully: " + PIECES
                    + " pieces of obsidian.").withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Held from the first use until let go: nothing happens while it is held. */
    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return 72000;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.NONE;
    }

    public static void forget(UUID player) {
        firstUse.remove(player);
        heldSince.remove(player);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot,
            boolean selected) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        long[] since = heldSince.get(player.getUUID());
        if (selected) {
            if (since == null || since[0] != slot) {
                heldSince.put(player.getUUID(), new long[] {slot, level.getGameTime()});
            }
        } else if (since != null && since[0] == slot) {
            heldSince.remove(player.getUUID());
        }
    }

    /** How long yet before the chunk in hand can be thrown, in whole seconds - 0 when it can. */
    public static int secondsBeforeThrow(Player player) {
        long[] since = heldSince.get(player.getUUID());
        long held = since == null ? 0L : player.level().getGameTime() - since[1];
        return held >= HOLD_BEFORE_THROW ? 0 : (int) ((HOLD_BEFORE_THROW - held + 19L) / 20L);
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Struck with, as if it were a hammerstone: it bursts. A few pieces of obsidian are left - and, more often than
     * not, a flake or two with an edge on them.
     */
    public static void burst(ServerLevel level, BlockPos at, @Nullable ServerPlayer who) {
        level.playSound(null, at, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.OBSIDIAN_CHUNK.get())),
                at.getX() + 0.5D, at.getY() + 0.8D, at.getZ() + 0.5D, 18, 0.2D, 0.2D, 0.2D, 0.12D);
        int pieces = 2 + level.random.nextInt(3);
        drop(level, at, new ItemStack(ModItems.OBSIDIAN_ROCK.get(), pieces));
        float roll = level.random.nextFloat();
        int flakes = roll < 0.25F ? 2 : roll < 0.7F ? 1 : 0;
        boolean flake = flakes > 0;
        for (int i = 0; i < flakes; i++) {
            drop(level, at, StoneMaterial.stamp(new ItemStack(ModItems.FLAKE.get()), StoneMaterial.OBSIDIAN));
        }
        if (who != null) {
            who.sendSystemMessage(Component.literal("The chunk is glass, not a hammerstone - it bursts in your hand. "
                    + pieces + " pieces of obsidian" + (flakes == 2 ? ", and two flakes with an edge on them."
                            : flake ? ", and a flake with an edge on it." : ".")
                    + " (It is a Levallois core: lay it out among the stone at a knapping station.)")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static void drop(ServerLevel level, BlockPos at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.getX() + 0.5D, at.getY() + 0.8D, at.getZ() + 0.5D, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("A Levallois core: lay it out at a knapping station").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Use twice, quickly: break it into " + PIECES + " obsidian")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Sneak-use: throw it (after 10 seconds in hand)").withStyle(ChatFormatting.DARK_GRAY));
    }
}
