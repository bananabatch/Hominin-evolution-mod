package dev.hominin.evolution.hunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.ModTags;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Bands;
import dev.hominin.evolution.network.HuntPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A hunting party. You pick what to go after - the screen says what each animal is and how it answers a blow - how
 * many go and what they carry. They go after it until it is down, or until night: nobody starts a hunt with dark
 * coming on, and a party that has not made its kill by nightfall gives up and comes back.
 *
 * <p>Megafauna is hunted in two groups. The attackers go for the one you picked and bring it down; the chasers strike
 * another of the herd, so the herd's anger follows them rather than closing round the kill. You go with whichever
 * group you choose.
 */
public final class HuntParty {
    public static final String[] WEAPONS = {"whatever they have", "spears", "clubs", "stone tools"};
    public static final int ATTACKERS = 0;
    public static final int CHASERS = 1;
    /** From here on, nobody wants to start a hunt: dark is coming. */
    private static final long TOO_LATE = 11000L;
    /** Nightfall: a party still out gives up. */
    private static final long NIGHTFALL = 12600L;
    private static final double LOOK = 48.0D;

    private record Party(List<UUID> attackers, List<UUID> chasers, UUID target, @Nullable UUID decoy, String name) {
    }

    private static final Map<UUID, Party> parties = new HashMap<>();

