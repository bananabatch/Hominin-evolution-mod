package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.ModEffects;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.block.CookingSpitBlockEntity;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.guide.Alerts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

/**
 * The Feast - a band's second culture, beside the Pile. When something big happens (ready to become something new,
 * a band as big as a people, a great weapon, the first great beast brought down), the band feasts.
 *
 * <p>First, two days of getting ready. Everyone gathers - not only you: the band forages and hunts past what it
 * needs, brings the food to the feast place and lays it on food piles, puts up cooking racks over fire pits, hangs
 * the meat on them, and keeps the fires fed. Then the feast: everyone comes to the fire and eats - a great share of
 * what was gathered, and of what they carry. Your own hunger is forgotten while it lasts, and you eat as they do.
 *
 * <p>After it everyone is full: hunger falls far more slowly for a day and a half. Cohesion rises a little (+4);
 * what rises a lot is the bond - with you, and between every one of them who was there. A close band steals from
 * itself less, and fights less. A thin feast, with little gathered, is a thin reward.
 */
public final class Feast {
    private static final String PREP_UNTIL = "feast_prep_until";
    private static final String UNTIL = "feast_until";
    private static final String NEXT = "feast_next";
    private static final String X = "feast_x";
    private static final String Y = "feast_y";
    private static final String Z = "feast_z";
    private static final String EATEN = "feast_eaten";
    private static final String ATTENDED = "feast_attended";
    private static final String PLAYER_ATE = "feast_player_ate";
    private static final String PLAYER_MAY_EAT = "feast_player_may_eat";
    private static final String MEGAFAUNA = "feast_megafauna";

    /** Two days of gathering, in minutes of game time. */
    public static final int PREP_MINUTES = 40;
    /** The feast itself. */
    private static final int FEAST_MINUTES = 3;
    /** Three days after a feast before another. */
    private static final int COOLDOWN_MINUTES = 60;
    /** A day and a half, full. */
    public static final int FULL_TICKS = 36000;
    /** How much slower hunger falls, full: a quarter of the usual. */
    public static final float FULL_HUNGER = 0.25F;
    private static final int COHESION = 4;
    private static final int BOND = 4;
    private static final int AFFINITY = 3;
    /** Near enough the fire to be at the feast. */
    public static final double AT_THE_FEAST = 14.0D;
    /** How far round the feast place its food piles and racks count. */
    public static final int FEAST_REACH = 10;

    /** What a feast is for: none of our own, and nothing that has turned. */
    public static final Predicate<ItemStack> FEAST_FOOD = stack -> BandMember.edible(stack)
            && !stack.is(ModItems.HOMININ_MEAT.get()) && !stack.is(ModItems.COOKED_HOMININ_MEAT.get())
            && !stack.is(ModItems.HOMININ_BRAIN.get()) && !stack.is(ModItems.WATER_EGGSHELL.get());

    private static final Map<UUID, String> reasons = new HashMap<>();
    private static final Map<UUID, Float> exhaustion = new HashMap<>();
    /** Who of each band has taken on putting up a rack, so two do not try at once. */
    private static final Map<UUID, UUID> builders = new HashMap<>();

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    private static int minute(ServerPlayer player) {
        return (int) (player.level().getGameTime() / 1200L);
    }

    /** Whether the band keeps the Feast at all. */
    public static boolean kept(ServerPlayer player) {
        return Morals.holds(player, Morals.Moral.THE_FEAST);
    }

    public static boolean preparing(ServerPlayer player) {
        return counters(player).getOrDefault(PREP_UNTIL, 0) > minute(player);
    }

    public static boolean feasting(ServerPlayer player) {
        return counters(player).getOrDefault(UNTIL, 0) > minute(player);
    }

    public static boolean preparing(BandMember member) {
        return !member.isWild() && member.leaderPlayer() instanceof ServerPlayer leader && preparing(leader);
    }

    public static boolean feasting(BandMember member) {
        return !member.isWild() && member.leaderPlayer() instanceof ServerPlayer leader && feasting(leader);
    }

    /** Getting ready, and not carrying plenty already: out gathering for it. */
    public static boolean gathering(BandMember member) {
        return !member.isBaby() && preparing(member) && member.countOf(FEAST_FOOD) < 12;
    }

