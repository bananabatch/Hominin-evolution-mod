package dev.hominin.evolution.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModSounds;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.advancement.HomininAdvancements;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.entity.ThrownObject;
import dev.hominin.evolution.event.EvolutionEventHandler;
import dev.hominin.evolution.network.BodyAnimationPayload;
import dev.hominin.evolution.network.TiredApesPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The charging display chimpanzees still use: pant-hooting, leaping up and down, and
 * throwing whatever is to hand. There is no good evidence early hominins bared their
 * teeth at anything - their canines were already small - but a display like this
 * needs no fangs, and it scares things.
 *
 * <p>It works better in company. Every member of the band within earshot joins in,
 * and each one makes the display more likely to drive something off, and from further
 * away.
 *
 * <p>Throwing is a different matter. Before erectus a throw is a flailing overarm heave
 * - it goes roughly the right way, stings, and frightens. Erectus has the shoulder for
 * a real throw: straight, hard, and worth aiming. By then the screaming has stopped.
 */
public final class ThreatDisplay {
    private static final Set<ResourceLocation> DISPLAY_STAGES = Set.of(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "ardipithecus"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "australopithecus"),
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "homo_habilis"));

    private static final double SCREAM_RADIUS = 10.0D;
    private static final float SCREAM_CHANCE = 0.45F;
    /** A throw reaches further and convinces more of what it reaches. */
    private static final double THROW_RADIUS = 12.0D;
    private static final float THROW_CHANCE = 0.6F;

    /** As long as the pant hoot itself, so calls never pile on top of each other. */
    private static final long CALL_COOLDOWN_TICKS = 110L;
    private static final int THROW_COOLDOWN_TICKS = 20;

    private static final int HOPS = 3;
    private static final int HOP_INTERVAL_TICKS = 9;
    private static final double HOP_VELOCITY = 0.42D;

    /** A flailing heave: well off line, and barely hurts. */
    private static final Throw WILD_THROW = new Throw(0.9F, 9.0F, 1.0F, 0.5F);
    /** Erectus: a real overarm throw. */
    private static final Throw AIMED_THROW = new Throw(1.4F, 1.0F, 4.0F, 2.0F);

    private record Throw(float speed, float inaccuracy, float rockDamage, float branchDamage) {
    }

    private static final int POINTLESS_DISPLAYS = 3;
    private static final long POINTLESS_WINDOW_TICKS = 60 * 20;

    private static final Map<UUID, Long> lastCall = new HashMap<>();
    /** Displays in a row with nothing to display at, and when the last one was. */
    private static final Map<UUID, int[]> pointless = new HashMap<>();
    /** Hops still to come, and the game time each is due. */
    private static final Map<UUID, int[]> hopsLeft = new HashMap<>();
    private static final Map<UUID, Long> nextHop = new HashMap<>();

    public static boolean isThrowable(ItemStack stack) {
        return ModItems.isLongBranch(stack)
                || stack.is(ModItems.ROCK.get())
                || stack.is(ModTags.Items.KNAPPABLE_STONE);
    }

    private static boolean displays(ServerPlayer player) {
        return DISPLAY_STAGES.contains(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage());
    }

    /** Empty-handed: pant-hoot and leap, and call the band in. */
    public static void scream(ServerPlayer player) {
        if (!displays(player) || !callOffCooldown(player)) {
            return;
        }
        call(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new BodyAnimationPayload(player.getId(), "threat_display"));
        hopsLeft.put(player.getUUID(), new int[] {HOPS});
        nextHop.put(player.getUUID(), player.level().getGameTime());

        int band = Band.rally(player);
        double radius = Band.radiusWith(SCREAM_RADIUS, band);
        boolean threatNearby = !player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                player.getBoundingBox().inflate(radius),
                mob -> mob.getType().is(ModTags.EntityTypes.PREDATORS)).isEmpty();
        int startled = EvolutionEventHandler.startleNearby(player, radius,
                Band.chanceWith(SCREAM_CHANCE, band) + dev.hominin.evolution.hunt.Predation.displayBonus(player), true);
        startled += dev.hominin.evolution.entity.Pachycrocuta.scareNear(player, radius + 8.0D, band);
        startled += dev.hominin.evolution.entity.Crocuta.displayAt(player, radius + 8.0D, band);
        startled += dev.hominin.evolution.band.Paranthropus.scareNear(player, radius + 8.0D);
        if (dev.hominin.evolution.band.Mating.guarding(player)) {
            // Guarding a birth: nothing gets past this, not even what fears nothing.
            for (net.minecraft.world.entity.PathfinderMob mob : player.level().getEntitiesOfClass(
                    net.minecraft.world.entity.PathfinderMob.class, player.getBoundingBox().inflate(radius + 8.0D),
                    m -> m.isAlive() && !(m instanceof dev.hominin.evolution.band.BandMember)
                            && (m.getType().is(ModTags.EntityTypes.PREDATORS)
                                    || m instanceof net.minecraft.world.entity.monster.Enemy
                                    || m.getType().is(ModTags.EntityTypes.FEARLESS)))) {
                dev.hominin.evolution.combat.Scare.scare(mob, player.position(), 30 * 20);
                startled++;
            }
        }
        // Chimpanzees take it as a challenge too - but they give you the chance to take it back.
        dev.hominin.evolution.entity.Chimpanzee.answerDisplay(player, radius);
        // One thing out here does not back down, and finding that out is the lesson.
        if (dev.hominin.evolution.entity.Dinopithecus.answerDisplay(player, radius)) {
            dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/nice_try_genius");
            player.displayClientMessage(Component.literal(
                    "It does not back away. It comes straight at you.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
        }
        countPointlessDisplays(player, threatNearby);
        if (startled > 0) {
            player.displayClientMessage(Component.literal(band > 0
                    ? "The band takes up the call, and it backs away."
                    : "You call and leap, and it backs away."), true);
        }
    }

    /**
     * Throws one of the held branch or rock. Returns false if nothing was thrown, so the
     * click is left for anything else that wants it.
     */
    public static boolean throwHeld(ServerPlayer player, ItemStack held) {
        if (player.getCooldowns().isOnCooldown(held.getItem())) {
            return false;
        }
        boolean displaying = displays(player);
        Throw style = displaying ? WILD_THROW : AIMED_THROW;
        boolean branch = ModItems.isLongBranch(held);

        ThrownObject thrown = new ThrownObject(player.level(), player);
        thrown.setItem(held.copyWithCount(1));
        thrown.setDamage(branch ? style.branchDamage() : style.rockDamage());
        thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, style.speed(), style.inaccuracy());
        player.level().addFreshEntity(thrown);

        player.getCooldowns().addCooldown(held.getItem(), THROW_COOLDOWN_TICKS);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.8F, branch ? 0.5F : 0.7F);

        if (displaying) {
            int band = 0;
            if (callOffCooldown(player)) {
                call(player);
                band = Band.rally(player);
            }
            int startled = EvolutionEventHandler.startleNearby(player, Band.radiusWith(THROW_RADIUS, band),
                    Band.chanceWith(THROW_CHANCE, band), true);
            if (startled > 0) {
                player.displayClientMessage(Component.literal("You hurl it with a cry, and it scatters."), true);
            }
        }
        return true;
    }

    /**
     * Three displays in a row at nothing at all. Chimpanzees do this too: the display
     * is its own reward, and nobody in the band ever seems to tire of it.
     */
    private static void countPointlessDisplays(ServerPlayer player, boolean threatNearby) {
        long now = player.level().getGameTime();
        int[] streak = pointless.computeIfAbsent(player.getUUID(), id -> new int[2]);
        if (threatNearby || now - streak[1] > POINTLESS_WINDOW_TICKS) {
            streak[0] = 0;
        }
        streak[1] = (int) now;
        if (threatNearby) {
            return;
        }
        if (++streak[0] >= POINTLESS_DISPLAYS) {
            streak[0] = 0;
            PacketDistributor.sendToPlayer(player, new TiredApesPayload());
            HomininAdvancements.award(player, "hominin/tired_apes");
        }
    }

    private static boolean callOffCooldown(ServerPlayer player) {
        Long last = lastCall.get(player.getUUID());
        return last == null || player.level().getGameTime() - last >= CALL_COOLDOWN_TICKS;
    }

    private static void call(ServerPlayer player) {
        lastCall.put(player.getUUID(), player.level().getGameTime());
        player.level().playSound(null, player.blockPosition(), ModSounds.PANT_HOOT.get(),
                SoundSource.PLAYERS, 1.5F, 0.95F + player.getRandom().nextFloat() * 0.1F);
    }

    /** Runs the leaps of a display, one each time the feet are back on the ground. */
    public static void tick(ServerPlayer player) {
        int[] left = hopsLeft.get(player.getUUID());
        if (left == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (now < nextHop.getOrDefault(player.getUUID(), 0L) || !player.onGround()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, HOP_VELOCITY, motion.z);
        player.hurtMarked = true;
        if (--left[0] <= 0) {
            hopsLeft.remove(player.getUUID());
            nextHop.remove(player.getUUID());
        } else {
            nextHop.put(player.getUUID(), now + HOP_INTERVAL_TICKS);
        }
    }

    public static void forget(UUID player) {
        lastCall.remove(player);
        pointless.remove(player);
        hopsLeft.remove(player);
        nextHop.remove(player);
    }

    private ThreatDisplay() {
    }
}
