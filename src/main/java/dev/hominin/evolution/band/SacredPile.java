package dev.hominin.evolution.band;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.block.ToolPileBlockEntity;
import dev.hominin.evolution.guide.Alerts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The Pile - a band's first culture beyond how it treats its dead. When something happens that matters (a birth, a
 * new roof, a new ally), the band wants it marked: before the next night is over, the best thing you carry goes on
 * the Pile. Only something worth giving up is taken - obsidian, a chert hammerstone, a face pebble, a fine tool -
 * and nothing is ever taken back off it.
 *
 * <p>Every thing given is +8 cohesion. Let the night pass without giving anything and it is -12. A desperate band
 * may one day come and take from it - and your band will not forget who did.
 */
public final class SacredPile {
    private static final String OPEN_UNTIL = "pile_open_until";
    private static final String GIVEN = "pile_given";
    private static final String WHY = "pile_why";
    private static final int GIVE_COHESION = 8;
    private static final int MISS_COHESION = -12;
    /** Up to this many things for one event - it is a mark, not a dump. */
    private static final int MOST_PER_EVENT = 3;

    private static final Map<UUID, String> reasons = new java.util.HashMap<>();

    // ------------------------------------------------------------ where it stands

    public static final int ACTION_WHERE = 65;
    private static final int HERE = 0;
    private static final int ON_STONE = 1;
    private static final int HIGH = 2;
    private static final int LATER = 3;
    /** Where the Pile stands decides what it does for the band: 0 anywhere, 1 on stone, 2 on high ground. */
    private static final String SITE = "pile_site";
    /** The places offered, waiting on your choice. */
    private static final Map<UUID, BlockPos[]> offered = new java.util.HashMap<>();

