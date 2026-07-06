package fr.chixi.cubeconquest;

import fr.chixi.cubeconquest.block.BlockIds;
import fr.chixi.cubeconquest.block.BlockItemIds;
import fr.chixi.cubeconquest.block.CubeBlock;
import fr.chixi.cubeconquest.command.CubeConquestCommand;
import fr.chixi.cubeconquest.item.TrackingCompassItem;
import fr.chixi.cubeconquest.network.CubePositionPayload;
import fr.chixi.cubeconquest.network.PlacementCountdownPayload;
import fr.chixi.cubeconquest.network.PlayerTeamPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CubeConquestMod implements ModInitializer {

    public static final String MOD_ID = "cubeconquest";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static CubeBlock RED_CUBE_BLOCK;
    public static CubeBlock BLUE_CUBE_BLOCK;
    public static TrackingCompassItem TRACKING_COMPASS;

    @Override
    public void onInitialize() {
        // Register blocks
        RED_CUBE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            BlockIds.RED_CUBE,
            new CubeBlock(Team.RED, BlockBehaviour.Properties.of().setId(BlockIds.RED_CUBE).mapColor(MapColor.COLOR_RED).strength(50f, 1200f).pushReaction(PushReaction.BLOCK))
        );
        BLUE_CUBE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            BlockIds.BLUE_CUBE,
            new CubeBlock(Team.BLUE, BlockBehaviour.Properties.of().setId(BlockIds.BLUE_CUBE).mapColor(MapColor.COLOR_BLUE).strength(50f, 1200f).pushReaction(PushReaction.BLOCK))
        );

        // Register block items (linked separately per MC 26.2)
        Registry.register(BuiltInRegistries.ITEM, BlockItemIds.RED_CUBE,
            new BlockItem(RED_CUBE_BLOCK, new Item.Properties().setId(BlockItemIds.RED_CUBE).useBlockDescriptionPrefix()));
        Registry.register(BuiltInRegistries.ITEM, BlockItemIds.BLUE_CUBE,
            new BlockItem(BLUE_CUBE_BLOCK, new Item.Properties().setId(BlockItemIds.BLUE_CUBE).useBlockDescriptionPrefix()));

        // Tracking compass
        ResourceKey<Item> compassKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, "tracking_compass"));
        TRACKING_COMPASS = Registry.register(
            BuiltInRegistries.ITEM,
            compassKey,
            new TrackingCompassItem(new Item.Properties().setId(compassKey).stacksTo(1))
        );

        // Register S2C payload types
        PayloadTypeRegistry.clientboundPlay().register(CubePositionPayload.TYPE, CubePositionPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlacementCountdownPayload.TYPE, PlacementCountdownPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayerTeamPayload.TYPE, PlayerTeamPayload.CODEC);

        // Register game manager events and commands
        CubeConquestGameManagerEvents.register();
        CubeConquestCommand.register();

        LOGGER.info("CubeConquest loaded.");
    }
}
