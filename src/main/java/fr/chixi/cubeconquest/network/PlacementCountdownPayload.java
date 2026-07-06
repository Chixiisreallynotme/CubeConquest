package fr.chixi.cubeconquest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlacementCountdownPayload(int ticksRemaining) implements CustomPacketPayload {
    public static final Type<PlacementCountdownPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("cubeconquest", "placement_countdown"));
    public static final StreamCodec<FriendlyByteBuf, PlacementCountdownPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeInt(p.ticksRemaining()),
            buf -> new PlacementCountdownPayload(buf.readInt())
        );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
