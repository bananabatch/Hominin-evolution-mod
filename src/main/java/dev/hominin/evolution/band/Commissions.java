package dev.hominin.evolution.band;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.item.AcheuleanToolItem;
import dev.hominin.evolution.knapping.Acheulean;
import dev.hominin.evolution.network.ChoicesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Asking somebody to make you a tool. You pick out a member and ask; they name a price - what
 * they want, or their favourite food, more of it the better their hands - and once it is paid
 * and they have the stone, they sit down and knap it for you.
 *
 * <p>Their skill decides what comes out: a master knapper's hand axe is excellent, and flawless
 * off chert or obsidian - which is exactly why a master is worth what they ask. Somebody who
 * likes you asks less, and somebody who likes you a great deal asks nothing at all.
 */
public final class Commissions {
    public static final int ACTION_KNAP = 2;

    private static final int HAND_AXE = 0;
    private static final int CLEAVER = 1;
    private static final int CHOPPER = 2;
    private static final String[] KIND_NAMES = {"hand axe", "cleaver", "chopper"};

    /** An unpaid ask lapses after this long. */
    private static final long UNPAID_LASTS = 10 * 60 * 20L;
    /** Sitting down and knapping it. */
    private static final int WORK_TICKS = 25 * 20;
    /** Close enough to be handed the tool when it is done. */
    private static final double HAND_OVER = 16.0D;
    /** Bond at which they make it cheaper, and at which they make it for nothing. */
    public static final int CHEAP_BOND = 5;
    public static final int FREE_BOND = 8;

    // ------------------------------------------------------------ asking

    /** Whether this member can make Acheulean tools: erectus and after. */
    private static boolean makesAcheulean(BandMember member) {
        String stage = member.getStage().getPath();
        return dev.hominin.evolution.band.goal.CraftGoal.canCraft(member) && !stage.equals("homo_habilis")
                && !stage.equals("homo_rudolfensis") && !Paranthropus.is(member);
    }

    /** What they would usually turn out, for the choice labels. */
    private static int usualTier(BandMember member) {
        return Species.capQuality(member.getStage(), Math.max(1, member.getKnapLevel()));
    }

    /** The list of what you could ask this member to make. */
    public static void open(ServerPlayer player, BandMember member) {
        member.ensureName();
        String name = member.getName().getString();
        if (member.isBaby() || member.isWild() || !member.isLedBy(player)) {
            player.displayClientMessage(Component.literal(name + " will not make anything for you."), true);
            return;
        }
        if (!member.getCommission().isEmpty()) {
            CompoundTag job = member.getCommission();
            player.displayClientMessage(Component.literal(name + " is already making you a "
                    + KIND_NAMES[job.getInt("Kind")] + (job.getBoolean("Paid") ? "." : " - once it is paid for.")), true);
            return;
        }
        if (!dev.hominin.evolution.band.goal.CraftGoal.canCraft(member)) {
            player.displayClientMessage(Component.literal(name + " does not know how to make tools yet."), true);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        int level = member.getKnapLevel();
        if (makesAcheulean(member)) {
            String usual = AcheuleanToolItem.TIER_NAMES[usualTier(member)].toLowerCase();
            String best = level <= 1 ? ", flawless off chert or obsidian" : level == 2 ? ", excellent now and then" : "";
            labels.add("A hand axe (usually " + usual + best + ") - " + priceWords(member, HAND_AXE));
            values.add(HAND_AXE);
            labels.add("A cleaver (usually " + usual + best + ") - " + priceWords(member, CLEAVER));
            values.add(CLEAVER);
        }
        labels.add("A chopper - " + priceWords(member, CHOPPER));
        values.add(CHOPPER);
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(member.getId(), ACTION_KNAP,
                "Ask " + name + " (knapping level " + level + ") to make...", labels, values));
    }

    /** How many of the price item they ask: more for better hands, less for a friend. */
    private static int price(BandMember member, int kind) {
        int base = kind == CHOPPER ? 1 : switch (member.getKnapLevel()) {
            case 1 -> 4;
            case 2 -> 3;
            case 3 -> 2;
            default -> 1;
        };
        if (member.getBond() >= FREE_BOND) {
            return 0;
        }
        return member.getBond() >= CHEAP_BOND ? Math.max(1, base - 1) : base;
    }

    /** What they are paid in: what they want right now, or else the food they like best. */
    private static Item payItem(BandMember member) {
        return member.getWant() != null ? member.getWant() : member.favouriteFood();
    }

