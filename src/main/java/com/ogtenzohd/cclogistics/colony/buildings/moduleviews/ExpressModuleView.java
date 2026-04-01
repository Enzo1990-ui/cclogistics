package com.ogtenzohd.cclogistics.colony.buildings.moduleviews;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.ogtenzohd.cclogistics.colony.buildings.gui.ExpressWindow;
import com.ogtenzohd.cclogistics.network.CCLPackets;
import com.ogtenzohd.cclogistics.network.UpdateExpressModulePacket;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ExpressModuleView extends AbstractBuildingModuleView {

    private final List<String> expressBuildings = new ArrayList<>();
    private final List<String> availableBuildings = new ArrayList<>(); // Store the synced list

    public ExpressModuleView() {
        super();
    }

    @Override
    public BOWindow getWindow() {
        return new ExpressWindow(this);
    }

    public List<String> getExpressBuildings() {
        return expressBuildings;
    }

    public List<String> getAvailableBuildings() {
        return availableBuildings;
    }

    public void toggleBuilding(String buildingName, boolean enabled) {
        if (enabled) {
            if (!expressBuildings.contains(buildingName)) expressBuildings.add(buildingName);
        } else {
            expressBuildings.remove(buildingName);
        }

        if (getBuildingView() != null) {
            CCLPackets.sendToServer(new UpdateExpressModulePacket(
                    getBuildingView().getPosition(), buildingName, enabled
            ));
        }
    }

    @Override
    public void deserialize(RegistryFriendlyByteBuf buf) {
        expressBuildings.clear();
        expressBuildings.addAll(buf.readList(b -> b.readUtf()));
        availableBuildings.clear();
        availableBuildings.addAll(buf.readList(b -> b.readUtf()));
    }

    @Override
    public Component getDesc() {
        return Component.literal("Express Delivery");
    }
}