    /**
     * The band has taken up the Pile: where will it be? Here; on the stone outcrop nearby, if there is one - the stone
     * gives to the stone, and every gift pulls the band closer; or on the highest ground near you, where it is seen from
     * everywhere - every gift makes the band's presence felt. Never on anything built.
     */
    public static void askWhere(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (find(level, player.getUUID()) != null) {
            return;
        }
        BlockPos here = ToolPiles.canPileAt(level, player.blockPosition()) ? player.blockPosition() : null;
        BlockPos stone = null;
        BlockPos high = null;
        double stoneBest = Double.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                int x = player.getBlockX() + dx;
                int z = player.getBlockZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos top = new BlockPos(x, y, z);
                net.minecraft.world.level.block.state.BlockState under = level.getBlockState(at.set(x, y - 1, z));
                if (!ToolPiles.canPileAt(level, top) || dev.hominin.evolution.build.Sites.containing(level, top) != null) {
                    continue;
                }
                boolean deposit = under.is(dev.hominin.evolution.ModBlocks.CHERT_DEPOSIT.get())
                        || under.is(dev.hominin.evolution.ModBlocks.FINE_CHERT_DEPOSIT.get())
                        || under.is(dev.hominin.evolution.ModBlocks.OBSIDIAN_DEPOSIT.get())
                        || under.is(dev.hominin.evolution.ModBlocks.QUARTZITE_DEPOSIT.get())
                        || under.is(dev.hominin.evolution.ModBlocks.BASALT_DEPOSIT.get())
                        || under.is(dev.hominin.evolution.ModBlocks.LIMESTONE_DEPOSIT.get());
                boolean built = !deposit && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(under.getBlock())
                        .getNamespace().equals(dev.hominin.evolution.HomininEvolutionMod.MODID);
                if (deposit && dx * dx + dz * dz < stoneBest) {
                    stoneBest = dx * dx + dz * dz;
                    stone = top;
                }
                if (!built && dx * dx + dz * dz <= 20 * 20 && y > highest && !under.is(net.minecraft.tags.BlockTags.LEAVES)
                        && !under.is(net.minecraft.tags.BlockTags.LOGS)) {
                    highest = y;
                    high = top;
                }
            }
        }
        offered.put(player.getUUID(), new BlockPos[] {here, stone, high});
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Integer> values = new java.util.ArrayList<>();
        if (here != null) {
            labels.add("Here, where I stand");
            values.add(HERE);
        }
        if (stone != null) {
            labels.add("On the stone outcrop, " + (int) Math.sqrt(stoneBest) + " blocks off - it pulls the band closer");
            values.add(ON_STONE);
        }
        if (high != null) {
            labels.add("On the highest ground near here - seen from everywhere");
            values.add(HIGH);
        }
        labels.add("Later - I will lay the first thing on it myself");
        values.add(LATER);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new dev.hominin.evolution.network.ChoicesPayload(
                player.getId(), ACTION_WHERE, "Where will the Pile be?", labels, values));
    }

    public static void chooseWhere(ServerPlayer player, int choice) {
        BlockPos[] places = offered.remove(player.getUUID());
        ServerLevel level = player.serverLevel();
        if (places == null || choice == LATER || choice < 0 || choice > HIGH || places[choice] == null
                || find(level, player.getUUID()) != null) {
            if (choice == LATER) {
                player.sendSystemMessage(Component.literal("When something happens that deserves it, sneak-use the ground "
                        + "with something worth giving up: the Pile starts there.").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            return;
        }
        BlockPos at = places[choice];
        if (!ToolPiles.canPileAt(level, at)) {
            player.displayClientMessage(Component.literal("Something is in the way there now."), true);
            return;
        }
        level.setBlock(at, ModBlocks.TOOL_PILE.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(at) instanceof ToolPileBlockEntity pile)) {
            return;
        }
        pile.setKind(ToolPileBlockEntity.Kind.SACRED);
        pile.setOwner(player.getUUID());
        pile.setMadeAt(level.getGameTime());
        // The first stone on it is the band's: something to build on.
        pile.add(new ItemStack(dev.hominin.evolution.ModItems.CHERT_ROCK.get()), player.getUUID(), "the band");
        ToolPiles.adopt(level, player.getUUID(), at);
        counters(player).put(SITE, choice);
        level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.7F);
        String how = choice == ON_STONE
                ? "The band lays the first stone on the outcrop itself: the Pile rises out of the rock. Every gift on it "
                        + "will pull the band closer."
                : choice == HIGH ? "The band lays the first stone on the high ground, where anyone for miles can see it. "
                        + "Every gift on it will make your band's presence felt."
                : "The band lays the first stone where you stand. This is the Pile now.";
        player.sendSystemMessage(Component.literal(how + " (" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Something taken back off the Pile. It can be done - and everyone sees it done. */
    public static void takenBack(ServerPlayer player, ItemStack taken) {
        Cohesion.add(player, -4, "took " + taken.getHoverName().getString().toLowerCase() + " back off the Pile");
        player.sendSystemMessage(Component.literal("You take the " + taken.getHoverName().getString().toLowerCase()
                + " back off the Pile. The band goes quiet. (Cohesion -4)").withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static Map<String, Integer> counters(ServerPlayer player) {
        return player.getData(Attachments.PLAYER_EVOLUTION_DATA).getCriterionCounters();
    }

    /** Whether the band keeps the Pile at all. */
    public static boolean kept(ServerPlayer player) {
        return Morals.holds(player, Morals.Moral.THE_PILE);
    }

    /** Whether something is waiting to be marked on it now. */
    public static boolean open(ServerPlayer player) {
        return counters(player).getOrDefault(OPEN_UNTIL, 0) > minute(player);
    }

    private static int minute(ServerPlayer player) {
        return (int) (player.level().getGameTime() / 1200L);
    }

    /** Minutes of game time until the end of the next night from now. */
    private static int untilNextNightEnds(ServerPlayer player) {
        long time = player.level().getDayTime() % 24000L;
        // Night runs to 23000; if it is already night, the one after this.
        long ticks = time < 13000L ? 23000L - time : 24000L - time + 23000L;
        return (int) Math.ceil(ticks / 1200.0D);
    }

    /** Something worth marking has happened. */
    public static void event(ServerPlayer player, String what) {
        if (!kept(player)) {
            return;
        }
        Map<String, Integer> counters = counters(player);
        if (open(player)) {
            reasons.put(player.getUUID(), reasons.getOrDefault(player.getUUID(), what) + ", and " + what);
            return;
        }
        counters.put(OPEN_UNTIL, minute(player) + untilNextNightEnds(player));
        counters.put(GIVEN, 0);
        reasons.put(player.getUUID(), what);
        BlockPos pile = find(player.serverLevel(), player.getUUID());
        Alerts.urgent(player, Alerts.Kind.BAND, Component.literal("For " + what + ", the band wants something laid on the "
                + "Pile - the best thing you carry - before the next night is out. "
                + (pile == null ? "There is no Pile yet: sneak-use the ground on your own ground with something worth "
                        + "giving up, and it starts there." : "The Pile is " + (int) Math.sqrt(pile.distSqr(player.blockPosition()))
                        + " blocks away."))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Every few seconds: a window that closed with nothing given costs. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 200 != 91) {
            return;
        }
        Map<String, Integer> counters = counters(player);
        int until = counters.getOrDefault(OPEN_UNTIL, 0);
        if (until <= 0 || until > minute(player)) {
            return;
        }
        counters.remove(OPEN_UNTIL);
        int given = counters.getOrDefault(GIVEN, 0);
        counters.remove(GIVEN);
        String why = reasons.remove(player.getUUID());
        if (given == 0 && kept(player)) {
            Cohesion.add(player, MISS_COHESION, "marked " + (why == null ? "it" : why) + " on the Pile");
            Alerts.urgent(player, Alerts.Kind.WARNING, Component.literal("The night passed and nothing went on the Pile"
                    + (why == null ? "." : " for " + why + ".") + " The band noticed. (Cohesion " + MISS_COHESION + ")")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** The band's Pile, if it has one. */
    @Nullable
    public static BlockPos find(ServerLevel level, UUID owner) {
        for (BlockPos pos : ToolPiles.piles(level, owner)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ToolPileBlockEntity pile
                    && pile.kind() == ToolPileBlockEntity.Kind.SACRED) {
                return pos;
            }
        }
        return null;
    }

    /** Sneak-using the ground with something worth it while the band is waiting: the Pile starts here. */
    public static boolean start(ServerPlayer player, BlockPos at, ItemStack stack) {
        ServerLevel level = player.serverLevel();
        if (!kept(player) || !open(player) || !ToolPileBlockEntity.important(stack) || find(level, player.getUUID()) != null
                || !ToolPiles.canPileAt(level, at)) {
            return false;
        }
        level.setBlock(at, ModBlocks.TOOL_PILE.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(at) instanceof ToolPileBlockEntity pile)) {
            return false;
        }
        pile.setKind(ToolPileBlockEntity.Kind.SACRED);
        pile.setOwner(player.getUUID());
        ToolPiles.adopt(level, player.getUUID(), at);
        player.sendSystemMessage(Component.literal("You lay it on the ground, and the band gathers round: this is the "
                + "Pile now. What goes on it stays.").withStyle(ChatFormatting.LIGHT_PURPLE));
        give(player, pile, at, stack);
        return true;
    }

    /** Something laid on the Pile. */
    public static void offer(ServerPlayer player, BlockPos pos, ItemStack stack) {
        if (!(player.serverLevel().getBlockEntity(pos) instanceof ToolPileBlockEntity pile)) {
            return;
        }
        if (!kept(player)) {
            player.displayClientMessage(Component.literal("Your band does not keep the Pile. (Culture tab: \"The Pile\".)"),
                    true);
            return;
        }
        if (!open(player)) {
            player.displayClientMessage(Component.literal("The Pile is added to when something happens that deserves it "
                    + "- a birth, a new roof, a new ally. Not now."), true);
            return;
        }
        if (!ToolPileBlockEntity.important(stack)) {
            player.displayClientMessage(Component.literal("That is not worth the Pile. It takes what is hard to give up: "
                    + "obsidian, a chert hammerstone, a face pebble, a fine tool."), true);
            return;
        }
        if (counters(player).getOrDefault(GIVEN, 0) >= MOST_PER_EVENT) {
            player.displayClientMessage(Component.literal("That is enough for this one. Keep the rest for the next."),
                    true);
            return;
        }
        give(player, pile, pos, stack);
    }

    private static void give(ServerPlayer player, ToolPileBlockEntity pile, BlockPos pos, ItemStack stack) {
        ItemStack one = stack.copyWithCount(1);
        if (!pile.add(one, player.getUUID(), player.getName().getString())) {
            player.displayClientMessage(Component.literal("There is no more room on the Pile."), true);
            return;
        }
        stack.shrink(1);
        counters(player).merge(GIVEN, 1, Integer::sum);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
        Cohesion.add(player, GIVE_COHESION);
        int site = counters(player).getOrDefault(SITE, HERE);
        if (site == ON_STONE) {
            Cohesion.add(player, 3);
        } else if (site == HIGH) {
            Presence.add(player, 1, "the Pile stands where everyone can see it");
        }
        player.sendSystemMessage(Component.literal("You lay the " + one.getHoverName().getString().toLowerCase()
                + " on the Pile. The band watches, and nobody says anything. (Cohesion +" + GIVE_COHESION + ")")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        for (BandMember member : Band.ownNear(player, 24.0D)) {
            if (!member.isBaby()) {
                member.getLookControl().setLookAt(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            }
        }
    }

    /**
     * A desperate band's people come in the night and take from the Pile. The band will not forget: standing falls
     * through the floor, and they are marked - your band holds it against them from then on.
     */
    public static boolean robbed(ServerPlayer player, Bands.Record band) {
        ServerLevel level = player.serverLevel();
        BlockPos at = find(level, player.getUUID());
        if (at == null || !(level.getBlockEntity(at) instanceof ToolPileBlockEntity pile) || pile.isEmpty()) {
            return false;
        }
        ItemStack taken = pile.takeTop();
        if (pile.isEmpty()) {
            level.removeBlock(at, false);
        }
        band.pileThieves.add(player.getUUID());
        Bands.changed(level);
        Relations.change(player, band, -25, "they took from the Pile");
        Cohesion.add(player, -4, null);
        Alerts.urgent(player, Alerts.Kind.DANGER, Component.literal(BandNames.capital(band.name) + " came in the night "
                + "and took " + taken.getHoverName().getString().toLowerCase() + " off the Pile. Your band will not "
                + "forget who did it.").withStyle(ChatFormatting.DARK_RED));
        return true;
    }

    /** Whether this band stole from the player's Pile. */
    public static boolean grudge(ServerPlayer player, Bands.Record band) {
        return band.pileThieves.contains(player.getUUID());
    }

    /** For the journal. */
    @Nullable
    public static String describe(ServerPlayer player) {
        if (!kept(player) || !open(player)) {
            return null;
        }
        int left = counters(player).getOrDefault(OPEN_UNTIL, 0) - minute(player);
        int given = counters(player).getOrDefault(GIVEN, 0);
        return "The Pile: " + reasons.getOrDefault(player.getUUID(), "something that happened") + " - "
                + (given > 0 ? given + " given so far" : "nothing given yet, " + left + " min to the end of the night")
                + ". (+8 cohesion each; nothing given by then, -12.)";
    }

    private SacredPile() {
    }
}
