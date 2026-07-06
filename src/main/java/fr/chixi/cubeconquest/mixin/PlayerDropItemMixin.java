package fr.chixi.cubeconquest.mixin;

import fr.chixi.cubeconquest.CubeConquestSavedData;
import fr.chixi.cubeconquest.CubeConquestState;
import fr.chixi.cubeconquest.GamePhase;
import fr.chixi.cubeconquest.block.CubeBlock;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents players from dropping CubeBlock items (via Q or any drop mechanism)
 * while a CubeConquest game is running, to avoid cube duplication and griefing.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDropItemMixin {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void cubeconquest$preventCubeDrop(ItemStack itemStack, boolean throwRandomly, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayer serverPlayer = (ServerPlayer) (Object) this;
        if (serverPlayer.level().getServer() == null)
            return;

        // Block CubeBlock and TrackingCompass items
        boolean isCube = itemStack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof CubeBlock;
        boolean isCompass = itemStack.getItem() instanceof fr.chixi.cubeconquest.item.TrackingCompassItem;
        if (!isCube && !isCompass)
            return;

        CubeConquestState state = CubeConquestSavedData
                .getServerState(serverPlayer.level().getServer()).getState();
        if (state.getPhase() != GamePhase.IDLE) {
            // Game is active — cancel the drop entirely and return item to inventory
            serverPlayer.getInventory().add(itemStack);
            serverPlayer.inventoryMenu.sendAllDataToRemote();
            cir.setReturnValue(null);
        }
    }
}
