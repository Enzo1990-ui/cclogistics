package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.FreightTrackerModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class FreightTrackerWindow extends AbstractModuleWindow<FreightTrackerModuleView> {

    private final ScrollingList requestList;
    private final FreightTrackerModuleView moduleView;
    private int lastSize = -1;

    public FreightTrackerWindow(FreightTrackerModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/freight_tracker_module.xml"));
        this.moduleView = moduleView;
        this.requestList = window.findPaneOfTypeByID("requestList", ScrollingList.class);
        
        updateList();
    }

    private void updateList() {
        if (requestList == null) return;
        
        List<FreightTrackerModule.TrackedRequest> requests = moduleView.getActiveRequests();

        requestList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return requests.size();
            }

            @Override
            public void updateElement(int index, Pane rowPane) {
                if (index < 0 || index >= requests.size()) return;
                
                FreightTrackerModule.TrackedRequest req = requests.get(index);
                Text text = rowPane.findPaneOfTypeByID("requestText", Text.class);
                
                if (text != null) {
                    // Build the base text: "64x Oak Log - "
                    MutableComponent display = Component.literal(req.amount + "x " + req.itemName + " - ");
                    
                    // Build the colored status: "[Requested]" in Yellow, etc.
                    MutableComponent statusText = Component.literal("[" + req.status.displayName + "]")
                                                           .withStyle(req.status.color);
                    
                    text.setText(display.append(statusText));
                }
            }
        });
    }

    // Auto-refresh the list while the GUI is open if data changes!
    @Override
    public void onUpdate() {
        super.onUpdate();
        List<FreightTrackerModule.TrackedRequest> currentRequests = moduleView.getActiveRequests();
        if (currentRequests.size() != lastSize) {
            updateList();
            lastSize = currentRequests.size();
        }
    }
}