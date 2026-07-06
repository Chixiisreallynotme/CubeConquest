package fr.chixi.cubeconquest.client;

import com.mojang.serialization.MapCodec;
import fr.chixi.cubeconquest.Team;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Custom RangeSelectItemModelProperty that computes the angle from the player
 * toward the enemy cube position. Returns [0.0, 1.0] representing a full rotation.
 */
public final class TrackingCompassPropertyHandler implements RangeSelectItemModelProperty {

    public static final TrackingCompassPropertyHandler INSTANCE = new TrackingCompassPropertyHandler();
    public static final MapCodec<TrackingCompassPropertyHandler> MAP_CODEC =
        MapCodec.unit(INSTANCE);

    public static final Identifier ID =
        Identifier.fromNamespaceAndPath("cubeconquest", "compass_angle");

    private TrackingCompassPropertyHandler() {}

    /**
     * Register this property into the vanilla ID_MAPPER so the item JSON can reference it.
     * Must be called during client initialization.
     */
    public static void register() {
        // ponytail: access widener exposes ID_MAPPER; put() is public
        RangeSelectItemModelProperties.ID_MAPPER.put(ID, MAP_CODEC);
    }

    // ponytail: called on render thread; TrackingCompassClientHandler reads are safe here
    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level,
                     @Nullable ItemOwner owner, int seed) {
        if (owner == null || level == null) return 0f;

        // ponytail: use stored clientTeam to point at the enemy; fallback if team unknown
        Team myTeam = TrackingCompassClientHandler.getClientTeam();
        Team enemyTeam = myTeam != null ? myTeam.opponent() : Team.BLUE;

        String enemyDimension = TrackingCompassClientHandler.getDimension(enemyTeam).orElse(null);
        String playerDimension = level.dimension().identifier().toString();
        
        if (enemyDimension == null || !enemyDimension.equals(playerDimension)) {
            return 0f;
        }

        BlockPos target = TrackingCompassClientHandler.getPosition(enemyTeam).orElse(null);
        // ponytail: 0f for no-target is indistinguishable from target-directly-ahead; item model treats 0 as neutral/resting
        if (target == null) return 0f;

        Vec3 pos = owner.position();
        double dx = target.getX() + 0.5 - pos.x;
        double dz = target.getZ() + 0.5 - pos.z;
        if (dx == 0 && dz == 0) return 0f;

        // ponytail: MC yaw: south=0, CW. atan2(-dx, dz) gives exactly MC yaw.
        double targetAngleDeg = Math.toDegrees(Math.atan2(-dx, dz));
        double yaw = owner.getVisualRotationYInDegrees();
        // ponytail: yaw - targetAngleDeg + 180 fixes both X and Y inversion
        double relAngle = ((yaw - targetAngleDeg + 180) % 360 + 360) % 360;
        return (float) (relAngle / 360.0);
    }

    @Override
    public MapCodec<TrackingCompassPropertyHandler> type() {
        return MAP_CODEC;
    }
}
