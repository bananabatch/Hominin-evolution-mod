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
                                        .suggestResource(StageRegistry.all().keySet(), builder))
                                .executes(ctx -> setStage(ctx, ctx.getSource().getPlayerOrException(),
                                        ResourceLocationArgument.getId(ctx, "stage")))))
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
                .then(Commands.literal("dev")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> toggleDeveloperMode(ctx, ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> toggleDeveloperMode(ctx, EntityArgument.getPlayer(ctx, "player"))))));
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
        StageDefinition stage = StageRegistry.get(data.getStage());
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
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        StageDefinition previous = StageRegistry.get(data.getStage());
        data.setStage(stageId);
        data.getCriterionCounters().clear();
        data.getNotifiedReadyStages().clear();
        // Same reset the natural evolution path does, so jumping stages for testing
        // doesn't carry distance progress across with it.
        data.setStageStartWalkDistance(player.walkDist);
        data.setDistanceCredits(0);
        StageSync.sync(player);
        HomininAdvancements.awardStages(player);
        // The same sequence evolving plays - dark, deep time, the new name - but without the
        // move and the fresh inventory, so testing a stage does not cost you your things.
        String age = dev.hominin.evolution.stage.StageAge.ago(stage.yearsAgo());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new dev.hominin.evolution.network.CutsceneStartPayload(
                        previous == null ? "" : dev.hominin.evolution.stage.StageAge.later(previous.yearsAgo(),
                                stage.yearsAgo()),
                        age.isEmpty() ? stage.displayName() : stage.displayName() + " - " + age));
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
