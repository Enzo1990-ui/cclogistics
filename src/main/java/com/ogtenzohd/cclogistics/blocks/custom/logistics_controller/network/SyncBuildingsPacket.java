package com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.network;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SyncBuildingsPacket(BlockPos pos, List<String> buildings) implements CustomPacketPayload {

    public static final Type<SyncBuildingsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MODID, "sync_buildings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBuildingsPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SyncBuildingsPacket::pos,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), SyncBuildingsPacket::buildings,
            SyncBuildingsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncBuildingsPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.isClientSide) {
                BlockEntity be = level.getBlockEntity(payload.pos());
                if (be instanceof LogisticsControllerBlockEntity controller) {
                    controller.setClientAvailableBuildings(payload.buildings());
                }
            }
        });
    }
}