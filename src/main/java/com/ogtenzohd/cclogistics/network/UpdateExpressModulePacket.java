package com.ogtenzohd.cclogistics.network;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.modules.ExpressModule;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public record UpdateExpressModulePacket(BlockPos pos, String buildingName, boolean enabled) implements CustomPacketPayload {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<UpdateExpressModulePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateColonyLogistics.MODID, "update_express_module"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateExpressModulePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateExpressModulePacket::pos,
            ByteBufCodecs.STRING_UTF8, UpdateExpressModulePacket::buildingName,
            ByteBufCodecs.BOOL, UpdateExpressModulePacket::enabled,
            UpdateExpressModulePacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final UpdateExpressModulePacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) {
                LOGGER.info("[Network] Received UpdateExpressModulePacket for Pos: " + payload.pos);
            }

            IColony colony = MinecoloniesAPIProxy.getInstance().getColonyManager().getIColony(player.level(), payload.pos);
            if (colony != null) {
                IBuilding building = colony.getServerBuildingManager().getBuilding(payload.pos);
                if (building != null) {
                    ExpressModule module = building.getModule(ExpressModule.class);
                    if (module != null) {
                        module.toggleBuilding(payload.buildingName, payload.enabled);
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[Network] Updated Express Module via Module Packet.");
                    }
                }
            }
        });
    }
}