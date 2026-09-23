package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Needs, as against wants. A want is a taste; a need is a member who cannot manage without
 * something - a pregnant one who needs a proper meal, an injured one who needs a good stone to
 * keep their hands busy while they are laid up.
 *
 * <p>The band has at most one at a time, and it is said out loud and marked. Meet it and the band
 * feels it; let it run out and the band does not forget - it costs far more cohesion than any
 * want, and the member themselves a good deal of their feeling for you.
 */
public final class Needs {
    /** How long a need can wait. */
    private static final long NEED_LASTS = 10 * 60 * 20L;
    /** Between one need settling and the next arising. */
    private static final long BETWEEN_NEEDS = 5 * 60 * 20L;
    private static final int CHECK_TICKS = 30 * 20;
    private static final int MET_COHESION = 2;
    private static final int MET_BOND = 2;
    private static final int IGNORED_COHESION = 6;
    private static final int IGNORED_BOND = 3;

    public enum Kind {
        BIG_MEAL("a proper meal - a rib, a meat chunk, a grub or a termite stick", "is eating for two and needs"),
        STONE("a good stone to work while they are laid up", "is hurt, and needs");

        private final String what;
        private final String says;

        Kind(String what, String says) {
            this.what = what;
            this.says = says;
        }

        public String what() {
            return what;
        }

        boolean accepts(ItemStack stack) {
            return switch (this) {
                case BIG_MEAL -> stack.is(ModItems.RIB.get()) || stack.is(ModItems.MEAT_CHUNK.get())
                        || stack.is(ModItems.COOKED_MEAT_CHUNK.get()) || stack.is(ModItems.GRUB.get())
                        || stack.is(ModItems.TERMITE_STICK.get());
                case STONE -> Wants.isGoodStone(stack) && !stack.is(ModItems.ROCK.get());
            };
        }
    }

    private record Need(UUID member, Kind kind, long until, boolean reminded) {
    }

    private static final Map<UUID, Need> needs = new HashMap<>();
    private static final Map<UUID, Long> nextNeed = new HashMap<>();

    /** Every thirty seconds per player: a need arises, is reminded of, or runs out. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_TICKS != 45) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Need need = needs.get(player.getUUID());
        if (need != null) {
            BandMember member = level.getEntity(need.member()) instanceof BandMember m && m.isAlive() && m.isLedBy(player)
                    ? m : null;
            if (member == null || !stillNeeds(member, need.kind())) {
                // Nobody left needing it: born, healed, gone.
                needs.remove(player.getUUID());
                nextNeed.put(player.getUUID(), now + BETWEEN_NEEDS);
                return;
            }
            member.ensureName();
            if (now >= need.until()) {
                needs.remove(player.getUUID());
                nextNeed.put(player.getUUID(), now + BETWEEN_NEEDS);
                member.addBond(-IGNORED_BOND);
                player.sendSystemMessage(Component.literal(member.getName().getString() + " went without "
                        + need.kind().what() + ". Everyone saw it. (Bond -" + IGNORED_BOND + ")")
                        .withStyle(ChatFormatting.DARK_RED));
                Cohesion.add(player, -IGNORED_COHESION, "brought " + member.getName().getString() + " "
                        + need.kind().what() + " when they needed it");
                return;
            }
            if (!need.reminded() && now >= need.until() - NEED_LASTS / 2) {
                needs.put(player.getUUID(), new Need(need.member(), need.kind(), need.until(), true));
                mark(member);
                player.sendSystemMessage(Component.literal(member.getName().getString() + " is still waiting for "
                        + need.kind().what() + ". Half the time is gone.").withStyle(ChatFormatting.RED));
            }
            return;
        }
        if (now < nextNeed.getOrDefault(player.getUUID(), now + 20 * 60)) {
            nextNeed.putIfAbsent(player.getUUID(), now + 2 * 60 * 20);
            return;
        }
        List<BandMember> candidates = new ArrayList<>();
        List<Kind> kinds = new ArrayList<>();
        for (BandMember member : Band.all(player)) {
            if (member.isBaby() || member.distanceToSqr(player) > 64.0D * 64.0D) {
                continue;
            }
            if (member.isPregnant()) {
                candidates.add(member);
                kinds.add(Kind.BIG_MEAL);
            } else if (member.isInjured()) {
                candidates.add(member);
                kinds.add(Kind.STONE);
            }
        }
        if (candidates.isEmpty()) {
            nextNeed.put(player.getUUID(), now + CHECK_TICKS * 4L);
            return;
        }
        int pick = player.getRandom().nextInt(candidates.size());
        BandMember member = candidates.get(pick);
        Kind kind = kinds.get(pick);
        needs.put(player.getUUID(), new Need(member.getUUID(), kind, now + NEED_LASTS, false));
        member.ensureName();
        mark(member);
        player.sendSystemMessage(Component.literal(member.getName().getString() + " " + kind.says + " "
                + kind.what() + ".").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" A need, not a want - it comes first, and the band will not forget if it "
                        + "goes unmet. (Hand it over with \"Here, take this\".)").withStyle(ChatFormatting.GRAY)));
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.NEED);
    }

    private static boolean stillNeeds(BandMember member, Kind kind) {
        return kind == Kind.BIG_MEAL ? member.isPregnant() : member.isInjured();
    }

    private static void mark(BandMember member) {
        member.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10 * 20, 0, false, false));
    }

    /** Handed something: returns true if it met the band's need. */
    public static boolean receive(BandMember member, Player player, ItemStack held) {
        Need need = needs.get(player.getUUID());
        if (need == null || !need.member().equals(member.getUUID()) || !need.kind().accepts(held)
                || !(player instanceof ServerPlayer server)) {
            return false;
        }
        ItemStack taken = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        needs.remove(player.getUUID());
        nextNeed.put(player.getUUID(), player.level().getGameTime() + BETWEEN_NEEDS);
        member.ensureName();
        String name = member.getName().getString();
        if (need.kind() == Kind.BIG_MEAL) {
            member.feed(taken);
        } else {
            member.addToInventory(taken);
            // They start on it at once.
            member.practiseKnapping();
        }
        member.addBond(MET_BOND);
        member.playSound(SoundEvents.PLAYER_BURP, 0.6F, 1.1F);
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HEART, member.getX(), member.getEyeY() + 0.3D,
                member.getZ(), 6, 0.3D, 0.2D, 0.3D, 0.0D);
        player.sendSystemMessage(Component.literal(name + (need.kind() == Kind.BIG_MEAL
                ? " eats it gratefully - and so does the child they carry."
                : " turns the stone over in their hands, and starts working it.")
                + " The band saw you put them first.").withStyle(ChatFormatting.LIGHT_PURPLE));
        Cohesion.add(server, MET_COHESION);
        return true;
    }

    /** For the band list: the need, if there is one. */
    @Nullable
    public static String describe(ServerPlayer player) {
        Need need = needs.get(player.getUUID());
        if (need == null || !(player.serverLevel().getEntity(need.member()) instanceof BandMember member)) {
            return null;
        }
        member.ensureName();
        long minutes = Math.max(0, (need.until() - player.level().getGameTime()) / 1200L);
        return member.getName().getString() + " needs " + need.kind().what() + " (" + minutes + " min left)";
    }

    @Nullable
    public static BandMember needy(ServerPlayer player) {
        Need need = needs.get(player.getUUID());
        return need != null && player.serverLevel().getEntity(need.member()) instanceof BandMember member ? member : null;
    }

    public static void forget(UUID player) {
        needs.remove(player);
        nextNeed.remove(player);
    }

    private Needs() {
    }
}
