package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Needs, as against wants. A want is a taste; a need is a member who cannot manage without
 * something - a pregnant one who needs a proper meal, an injured one who needs a good stone to
 * keep their hands busy while they are laid up, a grieving one who needs looking after, a sour one
 * who needs somebody to hang out with them.
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
    /** Sitting with someone who is grieving: this long, close by, and that is looking after them. */
    private static final int COMPANY_SECONDS = 40;
    private static final double COMPANY_RANGE = 5.0D;

    public enum Kind {
        BIG_MEAL("a proper meal - a rib, a meat chunk, a grub or a termite stick", "is eating for two and needs"),
        STONE("a good stone to work while they are laid up", "is hurt, and needs"),
        LOOK_AFTER("looking after - sit with them a while, groom them, or give them a good stone or something to eat",
                "is grieving, and needs"),
        HANG_OUT("somebody to hang out with them - they want grooming, then something big to eat",
                "has gone sour, and needs");

        private final String what;
        private final String says;

        Kind(String what, String says) {
            this.what = what;
            this.says = says;
        }

        public String what() {
            return what;
        }

        boolean accepts(ItemStack stack, int step) {
            return switch (this) {
                case BIG_MEAL -> bigMeal(stack);
                case STONE -> Wants.isGoodStone(stack) && !stack.is(ModItems.ROCK.get());
                case LOOK_AFTER -> Wants.isGoodStone(stack) || stack.has(DataComponents.FOOD);
                case HANG_OUT -> step == 1 && bigMeal(stack);
            };
        }
    }

    static boolean bigMeal(ItemStack stack) {
        return stack.is(ModItems.RIB.get()) || stack.is(ModItems.COOKED_RIB.get()) || stack.is(ModItems.MEAT_CHUNK.get())
                || stack.is(ModItems.COOKED_MEAT_CHUNK.get()) || stack.is(ModItems.ROASTED_MARROW.get())
                || stack.is(ModItems.GRUB.get())
                || stack.is(ModItems.TERMITE_STICK.get());
    }

    /** A need: whose, what, until when, whether they have been reminded - and, hanging out, how far along. */
    private record Need(UUID member, Kind kind, long until, boolean reminded, int step) {
    }

    private static final Map<UUID, Need> needs = new HashMap<>();
    private static final Map<UUID, Long> nextNeed = new HashMap<>();
    /** Seconds spent close to whoever is grieving, while the need to look after them stands. */
    private static final Map<UUID, Integer> company = new HashMap<>();

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
                settle(player, now);
                return;
            }
            member.ensureName();
            if (now >= need.until()) {
                settle(player, now);
                member.addBond(-IGNORED_BOND);
                player.sendSystemMessage(Component.literal(member.getName().getString() + " went without "
                        + need.kind().what() + ". Everyone saw it. (Bond -" + IGNORED_BOND + ")")
                        .withStyle(ChatFormatting.DARK_RED));
                Cohesion.add(player, -IGNORED_COHESION, "gave " + member.getName().getString() + " "
                        + need.kind().what() + " when they needed it");
                if (need.kind() == Kind.LOOK_AFTER && member.getRandom().nextBoolean()) {
                    Troubles.goneSour(player, member);
                }
                return;
            }
            if (!need.reminded() && now >= need.until() - NEED_LASTS / 2) {
                needs.put(player.getUUID(), new Need(need.member(), need.kind(), need.until(), true, need.step()));
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
        List<BandMember> troubled = new ArrayList<>();
        List<Kind> troubles = new ArrayList<>();
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
            } else if (member.getTrouble() == Troubles.TROUBLED) {
                troubled.add(member);
                troubles.add(Kind.LOOK_AFTER);
            } else if (member.getTrouble() == Troubles.SOUR && member.isAntisocial()) {
                troubled.add(member);
                troubles.add(Kind.HANG_OUT);
            }
        }
        // The body comes first; grief the moment there is room for it.
        if (candidates.isEmpty()) {
            candidates = troubled;
            kinds = troubles;
        }
        if (candidates.isEmpty()) {
            nextNeed.put(player.getUUID(), now + CHECK_TICKS * 4L);
            return;
        }
        int pick = player.getRandom().nextInt(candidates.size());
        BandMember member = candidates.get(pick);
        Kind kind = kinds.get(pick);
        needs.put(player.getUUID(), new Need(member.getUUID(), kind, now + NEED_LASTS, false, 0));
        company.remove(player.getUUID());
        member.ensureName();
        mark(member);
        if (kind == Kind.LOOK_AFTER || kind == Kind.HANG_OUT) {
            announceTrouble(player, member, kind);
            return;
        }
        player.sendSystemMessage(Component.literal(member.getName().getString() + " " + kind.says + " "
                + kind.what() + ".").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" A need, not a want - it comes first, and the band will not forget if it "
                        + "goes unmet. (Hand it over with \"Here, take this\".)").withStyle(ChatFormatting.GRAY)));
        dev.hominin.evolution.guide.Tips.offer(player, dev.hominin.evolution.guide.Tips.Tip.NEED);
    }

    /** Somebody else in the band says it - they noticed first. */
    private static void announceTrouble(ServerPlayer player, BandMember member, Kind kind) {
        BandMember speaker = null;
        for (BandMember other : Band.ownNear(player, 32.0D)) {
            if (other != member && !other.isBaby()) {
                speaker = other;
                break;
            }
        }
        String name = member.getName().getString();
        String lost = member.getGrievingFor();
        String line = kind == Kind.LOOK_AFTER
                ? "Hey - we need you to look after " + name + ". They haven't been doing the best lately"
                        + (lost.isEmpty() ? "." : ", not since " + lost + ".")
                : "We need you to hang out with " + name + ". They've been off on their own"
                        + (lost.isEmpty() ? "." : " since " + lost + ". Nobody sat with them.");
        if (speaker != null) {
            speaker.ensureName();
            player.sendSystemMessage(Component.literal("<" + speaker.getName().getString() + "> ")
                    .withStyle(ChatFormatting.GOLD).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
        } else {
            player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GOLD));
        }
        player.sendSystemMessage(Component.literal(kind == Kind.LOOK_AFTER
                ? "(A need: sit with " + name + " a while, groom them, or give them a good stone or something to eat.)"
                : "(A need: hang out with " + name + ". They will ask for things - give them what they ask for.)")
                .withStyle(ChatFormatting.GRAY));
        if (kind == Kind.HANG_OUT) {
            Band.announceDiscovery(member, ": \"Hey... can you groom me? I haven't been keeping up lately.\"");
        }
    }

    private static void settle(ServerPlayer player, long now) {
        needs.remove(player.getUUID());
        company.remove(player.getUUID());
        nextNeed.put(player.getUUID(), now + BETWEEN_NEEDS);
    }

    private static boolean stillNeeds(BandMember member, Kind kind) {
        return switch (kind) {
            case BIG_MEAL -> member.isPregnant();
            case STONE -> member.isInjured();
            case LOOK_AFTER -> member.getTrouble() == Troubles.TROUBLED;
            case HANG_OUT -> member.getTrouble() == Troubles.SOUR;
        };
    }

    private static void mark(BandMember member) {
        member.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10 * 20, 0, false, false));
    }

    /** Handed something: returns true if it met the band's need. */
    public static boolean receive(BandMember member, Player player, ItemStack held) {
        Need need = needs.get(player.getUUID());
        if (need == null || !need.member().equals(member.getUUID()) || !need.kind().accepts(held, need.step())
                || !(player instanceof ServerPlayer server)) {
            return false;
        }
        ItemStack taken = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        member.ensureName();
        String name = member.getName().getString();
        if (taken.has(DataComponents.FOOD) && (need.kind() == Kind.BIG_MEAL || need.kind() == Kind.HANG_OUT
                || need.kind() == Kind.LOOK_AFTER)) {
            member.feed(taken);
        } else {
            member.addToInventory(taken);
            // They start on it at once.
            member.practiseKnapping();
            Troubles.gotStone(member);
        }
        member.playSound(SoundEvents.PLAYER_BURP, 0.6F, 1.1F);
        hearts(member);
        if (need.kind() == Kind.HANG_OUT) {
            settle(server, player.level().getGameTime());
            Troubles.backRound(server, member);
            return true;
        }
        settle(server, player.level().getGameTime());
        met(server, member);
        player.sendSystemMessage(Component.literal(name + switch (need.kind()) {
            case BIG_MEAL -> " eats it gratefully - and so does the child they carry.";
            case STONE -> " turns the stone over in their hands, and starts working it.";
            default -> " takes it, and for a while they talk about " + (member.getGrievingFor().isEmpty() ? "it"
                    : member.getGrievingFor()) + ".";
        } + " The band saw you put them first.").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (need.kind() == Kind.LOOK_AFTER) {
            Troubles.eased(server, member);
        }
        return true;
    }

    private static void met(ServerPlayer player, BandMember member) {
        member.addBond(MET_BOND);
        Cohesion.add(player, MET_COHESION);
    }

    private static void hearts(BandMember member) {
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HEART, member.getX(), member.getEyeY() + 0.3D,
                member.getZ(), 6, 0.3D, 0.2D, 0.3D, 0.0D);
    }

    /** You groomed someone: if they are the one grieving, or the sour one waiting to be groomed, it counts. */
    public static void groomed(ServerPlayer player, BandMember member) {
        Need need = needs.get(player.getUUID());
        if (need == null || !need.member().equals(member.getUUID())) {
            return;
        }
        member.ensureName();
        if (need.kind() == Kind.LOOK_AFTER) {
            settle(player, player.level().getGameTime());
            met(player, member);
            hearts(member);
            Troubles.eased(player, member);
        } else if (need.kind() == Kind.HANG_OUT && need.step() == 0) {
            // Groomed - and like any primate, they will do the same for you. Now they are hungry.
            needs.put(player.getUUID(), new Need(need.member(), need.kind(), need.until(), need.reminded(), 1));
            member.oweGrooming(player);
            Band.announceDiscovery(member, ": \"That's better. Now - I want some food. Something big, like a grub, a "
                    + "termite stick, a meat chunk or a rib.\"");
        }
    }

    /** Once a second: time spent close to someone grieving is looking after them. */
    static void company(ServerPlayer player) {
        Need need = needs.get(player.getUUID());
        if (need == null || need.kind() != Kind.LOOK_AFTER
                || !(player.serverLevel().getEntity(need.member()) instanceof BandMember member)
                || member.distanceToSqr(player) > COMPANY_RANGE * COMPANY_RANGE) {
            return;
        }
        int seconds = company.merge(player.getUUID(), 1, Integer::sum);
        if (seconds == COMPANY_SECONDS / 2) {
            member.ensureName();
            player.displayClientMessage(Component.literal("You sit with " + member.getName().getString()
                    + ". Neither of you says much.").withStyle(ChatFormatting.GRAY), true);
        }
        if (seconds >= COMPANY_SECONDS) {
            member.ensureName();
            settle(player, player.level().getGameTime());
            met(player, member);
            hearts(member);
            player.sendSystemMessage(Component.literal("You sat with " + member.getName().getString() + " a long while. "
                    + "It helped. The band saw you put them first.").withStyle(ChatFormatting.LIGHT_PURPLE));
            Troubles.eased(player, member);
        }
    }

    /**
     * One of the band meets the need before you do - hands over what they have. They get the credit, the band
     * feels it a little, and you do not.
     */
    static boolean metByMember(ServerPlayer player, BandMember helper) {
        Need need = needs.get(player.getUUID());
        if (need == null || need.member().equals(helper.getUUID()) || need.kind() == Kind.LOOK_AFTER
                || need.kind() == Kind.HANG_OUT
                || !(player.serverLevel().getEntity(need.member()) instanceof BandMember member)
                || member.distanceToSqr(helper) > 16.0D * 16.0D) {
            return false;
        }
        ItemStack given = helper.takeFirst(stack -> need.kind().accepts(stack, need.step()));
        if (given.isEmpty()) {
            return false;
        }
        if (need.kind() == Kind.BIG_MEAL) {
            member.feed(given);
        } else {
            member.addToInventory(given);
        }
        member.addAffinity(helper, 3);
        helper.addAffinity(member, 1);
        hearts(member);
        settle(player, player.level().getGameTime());
        member.ensureName();
        helper.ensureName();
        player.sendSystemMessage(Component.literal(helper.getName().getString() + " brings " + member.getName().getString()
                + " " + given.getHoverName().getString().toLowerCase() + " before you did. " + member.getName().getString()
                + " will not forget who looked after them.").withStyle(ChatFormatting.GRAY));
        Cohesion.addLimited(player, "need_met_by_band", 1, 12000L);
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
        String what = need.kind() == Kind.HANG_OUT ? (need.step() == 0 ? "grooming - you hanging out with them"
                : "something big to eat, while you hang out with them") : need.kind().what();
        return member.getName().getString() + " needs " + what + " (" + minutes + " min left)";
    }

    @Nullable
    public static BandMember needy(ServerPlayer player) {
        Need need = needs.get(player.getUUID());
        return need != null && player.serverLevel().getEntity(need.member()) instanceof BandMember member ? member : null;
    }

    /** Whether you are hanging out with a sour one right now: nobody else's wants are said out loud meanwhile. */
    public static boolean hangingOut(Player player, BandMember other) {
        Need need = needs.get(player.getUUID());
        return need != null && need.kind() == Kind.HANG_OUT && !need.member().equals(other.getUUID());
    }

    public static void forget(UUID player) {
        needs.remove(player);
        nextNeed.remove(player);
        company.remove(player);
    }

    private Needs() {
    }
}
