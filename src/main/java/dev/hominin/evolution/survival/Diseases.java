package dev.hominin.evolution.survival;

import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.BandMember;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What a bad diet and a bad wound do in the end.
 *
 * <p><b>Dental abscess.</b> Roots are hard, gritty food: live on them and a tooth goes bad. Eat roots as most of
 * what you eat and an abscess comes up - toothache, fever. It can be drawn out: ten handfuls of sweet berries, or a
 * great deal of water, or some of each. Leave it two days and it goes into the blood.
 *
 * <p><b>Septic shock.</b> Poison in the blood. A minute of fever and shaking, and then death. Nothing stops it once
 * it has started.
 *
 * <p><b>Toxic shock.</b> Spoiled meat eaten with a wound already torn open: the rot goes straight in. Septic shock
 * comes with it. (The laceration's own warning says not to eat raw meat. Spoiled meat, well.)
 *
 * <p>Band members live on roots too, now and then - and grumble about it - and can go the same way.
 */
public final class Diseases {
    public static final ResourceKey<DamageType> SEPTIC_SHOCK = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(dev.hominin.evolution.HomininEvolutionMod.MODID, "septic_shock"));

    /** Two days for a tooth to go into the blood; a minute for the blood to finish it. */
    public static final int ABSCESS_TICKS = 2 * 24000;
    public static final int SEPTIC_TICKS = 60 * 20;
    /** How far into "mostly roots" a diet goes before a tooth gives: roots count up, anything else counts down. */
    private static final int ROOT_STREAK = 6;
    /** Drawing it out: a handful of berries is 2, a drink 1; 20 in all - ten handfuls, or plenty of water. */
    private static final int TO_TREAT = 20;
    private static final int BERRY = 2;
    private static final int DRINK = 1;
    private static final String STREAK = "root_streak";
    private static final String TREATED = "abscess_treated";

    public static boolean isRoots(ItemStack stack) {
        return stack.is(ModItems.ROOTS.get());
    }

    private static CompoundTag data(LivingEntity entity) {
        return entity.getPersistentData();
    }

    // ------------------------------------------------------------ eating

    /** Something eaten: roots add to the streak, anything else takes from it; berries draw an abscess out. */
    public static void ate(LivingEntity eater, ItemStack food) {
        if (eater.level().isClientSide()) {
            return;
        }
        CompoundTag data = data(eater);
        int streak = data.getInt(STREAK);
        if (isRoots(food)) {
            streak++;
            if (eater.hasEffect(ModEffects.DENTAL_ABSCESS) && eater instanceof ServerPlayer player) {
                player.displayClientMessage(Component.literal("Grit in the bad tooth. It throbs.")
                        .withStyle(ChatFormatting.RED), true);
            }
        } else {
            streak = Math.max(0, streak - 1);
        }
        data.putInt(STREAK, streak);
        if (streak >= ROOT_STREAK && !eater.hasEffect(ModEffects.DENTAL_ABSCESS)
                && !eater.hasEffect(ModEffects.SEPTIC_SHOCK)) {
            data.putInt(STREAK, 0);
            data.putInt(TREATED, 0);
            eater.addEffect(new MobEffectInstance(ModEffects.DENTAL_ABSCESS, ABSCESS_TICKS, 0, false, true, true));
            if (eater instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("Nothing but roots, and a tooth has gone bad: a dental "
                        + "abscess. Draw it out - sweet berries (ten handfuls) or a great deal of water, or both - "
                        + "within two days, or it goes into the blood.").withStyle(ChatFormatting.RED));
            } else if (eater instanceof BandMember member) {
                dev.hominin.evolution.band.Lines.say(member, "abscess");
            }
        }
        if (food.is(net.minecraft.world.item.Items.SWEET_BERRIES)) {
            treat(eater, BERRY);
        }
    }

    /** Water going in: a drink draws an abscess a little further out. */
    public static void drank(LivingEntity drinker, int amount) {
        if (amount > 0) {
            treat(drinker, DRINK * Math.max(1, amount / 2));
        }
    }

    private static void treat(LivingEntity entity, int points) {
        if (!entity.hasEffect(ModEffects.DENTAL_ABSCESS)) {
            return;
        }
        CompoundTag data = data(entity);
        int treated = data.getInt(TREATED) + points;
        if (treated >= TO_TREAT) {
            data.remove(TREATED);
            entity.removeEffect(ModEffects.DENTAL_ABSCESS);
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("The swelling goes down. The tooth still aches, but the "
                        + "abscess is gone.").withStyle(ChatFormatting.GREEN));
            }
            return;
        }
        data.putInt(TREATED, treated);
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("Drawing the abscess out... (" + treated + "/" + TO_TREAT + ")")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Whether water still goes down past a full belly: an abscess to wash out. */
    public static boolean drinksPastFull(ServerPlayer player) {
        return player.hasEffect(ModEffects.DENTAL_ABSCESS);
    }

    // ------------------------------------------------------------ the blood

    /** Spoiled meat with a wound torn open: toxic shock, and septic shock with it. */
    public static void rotIntoTheWound(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.TOXIC_SHOCK, SEPTIC_TICKS, 0, false, true, true));
        septic(player);
        player.sendSystemMessage(Component.literal("Rotten meat, and a wound already torn open. The rot goes straight "
                + "into the blood: toxic shock. There is nothing to be done.").withStyle(ChatFormatting.DARK_RED));
        dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/healthcare_genius");
    }

    private static void septic(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(ModEffects.SEPTIC_SHOCK, SEPTIC_TICKS, 0, false, true, true));
        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, SEPTIC_TICKS, 0, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SEPTIC_TICKS, 1, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SEPTIC_TICKS, 1, false, false, false));
    }

    /** Every tick, for a player or a band member: an abscess left too long goes into the blood; septic shock ends it. */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide() || entity.tickCount % 10 != 3) {
            return;
        }
        MobEffectInstance abscess = entity.getEffect(ModEffects.DENTAL_ABSCESS);
        if (abscess != null && abscess.getDuration() <= 20) {
            entity.removeEffect(ModEffects.DENTAL_ABSCESS);
            septic(entity);
            if (entity instanceof ServerPlayer player) {
                player.sendSystemMessage(Component.literal("The abscess was left too long. It has gone into the blood: "
                        + "septic shock.").withStyle(ChatFormatting.DARK_RED));
            } else if (entity instanceof BandMember member && member.leaderPlayer() instanceof ServerPlayer leader) {
                member.ensureName();
                leader.sendSystemMessage(Component.literal(member.getName().getString() + "'s bad tooth has gone into "
                        + "the blood. They are burning up.").withStyle(ChatFormatting.DARK_RED));
            }
            return;
        }
        MobEffectInstance septic = entity.getEffect(ModEffects.SEPTIC_SHOCK);
        if (septic != null && septic.getDuration() <= 20) {
            entity.removeEffect(ModEffects.SEPTIC_SHOCK);
            entity.hurt(entity.damageSources().source(SEPTIC_SHOCK), Float.MAX_VALUE);
        }
        // An abscess aches; now and then it takes the edge off everything.
        if (abscess != null && entity.getRandom().nextInt(60) == 0 && entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("Your tooth throbs. (Dental abscess - "
                    + abscess.getDuration() / 1200 + " minutes before it goes into the blood)")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private Diseases() {
    }
}
