package com.ogtenzohd.cclogistics.colony.network;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public record UpdateLogisticsModulePacket(BlockPos pos, String colonyName, String cityTarget) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<UpdateLogisticsModulePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MODID, "update_logistics_module"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateLogisticsModulePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, UpdateLogisticsModulePacket::pos,
        ByteBufCodecs.STRING_UTF8, UpdateLogisticsModulePacket::colonyName,
        ByteBufCodecs.STRING_UTF8, UpdateLogisticsModulePacket::cityTarget,
        UpdateLogisticsModulePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final UpdateLogisticsModulePacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            LOGGER.info("[Network] Received UpdateLogisticsModulePacket for Pos: " + payload.pos);
            LOGGER.info("   -> Colony: " + payload.colonyName + ", City: " + payload.cityTarget);

            if (player.level().getBlockEntity(payload.pos) instanceof FreightDepotBlockEntity depot) {
                depot.setColonyName(payload.colonyName);
                depot.setCityTarget(payload.cityTarget);
                // No packages list to update
                depot.setChanged();
                player.level().sendBlockUpdated(payload.pos, depot.getBlockState(), depot.getBlockState(), 3);
                LOGGER.info("[Network] Updated Freight Depot via Module Packet.");
            } else {
                LOGGER.warn("[Network] Failed to find FreightDepotBlockEntity at " + payload.pos);
            }
        });
    }
}
