package dev.hominin.evolution.survival;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * The year, as the savanna has it: not four seasons but two. The rains, when everything is
 * green and fat and there is plenty for everyone - and the dry, when the grass burns off, the
 * herds walk away after the water, and every band keeps what it has.
 *
 * <p>Each lasts five days and then gives way to the other. In the dry, kills and carcasses give
 * up less, foraging is harder, dry days come far more often and other bands are sharp with you.
 * In a prosperous season it all runs the other way. Like {@link Drought}, it is worked out from
 * the day, so every player in a world lives in the same season.
 */
public final class Seasons {
    public enum Season {
        PROSPEROUS("Prosperous season", ChatFormatting.GREEN),
        DRY("Dry season", ChatFormatting.GOLD);

        private final String label;
        private final ChatFormatting colour;

        Season(String label, ChatFormatting colour) {
            this.label = label;
            this.colour = colour;
        }

        public String label() {
            return label;
        }

        public ChatFormatting colour() {
            return colour;
        }
    }

    public static final int DAYS = 5;

    /** Set by {@code /hominin season}, for testing; forgotten on restart. */
    @Nullable
    private static Season forced;

    public static void force(@Nullable Season season) {
        forced = season;
    }

    public static Season of(Level level) {
        if (forced != null) {
            return forced;
        }
        return (Drought.dayOf(level) / DAYS) % 2L == 0L ? Season.PROSPEROUS : Season.DRY;
    }

    public static boolean isDry(Level level) {
        return of(level) == Season.DRY;
    }

    public static boolean isProsperous(Level level) {
        return of(level) == Season.PROSPEROUS;
    }

    /** Days of this season still to come, today included. */
    public static int daysLeft(Level level) {
        return DAYS - (int) (Drought.dayOf(level) % DAYS);
    }

    /** Hard times: a dry season, or a dry day in any season. */
    public static boolean strained(Level level) {
        return isDry(level) || Drought.isActive(level);
    }

    /** Good times: a prosperous season, on a day that is not dry. */
    public static boolean plentiful(Level level) {
        return isProsperous(level) && !Drought.isActive(level);
    }

    /** How foraging fares this season, before any dry day on top. */
    public static float forageFactor(Level level) {
        return isDry(level) ? 0.75F : 1.35F;
    }

    // ------------------------------------------------------------ what a body gives up

    /**
     * Kills and carcasses in the dry are thin: fewer of everything, some of it gone to nothing.
     * In the rains they are fat, and there is more on every one of them.
     */
    public static void adjust(Level level, List<ItemStack> drops, RandomSource random) {
        if (isDry(level)) {
            ItemStack keep = drops.isEmpty() ? ItemStack.EMPTY : drops.get(0).copyWithCount(1);
            for (ItemStack stack : drops) {
                int lost = 0;
                for (int i = 0; i < stack.getCount(); i++) {
                    if (random.nextFloat() < 0.45F) {
                        lost++;
                    }
                }
                stack.shrink(lost);
            }
            drops.removeIf(ItemStack::isEmpty);
            // Thin, not empty: there is always something on a body.
            if (drops.isEmpty() && !keep.isEmpty()) {
                drops.add(keep);
            }
        } else {
            for (ItemStack stack : drops) {
                if (stack.getCount() < stack.getMaxStackSize() && random.nextFloat() < 0.6F) {
                    stack.grow(Math.max(1, stack.getCount() / 2));
                }
            }
        }
    }

    /** An animal's drops, the same way. Hominins and their kin leave a carcass instead. */
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || dead instanceof Player
                || dead instanceof dev.hominin.evolution.band.BandMember) {
            return;
        }
        // The entities' own stacks, so shrinking or growing them changes what actually drops.
        List<ItemStack> stacks = new java.util.ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            stacks.add(drop.getItem());
        }
        adjust(dead.level(), stacks, dead.getRandom());
        event.getDrops().removeIf(drop -> drop.getItem().isEmpty());
        if (event.getDrops().isEmpty() && !stacks.isEmpty() && !stacks.get(0).isEmpty()) {
            event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY(), dead.getZ(), stacks.get(0)));
        }
    }

    // ------------------------------------------------------------ telling people

    private static final Map<UUID, Long> told = new HashMap<>();

    /** Once a day at most: when a season turns, or on first seeing one, every player hears of it. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 100 != 40) {
            return;
        }
        if (isDry(player.level()) && !player.isCreative()) {
            // Heat and thin food: a dry season empties you faster.
            player.causeFoodExhaustion(0.15F);
        }
        long block = Drought.dayOf(player.level()) / DAYS + (forced == null ? 0 : 1000 + forced.ordinal());
        Long last = told.get(player.getUUID());
        if (last != null && last == block) {
            return;
        }
        told.put(player.getUUID(), block);
        Season season = of(player.level());
        int left = daysLeft(player.level());
        String news = season == Season.DRY
                ? "The dry season is here. The grass is burning off and the herds are walking after the water. "
                        + "Kills and carcasses will be thin, foraging hard, dry days frequent - and other bands "
                        + "will keep what they have."
                : "The rains have come back: a prosperous season. Everything is green, the herds are fat, "
                        + "foraging is easy and other bands are in a giving mood.";
        player.sendSystemMessage(Component.literal(news).withStyle(season.colour()));
        if (season == Season.DRY) {
            dev.hominin.evolution.guide.Tips.drySeason(player);
        }
        player.sendSystemMessage(Component.literal("(" + season.label() + ": " + left
                + (left == 1 ? " day" : " days") + " left.)").withStyle(ChatFormatting.DARK_GRAY));
        for (dev.hominin.evolution.band.BandMember member : dev.hominin.evolution.band.Band.ownNear(player, 32.0D)) {
            if (!member.isBaby()) {
                dev.hominin.evolution.band.Lines.say(member, season == Season.DRY ? "season_dry" : "season_green");
                break;
            }
        }
    }

    public static void forget(UUID player) {
        told.remove(player);
    }

    private Seasons() {
    }
}
