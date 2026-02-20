package com.ogtenzohd.cclogistics.blocks.custom.freight_depot.network;

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

public record UpdateFreightDepotPacket(BlockPos pos, String colonyName, String cityTarget) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<UpdateFreightDepotPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MODID, "update_freight_depot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateFreightDepotPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, UpdateFreightDepotPacket::pos,
        ByteBufCodecs.STRING_UTF8, UpdateFreightDepotPacket::colonyName,
        ByteBufCodecs.STRING_UTF8, UpdateFreightDepotPacket::cityTarget,
        UpdateFreightDepotPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final UpdateFreightDepotPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            LOGGER.info("[Network] Received UpdateFreightDepotPacket for Pos: " + payload.pos);
            LOGGER.info("   -> Colony: " + payload.colonyName + ", City: " + payload.cityTarget);
            
            if (player.level().getBlockEntity(payload.pos) instanceof FreightDepotBlockEntity depot) {
                depot.setColonyName(payload.colonyName);
                depot.setCityTarget(payload.cityTarget);
                depot.setChanged();
                player.level().sendBlockUpdated(payload.pos, depot.getBlockState(), depot.getBlockState(), 3);
                LOGGER.info("[Network] Successfully updated Freight Depot at " + payload.pos);
            } else {
                LOGGER.warn("[Network] Failed to find FreightDepotBlockEntity at " + payload.pos);
            }
        });
    }
}
