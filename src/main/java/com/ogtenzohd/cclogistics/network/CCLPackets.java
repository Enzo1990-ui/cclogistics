package com.ogtenzohd.cclogistics.network;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.network.UpdateFreightDepotPacket;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.network.ConfigureRequesterPacket;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.network.SyncBuildingsPacket;
import com.ogtenzohd.cclogistics.colony.network.UpdateLogisticsModulePacket; // Import
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class CCLPackets {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(CreateColonyLogistics.MODID).versioned("1.0.0");

        registrar.playBidirectional(
            UpdateFreightDepotPacket.TYPE,
            UpdateFreightDepotPacket.STREAM_CODEC,
            UpdateFreightDepotPacket::handle
        );

        registrar.playBidirectional(
            ConfigureRequesterPacket.TYPE,
            ConfigureRequesterPacket.STREAM_CODEC,
            ConfigureRequesterPacket::handle
        );

        registrar.playToClient(
            SyncBuildingsPacket.TYPE,
            SyncBuildingsPacket.STREAM_CODEC,
            SyncBuildingsPacket::handle
        );
		
        registrar.playBidirectional(
            UpdateLogisticsModulePacket.TYPE,
            UpdateLogisticsModulePacket.STREAM_CODEC,
            UpdateLogisticsModulePacket::handle
        );
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(CustomPacketPayload payload, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}