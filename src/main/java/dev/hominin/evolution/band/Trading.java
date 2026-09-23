package dev.hominin.evolution.band;

import java.util.Map;

import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Exchange with another band. There is no shared language and no money: you hold
 * something out, and they weigh it against what they carry.
 *
 * <p>Everything worth having falls into a tier, by what it cost to get. Anything can
 * be traded for something of its own tier or any tier below - a long branch buys food,
 * a flake buys a long branch - but never for something ranked above it. At the top
 * are the things a band may never have made or found at all: obsidian, a chert
 * hammerstone, an Oldowan multi tool.
 */
public final class Trading {
    public static final int MAX_TIER = 5;

    /** Tier names, lowest first, as the guidebook describes them. */
    public static final String[] TIER_NAMES = {"", "Common", "Useful", "Crafted", "Prized", "Treasured"};

    private static final Map<String, Integer> TIERS = Map.ofEntries(
            // 1 - picked up anywhere
            Map.entry("rock", 1), Map.entry("grub", 1), Map.entry("beetle", 1), Map.entry("earthworm", 1),
            Map.entry("meat_chunk", 1), Map.entry("nesting_material", 1),
            // 2 - worth a walk or a little work
            Map.entry("long_branch", 2), Map.entry("sharpened_stick", 2), Map.entry("bone_marrow", 2),
            Map.entry("long_bone", 2), Map.entry("termite_stick", 2), Map.entry("hammerstone", 2),
            Map.entry("limestone_rock", 2), Map.entry("granite_rock", 2), Map.entry("basalt_rock", 2),
            // 3 - made, or good stone
            Map.entry("flake", 3), Map.entry("pointy_stick", 3), Map.entry("lomekwian_tool", 3),
            Map.entry("digging_stick", 3), Map.entry("grinding_rock", 3), Map.entry("chert_rock", 3),
            Map.entry("nest", 3),
            // 4 - real tools
            Map.entry("chopper", 4), Map.entry("sharpened_spear", 4), Map.entry("wooden_club", 4),
            // 5 - rare, or skill a band may not have
            Map.entry("obsidian_rock", 5), Map.entry("chert_hammerstone", 5), Map.entry("oldowan_multitool", 5),
            Map.entry("fire_hardened_spear", 5),
            // Erectus work. An Acheulean tool's tier is then set by its quality - see qualityPenalty.
            Map.entry("hand_axe", 5), Map.entry("cleaver", 5), Map.entry("hide", 2),
            Map.entry("workable_branch", 2), Map.entry("workable_shaft", 3), Map.entry("twine", 2),
            Map.entry("thatch", 1), Map.entry("cooked_meat_chunk", 2));

    /** Finer ordering inside a tier, so "the best thing they have" is well defined. */
    private static final Map<String, Integer> WITHIN_TIER = Map.of(
            "oldowan_multitool", 3, "chert_hammerstone", 2, "chopper", 2, "flake", 2, "termite_stick", 1,
            "hand_axe", 4, "cleaver", 3);

    /**
     * How far below its best an Acheulean tool trades, by quality. A flawless or excellent one is
     * the best thing anybody owns; a crude one is still a real tool, but nobody is fooled.
     */
    private static int qualityPenalty(ItemStack stack) {
        if (!(stack.getItem() instanceof dev.hominin.evolution.item.AcheuleanToolItem)) {
            return 0;
        }
        return switch (dev.hominin.evolution.item.AcheuleanToolItem.qualityOf(stack)) {
            case 0, 1 -> 0;
            case 2, 3 -> 1;
            default -> 2;
        };
    }

    /** Finer ordering among Acheulean tools of the same tier: the better-made one first. */
    private static int qualityBonus(ItemStack stack) {
        return stack.getItem() instanceof dev.hominin.evolution.item.AcheuleanToolItem
                ? 4 - dev.hominin.evolution.item.AcheuleanToolItem.qualityOf(stack) : 0;
    }

