package dev.hominin.evolution.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.advancement.HomininAdvancements;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.guide.GuideBook;
import dev.hominin.evolution.stage.ChecklistTracker;
import dev.hominin.evolution.stage.StageDefinition;
import dev.hominin.evolution.stage.StageRegistry;
import dev.hominin.evolution.stage.StageSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class HomininCommand {
    private HomininCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hominin")
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("checklist")
                        .executes(ctx -> toggleChecklist(ctx, ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("guide")
                        .executes(ctx -> giveGuide(ctx, ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("start")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("stage", ResourceLocationArgument.id())
                                        .executes(HomininCommand::start))))
                .then(Commands.literal("bypass")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("criterion", StringArgumentType.word())
                                        .executes(HomininCommand::promptBypass))))
                .then(Commands.literal("bypassconfirm")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("criterion", StringArgumentType.word())
                                        .executes(HomininCommand::confirmBypass))))
                .then(Commands.literal("band")
                        .requires(src -> src.hasPermission(2))
                        .executes(HomininCommand::summonBandMember))
                .then(Commands.literal("become")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("stage", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                        .suggestResource(StageRegistry.all().keySet().stream()
                                                // A path's own goals are not a stage to become.
                                                .filter(id -> !id.getPath().contains("/")), builder))
                                .executes(ctx -> setStage(ctx, ctx.getSource().getPlayerOrException(),
                                        ResourceLocationArgument.getId(ctx, "stage")))))
                .then(Commands.literal("season")
                        .executes(ctx -> season(ctx, null, false))
                        .then(Commands.literal("dry").requires(src -> src.hasPermission(2))
                                .executes(ctx -> season(ctx, dev.hominin.evolution.survival.Seasons.Season.DRY, true)))
                        .then(Commands.literal("prosperous").requires(src -> src.hasPermission(2))
                                .executes(ctx -> season(ctx,
                                        dev.hominin.evolution.survival.Seasons.Season.PROSPEROUS, true)))
                        .then(Commands.literal("superdry").requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    dev.hominin.evolution.survival.Seasons.forceExtreme(
                                            dev.hominin.evolution.survival.Seasons.Season.DRY);
                                    return season(ctx, null, false);
                                }))
                        .then(Commands.literal("veryprosperous").requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    dev.hominin.evolution.survival.Seasons.forceExtreme(
                                            dev.hominin.evolution.survival.Seasons.Season.PROSPEROUS);
                                    return season(ctx, null, false);
                                }))
                        .then(Commands.literal("natural").requires(src -> src.hasPermission(2))
                                .executes(ctx -> season(ctx, null, true))))
                .then(Commands.literal("unlockadvancements")
                        .requires(src -> src.hasPermission(2))
                        .executes(HomininCommand::unlockAdvancements))
                .then(Commands.literal("wildband")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            int size = dev.hominin.evolution.band.WildBands.spawnNear(
                                    ctx.getSource().getPlayerOrException(), 12, 20);
                            if (size == 0) {
                                ctx.getSource().sendFailure(Component.literal("Nowhere nearby for a band to stand."));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "A wild band of " + size + " appears nearby."), false);
                            return 1;
                        }))
                .then(Commands.literal("lead")
                        .then(Commands.literal("band").then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> band(ctx, false))))
                        .then(Commands.literal("stop").executes(ctx -> {
                            dev.hominin.evolution.mind.MentalMap.stop(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("place").then(Commands.argument("x",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer()).then(Commands.argument("z",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x");
                                    int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "z");
                                    String label = "that place";
                                    for (var poi : dev.hominin.evolution.world.Pois.known(player)) {
                                        if (poi.pos().getX() == x && poi.pos().getZ() == z) {
                                            label = poi.label();
                                        }
                                    }
                                    dev.hominin.evolution.mind.MentalMap.lead(player,
                                            new net.minecraft.core.BlockPos(x, player.getBlockY(), z), label, "");
                                    return 1;
                                })))))
                .then(Commands.literal("ransom").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> band(ctx, true))))
                .then(Commands.literal("map").executes(ctx -> {
                    dev.hominin.evolution.mind.MentalMap.send(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("answer").then(Commands.argument("choice",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 5)).executes(ctx -> {
                            dev.hominin.evolution.band.Claims.answer(ctx.getSource().getPlayerOrException(),
                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "choice"));
                            return 1;
                        })))
                .then(Commands.literal("territory")
                        .then(Commands.literal("pack").executes(ctx -> {
                            dev.hominin.evolution.hunt.Predation.packUp(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("set").executes(ctx -> {
                            dev.hominin.evolution.hunt.Predation.settleHere(ctx.getSource().getPlayerOrException());
                            return 1;
                        })))
                .then(Commands.literal("others").executes(ctx -> {
                    dev.hominin.evolution.band.Relations.sendOthers(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("nameband").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            dev.hominin.evolution.band.Relations.nameOwnBand(ctx.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(ctx, "name"));
                            return 1;
                        })))
                .then(Commands.literal("tips")
                        .executes(ctx -> tips(ctx, null))
                        .then(Commands.literal("on").executes(ctx -> tips(ctx, false)))
                        .then(Commands.literal("off").executes(ctx -> tips(ctx, true)))
                        .then(Commands.literal("reset").executes(ctx -> {
                            dev.hominin.evolution.guide.Tips.reset(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("read")
                                .then(Commands.argument("entry", ResourceLocationArgument.id())
                                        .then(Commands.argument("page",
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99))
                                                .executes(HomininCommand::readTip)))))
                .then(Commands.literal("blueprint")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("capture")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                                .then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                                        .then(Commands.argument("door", StringArgumentType.word())
                                                                .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider
                                                                        .suggest(new String[] {"north", "south", "east", "west"}, builder))
                                                                .executes(HomininCommand::captureBlueprint))))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(HomininCommand::resetBlueprint)))
                        .then(Commands.literal("list").executes(HomininCommand::listBlueprints)))
                .then(Commands.literal("dev")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> toggleDeveloperMode(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> toggleDeveloperMode(ctx, EntityArgument.getPlayer(ctx, "player"))))));
    }

    /**
     * Writes what stands between two corners as a blueprint (over the one of that name), its door on the given
     * side - build it by hand exactly as it should be, then make it the blueprint.
     */
    private static int captureBlueprint(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        net.minecraft.core.Direction door = net.minecraft.core.Direction.byName(StringArgumentType.getString(ctx, "door"));
        if (door == null || !door.getAxis().isHorizontal() || !name.matches("[a-z0-9_]+")) {
            ctx.getSource().sendFailure(Component.literal("Name it with a-z, 0-9 and _, and give the door as north, "
                    + "south, east or west."));
            return 0;
        }
        try {
            dev.hominin.evolution.build.Blueprint made = dev.hominin.evolution.build.Blueprints.capture(
                    ctx.getSource().getLevel(),
                    net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                    net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "to"), door, name);
            dev.hominin.evolution.build.Building.syncBlueprintsToAll(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal("Blueprint " + name + " captured: " + made.width() + " wide, "
                    + made.depth() + " deep, " + made.height() + " high - " + made.materialsText() + ". Saved to "
                    + dev.hominin.evolution.build.Blueprints.overrideDir().resolve(name + ".json")), true);
            return 1;
        } catch (java.io.IOException | RuntimeException e) {
            ctx.getSource().sendFailure(Component.literal("Could not capture it: " + e.getMessage()));
            return 0;
        }
    }

    private static int resetBlueprint(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name").toLowerCase(java.util.Locale.ROOT);
        try {
            boolean gone = dev.hominin.evolution.build.Blueprints.reset(name);
            dev.hominin.evolution.build.Building.syncBlueprintsToAll(ctx.getSource().getServer());
            ctx.getSource().sendSuccess(() -> Component.literal(gone ? "Blueprint " + name + " is the mod's own again."
                    : "There was no captured " + name + " to take away."), true);
            return 1;
        } catch (java.io.IOException e) {
            ctx.getSource().sendFailure(Component.literal("Could not reset it: " + e.getMessage()));
            return 0;
        }
    }

    private static int listBlueprints(CommandContext<CommandSourceStack> ctx) {
        for (dev.hominin.evolution.build.Blueprint blueprint : dev.hominin.evolution.build.Blueprints.list()) {
            ctx.getSource().sendSuccess(() -> Component.literal(blueprint.id().getPath() + ": " + blueprint.name() + ", "
                    + blueprint.width() + "x" + blueprint.depth() + "x" + blueprint.height() + " - "
                    + blueprint.materialsText()), false);
        }
        return 1;
    }

    /**
     * Tips on or off, or how things stand. No permission level: whether you want a word in your ear is
     * yours to decide.
     */
    private static int tips(CommandContext<CommandSourceStack> ctx, @javax.annotation.Nullable Boolean off)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (off == null) {
            dev.hominin.evolution.guide.Tips.status(player);
        } else {
            dev.hominin.evolution.guide.Tips.setOff(player, off);
        }
        return 1;
    }

    /** Following a band's call, or paying one off: what the links in chat run. */
    private static int band(CommandContext<CommandSourceStack> ctx, boolean ransom)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        dev.hominin.evolution.band.Bands.Record band;
        try {
            band = dev.hominin.evolution.band.Bands.get(player.serverLevel(),
                    java.util.UUID.fromString(StringArgumentType.getString(ctx, "id")));
        } catch (IllegalArgumentException e) {
            band = null;
        }
        if (band == null) {
            ctx.getSource().sendFailure(Component.literal("That band is gone."));
            return 0;
        }
        if (ransom) {
            dev.hominin.evolution.band.Relations.payRansom(player, band);
        } else {
            dev.hominin.evolution.band.Relations.lead(player, band);
        }
        return 1;
    }

    /** What double-clicking a tip runs: the guide, open at the tip's page. */
    private static int readTip(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!dev.hominin.evolution.guide.Tips.openBook(player, ResourceLocationArgument.getId(ctx, "entry"),
                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page"))) {
            ctx.getSource().sendFailure(Component.literal("The guide needs Patchouli installed."));
            return 0;
        }
        return 1;
    }

    /** What season it is - and, for testing, which season it should be. */
    private static int season(CommandContext<CommandSourceStack> ctx,
            @javax.annotation.Nullable dev.hominin.evolution.survival.Seasons.Season season, boolean set) {
        var level = ctx.getSource().getLevel();
        if (set) {
            dev.hominin.evolution.survival.Seasons.force(season);
        }
        var now = dev.hominin.evolution.survival.Seasons.of(level);
        int left = dev.hominin.evolution.survival.Seasons.daysLeft(level);
        boolean dry = dev.hominin.evolution.survival.Drought.isActive(level);
        ctx.getSource().sendSuccess(() -> Component.literal(dev.hominin.evolution.survival.Seasons.label(level)
                + (set && season != null ? " (forced)" : "")
                + ", " + left + (left == 1 ? " day" : " days") + " left" + (dry ? " - and today is a dry day." : ".")),
                false);
        return 1;
    }

    /** Testing aid: one more member for the caller's band. */
    private static int summonBandMember(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (dev.hominin.evolution.band.Band.spawnMember(player) == null) {
            ctx.getSource().sendFailure(Component.literal("Could not place a band member here."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("A new member joins your band."), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition stage = StageRegistry.current(data);
        String stageName = stage != null ? stage.displayName() : data.getStage().toString();
        ctx.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " — stage: " + stageName), false);
        if (stage != null) {
            boolean requiredOk = EvolutionManager.isRequiredSatisfied(data, stage);
            int optionalCount = EvolutionManager.optionalSatisfiedCount(data, stage);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Required gate satisfied: " + requiredOk + " | Optional: " + optionalCount + "/" + stage.gate().chooseCount()), false);
        }
        if (data.isDeveloperMode()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Developer mode is ON — gates and tool requirements are being ignored."), false);
        }
        return 1;
    }

    /**
     * Replaces a lost guidebook. No permission level: it only teaches, and the book
     * is not craftable, so losing it should never be permanent.
     */
    private static int giveGuide(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (!GuideBook.give(player)) {
            ctx.getSource().sendFailure(Component.literal("The guidebook needs Patchouli installed."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("A fresh copy of the guide, in your pack."), false);
        return 1;
    }

    /**
     * Shows or hides the standing checklist overlay. No permission level: it is a
     * display preference, not a change to anyone's progress.
     */
    private static int toggleChecklist(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        boolean shown = ChecklistTracker.toggle(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Evolution checklist " + (shown ? "shown." : "hidden.")), false);
        return 1;
    }

    /**
     * Testing escape hatch. Waves the player through every readiness gate and every
     * tool requirement at once, so a feature can be reached without first playing
     * the progression that normally unlocks it.
     */
    private static int toggleDeveloperMode(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        boolean enabled = !data.isDeveloperMode();
        data.setDeveloperMode(enabled);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.hominin.evolution.network.DevFlagPayload(enabled));

        String name = player.getGameProfile().getName();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Developer mode " + (enabled ? "ENABLED" : "disabled") + " for " + name), true);
        player.sendSystemMessage(Component.literal(enabled
                ? "Developer mode on — evolution gates and tool requirements are ignored."
                : "Developer mode off — gates and tool requirements apply again."));
        return 1;
    }

    /** Testing aid: every one of this mod's advancements, at once. */
    private static int unlockAdvancements(CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int count = 0;
        for (var holder : player.server.getAdvancements().getAllAdvancements()) {
            if (holder.id().getNamespace().equals(dev.hominin.evolution.HomininEvolutionMod.MODID)) {
                HomininAdvancements.award(player, holder.id().getPath());
                count++;
            }
        }
        int unlocked = count;
        ctx.getSource().sendSuccess(() -> Component.literal("Unlocked " + unlocked + " advancements."), false);
        return count;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return setStage(ctx, EntityArgument.getPlayer(ctx, "player"), ResourceLocationArgument.getId(ctx, "stage"));
    }

    private static int setStage(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ResourceLocation stageId) {
        StageDefinition stage = StageRegistry.get(stageId);
        if (stage == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown stage: " + stageId));
            return 0;
        }
        // Exactly what evolving does: the cutscene, deep time, a new place, a fresh
        // inventory and a new band. Skills carry over, as they would.
        EvolutionManager.become(player, stageId);
        ctx.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " set to stage " + stage.displayName()), true);
        return 1;
    }

    private static int promptBypass(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String criterion = StringArgumentType.getString(ctx, "criterion");
        String playerName = player.getGameProfile().getName();
        MutableComponent confirm = Component.literal("[Click to confirm bypass of '" + criterion + "' for " + playerName + "]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/hominin bypassconfirm " + playerName + " " + criterion))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(
                                "This bypasses a readiness gate criterion. The milestone action still has to be performed manually."))));
        ctx.getSource().sendSuccess(() -> confirm, false);
        return 1;
    }

    private static int confirmBypass(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String criterion = StringArgumentType.getString(ctx, "criterion");
        EvolutionManager.forceSatisfyCriterion(player, criterion);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Bypassed criterion '" + criterion + "' for " + player.getGameProfile().getName()), true);
        return 1;
    }
}
