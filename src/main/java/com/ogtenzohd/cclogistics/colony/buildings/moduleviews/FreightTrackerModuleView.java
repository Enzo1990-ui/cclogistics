package com.ogtenzohd.cclogistics.colony.buildings.moduleviews;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.ogtenzohd.cclogistics.colony.buildings.gui.FreightTrackerWindow;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FreightTrackerModuleView extends AbstractBuildingModuleView {
    
    public final Map<String, FreightTrackerModule.TrackedReq> requests = new ConcurrentHashMap<>();

    public FreightTrackerModuleView() {
        super();
    }

    @Override
    public BOWindow getWindow() {
        return new FreightTrackerWindow(this);
    }

    @Override
    public void deserialize(RegistryFriendlyByteBuf buf) {
        requests.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf();
            FreightTrackerModule.TrackedReq r = new FreightTrackerModule.TrackedReq();
            r.itemName = buf.readUtf();
            r.amount = buf.readInt();
            r.status = FreightTrackerModule.TrackStatus.values()[buf.readInt()];
            r.override = buf.readUtf();
            r.timestamp = buf.readLong();
            requests.put(id, r);
        }
    }

    @Override
    public Component getDesc() {
        return Component.literal("Freight Tracker");
    }
}