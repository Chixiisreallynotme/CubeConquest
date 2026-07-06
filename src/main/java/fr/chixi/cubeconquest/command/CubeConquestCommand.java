package fr.chixi.cubeconquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import fr.chixi.cubeconquest.CubeConquestGameManager;
import fr.chixi.cubeconquest.CubeConquestGameManagerEvents;
import fr.chixi.cubeconquest.CubeConquestSavedData;
import fr.chixi.cubeconquest.CubeConquestState;
import fr.chixi.cubeconquest.GamePhase;
import fr.chixi.cubeconquest.Team;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CubeConquestCommand {

    private CubeConquestCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cubeconquest")
            .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("start")
                .executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
                    if (state.getPhase() != GamePhase.IDLE) {
                        ctx.getSource().sendFailure(Component.literal("A game is already running."));
                        return 0;
                    }
                    try {
                        CubeConquestGameManagerEvents.startGame(server);
                        ctx.getSource().sendSuccess(() -> Component.literal("Game started!"), true);
                        return 1;
                    } catch (IllegalStateException e) {
                        ctx.getSource().sendFailure(Component.literal(e.getMessage()));
                        return 0;
                    }
                }))
            .then(Commands.literal("stop")
                .executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
                    if (state.getPhase() == GamePhase.IDLE) {
                        ctx.getSource().sendFailure(Component.literal("No game is running."));
                        return 0;
                    }
                    CubeConquestGameManagerEvents.stopGame(server);
                    ctx.getSource().sendSuccess(() -> Component.literal("Game stopped. Team rosters cleared."), true);
                    return 1;
                }))
            .then(Commands.literal("setPreparationTime")
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                        MinecraftServer server = ctx.getSource().getServer();
                        CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
                        if (state.getPhase() != GamePhase.IDLE) {
                            ctx.getSource().sendFailure(Component.literal("Cannot change preparation time while a game is running."));
                            return 0;
                        }
                        state.setPreparationDurationTicks(minutes * 1200);
                        ctx.getSource().sendSuccess(() -> Component.literal("Preparation time set to " + minutes + " minutes."), true);
                        return 1;
                    })))
            .then(Commands.literal("overworldOnly")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                        MinecraftServer server = ctx.getSource().getServer();
                        CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
                        if (state.getPhase() != GamePhase.IDLE) {
                            ctx.getSource().sendFailure(Component.literal("Cannot change overworld-only setting while a game is running."));
                            return 0;
                        }
                        state.setOverworldOnly(enabled);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "Overworld-only placement: " + (enabled ? "enabled" : "disabled")), true);
                        return 1;
                    })))
            .then(Commands.literal("waitForCountdown")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                        MinecraftServer server = ctx.getSource().getServer();
                        CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
                        if (state.getPhase() != GamePhase.IDLE) {
                            ctx.getSource().sendFailure(Component.literal("Cannot change wait-for-countdown setting while a game is running."));
                            return 0;
                        }
                        state.setWaitForCountdown(enabled);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "Wait for countdown: " + (enabled ? "enabled" : "disabled")), true);
                        return 1;
                    })))
            .then(Commands.literal("team")
                .then(Commands.literal("add")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("team", StringArgumentType.word())
                            .executes(ctx -> {
                                String playerName = StringArgumentType.getString(ctx, "player");
                                String teamArg = StringArgumentType.getString(ctx, "team").toUpperCase();
                                Team team;
                                try { team = Team.valueOf(teamArg); }
                                catch (IllegalArgumentException e) {
                                    ctx.getSource().sendFailure(Component.literal("Unknown team: " + teamArg));
                                    return 0;
                                }
                                ServerPlayer target = ctx.getSource().getServer()
                                    .getPlayerList().getPlayerByName(playerName);
                                if (target == null) {
                                    ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
                                    return 0;
                                }
                                CubeConquestState state = CubeConquestSavedData.getServerState(ctx.getSource().getServer()).getState();
                                // ponytail: block mid-game team changes — new player skips start-game setup (no compass, no cube)
                                if (state.getPhase() != GamePhase.IDLE) {
                                    ctx.getSource().sendFailure(Component.literal("Cannot change teams while a game is running"));
                                    return 0;
                                }
                                state.addPlayer(team, target.getUUID());
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal(playerName + " added to " + team.displayName() + " team."),
                                    true);
                                return 1;
                            }))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            ServerPlayer target = ctx.getSource().getServer()
                                .getPlayerList().getPlayerByName(playerName);
                            if (target == null) {
                                ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
                                return 0;
                            }
                            CubeConquestState state = CubeConquestSavedData.getServerState(ctx.getSource().getServer()).getState();
                            // ponytail: block mid-game team changes
                            if (state.getPhase() != GamePhase.IDLE) {
                                ctx.getSource().sendFailure(Component.literal("Cannot change teams while a game is running"));
                                return 0;
                            }
                            state.removePlayer(target.getUUID());
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(playerName + " removed from their team."), true);
                            return 1;
                        })))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        CubeConquestState state = CubeConquestSavedData.getServerState(
                            ctx.getSource().getServer()).getState();
                        for (Team t : Team.values()) {
                            var names = state.getPlayers(t).stream()
                                .map(id -> {
                                    ServerPlayer p = ctx.getSource().getServer().getPlayerList().getPlayer(id);
                                    return p != null ? p.getName().getString() : id.toString();
                                })
                                .toList();
                            ctx.getSource().sendSuccess(
                                () -> Component.literal(t.displayName() + " team: " + names), false);
                        }
                        return 1;
                    }))));

        com.mojang.brigadier.Command<CommandSourceStack> drawVote = ctx -> {
            CommandSourceStack source = ctx.getSource();
            var server = source.getServer();
            CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
            CubeConquestState state = saved.getState();

            if (state.getPhase() != GamePhase.COMBAT) {
                source.sendFailure(Component.literal("/draw is only available during COMBAT phase"));
                return 0;
            }

            ServerPlayer voter = source.getPlayerOrException();
            // MEDIUM-2: non-team players produce misleading broadcasts; gate on membership
            if (state.getTeamOf(voter.getUUID()).isEmpty()) {
                source.sendFailure(Component.literal("You are not on a team"));
                return 0;
            }
            if (!CubeConquestGameManagerEvents.addDrawVote(voter.getUUID())) {
                source.sendFailure(Component.literal("Your draw vote is still active — use /draw refuse to withdraw"));
                return 0;
            }

            Set<UUID> redOnline = state.getPlayers(Team.RED).stream()
                .filter(id -> server.getPlayerList().getPlayer(id) != null)
                .collect(Collectors.toSet());
            Set<UUID> blueOnline = state.getPlayers(Team.BLUE).stream()
                .filter(id -> server.getPlayerList().getPlayer(id) != null)
                .collect(Collectors.toSet());

            Set<UUID> currentDrawVoters = CubeConquestGameManagerEvents.drawVotersSnapshot();
            long redVotes  = redOnline.stream().filter(currentDrawVoters::contains).count();
            long blueVotes = blueOnline.stream().filter(currentDrawVoters::contains).count();

            Component voteMsg = Component.literal(
                voter.getName().getString() + " voted for draw — RED: "
                + redVotes + "/" + redOnline.size()
                + ", BLUE: " + blueVotes + "/" + blueOnline.size());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(voteMsg);

            if (CubeConquestGameManager.isDrawThresholdMet(
                    currentDrawVoters, redOnline, blueOnline)) {
                // ponytail: re-check phase — same-tick second vote could fire on already-IDLE state
                if (state.getPhase() == GamePhase.COMBAT) {
                    CubeConquestGameManagerEvents.triggerDraw(server);
                }
            }
            return 1;
        };

        dispatcher.register(Commands.literal("draw")
            .requires(CommandSourceStack::isPlayer)
            .executes(drawVote)
            .then(Commands.literal("accept").executes(drawVote))
            .then(Commands.literal("refuse")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    CubeConquestState state = CubeConquestSavedData.getServerState(source.getServer()).getState();
                    if (state.getPhase() != GamePhase.COMBAT) {
                        source.sendFailure(Component.literal("/draw is only available during COMBAT phase"));
                        return 0;
                    }
                    ServerPlayer voter = source.getPlayerOrException();
                    if (state.getTeamOf(voter.getUUID()).isEmpty()) {
                        source.sendFailure(Component.literal("You are not on a team"));
                        return 0;
                    }
                    CubeConquestGameManagerEvents.removeDrawVote(voter.getUUID());
                    source.sendSuccess(() -> Component.literal("You withdrew your draw vote"), false);
                    return 1;
                })
            )
        );

        com.mojang.brigadier.Command<CommandSourceStack> ffVote = ctx -> {
            CommandSourceStack source = ctx.getSource();
            MinecraftServer server = source.getServer();
            CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
            CubeConquestState state = saved.getState();

            if (state.getPhase() != GamePhase.COMBAT) {
                source.sendFailure(Component.literal("/ff is only available during COMBAT phase"));
                return 0;
            }

            ServerPlayer voter = source.getPlayerOrException();
            Team voterTeam = state.getTeamOf(voter.getUUID()).orElse(null);
            if (voterTeam == null) {
                source.sendFailure(Component.literal("You are not on a team"));
                return 0;
            }

            boolean added = CubeConquestGameManagerEvents.addFfVote(voterTeam, voter.getUUID());
            if (!added) {
                source.sendFailure(Component.literal("Your forfeit vote is still active — use /ff refuse to withdraw"));
                return 0;
            }

            Set<UUID> teamOnline = state.getPlayers(voterTeam).stream()
                .filter(id -> server.getPlayerList().getPlayer(id) != null)
                .collect(Collectors.toSet());

            Set<UUID> teamYesVotes = CubeConquestGameManagerEvents.ffVoteSnapshot(voterTeam);
            long yesVotes = teamOnline.stream().filter(teamYesVotes::contains).count();
            long needed = (teamOnline.size() / 2) + 1;

            Component msg = Component.literal(
                voter.getName().getString() + " voted to forfeit for team "
                + voterTeam.name() + " — " + yesVotes + "/" + teamOnline.size()
                + " voted yes (" + needed + " needed)");
            
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (state.getPlayers(voterTeam).contains(p.getUUID())) {
                    p.sendSystemMessage(msg);
                }
            }

            if (CubeConquestGameManager.isForfeitPassing(teamYesVotes, teamOnline)) {
                // ponytail: re-check phase — simultaneous forfeit from both teams could call triggerVictory twice
                if (state.getPhase() == GamePhase.COMBAT) {
                    Team winner = voterTeam.opponent();
                    CubeConquestGameManagerEvents.triggerVictory(server, winner, state);
                }
            }
            return 1;
        };

        dispatcher.register(Commands.literal("ff")
            .requires(CommandSourceStack::isPlayer)
            .executes(ffVote)
            .then(Commands.literal("accept").executes(ffVote))
            .then(Commands.literal("refuse")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    MinecraftServer server = source.getServer();
                    CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
                    CubeConquestState state = saved.getState();

                    if (state.getPhase() != GamePhase.COMBAT) {
                        source.sendFailure(Component.literal("/ff is only available during COMBAT phase"));
                        return 0;
                    }

                    ServerPlayer voter = source.getPlayerOrException();
                    Team voterTeam = state.getTeamOf(voter.getUUID()).orElse(null);
                    if (voterTeam == null) {
                        source.sendFailure(Component.literal("You are not on a team"));
                        return 0;
                    }
                    CubeConquestGameManagerEvents.removeFfVote(voterTeam, voter.getUUID()); // ponytail: null-bucket guard is in removeFfVote
                    source.sendSuccess(
                        () -> Component.literal("You withdrew your forfeit vote"), false);
                    return 1;
                })
            )
        );
    }
}
