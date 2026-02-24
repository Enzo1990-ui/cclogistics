package com.ogtenzohd.cclogistics.colony.buildings.moduleviews;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.ogtenzohd.cclogistics.colony.buildings.gui.FreightTrackerWindow; // Make sure this window class exists!
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class FreightTrackerModuleView extends AbstractBuildingModuleView {

    private final List<FreightTrackerModule.TrackedRequest> activeRequests = new ArrayList<>();

    // Standard constructor to match LoggerModuleView
    public FreightTrackerModuleView() {
        super();
    }

    @Override
    public BOWindow getWindow() {
        // This opens your tracker UI
        return new FreightTrackerWindow(this);
    }

    @Override
    public void deserialize(RegistryFriendlyByteBuf buf) {
        activeRequests.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            String itemName = buf.readUtf();
            int amount = buf.readInt();
            String statusName = buf.readUtf();
            
            // Read the 4th field (the override string) to keep the buffer synced
            String override = buf.readUtf();
            if (override.isEmpty()) override = null;

            // Add to the list using the compatible TrackedRequest constructor
            activeRequests.add(new FreightTrackerModule.TrackedRequest(
                    itemName, 
                    amount, 
                    FreightTrackerModule.TrackStatus.valueOf(statusName),
                    override
            ));
        }
    }

    public List<FreightTrackerModule.TrackedRequest> getActiveRequests() {
        return activeRequests;
    }

    @Override
    public Component getDesc() {
        return Component.literal("Freight Tracker");
    }
}