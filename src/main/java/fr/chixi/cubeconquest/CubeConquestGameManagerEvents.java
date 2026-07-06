package fr.chixi.cubeconquest;

import fr.chixi.cubeconquest.block.CubeBlock;
import fr.chixi.cubeconquest.network.CubePositionPayload;
import fr.chixi.cubeconquest.network.PlacementCountdownPayload;
import fr.chixi.cubeconquest.network.PlayerTeamPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.stream.Collectors;
import java.util.EnumMap;

/**
 * MC-dependent event handlers for CubeConquest game logic.
 * Excluded from compileJava on JDK 21 (see build.gradle).
 */
public class CubeConquestGameManagerEvents {

    // ponytail: 3600 ticks = 3 min placement timeout (preparation duration is now configurable in State)
    private static final int PLACEMENT_TIMEOUT_TICKS = 3600;

    private static int tickCount = 0;
    // ponytail: restored from SavedData once on first tick after server (re)start — prevents restart resetting placement timer
    private static boolean tickCountRestored = false;
    // ponytail: transient — cleared on all game lifecycle events
    private static final Map<UUID, Integer> actionBarCountdown = new HashMap<>();
    // ponytail: transient — cleared on all lifecycle events
    private static final Set<UUID> timeoutDeaths = new HashSet<>();
    // ponytail: transient draw vote state — cleared on all game lifecycle events
    private static final Set<UUID> drawVoters = new HashSet<>();
    // ponytail: per-team buckets — RED and BLUE forfeits are independent; global set caused cross-contamination
    private static final Map<Team, Set<UUID>> ffVoteYes = new EnumMap<>(Team.class);

    public static boolean addDrawVote(UUID id) { return drawVoters.add(id); }
    public static void removeDrawVote(UUID id) { drawVoters.remove(id); }
    public static Set<UUID> drawVotersSnapshot() { return java.util.Collections.unmodifiableSet(drawVoters); }
    public static boolean addFfVote(Team team, UUID id) {
        return ffVoteYes.computeIfAbsent(team, k -> new java.util.HashSet<>()).add(id);
    }
    public static void removeFfVote(Team team, UUID id) {
        java.util.Set<UUID> bucket = ffVoteYes.get(team);
        if (bucket != null) bucket.remove(id);
    }
    public static Set<UUID> ffVoteSnapshot(Team team) {
        return java.util.Collections.unmodifiableSet(
            ffVoteYes.getOrDefault(team, java.util.Collections.emptySet()));
    }

