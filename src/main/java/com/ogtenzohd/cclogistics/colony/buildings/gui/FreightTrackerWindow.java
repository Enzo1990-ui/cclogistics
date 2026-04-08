package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.FreightTrackerModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class FreightTrackerWindow extends AbstractModuleWindow<FreightTrackerModuleView> {

    private final ScrollingList requestList;
    private final FreightTrackerModuleView moduleView;
    private int lastSize = -1;

    private final Button btnIncoming;
    private final Button btnOutgoing;
    private boolean viewingIncoming = true;

    public FreightTrackerWindow(FreightTrackerModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/freight_tracker_module.xml"));
        this.moduleView = moduleView;
        this.requestList = window.findPaneOfTypeByID("requestList", ScrollingList.class);

        this.btnIncoming = window.findPaneOfTypeByID("btnIncoming", Button.class);
        this.btnOutgoing = window.findPaneOfTypeByID("btnOutgoing", Button.class);

        if (this.btnIncoming != null) this.btnIncoming.setHandler(this::onIncomingClicked);
        if (this.btnOutgoing != null) this.btnOutgoing.setHandler(this::onOutgoingClicked);

        updateButtonStates();
        updateList();
    }

    private void onIncomingClicked(Button button) {
        if (viewingIncoming) return;
        viewingIncoming = true;
        updateButtonStates();
        updateList();
    }

    private void onOutgoingClicked(Button button) {
        if (!viewingIncoming) return;
        viewingIncoming = false;
        updateButtonStates();
        updateList();
    }

    private void updateButtonStates() {
        if (btnIncoming != null) btnIncoming.setEnabled(!viewingIncoming);
        if (btnOutgoing != null) btnOutgoing.setEnabled(viewingIncoming);
    }


    private void updateList() {
        if (requestList == null) return;

        List<FreightTrackerModule.TrackedReq> requests = new ArrayList<>();

        for (FreightTrackerModule.TrackedReq req : moduleView.requests.values()) {
            if (req.isIncoming == this.viewingIncoming) {
                requests.add(req);
            }
        }

        requests.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        requestList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return requests.size();
            }

            @Override
            public void updateElement(int index, Pane rowPane) {
                if (index < 0 || index >= requests.size()) return;

                FreightTrackerModule.TrackedReq req = requests.get(index);
                Text itemText = rowPane.findPaneOfTypeByID("requestText", Text.class);
                Text statusText = rowPane.findPaneOfTypeByID("statusText", Text.class);

                if (itemText != null && statusText != null) {
                    String rawName = (req.itemName != null && !req.itemName.isEmpty()) ? req.itemName : "Loading...";
                    String shortItemName = rawName.length() > 20 ? rawName.substring(0, 17) + "..." : rawName;

                    itemText.setText(Component.literal(req.amount + "x " + shortItemName).withStyle(net.minecraft.ChatFormatting.BLACK));

                    String symbol = "• ";
                    String statusStr = req.status.display;

                    if (req.override != null && !req.override.isEmpty() && req.status != FreightTrackerModule.TrackStatus.COMPLETED) {
                        statusStr = "§7[" + req.override + "]";
                    }

                    if (statusStr.contains("Out of Stock")) {
                        symbol = "§c✖ ";
                    } else if (statusStr.contains("Complete")) {
                        symbol = "§2✔ ";
                    } else if (statusStr.contains("Delivering")) {
                        symbol = "§d➔ ";
                    } else if (statusStr.contains("Depot Received")) {
                        symbol = "§b📦 ";
                    } else if (statusStr.contains("En Route")) {
                        symbol = "§a🚂 ";
                    } else if (statusStr.contains("Ticker Received")) {
                        symbol = "§e📥 ";
                    } else if (statusStr.contains("Requested")) {
                        symbol = "§e⏳ ";
                    }

                    statusText.setText(Component.literal(symbol + statusStr));
                }
            }
        });
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        int currentSize = moduleView.requests.size();
        if (currentSize != lastSize) {
            updateList();
            lastSize = currentSize;
        }
    }
}