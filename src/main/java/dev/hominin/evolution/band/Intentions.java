package dev.hominin.evolution.band;

import java.util.Locale;

import javax.annotation.Nullable;

import dev.hominin.evolution.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What they say, they do. A member who talks about grubs has a grub on them - and offers it, if they asked "want
 * one?"; one who says they will find a stone goes and finds one, and a little later has it to hand (and, with two,
 * makes something of it); roots, fruit: the same.
 */
public final class Intentions {
    /** How long a "later" is: half a minute to a minute and a half. */
    private static final int LATER = 600;

    /** A line just said out loud. */
    public static void said(BandMember speaker, @Nullable BandMember to, String line) {
        if (!(speaker.level() instanceof ServerLevel level) || speaker.isBaby()) {
            return;
        }
        String l = line.toLowerCase(Locale.ROOT);
        if (l.contains("grub")) {
            if (speaker.getRandom().nextFloat() < 0.6F) {
                ItemStack grub = new ItemStack(ModItems.GRUB.get());
                if (l.contains("want one") && to != null) {
                    // Offered, and handed over.
                    to.addToInventory(grub);
                } else {
                    speaker.addToInventory(grub);
                }
            }
            return;
        }
        if (l.contains("roots")) {
            later(level, speaker, new ItemStack(ModItems.ROOTS.get(), 1 + speaker.getRandom().nextInt(2)), null);
            return;
        }
        if (l.contains("fruit") || l.contains("berries")) {
            later(level, speaker, new ItemStack(Items.SWEET_BERRIES, 2 + speaker.getRandom().nextInt(2)), null);
            return;
        }
        boolean stone = l.contains("stone") || l.contains("rock");
        boolean going = l.contains("i'll find") || l.contains("i'll get") || l.contains("going to find")
                || l.contains("i need") || l.contains("take two") || l.contains("good stone here") || l.contains("rings right");
        if (stone && going) {
            Item kind = speaker.preferredStone();
            if (kind == null || kind == ModItems.LIMESTONE_ROCK.get()) {
                kind = speaker.getRandom().nextBoolean() ? ModItems.CHERT_ROCK.get() : ModItems.GRANITE_ROCK.get();
            }
            // Two: enough to strike one against the other, and make something of it.
            later(level, speaker, new ItemStack(kind, 2), "stone_found");
        }
    }

    /** A little later, it is in their hands. */
    private static void later(ServerLevel level, BandMember member, ItemStack stack, @Nullable String announce) {
        MinecraftServer server = level.getServer();
        int delay = LATER + member.getRandom().nextInt(LATER * 2);
        server.tell(new TickTask(server.getTickCount() + delay, () -> {
            if (member.isAlive() && !member.isRemoved()) {
                member.addToInventory(stack);
                if (announce != null) {
                    Lines.say(member, announce, "");
                }
            }
        }));
    }

    private Intentions() {
    }
}