    private static boolean playerHasItem(ServerPlayer player, Item item) {
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            if (player.containerMenu.slots.get(i).getItem().getItem() == item) return true;
        }
        if (player.containerMenu.getCarried().getItem() == item) {
            return true;
        }
        return false;
    }

    private static void removeExtraCubes(ServerPlayer player, Item item) {
        boolean found = false;
        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            ItemStack stack = player.containerMenu.slots.get(i).getItem();
            if (stack.getItem() == item) {
                if (found) {
                    player.containerMenu.slots.get(i).set(ItemStack.EMPTY);
                } else {
                    found = true;
                }
            }
        }
        if (player.containerMenu != null && player.containerMenu.getCarried().getItem() == item) {
            if (found) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
            } else {
                found = true;
            }
        }
    }

    static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                Item item = itemEntity.getItem().getItem();
                if (item == CubeConquestMod.RED_CUBE_BLOCK.asItem() || item == CubeConquestMod.BLUE_CUBE_BLOCK.asItem()) {
                    itemEntity.discard();
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(CubeConquestGameManagerEvents::onServerTick);
        AttackEntityCallback.EVENT.register(CubeConquestGameManagerEvents::onAttack);
        PlayerBlockBreakEvents.BEFORE.register(CubeConquestGameManagerEvents::onBlockBreak);
        ServerLivingEntityEvents.ALLOW_DEATH.register(CubeConquestGameManagerEvents::onAllowDeath);
        UseBlockCallback.EVENT.register(CubeConquestGameManagerEvents::onUseBlock);
        // ponytail: re-send team to reconnecting player mid-game
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            net.minecraft.server.level.ServerPlayer player = handler.getPlayer();
            CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
            CubeConquestState state = saved.getState();
            if (state.getPhase() != GamePhase.IDLE) {
                state.getTeamOf(player.getUUID()).ifPresent(team ->
                    ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
                );

                // ponytail: re-issue cube if porteur reconnects without it — covers fast-reconnect race
                if (state.getPhase() == GamePhase.PREPARATION || state.getPhase() == GamePhase.PLACEMENT) {
                    state.getTeamOf(player.getUUID()).ifPresent(team -> {
                        if (player.getUUID().equals(state.getPorteur(team)) && saved.getCubePos(team) == null) {
                            Item cubeItem = cubeBlockFor(team).asItem();
                            if (!playerHasItem(player, cubeItem)) {
                                player.getInventory().add(new ItemStack(cubeItem));
                            }
                        }
                    });
                }
                // ponytail: re-issue compass on reconnect — COMBAT only; compasses are not distributed until transitionToCombat
                if (state.getPhase() == GamePhase.COMBAT) {
                    state.getTeamOf(player.getUUID()).ifPresent(t -> {
                        if (!playerHasItem(player, CubeConquestMod.TRACKING_COMPASS)) {
                            player.getInventory().add(new ItemStack(CubeConquestMod.TRACKING_COMPASS));
                        }
                    });
                }
                // ponytail: clear stale Slowness — former porteur may have disconnected mid-PLACEMENT before effect expired
                state.getTeamOf(player.getUUID()).ifPresent(t -> {
                    UUID currentPorteur = state.getPorteur(t);
                    if (!player.getUUID().equals(currentPorteur)) {
                        player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
                    }
                });
                // ponytail: re-immobilize porteur immediately on reconnect — don't wait for next handlePlacementTick
                if (state.getPhase() == GamePhase.PLACEMENT) {
                    state.getTeamOf(player.getUUID()).ifPresent(t -> {
                        if (player.getUUID().equals(state.getPorteur(t))
                                && saved.getCubePos(t) == null
                                && !player.isCreative()) {
                            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.SLOWNESS,
                                PLACEMENT_TIMEOUT_TICKS + 20, 127, false, false));
                        }
                    });
                }
                // ponytail: sync cube positions on reconnect so compass works immediately
                if (state.getPhase() == GamePhase.COMBAT) {
                    for (Team t : Team.values()) {
                        ServerPlayNetworking.send(player, new CubePositionPayload(t,
                            Optional.ofNullable(saved.getCubePos(t)),
                            Optional.ofNullable(state.getCubeDimension(t))));
                    }
                }
                // ponytail: sync placement countdown for reconnecting player — porteur sees their timer
                if (state.getPhase() == GamePhase.PLACEMENT) {
                    int remaining = Math.max(0, PLACEMENT_TIMEOUT_TICKS - tickCount);
                    ServerPlayNetworking.send(player, new PlacementCountdownPayload(remaining));
                }
            } else {
                // ponytail: clean up orphaned cube/compass items — game ended while player was offline
                for (Team team : Team.values()) {
                    removeCubeFromInventory(player, team);
                }
                var inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).getItem() == CubeConquestMod.TRACKING_COMPASS) {
                        inv.setItem(i, ItemStack.EMPTY);
                        break;
                    }
                }
                if (player.containerMenu != null && player.containerMenu.getCarried().getItem() == CubeConquestMod.TRACKING_COMPASS) {
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register(CubeConquestGameManagerEvents::onPlayerDisconnect);
        // ponytail: clear stale transient state when server stops — prevents vote/timer bleed across singleplayer world switches
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(
            server -> resetTransientState()
        );

        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            MinecraftServer server = newPlayer.level().getServer();
            CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
            if (state.getPhase() == GamePhase.COMBAT) {
                state.getTeamOf(newPlayer.getUUID()).ifPresent(team -> {
                    if (!playerHasItem(newPlayer, CubeConquestMod.TRACKING_COMPASS)) {
                        newPlayer.getInventory().add(new ItemStack(CubeConquestMod.TRACKING_COMPASS));
                    }
                    // Re-sync cube positions pour que le compas fonctionne immédiatement
                    CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
                    for (Team t : Team.values()) {
                        ServerPlayNetworking.send(newPlayer, new CubePositionPayload(t,
                            java.util.Optional.ofNullable(saved.getCubePos(t)),
                            java.util.Optional.ofNullable(state.getCubeDimension(t))));
                    }
                    // Re-sync team
                    ServerPlayNetworking.send(newPlayer, new PlayerTeamPayload(team));
                });
            }
        });
    }

    // ── Tick handler ──────────────────────────────────────────────────────

    private static void onServerTick(MinecraftServer server) {
        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
        CubeConquestState state = saved.getState();
        // ponytail: sync tickCount from persistence on first tick after server (re)start
        if (!tickCountRestored) {
            tickCountRestored = true;
            if (state.getPhase() != GamePhase.IDLE) {
                tickCount = saved.getTickCount();
            }
        }
        switch (state.getPhase()) {
            case PREPARATION -> handlePreparationTick(server, saved, state);
            case PLACEMENT   -> handlePlacementTick(server, saved, state);
            case COMBAT      -> handleCombatTick(server, saved);
            default          -> {} // IDLE: do nothing
        }
        // ponytail: persist tickCount after each tick so server restart restores correct timer position
        if (state.getPhase() != GamePhase.IDLE) {
            saved.setTickCount(tickCount);
        }
    }

    // ponytail: shared ActionBar drain callback — single definition for both tick handlers (LOW-1)
    private static void drainActionBar(MinecraftServer server) {
        CubeConquestGameManager.drainActionBarCountdown(actionBarCountdown, (uuid, tick) -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.sendSystemMessage(Component.literal("The cube has been passed to you!")
                    .withStyle(ChatFormatting.GOLD), true);
            }
        });
    }

    private static void verifyPlacementState(MinecraftServer server, CubeConquestSavedData saved, CubeConquestState state) {
        for (Team team : Team.values()) {
            BlockPos registeredPos = saved.getCubePos(team);
            if (registeredPos != null) {
                ServerLevel cubeLevel = resolveCubeLevel(server, state, team);
                if (cubeLevel != null
                        && !(cubeLevel.getBlockState(registeredPos).getBlock() instanceof CubeBlock)) {
                    saved.setCubePos(team, null);
                    state.setCubeDimension(team, null);
                    UUID porteurId = state.getPorteur(team);
                    ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
                    if (porteur != null) {
                        Item cubeItem = cubeBlockFor(team).asItem();
                        if (!playerHasItem(porteur, cubeItem)) {
                            porteur.getInventory().add(new ItemStack(cubeItem));
                        }
                        porteur.sendSystemMessage(Component.literal(
                            "[CubeConquest] Your cube disappeared — place it again."));
                        if (state.getPhase() == GamePhase.PLACEMENT && !porteur.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) && !porteur.isCreative()) {
                            porteur.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                net.minecraft.world.effect.MobEffects.SLOWNESS,
                                PLACEMENT_TIMEOUT_TICKS + 20, 127, false, false));
                        }
                    }
                }
            }
        }
        for (Team team : Team.values()) {
            if (saved.getCubePos(team) != null) continue;
            UUID porteurId = state.getPorteur(team);
            if (porteurId == null) continue;
            ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
            if (porteur == null) continue;
            Item cubeItem = cubeBlockFor(team).asItem();
            if (!playerHasItem(porteur, cubeItem)) {
                boolean added = porteur.getInventory().add(new ItemStack(cubeItem));
                if (!added && tickCount % 60 == 0) {
                    porteur.sendSystemMessage(Component.literal(
                        "[CubeConquest] Inventory full — drop something to receive the cube!")
                        .withStyle(ChatFormatting.RED));
                }
            }
            removeExtraCubes(porteur, cubeItem);
        }
    }

    private static void handlePreparationTick(MinecraftServer server, CubeConquestSavedData saved, CubeConquestState state) {
        drainActionBar(server);
        verifyPlacementState(server, saved, state);
        
        tickCount++;
        boolean bothPlaced = saved.getCubePos(Team.RED) != null && saved.getCubePos(Team.BLUE) != null;
        if (bothPlaced && !state.isWaitForCountdown()) {
            tickCount = 0;
            transitionToCombat(server, saved, state);
            return;
        }

        if (tickCount >= state.getPreparationDurationTicks()) {
            tickCount = 0;
            if (bothPlaced) {
                transitionToCombat(server, saved, state);
            } else {
                state.setPhase(GamePhase.PLACEMENT);
                broadcast(server, Component.literal("Place your Cube!").withStyle(ChatFormatting.YELLOW));
                sendTitle(server, Component.literal("Place your Cube!").withStyle(ChatFormatting.YELLOW),
                    Component.literal("You have 3 minutes").withStyle(ChatFormatting.GRAY));
                PlacementCountdownPayload initial = new PlacementCountdownPayload(PLACEMENT_TIMEOUT_TICKS);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, initial);
                }
            }
            return;
        }
    }

    private static void handlePlacementTick(MinecraftServer server, CubeConquestSavedData saved,
                                             CubeConquestState state) {
        drainActionBar(server);
        verifyPlacementState(server, saved, state);
        
        tickCount++;
        if (saved.getCubePos(Team.RED) != null && saved.getCubePos(Team.BLUE) != null) {
            tickCount = 0;
            transitionToCombat(server, saved, state);
            return;
        }
        // Send countdown to all clients every second
        if (tickCount % 20 == 0) {
            int remaining = Math.max(0, PLACEMENT_TIMEOUT_TICKS - tickCount);
            PlacementCountdownPayload payload = new PlacementCountdownPayload(remaining);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, payload);
            }
        }
        // ponytail: symmetric dual-timeout draw — covers non-creative, offline, and mixed cases; no per-porteur online/creative check needed
        if (tickCount >= PLACEMENT_TIMEOUT_TICKS) {
            boolean redFailed = saved.getCubePos(Team.RED) == null && state.getPorteur(Team.RED) != null;
            boolean blueFailed = saved.getCubePos(Team.BLUE) == null && state.getPorteur(Team.BLUE) != null;
            if (redFailed && blueFailed) {
                triggerDraw(server);
                return;
            }
        }
        // Immobilize porteur every tick during PLACEMENT; kill at timeout
        for (Team team : Team.values()) {
            UUID porteurId = state.getPorteur(team);
            if (saved.getCubePos(team) == null && porteurId != null
                    && !timeoutDeaths.contains(porteurId)) {
                ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
                if (porteur != null) {
                    // ponytail: apply Slowness only if not already active — avoids per-tick object allocation
                    if (!porteur.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)) {
                        porteur.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.SLOWNESS,
                            PLACEMENT_TIMEOUT_TICKS + 20,
                            127,
                            false, false));
                    }
                    if (tickCount >= PLACEMENT_TIMEOUT_TICKS) {
                        if (porteur.isCreative()) {
                            // ponytail: re-check phase — other team may have already triggered victory this tick
                            if (state.getPhase() == GamePhase.PLACEMENT) {
                                triggerVictory(server, team.opponent(), state);
                            }
                        } else {
                            timeoutDeaths.add(porteur.getUUID());
                            porteur.kill(porteur.level());
                        }
                    }
                }
            }
        }
    }

    private static void handleCombatTick(MinecraftServer server, CubeConquestSavedData saved) {
        tickCount++;
        if (tickCount < 0) tickCount = 1; // ponytail: overflow guard — ~1240 days at 20 TPS
        if (tickCount % 20 == 0) {
            CubeConquestState state = saved.getState();
            for (Team team : Team.values()) {
                CubePositionPayload payload = new CubePositionPayload(team,
                    Optional.ofNullable(saved.getCubePos(team)),
                    Optional.ofNullable(state.getCubeDimension(team)));
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (state.getTeamOf(player.getUUID()).isPresent()) {
                        ServerPlayNetworking.send(player, payload);
                    }
                }
            }
        }
    }

    // ── Game start / stop (called from command) ───────────────────────────

    // ponytail: avoids duplicating the RED/BLUE ternary in startGame, transferCubeOnDeath, removeCubeFromInventory
    private static net.minecraft.world.level.block.Block cubeBlockFor(Team team) {
        return team == Team.RED ? CubeConquestMod.RED_CUBE_BLOCK : CubeConquestMod.BLUE_CUBE_BLOCK;
    }

    // ponytail: called from stopGame, triggerVictory, triggerDraw — single source of truth
    private static void resetTransientState() {
        tickCount = 0;
        tickCountRestored = false; // ponytail: reset so next server-start (world switch) re-reads persistence
        actionBarCountdown.clear();
        timeoutDeaths.clear();
        drawVoters.clear();
        ffVoteYes.clear();
        // ponytail: ffVoteNo removed — was vestigial (never read by isForfeitPassing)
    }

    public static void startGame(MinecraftServer server) {
        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
        CubeConquestState state = saved.getState();
        if (state.getPhase() != GamePhase.IDLE) {
            throw new IllegalStateException("Game already running");
        }

        // Pre-flight check — both teams must have at least one online member
        for (Team team : Team.values()) {
            long onlineCount = server.getPlayerList().getPlayers().stream()
                .map(Entity::getUUID)
                .filter(id -> state.getPlayers(team).contains(id))
                .count();
            if (onlineCount == 0) {
                throw new IllegalStateException("Cannot start: " + team.displayName() + " team has no online players");
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (state.getTeamOf(player.getUUID()).isPresent()) {
                player.getInventory().clearContent();
            }
        }

        // ponytail: defensive sweep — prior game may have left Slowness if server crashed mid-PLACEMENT
        removeSlownessFromAll(server);

        for (Team team : Team.values()) {
            List<UUID> members = server.getPlayerList().getPlayers().stream()
                .map(Entity::getUUID)
                .filter(id -> state.getPlayers(team).contains(id))
                .collect(Collectors.toList());

            if (members.isEmpty()) continue;

            UUID porteurId = CubeConquestGameManager.pickRandom(members);
            state.setPorteur(team, porteurId);

            ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
            if (porteur != null) {
                // ponytail: clearContent() above guarantees space; add() should never fail here
                if (!porteur.getInventory().add(new ItemStack(cubeBlockFor(team).asItem()))) {
                    CubeConquestMod.LOGGER.error("Failed to give cube to porteur {} — inventory full despite clearContent", porteur.getUUID());
                }
                porteur.sendSystemMessage(
                    Component.literal("You are the cube carrier!").withStyle(ChatFormatting.GOLD));
            }

        }

        resetTransientState();
        state.setPhase(GamePhase.PREPARATION);
        
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule locatorBar false");
        } catch (Exception e) {
            CubeConquestMod.LOGGER.error("Failed to disable locatorBar", e);
        }

        broadcast(server, Component.literal("Game starting! PvP disabled.").withStyle(ChatFormatting.GREEN));
        // Send each player their own team so the compass points at the enemy
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            state.getTeamOf(player.getUUID()).ifPresent(team ->
                ServerPlayNetworking.send(player, new PlayerTeamPayload(team))
            );
        }
    }

    // ponytail: cubes may be in any dimension when overworldOnly is false; resolve per team
    private static void removeCubeBlocksFromWorld(MinecraftServer server) {
        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
        CubeConquestState state = saved.getState();
        for (Team team : Team.values()) {
            BlockPos pos = saved.getCubePos(team);
            if (pos != null) {
                ServerLevel level = resolveCubeLevel(server, state, team);
                if (level != null) {
                    net.minecraft.world.level.block.state.BlockState existing = level.getBlockState(pos);
                    if (existing.getBlock() instanceof CubeBlock) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /**
     * Resolves the ServerLevel for a team's cube. Uses the stored dimension key,
     * falling back to Level.OVERWORLD if null (legacy saves or overworldOnly mode).
     */
    private static ServerLevel resolveCubeLevel(MinecraftServer server, CubeConquestState state, Team team) {
        String dimKey = state.getCubeDimension(team);
        if (dimKey == null) {
            return server.getLevel(Level.OVERWORLD);
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimKey));
        return server.getLevel(key);
    }

    public static void stopGame(MinecraftServer server) {
        // ponytail: remove cube items from all players before world/state reset — prevents item leaking into next game
        removeCubeItemsFromAll(server);
        removeSlownessFromAll(server);
        removeCompassFromAll(server);
        removeCubeBlocksFromWorld(server);
        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
        saved.getState().reset();
        saved.setTickCount(0);
        resetTransientState();
        clearClientCubePositions(server);
        
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule locatorBar true");
        } catch (Exception e) {
            CubeConquestMod.LOGGER.error("Failed to enable locatorBar", e);
        }

        broadcast(server, Component.literal("Game stopped.").withStyle(ChatFormatting.GRAY));
    }

    // ── PvP blocker ───────────────────────────────────────────────────────

    private static InteractionResult onAttack(Player attacker, Level level,
                                               net.minecraft.world.InteractionHand hand,
                                               Entity target,
                                               net.minecraft.world.phys.EntityHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        CubeConquestState state = CubeConquestSavedData.getServerState(serverLevel.getServer()).getState();
        // ponytail: PvP blocked during both PREPARATION and PLACEMENT phases
        GamePhase phase = state.getPhase();
        if ((phase == GamePhase.PREPARATION || phase == GamePhase.PLACEMENT) && target instanceof Player) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    // ── Block break handler ───────────────────────────────────────────────

    private static boolean onBlockBreak(net.minecraft.world.level.LevelAccessor level,
                                        Player player,
                                        BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState blockState,
                                        net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) return true;
        if (!(blockState.getBlock() instanceof CubeBlock cubeBlock)) return true;

        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(serverLevel.getServer());
        CubeConquestState state = saved.getState();
        if (state.getPhase() == GamePhase.PLACEMENT) return false; // ponytail: cube blocks indestructible during PLACEMENT — first-placer must not be penalized
        if (state.getPhase() != GamePhase.COMBAT) return true;

        Team cubeTeam = cubeBlock.getTeam();
        
        // Prevent fake/decorative cubes from triggering victory
        BlockPos officialPos = saved.getCubePos(cubeTeam);
        if (officialPos == null || !officialPos.equals(pos)) {
            return true;
        }

        Optional<Team> playerTeam = state.getTeamOf(player.getUUID());
        if (playerTeam.isEmpty()) {
            player.sendSystemMessage(Component.literal("You are not in the game!").withStyle(ChatFormatting.RED));
            return false;
        }

        if (playerTeam.get() == cubeTeam) {
            player.sendSystemMessage(
                Component.literal("You cannot destroy your own cube!").withStyle(ChatFormatting.RED));
            return false;
        }

        triggerVictory(serverLevel.getServer(), playerTeam.get(), state);
        return false;
    }

    // ── Overworld placement guard ─────────────────────────────────────────

    private static InteractionResult onUseBlock(Player player, Level level,
                                                 net.minecraft.world.InteractionHand hand,
                                                 net.minecraft.world.phys.BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BlockItem blockItem)) return InteractionResult.PASS;
        if (!(blockItem.getBlock() instanceof CubeBlock cubeBlock)) return InteractionResult.PASS;

        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(serverLevel.getServer());
        CubeConquestState state = saved.getState();
        GamePhase phase = state.getPhase();
        if (phase != GamePhase.PREPARATION && phase != GamePhase.PLACEMENT) return InteractionResult.FAIL;

        if (state.isOverworldOnly()
                && !CubeConquestGameManager.isOverworld(serverLevel.dimension().identifier().toString())) {
            player.sendSystemMessage(
                Component.literal("You must place the cube in the Overworld!").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        // Only the porteur can register the cube placement
        Team cubeTeam = cubeBlock.getTeam();
        UUID porteurId = state.getPorteur(cubeTeam);
        if (porteurId == null || !porteurId.equals(player.getUUID())) {
            // ponytail: prevent rogue cube block from triggering break-victory
            return InteractionResult.FAIL;
        }

        // ponytail: first placement wins; block re-placement — PASS would let porteur place untracked blocks
        if (saved.getCubePos(cubeTeam) != null) {
            return InteractionResult.FAIL;
        }

        net.minecraft.world.item.context.BlockPlaceContext ctx = new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(player, hand, hitResult));
        if (!ctx.canPlace()) {
            if (player instanceof ServerPlayer sp) {
                sp.inventoryMenu.sendAllDataToRemote();
            }
            return InteractionResult.FAIL;
        }
        BlockPos placedPos = ctx.getClickedPos();
        saved.setCubePos(cubeTeam, placedPos);
        state.setCubeDimension(cubeTeam, serverLevel.dimension().identifier().toString());
        CubeConquestMod.LOGGER.info("{} cube placed at {} in {}", cubeTeam, placedPos,
            serverLevel.dimension().identifier().toString());
        // ponytail: free porteur from Slowness as soon as cube is placed — transitionToCombat handles the other team's porteur
        ServerPlayer porteur = serverLevel.getServer().getPlayerList().getPlayer(porteurId);
        if (porteur != null) porteur.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);

        return InteractionResult.PASS;
    }

    // ── Death handler — transfer cube ─────────────────────────────────────

    private static boolean onAllowDeath(net.minecraft.world.entity.LivingEntity entity,
                                         net.minecraft.world.damagesource.DamageSource damageSource,
                                         float damageAmount) {
        if (!(entity instanceof ServerPlayer dead)) return true;
        MinecraftServer server = dead.level().getServer();
        if (server == null) return true;

        CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();

        // Prevent compass drop on death during combat by removing it beforehand
        if (state.getPhase() == GamePhase.COMBAT) {
            for (int i = 0; i < dead.getInventory().getContainerSize(); i++) {
                if (dead.getInventory().getItem(i).getItem() == CubeConquestMod.TRACKING_COMPASS) {
                    dead.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }

        if (state.getPhase() != GamePhase.PREPARATION && state.getPhase() != GamePhase.PLACEMENT) return true;

        // Timeout death check — porteur who didn't place in time: team loses immediately
        for (Team team : Team.values()) {
            if (dead.getUUID().equals(state.getPorteur(team)) && timeoutDeaths.remove(dead.getUUID())) {
                // Team loses — opponent wins, no cube transfer
                triggerVictory(server, team.opponent(), state);
                return true;
            }
        }

        for (Team team : Team.values()) {
            if (dead.getUUID().equals(state.getPorteur(team))) {
                if (CubeConquestSavedData.getServerState(server).getCubePos(team) != null) return true; // ponytail: cube already placed — porteur death is irrelevant
                if (state.getPhase() == GamePhase.PLACEMENT) {
                    // Porteur dies during PLACEMENT → team loses immediately
                    removeCubeFromInventory(dead, team);
                    triggerVictory(server, team.opponent(), state);
                } else {
                    // PREPARATION: transfer to another team member
                    transferCubeOnDeath(server, state, team, dead);
                }
                return true;
            }
        }
        return true;
    }

    // ponytail: no break — sweep all slots in case of duplicates (phantom + vanilla-rejection race)
    private static void removeCubeFromInventory(ServerPlayer player, Team team) {
        net.minecraft.world.item.Item cubeItem = cubeBlockFor(team).asItem();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() == cubeItem) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (player.containerMenu != null && player.containerMenu.getCarried().getItem() == cubeItem) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    private static void removeSlownessFromAll(MinecraftServer server) {
        // ponytail: sweep all online players — porteurs may change during transfer races
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
        }
    }

    // ponytail: one compass per player — break after first found
    private static void removeCompassFromAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).getItem() == CubeConquestMod.TRACKING_COMPASS) {
                    inv.setItem(i, ItemStack.EMPTY);
                    break;
                }
            }
            if (player.containerMenu != null && player.containerMenu.getCarried().getItem() == CubeConquestMod.TRACKING_COMPASS) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
            }
        }
    }

    // ponytail: sweep all players — porteur-only cleanup misses dropped/picked-up cube items
    private static void removeCubeItemsFromAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (Team team : Team.values()) {
                removeCubeFromInventory(player, team);
            }
        }
    }

    private static void transferCubeOnDeath(MinecraftServer server, CubeConquestState state,
                                             Team team, ServerPlayer deadPorteur) {
        // Remove only the cube item to prevent duplication when player reconnects mid-game
        removeCubeFromInventory(deadPorteur, team);

        List<UUID> candidates = server.getPlayerList().getPlayers().stream()
            .map(Entity::getUUID)
            .filter(id -> state.getPlayers(team).contains(id) && !id.equals(deadPorteur.getUUID()))
            .collect(Collectors.toList());

        Optional<UUID> next = CubeConquestGameManager.pickReplacementOpt(deadPorteur.getUUID(), candidates);
        if (next.isEmpty()) {
            triggerVictory(server, team.opponent(), state);
            return;
        }

        state.setPorteur(team, next.get());
        ServerPlayer newPorteur = server.getPlayerList().getPlayer(next.get());
        if (newPorteur != null) {
            newPorteur.getInventory().add(new ItemStack(cubeBlockFor(team).asItem()));
            actionBarCountdown.put(next.get(), 100);
            // ponytail: immobilize new porteur immediately — handlePlacementTick would cover next tick but 1-tick gap exists
            if (state.getPhase() == GamePhase.PLACEMENT && !newPorteur.isCreative()) {
                newPorteur.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS,
                    PLACEMENT_TIMEOUT_TICKS + 20, 127, false, false));
            }
        }
    }

    // ── Transitions ───────────────────────────────────────────────────────

    private static void transitionToCombat(MinecraftServer server, CubeConquestSavedData saved,
                                            CubeConquestState state) {
        tickCount = 0; // ponytail: reset so first combat broadcast fires at tick 20, not PREPARATION_TICKS
        // ponytail: remove Slowness from porteurs before COMBAT — effect would persist and immobilize them
        for (Team team : Team.values()) {
            UUID porteurId = state.getPorteur(team);
            if (porteurId != null) {
                ServerPlayer porteur = server.getPlayerList().getPlayer(porteurId);
                if (porteur != null) porteur.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
            }
        }
        state.setPhase(GamePhase.COMBAT);
        actionBarCountdown.clear(); // LOW-2: orphaned entries never drained in COMBAT; clear on transition
        broadcast(server, Component.literal("COMBAT! Destroy the enemy cube!").withStyle(ChatFormatting.RED));
        // ponytail: issue compasses at COMBAT transition — cube positions exist here, not during PREPARATION
        for (Team team : Team.values()) {
            for (UUID id : state.getPlayers(team)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) p.getInventory().add(new ItemStack(CubeConquestMod.TRACKING_COMPASS));
            }
        }
        // ponytail: send positions immediately so compass/HUD works from tick 1 of COMBAT (not tick 20)
        for (Team team : Team.values()) {
            CubePositionPayload payload = new CubePositionPayload(team,
                Optional.ofNullable(saved.getCubePos(team)),
                Optional.ofNullable(state.getCubeDimension(team)));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (state.getTeamOf(player.getUUID()).isPresent()) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
    }

    public static void triggerVictory(MinecraftServer server, Team winner, CubeConquestState state) {
        if (state.getPhase() == GamePhase.IDLE) return;
        // ponytail: mirror stopGame cube-item cleanup — handles creative porteur who never placed
        removeCubeItemsFromAll(server);
        broadcast(server, Component.literal(winner.displayName() + " Team wins!").withStyle(
            winner == Team.RED ? ChatFormatting.RED : ChatFormatting.BLUE));
        sendTitle(server,
            Component.literal(winner.displayName() + " Team wins!")
                .withStyle(winner == Team.RED ? ChatFormatting.RED : ChatFormatting.BLUE),
            Component.literal("GG").withStyle(ChatFormatting.YELLOW));
        removeSlownessFromAll(server);
        removeCubeBlocksFromWorld(server);
        removeCompassFromAll(server);
        CubeConquestSavedData.getServerState(server).setTickCount(0);
        state.resetGame();
        resetTransientState();
        clearClientCubePositions(server);
        
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule locatorBar true");
        } catch (Exception e) {
            CubeConquestMod.LOGGER.error("Failed to enable locatorBar", e);
        }
    }

    public static void triggerDraw(MinecraftServer server) {
        // ponytail: mirror stopGame cube-item cleanup — handles creative porteur who never placed
        CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
        if (state.getPhase() == GamePhase.IDLE) return;
        removeCubeItemsFromAll(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.literal("Draw! Both teams agreed.")
                .withStyle(ChatFormatting.YELLOW));
        }
        sendTitle(server,
            Component.literal("Draw").withStyle(ChatFormatting.YELLOW),
            Component.literal("Both teams agreed").withStyle(ChatFormatting.GRAY));
        CubeConquestSavedData saved = CubeConquestSavedData.getServerState(server);
        removeSlownessFromAll(server);
        removeCubeBlocksFromWorld(server);
        removeCompassFromAll(server);
        saved.setTickCount(0);
        saved.getState().resetGame();
        resetTransientState();
        clearClientCubePositions(server);
        
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule locatorBar true");
        } catch (Exception e) {
            CubeConquestMod.LOGGER.error("Failed to enable locatorBar", e);
        }
    }

    // broadcast empty positions + countdown reset so clients clear compass HUD and placement timer
    private static void clearClientCubePositions(MinecraftServer server) {
        PlacementCountdownPayload resetCountdown = new PlacementCountdownPayload(-1);
        for (Team team : Team.values()) {
            CubePositionPayload posPayload = new CubePositionPayload(team, Optional.empty(), Optional.empty());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, posPayload);
            }
        }
        // ponytail: send once after both position clears — was sent inside team loop (×2 per player)
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, resetCountdown);
        }
    }

    private static void broadcast(MinecraftServer server, Component text) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(text);
        }
    }

    // ponytail: sends title + subtitle to every online player; 10/60/10 ticks = standard feel
    private static void sendTitle(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    // ponytail: treat disconnect like death during PREP/PLACEMENT — reuses existing transfer path
    private static void onPlayerDisconnect(net.minecraft.server.network.ServerGamePacketListenerImpl handler, MinecraftServer server) {
        ServerPlayer player = handler.player;
        if (player == null) return;

        CubeConquestState state = CubeConquestSavedData.getServerState(server).getState();
        GamePhase phase = state.getPhase();

        // PREP/PLACEMENT porteur handling
        if (phase == GamePhase.PREPARATION || phase == GamePhase.PLACEMENT) {
            for (Team team : Team.values()) {
                if (player.getUUID().equals(state.getPorteur(team))) {
                    if (CubeConquestSavedData.getServerState(server).getCubePos(team) != null) return; // ponytail: cube already placed — disconnect is irrelevant
                    transferCubeOnDeath(server, state, team, player);
                    return; // a player is porteur for at most one team
                }
            }
        }

        // ponytail: remove votes on COMBAT disconnect — prevents stale "already voted" on reconnect
        if (phase == GamePhase.COMBAT) {
            removeDrawVote(player.getUUID());
            state.getTeamOf(player.getUUID()).ifPresent(team -> removeFfVote(team, player.getUUID()));
        }
    }
}
