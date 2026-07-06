package fr.chixi.cubeconquest.network;

import fr.chixi.cubeconquest.Team;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlayerTeamPayload(Team team) implements CustomPacketPayload {
    public static final Type<PlayerTeamPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("cubeconquest", "player_team"));
    public static final StreamCodec<FriendlyByteBuf, PlayerTeamPayload> CODEC =
        StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.team().name()),
            buf -> {
                try {
                    return new PlayerTeamPayload(Team.valueOf(buf.readUtf()));
                } catch (IllegalArgumentException ex) {
                    // ponytail: S2C-only so server is trusted; catch is safety net for malformed packets
                    return new PlayerTeamPayload(Team.RED);
                }
            }
        );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
