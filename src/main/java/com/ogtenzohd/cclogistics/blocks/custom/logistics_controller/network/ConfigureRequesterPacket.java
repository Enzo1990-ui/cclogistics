package com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.network;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlockEntity;
import com.ogtenzohd.cclogistics.util.BuildingRoutingEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record ConfigureRequesterPacket(BlockPos pos, List<BuildingRoutingEntry> packages) implements CustomPacketPayload {

    public static final Type<ConfigureRequesterPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MODID, "configure_requester"));

    public static final StreamCodec<ByteBuf, ConfigureRequesterPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ConfigureRequesterPacket::pos,
        BuildingRoutingEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), ConfigureRequesterPacket::packages,
        ConfigureRequesterPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final ConfigureRequesterPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.level().getBlockEntity(payload.pos) instanceof LogisticsControllerBlockEntity be) {
                be.setPackages(payload.packages);
                be.setChanged();
                player.level().sendBlockUpdated(payload.pos, be.getBlockState(), be.getBlockState(), 3);
            }
        });
    }
}