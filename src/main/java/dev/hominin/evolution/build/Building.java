package dev.hominin.evolution.build;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.ModBlocks;
import dev.hominin.evolution.ModItems;
import dev.hominin.evolution.band.Band;
import dev.hominin.evolution.band.BandMember;
import dev.hominin.evolution.band.Bands;
import dev.hominin.evolution.band.Cohesion;
import dev.hominin.evolution.band.Lines;
import dev.hominin.evolution.band.Mood;
import dev.hominin.evolution.band.Presence;
import dev.hominin.evolution.block.ThatchBlock;
import dev.hominin.evolution.guide.Tips;
import dev.hominin.evolution.network.BlueprintsPayload;
import dev.hominin.evolution.network.BuildActionPayload;
import dev.hominin.evolution.network.BuildMenuPayload;
import dev.hominin.evolution.network.ChoicesPayload;
import dev.hominin.evolution.network.SitesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Building from blueprints. Press the build key (O) for what you can build and a look at each; pick one and its
 * ghost follows where you look, green where it fits and red where it does not; the work key (P) marks it out.
 * The ghost stays, and each block goes into its place - only the right block goes in, and a block used on a ghost
 * is set straight into it. When the last one is in, the build stands, and you decide what it is for:
 *
 * <ul>
 * <li><b>A store</b> - food, bones and tools laid down inside, for the whole band. No beds in it.</li>
 * <li><b>Yours</b> - nobody sleeps in it but you and your mate, and you wake rested.</li>
 * <li><b>Theirs</b> - given to one of the band. It means a great deal to them, and to everyone watching if they
 * are hurt or carrying a child. They sleep in it, and heal in it twice as fast.</li>
 * </ul>
 */
public final class Building {
    /** {@link dev.hominin.evolution.network.ChoosePayload} actions: what a build is for, and who it is for. */
    public static final int ACTION_USE = 40;
    public static final int ACTION_GIVE = 41;

    public static final int MAX_PLANS = 3;
    private static final double PLAN_REACH = 32.0D;
    private static final double SYNC_RANGE = 160.0D;
    private static final int GIVE_BOND = 5;
    private static final int GIVE_COHESION = 7;
    /** How close a mate has to be to their leader's own roof to sleep under it. */
    private static final double MATE_ROOM_RANGE = 48.0D;

    // ------------------------------------------------------------ from the client

    public static void handle(ServerPlayer player, BuildActionPayload payload) {
        if (!Bands.erectusOn(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            // Before erectus there is nothing to plan and nothing to build - creative or not.
            player.displayClientMessage(Component.literal("Nobody of your kind builds yet - shelters come with "
                    + "erectus and the work station."), true);
            return;
        }
        switch (payload.action()) {
            case BuildActionPayload.OPEN -> openMenu(player);
            case BuildActionPayload.PLAN -> {
                ResourceLocation id = ResourceLocation.tryParse(payload.blueprint());
                if (id != null) {
                    plan(player, id, payload.pos(), Direction.from2DDataValue(payload.facing()), payload.site());
                }
            }
            case BuildActionPayload.CONFIRM -> confirm(player, payload.site());
            case BuildActionPayload.ABANDON -> abandon(player, payload.site());
            case BuildActionPayload.DECIDE -> decide(player, payload.site());
            case BuildActionPayload.FILL -> fill(player, payload.site(), payload.pos());
            default -> {
            }
        }
    }

    /** Why this cannot be built yet - empty if it can. */
    public static String lock(ServerPlayer player, Blueprint blueprint) {
        if (!Bands.erectusOn(player.getData(Attachments.PLAYER_EVOLUTION_DATA).getStage())) {
            return "Nobody of your kind builds yet - shelters come with erectus and the work station.";
        }
        ResourceLocation after = blueprint.after();
        if (after != null && !Sites.hasFinished(player.server, player.getUUID(), after)) {
            Blueprint first = Blueprints.get(after);
            return "Build a " + (first != null ? first.name().toLowerCase() : after.getPath().replace('_', ' '))
                    + " first.";
        }
        return "";
    }

    public static void openMenu(ServerPlayer player) {
        syncBlueprints(player);
        syncSites(player);
        List<String> ids = new ArrayList<>();
        List<String> locks = new ArrayList<>();
        for (Blueprint blueprint : Blueprints.list()) {
            ids.add(blueprint.id().toString());
            locks.add(lock(player, blueprint));
        }
        PacketDistributor.sendToPlayer(player, new BuildMenuPayload(ids, locks));
    }

    /** Marks a blueprint out on the ground, if it fits there. */
    public static void plan(ServerPlayer player, ResourceLocation id, BlockPos origin, Direction forward) {
        plan(player, id, origin, forward, 0);
    }

    /**
     * Marks a blueprint out on the ground, if it fits there - or, moving one already marked out that nothing has
     * been built of yet, marks it out here instead.
     */
    public static void plan(ServerPlayer player, ResourceLocation id, BlockPos origin, Direction forward, int moving) {
        Blueprint blueprint = Blueprints.get(id);
        ServerLevel level = player.serverLevel();
        if (blueprint == null || !forward.getAxis().isHorizontal()) {
            return;
        }
        Sites.Site old = moving > 0 ? Sites.byId(level, moving) : null;
        if (moving > 0 && (old == null || !old.owner().equals(player.getUUID()) || old.built() || old.placed > 0)) {
            tell(player, "That plan cannot be moved now - something is already built of it.", ChatFormatting.GRAY);
            return;
        }
        String lock = lock(player, blueprint);
        if (!lock.isEmpty()) {
            tell(player, lock, ChatFormatting.GRAY);
            return;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(origin)) > PLAN_REACH * PLAN_REACH) {
            tell(player, "That is too far off to mark out.", ChatFormatting.GRAY);
            return;
        }
        long plans = Sites.ownedBy(level, player.getUUID()).stream()
                .filter(site -> !site.built() && !site.proposed && site != old).count();
        if (plans >= MAX_PLANS) {
            tell(player, "You already have " + MAX_PLANS + " builds marked out. Finish one, or put a plan away (O).",
                    ChatFormatting.GRAY);
            return;
        }
        Footprint footprint = new Footprint(blueprint, origin, forward);
        Footprint.Fit fit = footprint.fit(level, pos -> {
            Sites.Site there = Sites.containing(level, pos);
            return there != null && there != old;
        });
        if (!fit.ok()) {
            tell(player, "You can't build it there: " + fit.reason(), ChatFormatting.GRAY);
            return;
        }
        if (old != null) {
            Sites.of(level).remove(old);
            syncAround(level, old);
        }
        Sites.Site site = Sites.of(level).add(id, origin, forward, player.getUUID());
        if (old != null) {
            player.sendSystemMessage(Component.literal(old.proposed
                    ? "You mark the " + site.name() + " out where you think it should go, not where " + old.proposer
                            + " said. Near enough - they nod."
                    : "You move the plan for the " + site.name() + ".").withStyle(ChatFormatting.AQUA));
        } else {
            player.sendSystemMessage(Component.literal("You mark out a " + site.name() + " on the ground. Its ghost "
                    + "shows what goes where: " + blueprint.materialsText() + ". Place them into it - or use one on a "
                    + "ghost block to set it straight in.").withStyle(ChatFormatting.AQUA));
        }
        level.playSound(null, origin, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 0.8F, 0.8F);
        Tips.offer(player, Tips.Tip.BUILDING);
        check(level, site);
        syncAround(level, site);
    }

