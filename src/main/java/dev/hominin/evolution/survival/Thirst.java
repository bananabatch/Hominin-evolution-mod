package dev.hominin.evolution.survival;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.network.ThirstPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * How thirsty you are. Water was the one thing a hominin could not go a day without and
 * could not carry, which is why it decided where bands lived and who they had to share
 * with - so it is tracked on its own bar rather than folded into hunger.
 *
 * <p>It empties faster in the heat and faster again while running. Run it down and you
 * slow, tire, and eventually start to fail.
 */
public final class Thirst {
    public static final int MAX = 20;
    /** Below this, the body starts to complain. */
    private static final int PARCHED = 6;
    private static final int TICKS_PER_POINT = 900;

    public static final int DRINK_FROM_SOURCE = 6;
    public static final int DRINK_FROM_SHELL = 8;

    public static int get(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getThirst();
    }

    public static void set(ServerPlayer player, int value) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        int clamped = Math.max(0, Math.min(MAX, value));
        if (clamped != data.getThirst()) {
            data.setThirst(clamped);
            sync(player);
        }
    }

    public static void drink(ServerPlayer player, int amount) {
        set(player, get(player) + amount);
        // Somebody bleeding out is drinking for a different reason than thirst, and
        // every mouthful goes on that count whether or not they needed the water.
        if (amount > 0) {
            dev.hominin.evolution.combat.Bleeding.drank(player, amount);
            // And somebody sick from bad meat needs every drop of it.
            FoodIllness.drank(player, amount);
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ThirstPayload(get(player)));
    }

    /** Every tick, for every player. */
    public static void tick(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        int drain = data.tickThirstClock(costThisTick(player));
        if (drain > 0) {
            set(player, data.getThirst() - drain);
            if (data.getThirst() == PARCHED) {
                player.displayClientMessage(Component.literal("Your mouth is dry. You need water.")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
        if (player.tickCount % 40 != 0) {
            return;
        }
        int thirst = data.getThirst();
        if (thirst <= 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
            if (player.tickCount % 120 == 0 && player.getHealth() > 2.0F) {
                player.hurt(player.damageSources().dryOut(), 1.0F);
            }
        } else if (thirst < PARCHED) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, true));
        }
    }

    /**
     * What this tick costs, in hundredths of the usual rate. Heat and effort tell: the
     * savanna at noon takes water out of you far faster than a night under the trees.
     */
    private static int costThisTick(ServerPlayer player) {
        int cost = 100;
        if (player.isSprinting()) {
            cost += 120;
        } else if (player.walkDist != player.walkDistO) {
            cost += 30;
        }
        Biome biome = player.level().getBiome(player.blockPosition()).value();
        float temperature = biome.getBaseTemperature();
        if (temperature >= 1.5F) {
            cost += 80;
        } else if (temperature >= 0.9F) {
            cost += 40;
        } else if (temperature < 0.3F) {
            cost -= 30;
        }
        if (player.level().isDay() && player.level().canSeeSky(player.blockPosition())) {
            cost += 20;
        }
        if (player.isInWaterOrRain()) {
            cost -= 40;
        }
        return Math.max(20, cost);
    }

    /** Ticks of clock one point of thirst is worth, before effort and heat are counted. */
    public static int ticksPerPoint() {
        return TICKS_PER_POINT;
    }

    private Thirst() {
    }
}
