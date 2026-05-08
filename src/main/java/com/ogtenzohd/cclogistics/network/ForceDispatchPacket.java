package com.ogtenzohd.cclogistics.network;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.util.LogisticsRequestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collections;
import java.util.HashSet;

public record ForceDispatchPacket(BlockPos depotPos, String trackingId) implements CustomPacketPayload {

    public static final Type<ForceDispatchPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cclogistics", "force_dispatch_packet"));

    public static final StreamCodec<FriendlyByteBuf, ForceDispatchPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.depotPos());
                buf.writeUtf(packet.trackingId());
            },
            buf -> new ForceDispatchPacket(buf.readBlockPos(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ForceDispatchPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            BlockEntity be = level.getBlockEntity(packet.depotPos());

            if (be instanceof FreightDepotBlockEntity depotBE) {
                IColony colony = IColonyManager.getInstance().getIColony(level, packet.depotPos());
                if (colony == null) return;

                if (colony.getRequestManager() instanceof IStandardRequestManager stdManager) {
                    var requestStore = stdManager.getRequestIdentitiesDataStore();

                    for (IRequest<?> req : requestStore.getIdentities().values()) {
                        if (req.getId() != null && req.getId().toString().equals(packet.trackingId())) {

                            LogisticsRequestHelper.processRequests(
                                    colony,
                                    depotBE.getStockTicker(),
                                    null,
                                    new HashSet<>(),
                                    new HashSet<>(Collections.singletonList(req.getId().toString())),
                                    r -> depotBE.getColonyName(),

                                    msg -> depotBE.addIncomingLog(msg, 100),
                                    stack -> {},
                                    (trackId, itemName, amount, status, overrideMsg) -> {
                                        com.minecolonies.api.colony.buildings.IBuilding b = colony.getServerBuildingManager().getBuilding(packet.depotPos());
                                        if (b != null) {
                                            com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule mod = b.getModule(com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule.class);
                                            if (mod != null) mod.updateRequest(trackId, itemName, amount, status, overrideMsg, true);
                                        }
                                    },
                                    null
                            );

                            break;
                        }
                    }
                }
            }
        });
    }
}