package com.ogtenzohd.cclogistics.colony.network;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.IColony;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public record UpdateLogisticsModulePacket(BlockPos pos, String colonyName, String cityTarget, int stockTarget, boolean instantDispatch) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<UpdateLogisticsModulePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MODID, "update_logistics_module"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateLogisticsModulePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateLogisticsModulePacket::pos,
            ByteBufCodecs.STRING_UTF8, UpdateLogisticsModulePacket::colonyName,
            ByteBufCodecs.STRING_UTF8, UpdateLogisticsModulePacket::cityTarget,
            ByteBufCodecs.INT, UpdateLogisticsModulePacket::stockTarget,
            ByteBufCodecs.BOOL, UpdateLogisticsModulePacket::instantDispatch,
            UpdateLogisticsModulePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final UpdateLogisticsModulePacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) {
                LOGGER.info("[Network] Received UpdateLogisticsModulePacket for Pos: " + payload.pos);
                LOGGER.info("   -> Colony: " + payload.colonyName + ", City: " + payload.cityTarget + ", Max Stock: " + payload.stockTarget);
            }
            if (player.level().getBlockEntity(payload.pos) instanceof FreightDepotBlockEntity depot) {
                if (payload.instantDispatch) {
                    IColony colony = MinecoloniesAPIProxy.getInstance().getColonyManager().getIColony(player.level(), payload.pos);
                    ResourceLocation tabletResearch = ResourceLocation.fromNamespaceAndPath("cclogistics", "cclogistics/stock_tablets");

                    if (colony == null || !colony.getResearchManager().getResearchTree().isComplete(tabletResearch)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Stock Tablets research not complete.")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        return;
                    }
                }
                depot.setColonyName(payload.colonyName);
                depot.setCityTarget(payload.cityTarget);
                depot.setPlayerStockTarget(payload.stockTarget);
                depot.setInstantDispatch(payload.instantDispatch());
                depot.setChanged();
                player.level().sendBlockUpdated(payload.pos, depot.getBlockState(), depot.getBlockState(), 3);
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS))LOGGER.info("[Network] Updated Freight Depot via Module Packet.");
            } else {
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS))LOGGER.warn("[Network] Failed to find FreightDepotBlockEntity at " + payload.pos);
            }
        });
    }
}