    private static List<BandMember> available(ServerPlayer player) {
        List<BandMember> list = new ArrayList<>();
        for (BandMember member : Band.ownNear(player, 32.0D)) {
            if (!member.isBaby() && !member.isInjured() && !member.isPregnant()
                    && !dev.hominin.evolution.band.Parties.away(member)) {
                list.add(member);
            }
        }
        list.sort(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player)));
        return list;
    }

    /** Something worth a hunting party: an animal, not a person, not anyone's, and never a bonobo. */
    private static boolean huntable(LivingEntity entity) {
        return entity.isAlive() && !(entity instanceof net.minecraft.world.entity.player.Player)
                && !(entity instanceof BandMember) && !(entity instanceof dev.hominin.evolution.entity.Bonobo)
                && (entity instanceof net.minecraft.world.entity.animal.Animal || entity.getType().is(ModTags.EntityTypes.MEGAFAUNA)
                        || entity.getType().is(ModTags.EntityTypes.PREDATORS))
                && !(entity instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame())
                && !(entity instanceof net.minecraft.world.entity.Mob mob && (mob.isLeashed() || mob.hasCustomName()));
    }

    /** "Let's hunt": what is about, for the screen. */
    public static void open(ServerPlayer player) {
        long time = player.level().getDayTime() % 24000L;
        if (time >= TOO_LATE) {
            player.displayClientMessage(Component.literal("It's too near dark. Nobody wants to start a hunt now."), true);
            return;
        }
        List<HuntPayload.Target> targets = new ArrayList<>();
        List<LivingEntity> near = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(LOOK),
                HuntParty::huntable);
        near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        for (LivingEntity entity : near.subList(0, Math.min(30, near.size()))) {
            int herd = player.level().getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(24.0D),
                    e -> e.getType() == entity.getType() && e.isAlive()).size();
            boolean mega = entity.getType().is(ModTags.EntityTypes.MEGAFAUNA);
            targets.add(new HuntPayload.Target(entity.getId(), entity.getName().getString(), MobClass.of(entity).label(),
                    (int) Math.sqrt(entity.distanceToSqr(player)), mega, herd));
        }
        PacketDistributor.sendToPlayer(player, new HuntPayload(targets, available(player).size()));
    }

    private static Predicate<ItemStack> weaponKind(int weapon) {
        return switch (weapon) {
            case 1 -> dev.hominin.evolution.item.SpearItem::isSpear;
            case 2 -> s -> s.is(ModItems.WOODEN_CLUB.get()) || s.is(ModItems.BONE_CLUB.get());
            case 3 -> s -> s.is(ModTags.Items.STONE_TOOLS);
            default -> null;
        };
    }

    /** How the party goes at it: closing in, or kinetic - throwing everything they have first. */
    public static final String[] STYLES = {"close in", "kinetic attacks (throw)"};
    public static final int KINETIC = 1;

    public static void start(ServerPlayer player, int entityId, int count, int weapon, int team) {
        start(player, entityId, count, weapon, team, 0);
    }

    public static void start(ServerPlayer player, int entityId, int count, int weapon, int team, int style) {
        ServerLevel level = player.serverLevel();
        if (level.getDayTime() % 24000L >= TOO_LATE) {
            player.displayClientMessage(Component.literal("It's too near dark. Nobody wants to start a hunt now."), true);
            return;
        }
        if (!(level.getEntity(entityId) instanceof LivingEntity target) || !huntable(target)) {
            player.displayClientMessage(Component.literal("It is gone."), true);
            return;
        }
        List<BandMember> free = available(player);
        List<BandMember> party = free.subList(0, Math.max(0, Math.min(count, free.size())));
        boolean mega = target.getType().is(ModTags.EntityTypes.MEGAFAUNA);
        LivingEntity decoy = null;
        if (mega) {
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(24.0D),
                    e -> e != target && e.isAlive() && e.getType() == target.getType())) {
                decoy = other;
                break;
            }
        }
        List<UUID> attackers = new ArrayList<>();
        List<UUID> chasers = new ArrayList<>();
        Predicate<ItemStack> carry = weaponKind(weapon);
        long ticks = Math.max(200L, NIGHTFALL - level.getDayTime() % 24000L);
        for (int i = 0; i < party.size(); i++) {
            BandMember member = party.get(i);
            member.setHuntWeapon(carry);
            member.startHunt((int) ticks);
            if (style == KINETIC) {
                member.focusOnThrowing(ticks);
            }
            boolean chaser = decoy != null && i % 2 == 1;
            (chaser ? chasers : attackers).add(member.getUUID());
            member.setTarget(chaser ? decoy : target);
            member.updateHands();
        }
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60 * 20, 0, false, false));
        String name = target.getName().getString().toLowerCase();
        parties.put(player.getUUID(), new Party(attackers, chasers, target.getUUID(), decoy == null ? null : decoy.getUUID(),
                name));
        // Bands that said they would hunt with you today: whoever of theirs is near comes too.
        for (Bands.Record band : Bands.all(level)) {
            if (dev.hominin.evolution.band.Parties.pledgedHunt(player, band)) {
                for (BandMember theirs : level.getEntitiesOfClass(BandMember.class, player.getBoundingBox().inflate(64.0D),
                        m -> m.isAlive() && band.id.equals(m.getBandId()) && !m.isBaby())) {
                    theirs.setTarget(target);
                }
            }
        }
        String who = party.isEmpty() ? "You go alone" : party.size() + " of the band go with you";
        if (decoy != null) {
            player.sendSystemMessage(Component.literal(who + " after the " + name + ". " + attackers.size()
                    + " attack it; " + chasers.size() + " go for another of the herd to draw it off. You are with the "
                    + (team == ATTACKERS ? "attackers - bring the glowing one down." : "chasers - strike another of the herd "
                            + "and lead it away."))
                    .withStyle(ChatFormatting.GOLD));
            if (team == CHASERS) {
                decoy.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60 * 20, 0, false, false));
            }
        } else {
            player.sendSystemMessage(Component.literal(who + " after the " + name + (weapon > 0 ? ", carrying "
                    + WEAPONS[weapon] : "") + (style == KINETIC ? ", throwing whatever they have at it first" : "")
                    + ". They keep at it until it is down - or until nightfall.")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Once a second: keep them at it; the kill ends it, and so does the dark. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 7) {
            return;
        }
        Party party = parties.get(player.getUUID());
        if (party == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = level.getEntity(party.target()) instanceof LivingEntity t && t.isAlive() ? t : null;
        LivingEntity decoy = party.decoy() != null && level.getEntity(party.decoy()) instanceof LivingEntity d && d.isAlive()
                ? d : null;
        long time = level.getDayTime() % 24000L;
        if (target == null) {
            end(player, level, party, "The " + party.name() + " is down. The hunting party gathers round it.");
            dev.hominin.evolution.band.Chatter.news(player, "news_kill", party.name().toLowerCase());
            return;
        }
        if (time >= NIGHTFALL) {
            end(player, level, party, "Night is coming on. The hunting party gives up on the " + party.name()
                    + " and comes back.");
            dev.hominin.evolution.band.Chatter.news(player, "news_lost", party.name().toLowerCase());
            return;
        }
        for (UUID id : party.attackers()) {
            if (level.getEntity(id) instanceof BandMember member && member.getTarget() == null
                    && member.distanceToSqr(target) < 48.0D * 48.0D) {
                member.setTarget(target);
            }
        }
        for (UUID id : party.chasers()) {
            if (level.getEntity(id) instanceof BandMember member && member.getTarget() == null) {
                // The decoy is down or gone: they join the kill.
                member.setTarget(decoy != null ? decoy : target);
            }
        }
    }

    private static void end(ServerPlayer player, ServerLevel level, Party party, String why) {
        parties.remove(player.getUUID());
        List<UUID> everyone = new ArrayList<>(party.attackers());
        everyone.addAll(party.chasers());
        for (UUID id : everyone) {
            if (level.getEntity(id) instanceof BandMember member) {
                member.setHuntWeapon(null);
                member.startHunt(0);
                if (member.getTarget() != null && (member.getTarget().getUUID().equals(party.target())
                        || member.getTarget().getUUID().equals(party.decoy()))) {
                    member.setTarget(null);
                }
            }
        }
        player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.GOLD));
    }

    private HuntParty() {
    }
}
