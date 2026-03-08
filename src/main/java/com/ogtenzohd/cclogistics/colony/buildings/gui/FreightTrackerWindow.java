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
                Text itemText = rowPane.findPaneOfTypeByID("requestText", Text.class);
                Text statusText = rowPane.findPaneOfTypeByID("statusText", Text.class);
                
                if (itemText != null && statusText != null) {
                    // Truncate the item name -- Bug fix
                    String shortItemName = req.itemName.length() > 20 ? req.itemName.substring(0, 17) + "..." : req.itemName;
                    
                    // Top line: Black text, "64x Item Name"
                    itemText.setText(Component.literal(req.amount + "x " + shortItemName).withStyle(net.minecraft.ChatFormatting.BLACK));
                    
                    // Add symbols because i found out i can...
                    String symbol = "• ";
                    String statusName = req.status.displayName;
                    if (statusName.contains("No Stock") || statusName.contains("Failed")) {
                        symbol = "✖ ";
                    } else if (statusName.contains("Accepted") || statusName.contains("Success")) {
                        symbol = "✔ ";
                    } else if (statusName.contains("Transit") || statusName.contains("Routing")) {
                        symbol = "➔ ";
                    }

                    statusText.setText(Component.literal(symbol + statusName).withStyle(req.status.color));
                }
            }
        });
    }

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