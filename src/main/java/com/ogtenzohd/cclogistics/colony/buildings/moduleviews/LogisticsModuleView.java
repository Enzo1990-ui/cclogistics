package com.ogtenzohd.cclogistics.colony.buildings.moduleviews;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.ogtenzohd.cclogistics.colony.buildings.gui.LogisticsWindow;
import com.ogtenzohd.cclogistics.network.CCLPackets;
import com.ogtenzohd.cclogistics.colony.network.UpdateLogisticsModulePacket;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class LogisticsModuleView extends AbstractBuildingModuleView {

    private String clientColonyName = "";
    private String clientCreateTarget = "";
    private int clientPlayerStockTarget = 128; // NEW

    public LogisticsModuleView() {
        super();
    }

    @Override
    public BOWindow getWindow() {
        return new LogisticsWindow(this);
    }

    public String getColonyName() { return clientColonyName; }
    public String getCreateTarget() { return clientCreateTarget; }
    public int getPlayerStockTarget() { return clientPlayerStockTarget; }
    public void updateData(String name, String target, int stockTarget) {
        this.clientColonyName = name;
        this.clientCreateTarget = target;
        this.clientPlayerStockTarget = stockTarget;

        if (getBuildingView() != null) {
            CCLPackets.sendToServer(new UpdateLogisticsModulePacket(
                    getBuildingView().getPosition(),
                    name,
                    target,
                    stockTarget
            ));
        }
    }

    public void syncFromBlockEntity() {
        if (getBuildingView() != null) {
            BlockPos pos = getBuildingView().getPosition();
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FreightDepotBlockEntity depot) {
                    this.clientColonyName = depot.getColonyName();
                    this.clientCreateTarget = depot.getCityTarget();
                    this.clientPlayerStockTarget = depot.getPlayerStockTarget();
                }
            }
        }
    }

    @Override
    public void deserialize(RegistryFriendlyByteBuf buf) {
        this.clientColonyName = buf.readUtf();
        this.clientCreateTarget = buf.readUtf();
        this.clientPlayerStockTarget = buf.readInt();
    }

    @Override
    public Component getDesc() {
        return Component.literal("Logistics");
    }
}