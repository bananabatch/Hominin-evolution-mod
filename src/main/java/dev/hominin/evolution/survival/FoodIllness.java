package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.food.Cooking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Food-borne illness: what meat that has turned does to you. The gut cramps and empties, it runs straight through
 * you, and now and then everything comes back up - so you are losing water as fast as you can drink it, and a big
 * meal will not stay down.
 *
 * <p>There is only one way through it: drink - a lot, and keep drinking - and eat small (a grub, a handful of
 * berries, a chunk of meat). Every mouthful of water and every small meal that stays down brings you closer to
 * over it; left alone it passes too, but slowly, and it is miserable the whole way.
 */
public final class FoodIllness {
    /** How far from over it you are: zero is well. */
    private static final String LEFT = "food_illness_left";
    private static final int CAUGHT = 16;
    private static final int WORSE = 6;
    private static final int MOST = 24;
    /** Cooking kills most of what was in it. Not all. */
    private static final float COOKED_CHANCE = 0.3F;
    /** A meal this small stays down, and helps; this big comes straight back up. */
    private static final int SMALL_MEAL = 3;
    private static final int BIG_MEAL = 5;
    /** The gut takes only so much at once: water counts once in fifteen seconds, a small meal once in twenty. */
    private static final long DRINK_GAP = 15 * 20L;
    private static final long MEAL_GAP = 20 * 20L;
    private static final Map<UUID, Long> lastDrink = new HashMap<>();
    private static final Map<UUID, Long> lastMeal = new HashMap<>();

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    public static boolean has(ServerPlayer player) {
        return counters(player).getOrDefault(LEFT, 0) > 0;
    }

    /** Ate something that had turned. */
    public static void ate(ServerPlayer player, ItemStack spoiled) {
        if (Cooking.isCooked(spoiled) && player.getRandom().nextFloat() >= COOKED_CHANCE) {
            player.displayClientMessage(Component.literal("It tasted off - but the fire seems to have done for "
                    + "whatever was in it.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int left = counters(player).getOrDefault(LEFT, 0);
        counters(player).put(LEFT, Math.min(MOST, left == 0 ? CAUGHT : left + WORSE));
        if (left == 0) {
            player.sendSystemMessage(Component.literal("That meat had turned, and your gut already knows it. You are "
                    + "going to be ill. Drink - a lot - and eat small until it passes.")
                    .withStyle(ChatFormatting.DARK_GREEN));
            dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.FOOD_ILLNESS);
        } else {
            player.sendSystemMessage(Component.literal("More bad meat, on top of the last. It will take longer to pass now.")
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 8 * 20, 0, false, false));
        symptoms(player);
    }

    /** A mouthful of water: it goes through you, but it is what gets you through. */
    public static void drank(ServerPlayer player, int amount) {
        if (!has(player) || amount <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        if (now - lastDrink.getOrDefault(player.getUUID(), -DRINK_GAP) < DRINK_GAP) {
            player.displayClientMessage(Component.literal("Slowly - a little at a time, and keep at it.")
                    .withStyle(ChatFormatting.DARK_GREEN), true);
            return;
        }
        lastDrink.put(player.getUUID(), now);
        recover(player, 2, "The water helps.");
    }

    /** Something eaten while ill: small stays down and helps; big comes back up. */
    public static void ateWhileIll(ServerPlayer player, ItemStack food) {
        FoodProperties properties = food.get(DataComponents.FOOD);
        if (!has(player) || properties == null) {
            return;
        }
        int nutrition = properties.nutrition();
        if (nutrition >= BIG_MEAL) {
            FoodData data = player.getFoodData();
            data.setFoodLevel(Math.max(0, data.getFoodLevel() - nutrition));
            data.setSaturation(Math.max(0.0F, data.getSaturationLevel() - properties.saturation()));
            vomit(player, "Too much, too soon. It comes straight back up. Eat small.");
        } else if (nutrition > 0 && nutrition <= SMALL_MEAL) {
            long now = player.level().getGameTime();
            if (now - lastMeal.getOrDefault(player.getUUID(), -MEAL_GAP) >= MEAL_GAP) {
                lastMeal.put(player.getUUID(), now);
                recover(player, 2, "A little, and it stays down.");
            }
        }
    }

    private static void recover(ServerPlayer player, int points, String how) {
        int left = counters(player).getOrDefault(LEFT, 0) - points;
        if (left <= 0) {
            cure(player);
            player.sendSystemMessage(Component.literal("Your gut settles at last. It has passed.")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        counters(player).put(LEFT, left);
        player.displayClientMessage(Component.literal(how + " (" + (left <= 4 ? "nearly over it" : left <= 10
                ? "getting better" : "still bad") + ")").withStyle(ChatFormatting.DARK_GREEN), true);
    }

    public static void cure(ServerPlayer player) {
        counters(player).remove(LEFT);
        lastDrink.remove(player.getUUID());
        lastMeal.remove(player.getUUID());
        player.removeEffect(ModEffects.FOOD_ILLNESS);
        player.removeEffect(MobEffects.HUNGER);
        Afflictions.relieve(player, Afflictions.Affliction.SICK);
    }

    /** Once a second while ill: the cramps, the thirst, and now and then the whole of it coming back up. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 11 || !has(player) || player.isSpectator()) {
            return;
        }
        symptoms(player);
        if (player.isCreative()) {
            return;
        }
        // It runs straight through you: water goes out as fast as it goes in.
        if (player.tickCount % (20 * 25) == 11) {
            Thirst.set(player, Thirst.get(player) - 1);
        }
        if (player.getRandom().nextInt(50) == 0) {
            FoodData data = player.getFoodData();
            data.setFoodLevel(Math.max(0, data.getFoodLevel() - 3));
            data.setSaturation(0.0F);
            Thirst.set(player, Thirst.get(player) - 2);
            vomit(player, "Your stomach heaves, and everything in it comes up.");
        }
        // Left alone, it passes - slowly.
        if (player.tickCount % (20 * 90) == 11) {
            recover(player, 1, "The cramps ease a little.");
        }
    }

    private static void symptoms(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.FOOD_ILLNESS, 60, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, false));
        Afflictions.afflict(player, Afflictions.Affliction.SICK, 100);
    }

    private static void vomit(ServerPlayer player, String line) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 6 * 20, 0, false, false));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.9F, 0.5F);
        player.serverLevel().sendParticles(ParticleTypes.SNEEZE, player.getX(), player.getEyeY() - 0.2D, player.getZ(),
                10, 0.15D, 0.1D, 0.15D, 0.02D);
        player.displayClientMessage(Component.literal(line).withStyle(ChatFormatting.DARK_GREEN), true);
    }

    /** For the journal. */
    public static String describe(ServerPlayer player) {
        int left = counters(player).getOrDefault(LEFT, 0);
        return left <= 0 ? "" : "Sick from bad meat - " + (left <= 4 ? "nearly over it" : left <= 10 ? "getting better"
                : "still bad") + ". Drink a lot; eat small.";
    }

    private FoodIllness() {
    }
}
