package com.ogtenzohd.cclogistics.network;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CancelFreightRequestPacket(BlockPos depotPos, String trackingId) implements CustomPacketPayload {

    public static final Type<CancelFreightRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cclogistics", "cancel_freight_request"));
    public static final StreamCodec<FriendlyByteBuf, CancelFreightRequestPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CancelFreightRequestPacket::depotPos,
            ByteBufCodecs.STRING_UTF8, CancelFreightRequestPacket::trackingId,
            CancelFreightRequestPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final CancelFreightRequestPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Level level = player.level();
                IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, payload.depotPos());

                if (colony != null) {
                    IBuilding building = colony.getServerBuildingManager().getBuilding(payload.depotPos());
                    if (building != null) {
                        FreightTrackerModule module = building.getModule(FreightTrackerModule.class);
                        if (module != null) {
                            module.removeRequest(payload.trackingId());
                        }
                    }
                }
            }
        });
    }
}