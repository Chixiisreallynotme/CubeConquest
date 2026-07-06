package fr.chixi.cubeconquest.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public final class BlockIds {
    public static final ResourceKey<Block> RED_CUBE = key("red_cube");
    public static final ResourceKey<Block> BLUE_CUBE = key("blue_cube");

    private static ResourceKey<Block> key(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("cubeconquest", name));
    }

    private BlockIds() {}
}