    private static String priceWords(BandMember member, int kind) {
        int price = price(member, kind);
        return price == 0 ? "free, as a friend" : price + " " + Wants.describeItem(payItem(member));
    }

    /** They picked what to ask for. */
    public static void choose(ServerPlayer player, int entityId, int kind) {
        if (kind < HAND_AXE || kind > CHOPPER || !(player.level().getEntity(entityId) instanceof BandMember member)
                || !member.isLedBy(player) || member.distanceToSqr(player) > 16.0D * 16.0D
                || !member.getCommission().isEmpty() || (kind != CHOPPER && !makesAcheulean(member))) {
            return;
        }
        member.ensureName();
        String name = member.getName().getString();
        int price = price(member, kind);
        Item pay = payItem(member);
        CompoundTag job = new CompoundTag();
        job.putInt("Kind", kind);
        job.putUUID("For", player.getUUID());
        job.putString("Pay", BuiltInRegistries.ITEM.getKey(pay).toString());
        job.putInt("Owed", price);
        job.putBoolean("Paid", price == 0);
        job.putLong("Expires", player.level().getGameTime() + UNPAID_LASTS);
        member.setCommission(job);
        member.attendTo(player, BandMember.ATTEND_TICKS);
        if (price == 0) {
            player.sendSystemMessage(Component.literal(name + ": \"For you? Nothing. I'll make you a good "
                    + KIND_NAMES[kind] + ".\"").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            player.sendSystemMessage(Component.literal(name + " will make you a " + KIND_NAMES[kind] + " for "
                    + price + " " + Wants.describeItem(pay) + ".").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" (Hold it and choose \"Here, take this\" under H.)")
                            .withStyle(ChatFormatting.GRAY)));
        }
        if (!hasStone(member, kind)) {
            player.sendSystemMessage(Component.literal("They will need " + (kind == CHOPPER ? "a stone" : "two good stones")
                    + " to work, too - they will look, or you can hand them some.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** A friend who knaps well, making you one unasked. Returns true if they took it on. */
    public static boolean offerUnasked(BandMember member, Player leader) {
        if (!member.getCommission().isEmpty() || !makesAcheulean(member) || !hasStone(member, HAND_AXE)) {
            return false;
        }
        var inventory = leader.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).getItem() instanceof AcheuleanToolItem) {
                return false;
            }
        }
        int kind = member.getRandom().nextBoolean() ? HAND_AXE : CLEAVER;
        CompoundTag job = new CompoundTag();
        job.putInt("Kind", kind);
        job.putUUID("For", leader.getUUID());
        job.putString("Pay", "minecraft:air");
        job.putInt("Owed", 0);
        job.putBoolean("Paid", true);
        member.setCommission(job);
        member.ensureName();
        leader.sendSystemMessage(Component.literal(member.getName().getString() + ": \"You have nothing with a real edge. "
                + "Wait - I'll make you a " + KIND_NAMES[kind] + ".\"").withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    // ------------------------------------------------------------ paying

    /** The player handed them something. Returns true if it went towards a commission. */
    public static boolean receive(BandMember member, Player player, ItemStack held) {
        CompoundTag job = member.getCommission();
        if (job.isEmpty() || job.getBoolean("Paid") || !job.hasUUID("For") || !job.getUUID("For").equals(player.getUUID())) {
            return false;
        }
        Item pay = itemOf(job.getString("Pay"));
        if (pay == null || !held.is(pay)) {
            return false;
        }
        int owed = job.getInt("Owed");
        int given = Math.min(owed, held.getCount());
        member.addToInventory(held.copyWithCount(given));
        if (!player.getAbilities().instabuild) {
            held.shrink(given);
        }
        owed -= given;
        job.putInt("Owed", owed);
        member.playSound(SoundEvents.ITEM_PICKUP, 0.7F, 1.1F);
        member.ensureName();
        String name = member.getName().getString();
        if (owed > 0) {
            player.displayClientMessage(Component.literal(name + " takes " + given + ". " + owed + " more "
                    + Wants.describeItem(pay) + " and they'll start."), true);
            return true;
        }
        job.putBoolean("Paid", true);
        if (member.getWant() == pay) {
            member.clearWant();
        }
        member.addBond(1);
        player.displayClientMessage(Component.literal(name + " takes it. A deal: they'll make it now.")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    // ------------------------------------------------------------ doing it

    private static boolean hasStone(BandMember member, int kind) {
        int stones = 0;
        var pack = member.getInventory();
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (Wants.isGoodStone(stack) || (kind == CHOPPER && stack.is(ModItems.ROCK.get()))) {
                stones += stack.getCount();
            }
        }
        return stones >= (kind == CHOPPER ? 1 : 2);
    }

    /** Once a second, per member. */
    public static void tick(BandMember member) {
        CompoundTag job = member.getCommission();
        if (job.isEmpty() || !job.hasUUID("For")) {
            return;
        }
        long now = member.level().getGameTime();
        Player player = member.level().getPlayerByUUID(job.getUUID("For"));
        int kind = job.getInt("Kind");
        member.ensureName();
        String name = member.getName().getString();
        if (!job.getBoolean("Paid")) {
            if (now > job.getLong("Expires")) {
                member.setCommission(new CompoundTag());
                if (player != null) {
                    player.displayClientMessage(Component.literal(name + " stops waiting to be paid for the "
                            + KIND_NAMES[kind] + "."), false);
                }
            }
            return;
        }
        long workUntil = job.getLong("WorkUntil");
        if (workUntil == 0L) {
            if (!hasStone(member, kind)) {
                if (player != null && now % (60 * 20) < 20 && member.distanceToSqr(player) < HAND_OVER * HAND_OVER) {
                    player.displayClientMessage(Component.literal(name + " needs " + (kind == CHOPPER ? "a stone"
                            : "two good stones") + " to make your " + KIND_NAMES[kind] + "."), true);
                }
                return;
            }
            job.putLong("WorkUntil", now + WORK_TICKS);
            return;
        }
        if (now < workUntil) {
            if (now % 60 < 20) {
                member.swing(InteractionHand.MAIN_HAND);
                member.level().playSound(null, member.blockPosition(), SoundEvents.STONE_HIT, SoundSource.NEUTRAL,
                        0.7F, 1.1F);
            }
            return;
        }
        // Done - but it is handed over, not left lying about: wait until you are near.
        if (player == null || member.distanceToSqr(player) > HAND_OVER * HAND_OVER) {
            return;
        }
        ItemStack tool = make(member, kind);
        if (tool.isEmpty()) {
            return;
        }
        member.setCommission(new CompoundTag());
        String made = tool.getItem() instanceof AcheuleanToolItem
                ? AcheuleanToolItem.TIER_NAMES[AcheuleanToolItem.qualityOf(tool)].toLowerCase() + " " + KIND_NAMES[kind]
                : KIND_NAMES[kind];
        player.sendSystemMessage(Component.literal(name + " hands you the " + made + " they made for you.")
                .withStyle(ChatFormatting.GOLD));
        member.level().playSound(null, member.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.8F, 1.0F);
        if (!player.getInventory().add(tool)) {
            player.drop(tool, false);
        }
    }

    /** Takes the stone and knaps it, at this member's level. */
    private static ItemStack make(BandMember member, int kind) {
        if (kind == CHOPPER) {
            ItemStack stone = member.takeFirst(s -> Wants.isGoodStone(s) || s.is(ModItems.ROCK.get()));
            return stone.isEmpty() ? ItemStack.EMPTY
                    : dev.hominin.evolution.item.StoneMaterial.stampFrom(new ItemStack(ModItems.CHOPPER.get()), stone);
        }
        ItemStack first = member.takeFirst(Wants::isGoodStone);
        if (first.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack second = member.takeFirst(s -> s.is(first.getItem()));
        if (second.isEmpty()) {
            member.takeFirst(Wants::isGoodStone);
        }
        int quality = Species.capQuality(member.getStage(), Acheulean.rollQuality(member.getKnapLevel(), first,
                member.getRandom()));
        member.practiseKnapping();
        Item tool = kind == HAND_AXE ? ModItems.HAND_AXE.get() : ModItems.CLEAVER.get();
        return dev.hominin.evolution.item.StoneMaterial.stampFrom(((AcheuleanToolItem) tool).make(quality), first);
    }

    /** For the band list: what this member is making, and for whom. */
    @Nullable
    public static String describe(BandMember member, UUID player) {
        CompoundTag job = member.getCommission();
        if (job.isEmpty() || !job.hasUUID("For") || !job.getUUID("For").equals(player)) {
            return null;
        }
        String kind = KIND_NAMES[job.getInt("Kind")];
        if (!job.getBoolean("Paid")) {
            Item pay = itemOf(job.getString("Pay"));
            return "will make you a " + kind + " for " + job.getInt("Owed") + " more " + Wants.describeItem(pay);
        }
        return job.getLong("WorkUntil") == 0L ? "needs stone for your " + kind : "making your " + kind;
    }

    @Nullable
    private static Item itemOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
    }

    private Commissions() {
    }
}