    /**
     * Technology, by the stage that first makes it, and how highly it ranks at that stage.
     * To a band that has not reached it yet, it is treasure; to one that has moved past it,
     * it is worth less and less.
     */
    private record Tech(int stage, int peakTier) {
    }

    private static final Map<String, Tech> TECHNOLOGY = Map.ofEntries(
            Map.entry("lomekwian_tool", new Tech(1, 5)), Map.entry("hammerstone", new Tech(1, 3)),
            Map.entry("sharpened_stick", new Tech(1, 3)),
            Map.entry("flake", new Tech(2, 3)), Map.entry("chopper", new Tech(2, 4)),
            Map.entry("pointy_stick", new Tech(2, 3)), Map.entry("digging_stick", new Tech(2, 3)),
            Map.entry("grinding_rock", new Tech(2, 3)), Map.entry("sharpened_spear", new Tech(2, 4)),
            Map.entry("chert_hammerstone", new Tech(2, 5)), Map.entry("oldowan_multitool", new Tech(2, 5)),
            Map.entry("wooden_club", new Tech(3, 4)), Map.entry("fire_hardened_spear", new Tech(3, 5)),
            Map.entry("hand_axe", new Tech(3, 5)), Map.entry("cleaver", new Tech(3, 5)));

    /** Tiers lost for each stage a band has moved past a technology. */
    private static final int TIERS_LOST_PER_STAGE = 3;

    private static int stageOrder(ResourceLocation stage) {
        return switch (stage.getPath()) {
            case "ardipithecus", "australopithecus", "australopithecus_anamensis", "paranthropus_boisei" -> 1;
            case "homo_habilis" -> 2;
            case "homo_erectus" -> 3;
            default -> 4;
        };
    }

    /** The trade tier of this item to a band at the given stage. */
    public static int tierOf(ItemStack stack, @javax.annotation.Nullable ResourceLocation stage) {
        if (stage == null || stack.isEmpty()) {
            return tierOf(stack);
        }
        Tech tech = TECHNOLOGY.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        if (tech == null || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(HomininEvolutionMod.MODID)) {
            return tierOf(stack);
        }
        int era = stageOrder(stage);
        if (tech.stage() > era) {
            return MAX_TIER;
        }
        return Math.max(1, tech.peakTier() - TIERS_LOST_PER_STAGE * (era - tech.stage()) - qualityPenalty(stack));
    }