    public static void abandon(ServerPlayer player, int id) {
        ServerLevel level = player.serverLevel();
        Sites.Site site = Sites.byId(level, id);
        if (site == null || !site.owner().equals(player.getUUID()) || site.built()) {
            return;
        }
        Sites.of(level).remove(site);
        if (site.proposed) {
            BandMember proposer = proposerOf(level, site);
            tell(player, "<" + site.proposer + "> " + (proposer != null && proposer.isAntisocial()
                    ? "Suit yourself." : "Another time, then."), ChatFormatting.GRAY);
            syncAround(level, site);
            return;
        }
        tell(player, "You put the plan for the " + site.name() + " away. Whatever of it was built stays standing.",
                ChatFormatting.GRAY);
        syncAround(level, site);
    }

    /** A build one of the band suggested: yes, there. */
    public static void confirm(ServerPlayer player, int id) {
        ServerLevel level = player.serverLevel();
        Sites.Site site = Sites.byId(level, id);
        if (site == null || !site.owner().equals(player.getUUID()) || !site.proposed) {
            return;
        }
        long plans = Sites.ownedBy(level, player.getUUID()).stream().filter(s -> !s.built() && !s.proposed).count();
        if (plans >= MAX_PLANS) {
            tell(player, "You already have " + MAX_PLANS + " builds marked out. Finish one first, or put one away.",
                    ChatFormatting.GRAY);
            return;
        }
        site.proposed = false;
        Sites.of(level).changed();
        BandMember proposer = proposerOf(level, site);
        if (proposer != null) {
            proposer.addBond(1);
        }
        Blueprint blueprint = Blueprints.get(site.blueprint());
        player.sendSystemMessage(Component.literal("<" + site.proposer + "> ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Good. It'll stand there.").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" The " + site.name() + " is marked out"
                        + (blueprint != null ? ": " + blueprint.materialsText() : "") + ".").withStyle(ChatFormatting.AQUA)));
        Tips.offer(player, Tips.Tip.BUILDING);
        syncAround(level, site);
    }

    /** Blocks already standing where it was suggested - those do not count as an answer. */
    private static int countedAtProposal(Sites.Site site) {
        return site.proposalPlaced;
    }

    @Nullable
    private static BandMember proposerOf(ServerLevel level, Sites.Site site) {
        return site.proposerId != null && level.getEntity(site.proposerId) instanceof BandMember member
                && member.isAlive() ? member : null;
    }

    // ------------------------------------------------------------ the band suggests

    private static final java.util.Map<java.util.UUID, Long> nextSuggestion = new java.util.HashMap<>();
    /** How long a suggestion waits for an answer: a day. */
    private static final long SUGGESTION_LASTS = 24000L;

    /**
     * Now and then, one of the band has an idea where something should be built - somewhere dry for one who is hurt
     * or carrying a child, somewhere to keep food, a roof for you, room for more of them - and marks it out on your
     * ground, where it would fit. You say yes, move it, or no.
     */
    /** Developer: somebody suggests something now, if there is anywhere it would go. */
    public static String devSuggest(ServerPlayer player) {
        nextSuggestion.remove(player.getUUID());
        long before = Sites.ownedBy(player.serverLevel(), player.getUUID()).stream().filter(s -> s.proposed).count();
        for (int i = 0; i < 12 && Sites.ownedBy(player.serverLevel(), player.getUUID()).stream()
                .filter(s -> s.proposed).count() == before; i++) {
            nextSuggestion.put(player.getUUID(), 0L);
            suggest(player, true);
        }
        return Sites.ownedBy(player.serverLevel(), player.getUUID()).stream().filter(s -> s.proposed).count() > before
                ? "Suggested." : "Nobody suggested anything - you need erectus, ground of your own, a member with bond 1+ "
                        + "nearby, room for another plan, and a clear spot 6-18 blocks from camp.";
    }

    private static void suggest(ServerPlayer player) {
        suggest(player, false);
    }

    private static void suggest(ServerPlayer player, boolean force) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        boolean open = false;
        for (Sites.Site site : Sites.ownedBy(level, player.getUUID())) {
            if (!site.proposed) {
                continue;
            }
            if (now - site.proposedAt > SUGGESTION_LASTS) {
                Sites.of(level).remove(site);
                tell(player, site.proposer + " has given up on the " + site.name() + " they wanted built.",
                        ChatFormatting.DARK_GRAY);
                syncAround(level, site);
            } else {
                open = true;
            }
        }
        if (open || now < nextSuggestion.getOrDefault(player.getUUID(), now + 6000L)
                || !dev.hominin.evolution.hunt.Predation.settled(player) || !force && player.getRandom().nextInt(3) != 0) {
            nextSuggestion.putIfAbsent(player.getUUID(), now + 6000L);
            return;
        }
        long plans = Sites.ownedBy(level, player.getUUID()).stream().filter(s -> !s.built()).count();
        if (plans >= MAX_PLANS) {
            return;
        }
        List<Blueprint> buildable = new ArrayList<>();
        for (Blueprint blueprint : Blueprints.list()) {
            if (lock(player, blueprint).isEmpty()) {
                buildable.add(blueprint);
            }
        }
        List<BandMember> near = new ArrayList<>();
        for (BandMember member : Band.ownNear(player, 32.0D)) {
            if (!member.isBaby() && member.getBond() >= 1 && !member.isAntisocial()) {
                near.add(member);
            }
        }
        if (buildable.isEmpty() || near.isEmpty()) {
            return;
        }
        BandMember proposer = near.get(player.getRandom().nextInt(near.size()));
        proposer.ensureName();
        // Why: whoever needs a roof most, then a store, then one for you, then room for more.
        String reason = null;
        Blueprint blueprint = buildable.get(0);
        for (BandMember member : Band.all(player)) {
            if ((member.isInjured() || member.isPregnant()) && Sites.givenTo(level, member.getUUID()) == null
                    && !member.isBaby()) {
                member.ensureName();
                String them = member == proposer ? "me" : member.getName().getString();
                reason = member.isInjured() ? "Somewhere dry for " + them + " to lie up while that heals."
                        : "Somewhere dry for " + them + " and the child.";
                break;
            }
        }
        List<Sites.Site> mine = Sites.ownedBy(level, player.getUUID());
        if (reason == null && mine.stream().noneMatch(s -> s.built() && s.use() == Sites.Use.STORE)) {
            reason = "Somewhere to keep food and tools out of the rain.";
        }
        if (reason == null && mine.stream().noneMatch(s -> s.built() && s.use() == Sites.Use.MINE)) {
            reason = "You should have a roof of your own.";
        }
        if (reason == null) {
            blueprint = buildable.get(buildable.size() - 1);
            reason = "Room for more of us to sleep dry.";
        }
        BlockPos camp = dev.hominin.evolution.hunt.Predation.campOf(player);
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0D;
            int distance = 6 + player.getRandom().nextInt(13);
            int x = camp.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = camp.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            BlockPos origin = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
                    .MOTION_BLOCKING_NO_LEAVES, x, z), z);
            // Its door faces the camp.
            Direction forward = Direction.getNearest(x - camp.getX(), 0, z - camp.getZ());
            if (!forward.getAxis().isHorizontal()) {
                forward = Direction.NORTH;
            }
            Footprint footprint = new Footprint(blueprint, origin, forward);
            if (!dev.hominin.evolution.hunt.Predation.onOwnGround(player, origin)
                    || !footprint.fit(level, pos -> Sites.taken(level, pos)).ok()) {
                continue;
            }
            Sites.Site site = Sites.of(level).add(blueprint.id(), origin, forward, player.getUUID());
            site.proposed = true;
            site.proposer = proposer.getName().getString();
            site.proposerId = proposer.getUUID();
            site.proposedAt = now;
            site.proposalPlaced = footprint.placed(level);
            site.placed = site.proposalPlaced;
            Sites.of(level).changed();
            nextSuggestion.put(player.getUUID(), now + SUGGESTION_LASTS);
            proposer.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
            int away = (int) Math.sqrt(player.blockPosition().distSqr(origin));
            player.sendSystemMessage(Component.literal("<" + site.proposer + "> ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("We should build a " + site.name() + " - there, look. " + reason)
                            .withStyle(ChatFormatting.WHITE)));
            player.sendSystemMessage(Component.literal("(" + site.proposer + " has marked its ghost out, " + away
                    + " blocks " + compass(player.blockPosition(), origin) + ". Press O to confirm it, move it, or cancel.)")
                    .withStyle(ChatFormatting.GRAY));
            syncAround(level, site);
            return;
        }
    }

    private static String compass(BlockPos from, BlockPos to) {
        double angle = Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        return names[Math.floorMod((int) Math.round(angle / 45.0D), 8)];
    }

    /** Asks again what a finished build is for - the question is put when it is finished, but can wait. */
    public static void decide(ServerPlayer player, int id) {
        Sites.Site site = Sites.byId(player.serverLevel(), id);
        if (site == null || !site.owner().equals(player.getUUID()) || !site.built()) {
            return;
        }
        if (site.use() != Sites.Use.NONE) {
            tell(player, "That is settled: " + site.label().toLowerCase() + ".", ChatFormatting.GRAY);
            return;
        }
        if (!shelter(site)) {
            tell(player, "The " + site.name() + " is for everyone - there is nothing to decide.", ChatFormatting.GRAY);
            return;
        }
        ask(player, site);
    }

    private static void ask(ServerPlayer player, Sites.Site site) {
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(site.id(), ACTION_USE, "What do we do with this?",
                List.of("Let's store food and items in it", "It's for me", "It's for the others"), List.of(1, 2, 3)));
    }

    /** A block used on a ghost: set straight into it, if it is the one that goes there. */
    public static void fill(ServerPlayer player, int id, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        Sites.Site site = Sites.byId(level, id);
        Footprint footprint = site != null ? site.footprint() : null;
        Blueprint.Cell cell = footprint != null ? footprint.cells().get(pos) : null;
        ItemStack held = player.getMainHandItem();
        if (cell == null || !(held.getItem() instanceof BlockItem item) || item.getBlock() != cell.block()
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > Math.pow(player.blockInteractionRange() + 1.5D, 2)
                || !level.isLoaded(pos) || !level.mayInteract(player, pos)) {
            return;
        }
        if (Blueprint.twoTall(cell.block())) {
            fillTwoTall(player, level, footprint, cell, pos, held);
            return;
        }
        BlockState now = level.getBlockState(pos);
        if (now.is(cell.block())) {
            return;
        }
        if (!Footprint.free(level, pos, now, false) || !level.getFluidState(pos).isEmpty()) {
            tell(player, "Something is in the way.", ChatFormatting.GRAY);
            return;
        }
        if (!now.isAir()) {
            // Grass and flowers get trampled.
            level.destroyBlock(pos, true, player);
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        BlockState state = Block.updateFromNeighbourShapes(cell.block().defaultBlockState(), level, pos);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (EventHooks.onBlockPlace(player, snapshot, Direction.UP)) {
            snapshot.restore();
            return;
        }
        var sound = state.getSoundType(level, pos, player);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                sound.getPitch() * 0.8F);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /** A rack post goes in whole: its foot on the ground, its fork above - whichever half of the ghost was used. */
    private static void fillTwoTall(ServerPlayer player, ServerLevel level, Footprint footprint, Blueprint.Cell cell,
            BlockPos pos, ItemStack held) {
        var half = net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF;
        BlockPos foot = cell.look().hasProperty(half)
                && cell.look().getValue(half) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                ? pos.below() : pos;
        BlockPos top = foot.above();
        if (level.getBlockState(foot).is(cell.block())) {
            return;
        }
        for (BlockPos at : List.of(foot, top)) {
            BlockState there = level.getBlockState(at);
            if (!Footprint.free(level, at, there, false) || !level.getFluidState(at).isEmpty()) {
                tell(player, "Something is in the way.", ChatFormatting.GRAY);
                return;
            }
        }
        for (BlockPos at : List.of(foot, top)) {
            if (!level.getBlockState(at).isAir()) {
                level.destroyBlock(at, true, player);
            }
        }
        BlockState lower = cell.block().defaultBlockState().setValue(half,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        level.setBlock(foot, lower, Block.UPDATE_ALL);
        level.setBlock(top, lower.setValue(half, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
                Block.UPDATE_ALL);
        var sound = lower.getSoundType(level, foot, player);
        level.playSound(null, foot, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                sound.getPitch() * 0.8F);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /**
     * A camp that is already there when the descendants wake: a small hut, finished, somewhere near, and a fire
     * pit in front of its door. Returns false if nowhere near is flat and clear enough for the hut.
     */
    public static boolean raiseCamp(ServerPlayer player, BlockPos near) {
        ServerLevel level = player.serverLevel();
        ResourceLocation hut = ResourceLocation.fromNamespaceAndPath(dev.hominin.evolution.HomininEvolutionMod.MODID,
                "small_hut");
        Blueprint blueprint = Blueprints.get(hut);
        if (blueprint == null) {
            return false;
        }
        var random = player.getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = random.nextInt(17) - 8;
            int dz = random.nextInt(17) - 8;
            if (Math.abs(dx) < 3 && Math.abs(dz) < 3) {
                continue;
            }
            int x = near.getX() + dx;
            int z = near.getZ() + dz;
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            Direction forward = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            Footprint footprint = new Footprint(blueprint, new BlockPos(x, y, z), forward);
            if (!footprint.fit(level, pos -> Sites.containing(level, pos) != null).ok()) {
                continue;
            }
            for (var entry : footprint.cells().entrySet()) {
                BlockPos pos = entry.getKey();
                if (!level.getBlockState(pos).isAir()) {
                    level.destroyBlock(pos, false);
                }
                level.setBlock(pos, entry.getValue().look(), Block.UPDATE_ALL);
            }
            Sites.Site site = Sites.of(level).add(hut, footprint.origin(), forward, player.getUUID());
            site.built = true;
            site.placed = site.total;
            site.credited = true;
            Sites.of(level).changed();
            // The fire pit: out in front of the camp, on open ground clear of the hut.
            BoundingBox box = footprint.box();
            BlockPos centre = new BlockPos((box.minX() + box.maxX()) / 2, footprint.origin().getY(),
                    (box.minZ() + box.maxZ()) / 2);
            for (int step = 4; step <= 8; step++) {
                for (Direction side : new Direction[] {forward.getOpposite(), forward, forward.getClockWise(),
                        forward.getCounterClockWise()}) {
                    BlockPos spot = centre.relative(side, step);
                    spot = new BlockPos(spot.getX(), level.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(),
                            spot.getZ()), spot.getZ());
                    if (!footprint.contains(spot) && Sites.containing(level, spot) == null
                            && level.getBlockState(spot).canBeReplaced() && level.getFluidState(spot).isEmpty()
                            && level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)) {
                        level.setBlock(spot, ModBlocks.FIRE_PIT.get().defaultBlockState(), Block.UPDATE_ALL);
                        step = 99;
                        break;
                    }
                }
            }
            syncAround(level, site);
            return true;
        }
        return false;
    }

    private static final ResourceLocation COOKING_RACK = ResourceLocation.fromNamespaceAndPath(
            dev.hominin.evolution.HomininEvolutionMod.MODID, "cooking_rack");

    /** Whether a cooking rack - two posts, a fire pit between, a branch across - would stand here, facing north. */
    public static boolean rackFits(ServerLevel level, BlockPos at) {
        Blueprint blueprint = Blueprints.get(COOKING_RACK);
        return blueprint != null && new Footprint(blueprint, at, Direction.NORTH)
                .fit(level, pos -> Sites.containing(level, pos) != null).ok();
    }

    /** One of the band puts up a cooking rack whole, for a feast: it stands at once, and is the band's. */
    public static boolean raiseRack(ServerLevel level, java.util.UUID owner, BlockPos at) {
        Blueprint blueprint = Blueprints.get(COOKING_RACK);
        if (blueprint == null || !rackFits(level, at)) {
            return false;
        }
        Footprint footprint = new Footprint(blueprint, at, Direction.NORTH);
        for (var entry : footprint.cells().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.getBlockState(pos).isAir()) {
                level.destroyBlock(pos, false);
            }
            level.setBlock(pos, entry.getValue().look(), Block.UPDATE_ALL);
        }
        level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
        Sites.Site site = Sites.of(level).add(COOKING_RACK, at, Direction.NORTH, owner);
        site.built = true;
        site.placed = site.total;
        site.credited = true;
        Sites.of(level).changed();
        syncAround(level, site);
        return true;
    }

    /** Whether a build is somewhere to be inside of, rather than something that stands in the open. */
    public static boolean shelter(Sites.Site site) {
        Blueprint blueprint = Blueprints.get(site.blueprint());
        return blueprint == null || blueprint.shelter();
    }

    // ------------------------------------------------------------ what it is for

    /** The answer to "what do we do with this?". */
    public static void choose(ServerPlayer player, int id, int value) {
        ServerLevel level = player.serverLevel();
        Sites.Site site = Sites.byId(level, id);
        if (site == null || !site.owner().equals(player.getUUID()) || !site.built() || site.use() != Sites.Use.NONE) {
            return;
        }
        switch (value) {
            case 1 -> makeStore(player, level, site);
            case 2 -> {
                site.use = Sites.Use.MINE;
                Sites.of(level).changed();
                player.sendSystemMessage(Component.literal("The " + site.name() + " is yours. Nobody sleeps in it but "
                        + "you - and your mate, if you have one. Sleep in it and you wake up rested.")
                        .withStyle(ChatFormatting.AQUA));
                syncAround(level, site);
            }
            case 3 -> askWho(player, level, site);
            default -> {
            }
        }
    }

    private static void makeStore(ServerPlayer player, ServerLevel level, Sites.Site site) {
        site.use = Sites.Use.STORE;
        Sites.of(level).changed();
        boolean beds = false;
        Footprint footprint = site.footprint();
        if (footprint != null) {
            for (BlockPos pos : footprint.inside()) {
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.NEST.get()) || state.is(ModBlocks.THATCH_BEDDING.get())) {
                    level.destroyBlock(pos, true);
                    beds = true;
                }
            }
        }
        player.sendSystemMessage(Component.literal("The " + site.name() + " is the band's store now. Sneak-use the "
                + "floor inside with food, bones or anything else you carry to lay it down - stone tools too. The band "
                + "eats from it when it is hungry, cracks the bones in it for marrow, and puts spare food and tools by "
                + "in it. No beds in here." + (beds ? " The bedding comes out." : "")).withStyle(ChatFormatting.AQUA));
        Cohesion.addLimited(player, "store_made", 2, 24000L);
        syncAround(level, site);
    }

    private static void askWho(ServerPlayer player, ServerLevel level, Sites.Site site) {
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<BandMember> members = Band.all(player);
        members.sort(Comparator.comparingDouble(player::distanceToSqr));
        for (BandMember member : members) {
            if (member.isBaby() || Sites.givenTo(level, member.getUUID()) != null) {
                continue;
            }
            member.ensureName();
            String label = member.getName().getString();
            if (member.isInjured()) {
                label += " - hurt";
            }
            if (member.isPregnant()) {
                label += " - with child";
            }
            if (member.isMateOf(player.getUUID())) {
                label += " - your mate";
            }
            labels.add(label);
            values.add(member.getId());
            if (labels.size() >= 8) {
                break;
            }
        }
        if (labels.isEmpty()) {
            tell(player, "There is nobody in your band here to give it to - everyone close has a roof already.",
                    ChatFormatting.GRAY);
            return;
        }
        PacketDistributor.sendToPlayer(player, new ChoicesPayload(site.id(), ACTION_GIVE, "Who is it for?", labels,
                values));
    }

    /** Given to one of the band. */
    public static void give(ServerPlayer player, int id, int entityId) {
        ServerLevel level = player.serverLevel();
        Sites.Site site = Sites.byId(level, id);
        Entity entity = level.getEntity(entityId);
        if (site == null || !site.owner().equals(player.getUUID()) || !site.built() || site.use() != Sites.Use.NONE
                || !(entity instanceof BandMember member) || !member.isAlive() || !member.isLedBy(player)) {
            return;
        }
        if (Sites.givenTo(level, member.getUUID()) != null) {
            tell(player, "They have a roof of their own already.", ChatFormatting.GRAY);
            return;
        }
        member.ensureName();
        String name = member.getName().getString();
        site.use = Sites.Use.THEIRS;
        site.givenTo = member.getUUID();
        site.givenName = name;
        Sites.of(level).changed();
        member.addBond(GIVE_BOND);
        Mood.gave(player, 6);
        boolean hurt = member.isInjured();
        boolean carrying = member.isPregnant();
        String[] ordinary = {"For me? I'll sleep in it tonight.", "Mine? Nobody has ever made me anything like this.",
                "A roof of my own. I won't forget this."};
        String line = hurt ? "For me? I can lie still in there until this heals."
                : carrying ? "For me - for us. The little one will sleep dry."
                : ordinary[member.getRandom().nextInt(ordinary.length)];
        Band.announceDiscovery(member, ": \"" + line + "\"");
        player.sendSystemMessage(Component.literal("You give the " + site.name() + " to " + name + ". Bond +"
                + GIVE_BOND + ".").withStyle(ChatFormatting.GREEN));
        if (hurt || carrying) {
            player.sendSystemMessage(Component.literal("The whole band saw who you built it for - "
                    + (hurt ? "someone laid up and hurting." : "someone carrying a child.")).withStyle(ChatFormatting.AQUA));
            Cohesion.add(player, GIVE_COHESION);
        } else {
            Cohesion.addLimited(player, "gave_roof", 2, 24000L);
        }
        syncAround(level, site);
    }

    /** Whoever a build was given to is dead: it stands empty, for its builder to decide about again. */
    public static void memberDied(BandMember dead) {
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        Sites.Site site = Sites.givenTo(level, dead.getUUID());
        if (site == null) {
            return;
        }
        String name = site.givenName();
        site.use = Sites.Use.NONE;
        site.givenTo = null;
        site.givenName = "";
        Sites.of(level).changed();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
        if (owner != null) {
            tell(owner, name + "'s " + site.name() + " stands empty now. (O to decide what it is for.)",
                    ChatFormatting.GRAY);
        }
        syncAround(level, site);
    }

    // ------------------------------------------------------------ progress

    /** Counts what is in place - and when the last block goes in, it stands. */
    static void check(ServerLevel level, Sites.Site site) {
        Footprint footprint = site.footprint();
        if (footprint == null) {
            return;
        }
        int placed = footprint.placed(level);
        boolean changed = placed != site.placed || site.total != footprint.total();
        site.placed = placed;
        site.total = footprint.total();
        if (site.proposed && placed > 0 && placed > countedAtProposal(site)) {
            // Building into it is as good as saying yes.
            site.proposed = false;
            changed = true;
        }
        // Near enough is done: a block or two that will not go in (something in the way, a cell nobody can reach) no
        // longer leaves a build forever unfinished. The last of it is put in for you.
        int slack = Math.max(1, site.total / 20);
        if (!site.built && placed >= site.total - slack && allLoaded(level, footprint)) {
            for (var entry : footprint.cells().entrySet()) {
                if (!footprint.filled(level, entry.getKey())) {
                    if (!level.getBlockState(entry.getKey()).isAir()) {
                        level.destroyBlock(entry.getKey(), false);
                    }
                    level.setBlock(entry.getKey(), entry.getValue().look(), Block.UPDATE_ALL);
                }
            }
            site.placed = footprint.placed(level);
            complete(level, site);
            return;
        }
        if (site.built && placed == 0 && allLoaded(level, footprint)) {
            Sites.of(level).remove(site);
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
            if (owner != null) {
                tell(owner, "The " + site.name() + " is gone - not a block of it left.", ChatFormatting.GRAY);
            }
            syncAround(level, site);
            return;
        }
        if (changed) {
            Sites.of(level).changed();
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
            if (owner != null && !site.built && owner.level() == level
                    && owner.distanceToSqr(Vec3.atCenterOf(site.origin())) < 32.0D * 32.0D) {
                owner.displayClientMessage(Component.literal(site.label()).withStyle(ChatFormatting.AQUA), true);
            }
            syncAround(level, site);
        }
    }

    private static boolean allLoaded(ServerLevel level, Footprint footprint) {
        for (BlockPos pos : footprint.cells().keySet()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
        }
        return true;
    }

    private static void complete(ServerLevel level, Sites.Site site) {
        site.built = true;
        Sites.of(level).changed();
        Sites.markFinished(level.getServer(), site.owner(), site.blueprint());
        level.playSound(null, site.origin(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 0.7F);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(site.owner());
        if (!shelter(site)) {
            // A rack: it stands, and it is for everyone. No roof, no question, no occasion.
            if (owner != null && owner.level() == level) {
                owner.sendSystemMessage(Component.literal("The " + site.name() + " stands.").withStyle(ChatFormatting.GREEN));
            }
            syncAround(level, site);
            return;
        }
        if (owner != null) {
            // Erectus has to have built something to go on.
            dev.hominin.evolution.EvolutionManager.incrementCriterion(owner, "build_structure", 1);
        }
        if (owner != null && owner.level() == level) {
            boolean raw = false;
            Footprint footprint = site.footprint();
            if (footprint != null) {
                for (BlockPos pos : footprint.cells().keySet()) {
                    BlockState state = level.getBlockState(pos);
                    raw |= state.getBlock() instanceof ThatchBlock && !state.getValue(ThatchBlock.CURED);
                }
            }
            owner.sendSystemMessage(Component.literal("The " + site.name() + " stands."
                    + (raw ? " Stretch hide over the thatch and it will last - left raw, the weather takes it apart." : ""))
                    .withStyle(ChatFormatting.GREEN));
            if (!site.credited && dev.hominin.evolution.hunt.Predation.onOwnGround(owner, site.origin())) {
                site.credited = true;
                Presence.add(owner, 2, "a roof on your ground");
            }
            Cohesion.addLimited(owner, "built_roof", 2, 12000L);
            dev.hominin.evolution.band.SacredPile.event(owner, "the new " + site.name());
            dev.hominin.evolution.band.Chatter.news(owner, "news_built", site.name());
            Tips.offer(owner, Tips.Tip.BUILT);
            for (BandMember member : Band.ownNear(owner, 24.0D)) {
                Lines.say(member, "built");
                break;
            }
            if (owner.distanceToSqr(Vec3.atCenterOf(site.origin())) < 48.0D * 48.0D) {
                ask(owner, site);
            } else {
                tell(owner, "Press O to decide what it is for.", ChatFormatting.GRAY);
            }
        }
        syncAround(level, site);
    }

    /** Next tick, once the block that just went in or came out has settled. */
    private static void later(ServerLevel level, int id) {
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            Sites.Site site = Sites.byId(level, id);
            if (site != null) {
                check(level, site);
            }
        }));
    }

    // ------------------------------------------------------------ events

    /** Only the right block goes into a ghost. And no beds in a store. */
    public static void onPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState placed = event.getPlacedBlock();
        Sites.Site site = Sites.cellAt(level, pos);
        if (site != null) {
            Blueprint.Cell cell = site.footprint().cells().get(pos);
            if (!placed.is(cell.block())) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player) {
                    tell(player, "That isn't what goes there - the ghost wants a "
                            + cell.block().getName().getString().toLowerCase() + ".", ChatFormatting.GRAY);
                }
                return;
            }
            later(level, site.id());
        } else if (placed.is(ModBlocks.THATCH_BLOCK.get()) && !(event.getEntity() instanceof ServerPlayer creative
                && creative.isCreative())
                || placed.is(net.minecraft.tags.BlockTags.LOGS) && event.getEntity() instanceof ServerPlayer placer
                        && !placer.isCreative()) {
            // Thatch and logs are part of a building, or they are nothing: they go into a build's ghost, nowhere else.
            // No walls and roofs thrown up anywhere - a structure is something the band plans and builds.
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player) {
                tell(player, (placed.is(ModBlocks.THATCH_BLOCK.get()) ? "Thatch blocks" : "Logs")
                        + " go into a build - mark one out (O) and set them into its ghost.", ChatFormatting.GRAY);
            }
            return;
        }
        if (placed.is(ModBlocks.NEST.get()) || placed.is(ModBlocks.THATCH_BEDDING.get())) {
            Sites.Site room = Sites.roomAt(level, pos);
            if (room != null && room.use() == Sites.Use.STORE) {
                event.setCanceled(true);
                if (event.getEntity() instanceof ServerPlayer player) {
                    tell(player, "This is where the band keeps things. Beds go somewhere else.", ChatFormatting.GRAY);
                }
            }
        }
    }

    public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Sites.Site site = Sites.cellAt(level, event.getPos());
            if (site != null) {
                later(level, site.id());
            }
        }
    }

    /** A night under your own roof. */
    public static void onWakeUp(net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.wakeImmediately()) {
            return;
        }
        dev.hominin.evolution.band.Mating.nightSlept(player);
        BlockPos at = player.getSleepingPos().orElse(player.blockPosition());
        Sites.Site site = Sites.roomAt(player.serverLevel(), at);
        if (site == null) {
            site = Sites.roomAt(player.serverLevel(), at.above());
        }
        if (site != null && site.use() == Sites.Use.MINE && site.owner().equals(player.getUUID())) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 0));
            player.displayClientMessage(Component.literal("You slept under your own roof, and you wake up rested.")
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    // ------------------------------------------------------------ keeping clients in step

    public static void syncBlueprints(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (Blueprint blueprint : Blueprints.list()) {
            ids.add(blueprint.id().toString());
            texts.add(blueprint.json());
        }
        PacketDistributor.sendToPlayer(player, new BlueprintsPayload(ids, texts));
    }

    public static void syncBlueprintsToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncBlueprints(player);
            syncSites(player);
        }
    }

    public static void syncSites(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<SiteView> views = new ArrayList<>();
        for (Sites.Site site : Sites.all(level)) {
            boolean mine = site.owner().equals(player.getUUID());
            if (mine || site.origin().distSqr(player.blockPosition()) < SYNC_RANGE * SYNC_RANGE) {
                views.add(SiteView.of(site, mine));
            }
        }
        PacketDistributor.sendToPlayer(player, new SitesPayload(views));
    }

    private static void syncAround(ServerLevel level, Sites.Site site) {
        for (ServerPlayer player : level.players()) {
            if (player.getUUID().equals(site.owner())
                    || player.blockPosition().distSqr(site.origin()) < (SYNC_RANGE + 32.0D) * (SYNC_RANGE + 32.0D)) {
                syncSites(player);
            }
        }
    }

    public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> {
            syncBlueprints(player);
            syncSites(player);
        });
    }

    /** Now and then, so builds come and go from the ghosts drawn as you walk about. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 100 == 37) {
            syncSites(player);
        }
        if (player.tickCount % 200 == 137) {
            // However the last block went in - by you, by the band, by anything - the build notices it is done.
            ServerLevel level = player.serverLevel();
            for (Sites.Site site : Sites.ownedBy(level, player.getUUID())) {
                if (!site.built() && !site.proposed() && site.origin().distSqr(player.blockPosition()) < 96.0D * 96.0D) {
                    check(level, site);
                }
            }
        }
        if (player.tickCount % 1200 == 611) {
            suggest(player);
        }
    }

    // ------------------------------------------------------------ the band and its rooms

    /**
     * The room a member sleeps in, if they have one: the one they were given, or - for your mate - your own, if
     * it is near.
     */
    @Nullable
    public static Sites.Site roomFor(BandMember member) {
        if (!(member.level() instanceof ServerLevel level)) {
            return null;
        }
        Sites.Site given = Sites.givenTo(level, member.getUUID());
        if (given != null) {
            return given;
        }
        Player leader = member.leaderPlayer();
        if (leader == null || !member.isMateOf(leader.getUUID())) {
            return null;
        }
        for (Sites.Site site : Sites.ownedBy(level, leader.getUUID())) {
            if (site.built() && site.use() == Sites.Use.MINE
                    && site.origin().distSqr(member.blockPosition()) < MATE_ROOM_RANGE * MATE_ROOM_RANGE) {
                return site;
            }
        }
        return null;
    }

    /**
     * Whether a member may lie down here as far as rooms go: TRUE in their own (or their mate's), FALSE in anyone
     * else's or in a store, null if this is not in a room at all.
     */
    @Nullable
    public static Boolean mayRest(BandMember member, BlockPos pos) {
        if (!(member.level() instanceof ServerLevel level)) {
            return null;
        }
        Sites.Site site = Sites.roomAt(level, pos);
        if (site == null) {
            return null;
        }
        return switch (site.use()) {
            case STORE -> false;
            case MINE -> member.isMateOf(site.owner());
            case THEIRS -> member.getUUID().equals(site.givenTo()) || site.givenTo() != null && member.isMateOf(site.givenTo())
                    || member.isBaby();
            case NONE -> null;
        };
    }

    /** Whether a player may lie down here. Null if they may; otherwise who says no, and what they say. */
    @Nullable
    public static String refusal(ServerLevel level, Player player, BlockPos pos) {
        Sites.Site site = Sites.roomAt(level, pos);
        if (site == null) {
            return null;
        }
        return switch (site.use()) {
            case STORE -> "This is the band's store - nobody sleeps in here.";
            case MINE -> site.owner().equals(player.getUUID()) ? null : "Somebody else built this for themselves.";
            case THEIRS -> {
                BandMember owner = site.givenTo() != null && level.getEntity(site.givenTo()) instanceof BandMember m ? m : null;
                yield owner != null && owner.isMateOf(player.getUUID()) ? null
                        : "<" + site.givenName() + "> That's my roof. You gave it to me, remember?";
            }
            case NONE -> null;
        };
    }

    /** A bed of any size does, under a roof. */
    public static boolean inRoom(net.minecraft.world.level.Level level, BlockPos pos) {
        return level instanceof ServerLevel server ? Sites.roomAt(server, pos) != null : SiteView.inAnyRoom(pos);
    }

    /** Whether a member is lying up in the room they were given: it mends twice as fast there. */
    public static boolean inOwnRoom(BandMember member) {
        if (!(member.level() instanceof ServerLevel level)) {
            return false;
        }
        Sites.Site site = Sites.givenTo(level, member.getUUID());
        Footprint footprint = site != null ? site.footprint() : null;
        return footprint != null && (footprint.isInside(member.blockPosition())
                || footprint.isInside(member.blockPosition().above()));
    }

    /** A nest or bed already in a room, that nobody is lying in. */
    @Nullable
    public static BlockPos bedIn(ServerLevel level, Sites.Site site, BandMember member) {
        Footprint footprint = site.footprint();
        if (footprint == null) {
            return null;
        }
        for (BlockPos pos : footprint.inside()) {
            BlockState state = level.getBlockState(pos);
            if ((state.is(ModBlocks.NEST.get()) || state.is(ModBlocks.THATCH_BEDDING.get())) && !occupied(level, pos, member)) {
                return pos;
            }
        }
        return null;
    }

    /** A clear spot on the floor of a room, for a nest - the middle first. */
    @Nullable
    public static BlockPos floorIn(ServerLevel level, Sites.Site site) {
        Footprint footprint = site.footprint();
        if (footprint == null) {
            return null;
        }
        BlockPos middle = BlockPos.containing(footprint.box().getCenter().getX(), site.origin().getY(),
                footprint.box().getCenter().getZ());
        return footprint.inside().stream()
                .filter(pos -> pos.getY() == site.origin().getY())
                .filter(pos -> level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
                        && ModBlocks.NEST.get().defaultBlockState().canSurvive(level, pos))
                .min(Comparator.comparingDouble(pos -> pos.distSqr(middle)))
                .orElse(null);
    }

    private static boolean occupied(ServerLevel level, BlockPos pos, BandMember member) {
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(0.5D),
                other -> other != member && other.isSleeping()).isEmpty();
    }

    /** Whether a band member could set this build's block in here now: nothing solid in the way, nobody standing in it. */
    public static boolean canSetByHand(ServerLevel level, Sites.Site site, BlockPos pos) {
        Footprint footprint = site.footprint();
        if (footprint == null || !footprint.cells().containsKey(pos) || !level.isLoaded(pos)) {
            return false;
        }
        BlockState now = level.getBlockState(pos);
        return Footprint.free(level, pos, now, false) && level.getFluidState(pos).isEmpty()
                && level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(pos))
                        .isEmpty();
    }

    /** One of the band sets a block into the ghost, as you would. Counted like any other. */
    public static boolean setByMember(ServerLevel level, BandMember member, Sites.Site site, BlockPos pos) {
        if (!canSetByHand(level, site, pos)) {
            return false;
        }
        Blueprint.Cell cell = site.footprint().cells().get(pos);
        if (!level.getBlockState(pos).isAir()) {
            // Grass and flowers get trampled.
            level.destroyBlock(pos, false, member);
        }
        BlockState state = Block.updateFromNeighbourShapes(cell.block().defaultBlockState(), level, pos);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var sound = state.getSoundType(level, pos, member);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                sound.getPitch() * 0.8F);
        later(level, site.id());
        return true;
    }

    /** Whether any of these spots is part of a build. */
    public static boolean anyBuilt(ServerLevel level, BlockPos... cells) {
        for (BlockPos cell : cells) {
            if (Sites.containing(level, cell) != null) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ a new age

    /**
     * Evolving is a long time later: whatever the kind before you built has long since fallen down. Every build of
     * yours - finished or only marked out, huts, tents, racks - is gone, down to the ground.
     */
    public static void wipeOld(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int gone = 0;
        for (Sites.Site site : new ArrayList<>(Sites.ownedBy(level, player.getUUID()))) {
            Footprint footprint = site.footprint();
            if (footprint != null) {
                for (var entry : footprint.cells().entrySet()) {
                    BlockPos pos = entry.getKey();
                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                    if (level.getBlockState(pos).is(entry.getValue().block())) {
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
            Sites.of(level).remove(site);
            gone++;
        }
        if (gone > 0) {
            syncSites(player);
            player.sendSystemMessage(Component.literal("Whatever the ones before you built has long since fallen down.")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------ developer

    /** Every block of the nearest of your builds put in place. */
    public static String devFinish(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Sites.Site nearest = null;
        for (Sites.Site site : Sites.ownedBy(level, player.getUUID())) {
            if (!site.built() && (nearest == null
                    || site.origin().distSqr(player.blockPosition()) < nearest.origin().distSqr(player.blockPosition()))) {
                nearest = site;
            }
        }
        if (nearest == null || nearest.footprint() == null) {
            return "No build of yours is marked out.";
        }
        for (var entry : nearest.footprint().cells().entrySet()) {
            if (!level.getBlockState(entry.getKey()).is(entry.getValue().block())) {
                level.setBlock(entry.getKey(), entry.getValue().look(), Block.UPDATE_ALL);
            }
        }
        check(level, nearest);
        return "The " + nearest.name() + " is finished.";
    }

    public static String devMaterials(ServerPlayer player) {
        give(player, new ItemStack(ModItems.THATCH_BLOCK.get(), 64));
        give(player, new ItemStack(ModItems.BUILDING_BRANCH.get(), 8));
        give(player, new ItemStack(ModItems.HIDE.get(), 16));
        return "64 thatch blocks, 8 building branches and 16 hide.";
    }

    public static String devUnlock(ServerPlayer player) {
        for (Blueprint blueprint : Blueprints.list()) {
            Sites.markFinished(player.server, player.getUUID(), blueprint.id());
        }
        return "Every blueprint can be built.";
    }

    public static String devClear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int n = 0;
        for (Sites.Site site : Sites.ownedBy(level, player.getUUID())) {
            Sites.of(level).remove(site);
            n++;
        }
        syncSites(player);
        return n + " builds forgotten. The blocks stay.";
    }

    /** The nearest of your finished builds goes back to undecided, and the question is put again. */
    public static String devAskAgain(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Sites.Site nearest = null;
        for (Sites.Site site : Sites.ownedBy(level, player.getUUID())) {
            if (site.built() && (nearest == null
                    || site.origin().distSqr(player.blockPosition()) < nearest.origin().distSqr(player.blockPosition()))) {
                nearest = site;
            }
        }
        if (nearest == null) {
            return "No finished build of yours.";
        }
        nearest.use = Sites.Use.NONE;
        nearest.givenTo = null;
        nearest.givenName = "";
        Sites.of(level).changed();
        ask(player, nearest);
        syncSites(player);
        return "Asking again about the " + nearest.name() + ".";
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void tell(ServerPlayer player, String text, ChatFormatting style) {
        player.displayClientMessage(Component.literal(text).withStyle(style), false);
    }

    private Building() {
    }
}
