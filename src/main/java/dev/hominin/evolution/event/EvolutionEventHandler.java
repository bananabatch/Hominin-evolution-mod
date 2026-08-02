package dev.hominin.evolution.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.HomininEvolutionMod;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.stage.BuiltinMilestones;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class EvolutionEventHandler {
    public static final TagKey<Block> WORKABLE_STONE_DEPOSIT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "workable_stone_deposit"));

    public static final TagKey<Block> FORAGING_GROUND = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "foraging_ground"));

    private static final int DAY_CHECK_INTERVAL_TICKS = 200;
    private static final long TICKS_PER_DAY = 24000L;
    private static final long FORAGE_COOLDOWN_TICKS = 60L;

    private static final Map<UUID, Long> lastForageTick = new HashMap<>();

    private EvolutionEventHandler() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.is(WORKABLE_STONE_DEPOSIT)) {
            if (player.isShiftKeyDown()) {
                EvolutionManager.incrementCriterion(player, "notice_stone_deposit", 1);
            } else {
                EvolutionManager.attemptMilestone(player, BuiltinMilestones.STRIKE_FLAKE);
            }
        } else if (state.is(FORAGING_GROUND)) {
            long gameTime = event.getLevel().getGameTime();
            Long lastTick = lastForageTick.get(player.getUUID());
            if (lastTick != null && gameTime - lastTick < FORAGE_COOLDOWN_TICKS) {
                return;
            }
            lastForageTick.put(player.getUUID(), gameTime);
            Item[] insects = {ModItems.GRUB.get(), ModItems.BEETLE.get(), ModItems.EARTHWORM.get()};
            Item insect = insects[event.getLevel().getRandom().nextInt(insects.length)];
            ItemStack insectStack = new ItemStack(insect);
            Component insectName = insectStack.getHoverName();
            if (!player.getInventory().add(insectStack)) {
                player.drop(insectStack, false);
            }
            player.sendSystemMessage(Component.literal("You root through the soil and find a ")
                    .append(insectName)
                    .append(Component.literal(".")));
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Animal)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player) {
            return;
        }
        entity.level().addFreshEntity(new ItemEntity(entity.level(),
                entity.getX(), entity.getY(), entity.getZ(), new ItemStack(ModItems.CARCASS.get())));
    }

    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!stack.has(DataComponents.FOOD)) {
            return;
        }
        Holder<Biome> biomeHolder = player.level().getBiome(player.blockPosition());
        ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
        if (biomeKey == null) {
            return;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        data.addForaged(1);
        if (data.getForagedBiomes().add(biomeKey.location())) {
            EvolutionManager.incrementCriterion(player, "forage_biomes", 1);
        }
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Item craftedItem = event.getCrafting().getItem();
        if (craftedItem == ModItems.CHOPPER.get() || craftedItem == ModItems.HAMMERSTONE.get()) {
            EvolutionManager.incrementCriterion(player, "craft_oldowan_tools", 1);
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        ServerPlayer player = event.getEntity() instanceof ServerPlayer sp ? sp : null;
        if (player == null || player.tickCount % DAY_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        long currentDay = player.level().getDayTime() / TICKS_PER_DAY;
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        if (data.getLastCountedDay() < 0) {
            data.setLastCountedDay(currentDay);
            return;
        }
        if (currentDay > data.getLastCountedDay()) {
            data.setLastCountedDay(currentDay);
            EvolutionManager.incrementCriterion(player, "survive_days", 1);
        }
    }
}
