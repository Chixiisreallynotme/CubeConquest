package fr.chixi.cubeconquest.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public final class BlockItemIds {
    public static final ResourceKey<Item> RED_CUBE = key("red_cube");
    public static final ResourceKey<Item> BLUE_CUBE = key("blue_cube");

    private static ResourceKey<Item> key(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("cubeconquest", name));
    }

    private BlockItemIds() {}
}
