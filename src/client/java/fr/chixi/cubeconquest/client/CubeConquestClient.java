package fr.chixi.cubeconquest.client;

import fr.chixi.cubeconquest.network.CubePositionPayload;
import fr.chixi.cubeconquest.network.PlacementCountdownPayload;
import fr.chixi.cubeconquest.network.PlayerTeamPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CubeConquestClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register S2C packet receiver
        ClientPlayNetworking.registerGlobalReceiver(CubePositionPayload.TYPE,
            (payload, context) -> {
                context.client().execute(() -> {
                    TrackingCompassClientHandler.updatePosition(payload.team(), payload.pos());
                    TrackingCompassClientHandler.updateDimension(payload.team(), payload.dimension());
                });
            });

        ClientPlayNetworking.registerGlobalReceiver(PlacementCountdownPayload.TYPE,
            (payload, context) -> context.client().execute(() -> {
                TrackingCompassClientHandler.updatePlacementCountdown(payload.ticksRemaining());
                if (payload.ticksRemaining() == -1) {
                    TrackingCompassClientHandler.clearTeam(); // ponytail: game ended — clear stale team assignment
                }
            }));

        ClientPlayNetworking.registerGlobalReceiver(PlayerTeamPayload.TYPE,
            (payload, context) -> context.client().execute(
                () -> TrackingCompassClientHandler.updateClientTeam(payload.team())
            ));

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
            client -> TrackingCompassClientHandler.clientTick()
        );

        // ponytail: clear all client state on server disconnect — prevents stale HUD across sessions
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            client.execute(TrackingCompassClientHandler::clear)
        );

        // Register custom item model property for tracking compass needle
        TrackingCompassPropertyHandler.register();

        // Register HUD
        CubeConquestHud.register();
    }
}
