package fr.chixi.cubeconquest.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CubeConquestHud implements HudElement {

    private static final Identifier HUD_ID =
        Identifier.fromNamespaceAndPath("cubeconquest", "game_hud");

    private CubeConquestHud() {}

    public static void register() {
        HudElementRegistry.addLast(HUD_ID, new CubeConquestHud());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        int y = 5;
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 50;

        // Placement countdown
        int countdown = TrackingCompassClientHandler.getPlacementTicksRemaining();
        if (countdown >= 0) {
            graphics.text(mc.font,
                Component.translatable("cubeconquest.hud.placement_countdown", countdown / 20),
                x, y + 10, 0xFFFF55, true);
        }

        // ponytail: team indicator during COMBAT — clientTeam set by PlayerTeamPayload, cleared on game end
        fr.chixi.cubeconquest.Team team = TrackingCompassClientHandler.getClientTeam();
        if (team != null && countdown < 0) {
            int color = team == fr.chixi.cubeconquest.Team.RED ? 0xFF5555 : 0x5555FF;
            graphics.text(mc.font,
                Component.translatable("cubeconquest.hud.team", team.displayName()),
                x, y + 10, color, true);

            boolean holdingCompass = mc.player.getMainHandItem().getItem() == fr.chixi.cubeconquest.CubeConquestMod.TRACKING_COMPASS ||
                                     mc.player.getOffhandItem().getItem() == fr.chixi.cubeconquest.CubeConquestMod.TRACKING_COMPASS;

            if (holdingCompass) {
                fr.chixi.cubeconquest.Team enemyTeam = team.opponent();
                String enemyDimension = TrackingCompassClientHandler.getDimension(enemyTeam).orElse(null);
                String playerDimension = mc.level.dimension().identifier().toString();

                if (enemyDimension != null) {
                    int hudY = mc.getWindow().getGuiScaledHeight() - 59;
                    String friendlyDim = enemyDimension.equals("minecraft:overworld") ? "Overworld" :
                                         enemyDimension.equals("minecraft:the_nether") ? "The Nether" :
                                         enemyDimension.equals("minecraft:the_end") ? "The End" :
                                         enemyDimension;
                                         
                    if (!enemyDimension.equals(playerDimension)) {
                        Component combinedText = Component.literal("Cube adverse : " + friendlyDim + " — Dimension différente");
                        int textWidth = mc.font.width(combinedText);
                        graphics.text(mc.font, combinedText, (mc.getWindow().getGuiScaledWidth() - textWidth) / 2, hudY, 0xFFFFFF, true);
                    } else {
                        net.minecraft.core.BlockPos enemyPos = TrackingCompassClientHandler.getPosition(enemyTeam).orElse(null);
                        if (enemyPos != null) {
                            double dist = Math.sqrt(mc.player.distanceToSqr(enemyPos.getX() + 0.5, enemyPos.getY() + 0.5, enemyPos.getZ() + 0.5));
                            Component combinedText = Component.literal("Cube adverse : " + friendlyDim + " — " + Math.round(dist) + " blocs");
                            int textWidth = mc.font.width(combinedText);
                            graphics.text(mc.font, combinedText, (mc.getWindow().getGuiScaledWidth() - textWidth) / 2, hudY, 0xFFFFFF, true);
                        }
                    }
                }
            }
        }
    }
}