    /** Where it will be - the camp's fire - once one is called. */
    @Nullable
    public static BlockPos place(ServerPlayer player) {
        Map<String, Integer> counters = counters(player);
        if (!counters.containsKey(X)) {
            return null;
        }
        return new BlockPos(counters.get(X), counters.getOrDefault(Y, 64), counters.get(Z));
    }

    @Nullable
    public static BlockPos place(BandMember member) {
        return member.leaderPlayer() instanceof ServerPlayer leader ? place(leader) : null;
    }

    /** Minutes of game time until it begins, while getting ready. */
    public static int minutesToGo(ServerPlayer player) {
        return Math.max(0, counters(player).getOrDefault(PREP_UNTIL, 0) - minute(player));
    }

    // ------------------------------------------------------------ calling one

    /** Something big has happened. If the band keeps the Feast, and it is not too soon after the last, one is called. */
    public static void event(ServerPlayer player, String what) {
        if (!kept(player) || !Bands.erectusOn(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            return;
        }
        Map<String, Integer> counters = counters(player);
        if (preparing(player) || feasting(player)) {
            reasons.merge(player.getUUID(), what, (was, now) -> was.contains(now) ? was : was + ", and " + now);
            return;
        }
        int now = minute(player);
        if (counters.getOrDefault(NEXT, 0) > now) {
            return;
        }
        BlockPos at = feastPlace(player);
        counters.put(PREP_UNTIL, now + PREP_MINUTES);
        counters.put(X, at.getX());
        counters.put(Y, at.getY());
        counters.put(Z, at.getZ());
        counters.remove(UNTIL);
        reasons.put(player.getUUID(), what);
        builders.remove(player.getUUID());
        Alerts.urgent(player, Alerts.Kind.BAND, Component.literal("For " + what + ", the band will feast - in two days, "
                + "at the fire " + where(player, at) + ". Until then everyone gathers: meat and forage brought to food "
                + "piles by the fire, racks put up over fire pits, the fires kept fed. You too.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        Chatter.news(player, "news_feast", what);
    }

    /** The first great beast brought down at each stage is worth a feast. */
    public static void broughtDown(ServerPlayer player, String what) {
        Map<String, Integer> counters = counters(player);
        if (counters.getOrDefault(MEGAFAUNA, 0) == 0) {
            counters.put(MEGAFAUNA, 1);
            event(player, "the " + what + " brought down");
        }
    }

    /** At the camp's fire: the fire pit nearest the camp, or the camp itself. */
    private static BlockPos feastPlace(ServerPlayer player) {
        BlockPos camp = ErectusWork.camp(player);
        BlockPos fire = ErectusWork.nearest(player.serverLevel(), camp, dev.hominin.evolution.ModBlocks.FIRE_PIT.get(), 16);
        return fire != null ? fire : camp;
    }

    private static String where(ServerPlayer player, BlockPos at) {
        int distance = (int) Math.sqrt(at.distSqr(player.blockPosition()));
        return distance < 8 ? "here" : distance + " blocks away";
    }

    // ------------------------------------------------------------ over time

    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 11 || player.isSpectator()) {
            return;
        }
        Map<String, Integer> counters = counters(player);
        int now = minute(player);
        int prep = counters.getOrDefault(PREP_UNTIL, 0);
        if (prep > 0 && prep <= now) {
            counters.remove(PREP_UNTIL);
            begin(player);
            return;
        }
        int until = counters.getOrDefault(UNTIL, 0);
        if (until > 0) {
            if (until <= now) {
                end(player);
            } else {
                eat(player);
            }
        }
    }

    private static void begin(ServerPlayer player) {
        Map<String, Integer> counters = counters(player);
        counters.put(UNTIL, minute(player) + FEAST_MINUTES);
        counters.put(EATEN, 0);
        counters.put(ATTENDED, 0);
        counters.put(PLAYER_ATE, 0);
        int carried = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (FEAST_FOOD.test(stack)) {
                carried += stack.getCount();
            }
        }
        // A large share of what you carry: half of it, and at least a couple of mouthfuls.
        counters.put(PLAYER_MAY_EAT, Math.max(2, carried / 2));
        BlockPos at = place(player);
        int gathered = at == null ? 0 : sources(player.serverLevel(), at).stream().mapToInt(Source::count).sum();
        Alerts.urgent(player, Alerts.Kind.BAND, Component.literal("The feast begins! Everyone gathers at the fire"
                + (at == null ? "" : " " + where(player, at)) + " - " + gathered + " things to eat laid by, and whatever "
                + "they carry. Come and eat: for once, as much as you want.").withStyle(ChatFormatting.GOLD));
        for (BandMember member : Band.all(player)) {
            Lines.say(member, "feast_begin");
            break;
        }
    }

