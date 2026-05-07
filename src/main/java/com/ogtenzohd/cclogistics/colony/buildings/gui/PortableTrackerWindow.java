package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.ScrollingList;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.FreightTrackerModuleView;
import com.ogtenzohd.cclogistics.network.CancelFreightRequestPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PortableTrackerWindow extends BOWindow {

    private final ScrollingList requestList;
    private final FreightTrackerModuleView moduleView;
    private int lastSize = -1;

    private final Button btnIncoming;
    private final Button btnOutgoing;
    private boolean viewingIncoming = true;

    public PortableTrackerWindow(FreightTrackerModuleView moduleView) {
        super(ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/freight_tracker_module.xml"));
        this.moduleView = moduleView;

        this.requestList = this.findPaneOfTypeByID("requestList", ScrollingList.class);
        this.btnIncoming = this.findPaneOfTypeByID("btnIncoming", Button.class);
        this.btnOutgoing = this.findPaneOfTypeByID("btnOutgoing", Button.class);

        Button btnPrintClipboard = this.findPaneOfTypeByID("btnPrintClipboard", Button.class);
        if (btnPrintClipboard != null) {
            btnPrintClipboard.hide();
        }

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
        List<Map.Entry<String, FreightTrackerModule.TrackedReq>> requests = new ArrayList<>();

        for (Map.Entry<String, FreightTrackerModule.TrackedReq> entry : moduleView.requests.entrySet()) {
            if (entry.getValue().isIncoming == this.viewingIncoming) {
                requests.add(entry);
            }
        }

        requests.sort((a, b) -> Long.compare(b.getValue().timestamp, a.getValue().timestamp));

        requestList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return requests.size();
            }

            @Override
            public void updateElement(int index, Pane rowPane) {
                if (index < 0 || index >= requests.size()) return;
                Map.Entry<String, FreightTrackerModule.TrackedReq> entry = requests.get(index);
                String trackingId = entry.getKey();
                FreightTrackerModule.TrackedReq req = entry.getValue();

                Text itemText = rowPane.findPaneOfTypeByID("requestText", Text.class);
                Text statusText = rowPane.findPaneOfTypeByID("statusText", Text.class);
                Button btnDelete = rowPane.findPaneOfTypeByID("btnDelete", Button.class);

                if (btnDelete != null) {
                    btnDelete.setHandler(btn -> {
                        BlockPos pos = moduleView.getBuildingView().getPosition();
                        PacketDistributor.sendToServer(new CancelFreightRequestPacket(pos, trackingId));
                    });
                }

                if (itemText != null && statusText != null) {
                    String rawName = (req.itemName != null && !req.itemName.isEmpty()) ? req.itemName : "Loading...";
                    itemText.setText(Component.literal(req.amount + "x " + rawName).withStyle(net.minecraft.ChatFormatting.BLACK));

                    String symbol = "• ";
                    String statusStr = req.status.display;

                    if (req.override != null && !req.override.isEmpty() && req.status != FreightTrackerModule.TrackStatus.COMPLETED) {
                        statusStr = "§7[" + req.override + "]";
                    }

                    if (statusStr.contains("Out of Stock")) symbol = "§c✖ ";
                    else if (statusStr.contains("Complete")) symbol = "§2✔ ";
                    else if (statusStr.contains("Delivering")) symbol = "§d➔ ";
                    else if (statusStr.contains("Depot Received")) symbol = "§1📦 ";
                    else if (statusStr.contains("En Route")) symbol = "§3🚂 ";
                    else if (statusStr.contains("Ticker Received")) symbol = "§6📥 ";
                    else if (statusStr.contains("Requested")) symbol = "§6⏳ ";

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