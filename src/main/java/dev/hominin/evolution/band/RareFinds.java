package dev.hominin.evolution.band;

import dev.hominin.evolution.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Rare things, and the fuss made over them. A club-shaped limb that came down with the leaves; a whole nodule of
 * chert; a pebble with a face in it; a quartz crystal as clear as water. Whoever finds one - you, or one of your
 * band - everybody hears about it.
 *
 * <p>The pebble is the Makapansgat cobble: a jasperite stone some australopithecine carried kilometres from where
 * it lay, three million years ago, for no reason anyone can find except that it looks back at you. The crystals
 * are Wonderwerk's: clear quartz carried into a cave a million years ago by people who had no use for them but to
 * have them. Neither does anything. Both are the most treasured things a band owns.
 */
public final class RareFinds {
    /** One in this many successful forages turns up a pebble with a face in it. */
    public static final float PEBBLE_FORAGE_CHANCE = 0.006F;
    /** One in this many loose-rock scatters picked over. */
    public static final float PEBBLE_ROCK_CHANCE = 0.008F;
    /** One in this many faces struck off a deposit: a crystal in the break. */
    public static final float CRYSTAL_CHANCE = 0.008F;

    public static boolean isRare(ItemStack stack) {
        return stack.is(ModItems.WOODEN_CLUB.get()) || stack.is(ModItems.FACE_PEBBLE.get())
                || stack.is(ModItems.QUARTZ_CRYSTAL.get()) || stack.is(ModItems.CHERT_HAMMERSTONE.get());
    }

    private static String what(ItemStack stack) {
        String name = stack.getHoverName().getString().toLowerCase();
        return (name.startsWith("a") || name.startsWith("e") || name.startsWith("o") ? "an " : "a ") + name;
    }

    /** The player found something rare: said out loud, and the band crowds round. */
    public static void playerFound(ServerPlayer player, ItemStack stack, String how) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS,
                1.0F, 1.0F);
        player.sendSystemMessage(Component.literal("* Rare find: ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(how + " - " + what(stack) + ".").withStyle(ChatFormatting.LIGHT_PURPLE)));
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player) {
                other.sendSystemMessage(Component.literal(player.getGameProfile().getName() + " has found " + what(stack)
                        + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        for (BandMember member : Band.ownNear(player, 16.0D)) {
            if (!member.isBaby()) {
                member.getLookControl().setLookAt(player);
            }
        }
    }

    /** One of your band found something rare. */
    public static void memberFound(BandMember member, ItemStack stack) {
        Player leader = member.leaderPlayer();
        if (leader == null || member.isWild()) {
            return;
        }
        member.ensureName();
        member.level().playSound(null, member.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL,
                1.0F, 1.1F);
        leader.sendSystemMessage(Component.literal("* ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(member.getName().getString() + " has found something rare: " + what(stack)
                        + "! Everyone crowds round to see it.").withStyle(ChatFormatting.LIGHT_PURPLE)));
    }

    /** Something turned up out of the ground by one of your band: sometimes more than grubs. */
    public static void memberForaged(BandMember member) {
        if (member.getRandom().nextFloat() < PEBBLE_FORAGE_CHANCE) {
            ItemStack pebble = new ItemStack(ModItems.FACE_PEBBLE.get());
            member.addToInventory(pebble.copy());
            memberFound(member, pebble);
        }
    }

    /** A face struck off a deposit: sometimes a crystal in the break. */
    public static void memberQuarried(BandMember member) {
        if (member.getRandom().nextFloat() < CRYSTAL_CHANCE) {
            ItemStack crystal = new ItemStack(ModItems.QUARTZ_CRYSTAL.get());
            member.addToInventory(crystal.copy());
            memberFound(member, crystal);
        }
    }

    /** Handed a treasure: one of the band will not forget who gave it to them. */
    public static boolean treasured(BandMember member, ServerPlayer giver, ItemStack stack) {
        if (!stack.is(ModItems.FACE_PEBBLE.get()) && !stack.is(ModItems.QUARTZ_CRYSTAL.get())) {
            return false;
        }
        member.ensureName();
        member.addToInventory(stack.copyWithCount(1));
        stack.shrink(1);
        member.addBond(3);
        Cohesion.addLimited(giver, "treasure_given", 2, 20 * 60 * 20L);
        Mood.gave(giver, 4);
        member.level().playSound(null, member.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F,
                1.0F);
        giver.sendSystemMessage(Component.literal(member.getName().getString() + " turns it over, and over, and will not "
                + "put it down. They will not forget who gave them this. (Bond +3)").withStyle(ChatFormatting.LIGHT_PURPLE));
        return true;
    }

    private RareFinds() {
    }
}
