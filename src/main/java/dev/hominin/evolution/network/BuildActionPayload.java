package dev.hominin.evolution.network;

import dev.hominin.evolution.HomininEvolutionMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Something done with building: open the menu, mark a blueprint out, put a plan away, decide what a finished
 * build is for, or set a block straight into a ghost.
 */
public record BuildActionPayload(int action, String blueprint, BlockPos pos, int facing, int site)
        implements CustomPacketPayload {
    public static final int OPEN = 0;
    public static final int PLAN = 1;
    public static final int ABANDON = 2;
    public static final int DECIDE = 3;
    public static final int FILL = 4;
    /** A build one of the band suggested: yes, build it there. */
    public static final int CONFIRM = 5;

    public static final CustomPacketPayload.Type<BuildActionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(HomininEvolutionMod.MODID, "build_action"));

    public static final StreamCodec<ByteBuf, BuildActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BuildActionPayload::action,
            ByteBufCodecs.STRING_UTF8, BuildActionPayload::blueprint,
            BlockPos.STREAM_CODEC, BuildActionPayload::pos,
            ByteBufCodecs.VAR_INT, BuildActionPayload::facing,
            ByteBufCodecs.VAR_INT, BuildActionPayload::site,
            BuildActionPayload::new);

    public static BuildActionPayload simple(int action, int site) {
        return new BuildActionPayload(action, "", BlockPos.ZERO, 0, site);
    }

    public static void handle(BuildActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> dev.hominin.evolution.build.Building.handle(player, payload));
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