    /** One food source by the fire: a food pile, or a spit with something cooked on it. */
    private record Source(BlockPos pos, int count) {
    }

    private static List<Source> sources(ServerLevel level, BlockPos at) {
        List<Source> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FEAST_REACH, -3, -FEAST_REACH),
                at.offset(FEAST_REACH, 3, FEAST_REACH))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            var entity = level.getBlockEntity(pos);
            if (entity instanceof ToolPileBlockEntity pile && pile.kind() == ToolPileBlockEntity.Kind.FOOD) {
                int n = pile.total(FEAST_FOOD);
                if (n > 0) {
                    found.add(new Source(pos.immutable(), n));
                }
            } else if (entity instanceof CookingSpitBlockEntity spit) {
                int n = 0;
                for (int slot = 0; slot < spit.slots(); slot++) {
                    ItemStack on = spit.at(slot);
                    if (FEAST_FOOD.test(on) && dev.hominin.evolution.food.Cooking.isCooked(on)) {
                        n += on.getCount();
                    }
                }
                if (n > 0) {
                    found.add(new Source(pos.immutable(), n));
                }
            }
        }
        return found;
    }

    /** A mouthful from whatever is laid by at the feast, or empty. */
    private static ItemStack fromTheFeast(ServerLevel level, List<Source> sources, BandMember member) {
        var access = ToolPiles.access(member);
        for (Source source : sources) {
            var entity = level.getBlockEntity(source.pos());
            if (entity instanceof CookingSpitBlockEntity spit) {
                for (int slot = 0; slot < spit.slots(); slot++) {
                    ItemStack on = spit.at(slot);
                    if (FEAST_FOOD.test(on) && dev.hominin.evolution.food.Cooking.isCooked(on) && spit.mayTake(slot, access)) {
                        ItemStack all = spit.takeSlot(slot, access);
                        ItemStack one = all.split(1);
                        if (!all.isEmpty()) {
                            // The rest goes back on the hook for the next one.
                            spit.putBack(slot, all, member.getUUID(), member.getName().getString());
                        }
                        return one;
                    }
                }
            } else if (entity instanceof ToolPileBlockEntity pile) {
                ItemStack one = pile.takeSome(FEAST_FOOD, 1, access);
                if (pile.isEmpty()) {
                    level.removeBlock(source.pos(), false);
                }
                if (!one.isEmpty()) {
                    return one;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Every second of the feast: you are fed, and every few, everyone at the fire eats. */
    private static void eat(ServerPlayer player) {
        BlockPos at = place(player);
        if (at == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Map<String, Integer> counters = counters(player);
        if (player.distanceToSqr(at.getCenter()) < 24.0D * 24.0D) {
            counters.put(ATTENDED, 1);
            // Hunger is forgotten at a feast.
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(Math.max(food.getSaturationLevel(), 10.0F));
            if (player.tickCount % 100 == 11 && counters.getOrDefault(PLAYER_ATE, 0) < counters.getOrDefault(PLAYER_MAY_EAT, 2)) {
                for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                    ItemStack stack = player.getInventory().items.get(slot);
                    if (FEAST_FOOD.test(stack)) {
                        ItemStack one = stack.split(1);
                        munch(level, player, one);
                        counters.merge(PLAYER_ATE, 1, Integer::sum);
                        break;
                    }
                }
            }
        }
        if (player.tickCount % 60 != 11) {
            return;
        }
        List<Source> sources = sources(level, at);
        for (BandMember member : Band.all(player)) {
            if (member.distanceToSqr(at.getCenter()) > AT_THE_FEAST * AT_THE_FEAST || member.isSleeping()) {
                continue;
            }
            // What they carry first, then what was laid by.
            ItemStack food = member.takeFirst(FEAST_FOOD);
            if (food.isEmpty()) {
                food = fromTheFeast(level, sources, member);
            }
            if (food.isEmpty()) {
                continue;
            }
            member.feed(food);
            munch(level, member, food);
            counters.merge(EATEN, 1, Integer::sum);
            if (member.getRandom().nextInt(6) == 0) {
                Lines.say(member, "feast_eat");
            }
        }
    }

    private static void munch(ServerLevel level, net.minecraft.world.entity.LivingEntity who, ItemStack food) {
        level.playSound(null, who.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        if (!food.isEmpty()) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food), who.getX(), who.getEyeY() - 0.1D,
                    who.getZ(), 5, 0.15D, 0.05D, 0.15D, 0.05D);
        }
    }

    private static void end(ServerPlayer player) {
        Map<String, Integer> counters = counters(player);
        int eaten = counters.getOrDefault(EATEN, 0);
        boolean attended = counters.getOrDefault(ATTENDED, 0) == 1;
        BlockPos at = place(player);
        for (String key : List.of(UNTIL, EATEN, ATTENDED, PLAYER_ATE, PLAYER_MAY_EAT)) {
            counters.remove(key);
        }
        counters.put(NEXT, minute(player) + COOLDOWN_MINUTES);
        String why = reasons.remove(player.getUUID());
        builders.remove(player.getUUID());
        List<BandMember> band = Band.all(player);
        List<BandMember> there = new ArrayList<>();
        for (BandMember member : band) {
            if (at != null && member.distanceToSqr(at.getCenter()) < (AT_THE_FEAST * 2) * (AT_THE_FEAST * 2)) {
                there.add(member);
            }
        }
        boolean plenty = eaten >= Math.max(4, there.size() * 2);
        for (BandMember member : there) {
            if (plenty) {
                member.addEffect(new MobEffectInstance(ModEffects.FULL, FULL_TICKS, 0, false, true, true));
            }
            member.addBond(plenty ? BOND : 1);
            for (BandMember other : there) {
                if (other != member) {
                    member.addAffinity(other, plenty ? AFFINITY : 1);
                }
            }
        }
        if (attended && plenty) {
            player.addEffect(new MobEffectInstance(ModEffects.FULL, FULL_TICKS, 0, false, true, true));
            dev.hominin.evolution.advancement.HomininAdvancements.award(player, "hominin/the_feast");
        }
        Cohesion.add(player, plenty ? COHESION : 1, null);
        if (attended) {
            EvolutionManager.incrementCriterion(player, "hold_feast", 1);
        }
        String reason = why == null ? "" : " for " + why;
        if (plenty) {
            player.sendSystemMessage(Component.literal("The feast" + reason + " is over. Nobody could eat another "
                    + "mouthful" + (attended ? " - you included." : ".") + " Everyone is full: hunger will come back "
                    + "slowly for a day and a half. And the band is closer than it was - to you, and to each other. "
                    + "(Cohesion +" + COHESION + ", every bond +" + BOND + ")").withStyle(ChatFormatting.GOLD));
            Chatter.news(player, "news_feasted", why == null ? "the feast" : why);
        } else {
            player.sendSystemMessage(Component.literal("The feast" + reason + " is over, and it was a thin one - too little "
                    + "gathered for everyone to eat their fill. Still, they ate together. (Cohesion +1, bonds +1)")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!attended) {
            player.sendSystemMessage(Component.literal("You were not there. They noticed.").withStyle(ChatFormatting.RED));
        }
    }

    // ------------------------------------------------------------ getting ready

    /** Claims putting up a rack for this band's feast: true if this member may, or already has. */
    public static boolean claimBuilder(ServerPlayer leader, BandMember member) {
        UUID current = builders.get(leader.getUUID());
        if (current != null && !current.equals(member.getUUID())
                && leader.serverLevel().getEntity(current) instanceof BandMember other && other.isAlive()) {
            return false;
        }
        builders.put(leader.getUUID(), member.getUUID());
        return true;
    }

    public static void releaseBuilder(ServerPlayer leader, BandMember member) {
        builders.remove(leader.getUUID(), member.getUUID());
    }

    // ------------------------------------------------------------ full

    /** Every tick: while full, hunger falls at a quarter of the rate. */
    public static void fullTick(ServerPlayer player) {
        if (!player.hasEffect(ModEffects.FULL)) {
            exhaustion.remove(player.getUUID());
            return;
        }
        FoodData food = player.getFoodData();
        float now = food.getExhaustionLevel();
        Float last = exhaustion.get(player.getUUID());
        if (last != null && now > last) {
            food.setExhaustion(last + (now - last) * FULL_HUNGER);
        }
        exhaustion.put(player.getUUID(), food.getExhaustionLevel());
    }

    public static void forget(UUID player) {
        reasons.remove(player);
        exhaustion.remove(player);
        builders.remove(player);
    }

    private Feast() {
    }
}
