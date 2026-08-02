package dev.hominin.evolution.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.hominin.evolution.Attachments;
import dev.hominin.evolution.EvolutionManager;
import dev.hominin.evolution.data.PlayerEvolutionData;
import dev.hominin.evolution.stage.StageDefinition;
import dev.hominin.evolution.stage.StageRegistry;
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
                                        .executes(HomininCommand::confirmBypass)))));
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
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation stageId = ResourceLocationArgument.getId(ctx, "stage");
        StageDefinition stage = StageRegistry.get(stageId);
        if (stage == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown stage: " + stageId));
            return 0;
        }
        PlayerEvolutionData data = player.getData(Attachments.PLAYER_EVOLUTION_DATA);
        data.setStage(stageId);
        data.getCriterionCounters().clear();
        data.getNotifiedReadyStages().clear();
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