    public static int valueOf(ItemStack stack, @javax.annotation.Nullable ResourceLocation stage) {
        int tier = tierOf(stack, stage);
        if (tier == 0) {
            return 0;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return tier * 10 + WITHIN_TIER.getOrDefault(path, 0) + qualityBonus(stack);
    }

    /** The trade tier of one of this item, 0 if a band has no use for it. */
    public static int tierOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key.getNamespace().equals(HomininEvolutionMod.MODID)) {
            Integer tier = TIERS.get(key.getPath());
            if (tier != null) {
                return Math.max(1, tier - qualityPenalty(stack));
            }
        }
        if (stack.is(Items.STICK)) {
            return 1;
        }
        if (stack.is(ModTags.Items.KNAPPABLE_STONE)) {
            return 2;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            // A proper meal is worth more than a mouthful.
            return food.nutrition() >= 6 ? 2 : 1;
        }
        return 0;
    }

    /** A sortable worth: tier first, then the finer ordering inside it. Zero means worthless. */
    public static int valueOf(ItemStack stack) {
        int tier = tierOf(stack);
        if (tier == 0) {
            return 0;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return tier * 10 + WITHIN_TIER.getOrDefault(path, 0) + qualityBonus(stack);
    }

    /**
     * Offers what the player holds. The other band hands back the best thing it carries
     * from the offer's tier or below - never something ranked higher, and never the same
     * kind of thing straight back.
     */
    public static void offer(BandMember member, Player player, ItemStack offered) {
        ResourceLocation era = member.getStage();
        int offerTier = tierOf(offered, era);
        // In the rains they are generous; in hard times nobody parts with anything unless they come out ahead.
        // Generous, not foolish: something worthless to them stays worthless.
        if (offerTier > 0 && dev.hominin.evolution.survival.Seasons.plentiful(member.level())) {
            offerTier++;
        }
        if (dev.hominin.evolution.survival.Seasons.strained(member.level())) {
            offerTier--;
            if (offerTier <= 0) {
                player.displayClientMessage(Component.literal(member.getName().getString()
                        + " shakes their head. Nobody is trading that cheaply while the land is this dry."), true);
                return;
            }
        }
        if (offerTier <= 0) {
            player.displayClientMessage(Component.literal(
                    member.getName().getString() + " turns it over and hands it back. No use to them."), true);
            return;
        }
        SimpleContainer pack = member.getInventory();
        int bestSlot = -2;
        int bestValue = 0;
        ItemStack held = member.getMainHandItem();
        if (isFairReturn(held, offered, offerTier, era) && valueOf(held, era) > bestValue) {
            bestSlot = -1;
            bestValue = valueOf(held, era);
        }
        for (int slot = 0; slot < pack.getContainerSize(); slot++) {
            ItemStack stack = pack.getItem(slot);
            if (isFairReturn(stack, offered, offerTier, era) && valueOf(stack, era) > bestValue) {
                bestSlot = slot;
                bestValue = valueOf(stack, era);
            }
        }
        if (bestSlot == -2) {
            player.displayClientMessage(Component.literal(member.getName().getString()
                    + " looks at it, and at you, and keeps what they have. (Offer something of a higher tier.)"),
                    true);
            return;
        }
        ItemStack given = bestSlot == -1
                ? member.getMainHandItem().split(1)
                : pack.getItem(bestSlot).split(1);
        if (bestSlot == -1 && member.getMainHandItem().isEmpty()) {
            member.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        ItemStack taken = offered.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            offered.shrink(1);
        }
        member.addToInventory(taken);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // A fair exchange also buys the right to their water and their stone.
            Territory.offered(member, serverPlayer, taken);
        }
        Component givenName = given.getHoverName();
        if (!player.getInventory().add(given)) {
            player.drop(given, false);
        }
        member.playSound(SoundEvents.ITEM_PICKUP, 0.7F, 0.9F);
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, member.getX(), member.getEyeY(),
                member.getZ(), 5, 0.3D, 0.3D, 0.3D, 0.0D);
        player.displayClientMessage(Component.literal(member.getName().getString() + " takes it, and hands you ")
                .append(givenName).append("."), true);
    }

    /**
     * A trade the player set up by hand: this from my hotbar, for that from your pack.
     *
     * <p>The same rules as ever - worth is measured in their era, a drought costs a tier,
     * and nobody hands over something ranked above what they are given - but now the
     * player chooses what they want instead of being handed the best thing that fits.
     *
     * @param offerSlot  a hotbar slot, 0 to 8
     * @param wantedSlot a pack slot, or -1 for what is in their hand
     */
    public static void trade(net.minecraft.server.level.ServerPlayer player, BandMember member, int offerSlot,
            int wantedSlot) {
        ItemStack offered = offerSlot >= 0 && offerSlot < 9 ? player.getInventory().getItem(offerSlot) : ItemStack.EMPTY;
        ItemStack wanted = wantedSlot == -1 ? member.getMainHandItem()
                : wantedSlot >= 0 && wantedSlot < member.getInventory().getContainerSize()
                        ? member.getInventory().getItem(wantedSlot) : ItemStack.EMPTY;
        String name = member.getName().getString();
        if (offered.isEmpty()) {
            say(player, "Pick something from your hotbar to offer first.");
            return;
        }
        if (wanted.isEmpty()) {
            return;
        }
        if (member.refusesToPartWith(wanted)) {
            say(player, name + " closes a hand round it. That one is not for trading.");
            return;
        }
        ResourceLocation era = member.getStage();
        int offerTier = tierOf(offered, era);
        boolean dry = dev.hominin.evolution.survival.Seasons.strained(member.level());
        if (dry) {
            offerTier--;
        } else if (offerTier > 0 && dev.hominin.evolution.survival.Seasons.plentiful(member.level())) {
            offerTier++;
        }
        int wantedTier = tierOf(wanted, era);
        if (offerTier <= 0) {
            say(player, dry ? name + " shakes their head. Nobody trades that cheaply while the land is this dry."
                    : name + " turns it over and hands it back. No use to them.");
            return;
        }
        if (wanted.is(offered.getItem())) {
            say(player, "That is the same thing you are offering.");
            return;
        }
        boolean lowball = wantedTier > offerTier && Paranthropus.fallsForLowball(member, offerTier, wantedTier);
        if (wantedTier > offerTier && !lowball) {
            say(player, name + " looks at what you offer, and at what you want, and keeps it. (Offer a "
                    + TIER_NAMES[Math.max(1, Math.min(MAX_TIER, wantedTier + (dry ? 1 : 0)
                            - (!dry && dev.hominin.evolution.survival.Seasons.plentiful(member.level()) ? 1 : 0)))]
                    + " thing or better.)");
            return;
        }
        ItemStack given = wanted.split(1);
        if (wantedSlot == -1 && member.getMainHandItem().isEmpty()) {
            member.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        ItemStack taken = offered.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            offered.shrink(1);
        }
        member.addToInventory(taken);
        Territory.offered(member, player, taken);
        String species = member.getStage().getPath();
        if (member.isWild() && (species.equals("homo_erectus") || species.equals("homo_ergaster"))) {
            dev.hominin.evolution.EvolutionManager.incrementCriterion(player, "trade_erectus", 1);
        }
        Component givenName = given.getHoverName();
        if (!player.getInventory().add(given)) {
            player.drop(given, false);
        }
        member.playSound(SoundEvents.ITEM_PICKUP, 0.7F, 0.9F);
        ((ServerLevel) member.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, member.getX(), member.getEyeY(),
                member.getZ(), 5, 0.3D, 0.3D, 0.3D, 0.0D);
        player.displayClientMessage(lowball
                ? Component.literal(name + " turns it over, pleased, and hands you ").append(givenName)
                        .append(". You got the better of that one.").withStyle(net.minecraft.ChatFormatting.GOLD)
                : Component.literal(name + " takes it, and hands you ").append(givenName).append("."), true);
    }

    private static void say(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    /** What a member carries, sent to the player's trade screen. */
    public static void openTrade(net.minecraft.server.level.ServerPlayer player, BandMember member) {
        if (member.isLedBy(player) && member.isAntisocial() && member.getBond() < 6) {
            member.ensureName();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("<" + member.getName().getString()
                    + "> \"What's mine stays mine.\"").withStyle(net.minecraft.ChatFormatting.GOLD));
            return;
        }
        if (member.isLedBy(player)) {
            String refusal = Cohesion.refusesTrade(player, member);
            if (refusal != null) {
                member.ensureName();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("<" + member.getName().getString() + "> ")
                        .withStyle(net.minecraft.ChatFormatting.GOLD).append(net.minecraft.network.chat.Component
                                .literal(refusal).withStyle(net.minecraft.ChatFormatting.WHITE)));
                return;
            }
        }
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        if (!member.getMainHandItem().isEmpty()) {
            slots.add(-1);
            stacks.add(member.getMainHandItem().copy());
        }
        for (int slot = 0; slot < member.getInventory().getContainerSize(); slot++) {
            ItemStack stack = member.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                slots.add(slot);
                stacks.add(stack.copy());
            }
        }
        member.ensureName();
        member.attendTo(player, BandMember.ATTEND_TICKS);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.hominin.evolution.network.TradeOpenPayload(member.getId(), member.getName().getString(),
                        member.getStage().toString(), slots, stacks));
    }

    private static boolean isFairReturn(ItemStack candidate, ItemStack offered, int offerTier, ResourceLocation era) {
        int tier = tierOf(candidate, era);
        // In a dry spell nobody parts with anything unless they come out of it ahead.
        return tier > 0 && tier <= offerTier && !candidate.is(offered.getItem());
    }

    private Trading() {
    }
}
