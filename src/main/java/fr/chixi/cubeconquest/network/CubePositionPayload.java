package fr.chixi.cubeconquest.network;

import fr.chixi.cubeconquest.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record CubePositionPayload(Team team, Optional<BlockPos> pos, Optional<String> dimension) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CubePositionPayload> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("cubeconquest", "cube_position"));

    private static final StreamCodec<FriendlyByteBuf, Team> TEAM_CODEC =
        StreamCodec.of(
            (buf, t) -> buf.writeUtf(t.name()),
            buf -> {
                try {
                    return Team.valueOf(buf.readUtf());
                } catch (IllegalArgumentException ex) {
                    // ponytail: S2C-only; safety net for mismatched packet
                    return Team.RED;
                }
            }
        );

    private static final StreamCodec<FriendlyByteBuf, Optional<BlockPos>> OPT_POS_CODEC =
        StreamCodec.of(
            (buf, opt) -> {
                buf.writeBoolean(opt.isPresent());
                opt.ifPresent(buf::writeBlockPos);
            },
            buf -> buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty()
        );

    private static final StreamCodec<FriendlyByteBuf, Optional<String>> OPT_DIM_CODEC =
        StreamCodec.of(
            (buf, opt) -> {
                buf.writeBoolean(opt.isPresent());
                opt.ifPresent(buf::writeUtf);
            },
            buf -> buf.readBoolean() ? Optional.of(buf.readUtf()) : Optional.empty()
        );

    public static final StreamCodec<FriendlyByteBuf, CubePositionPayload> CODEC =
        StreamCodec.composite(
            TEAM_CODEC, CubePositionPayload::team,
            OPT_POS_CODEC, CubePositionPayload::pos,
            OPT_DIM_CODEC, CubePositionPayload::dimension,
            CubePositionPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
