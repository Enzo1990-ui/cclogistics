package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.FreightTrackerModuleView;
import com.ogtenzohd.cclogistics.network.CancelFreightRequestPacket;
import com.ogtenzohd.cclogistics.network.RequestPortableTrackerPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FreightTrackerWindow extends AbstractModuleWindow<FreightTrackerModuleView> {

    private final ScrollingList requestList;
    private final FreightTrackerModuleView moduleView;
    private int lastSize = -1;

    private final Button btnIncoming;
    private final Button btnOutgoing;
    private final Button btnPrintClipboard;
    private boolean viewingIncoming = true;

    public FreightTrackerWindow(FreightTrackerModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/freight_tracker_module.xml"));
        this.moduleView = moduleView;
        this.requestList = window.findPaneOfTypeByID("requestList", ScrollingList.class);
        this.btnIncoming = window.findPaneOfTypeByID("btnIncoming", Button.class);
        this.btnOutgoing = window.findPaneOfTypeByID("btnOutgoing", Button.class);

        this.btnPrintClipboard = window.findPaneOfTypeByID("btnPrintClipboard", Button.class);

        if (this.btnIncoming != null) this.btnIncoming.setHandler(this::onIncomingClicked);
        if (this.btnOutgoing != null) this.btnOutgoing.setHandler(this::onOutgoingClicked);

        if (this.btnPrintClipboard != null) {
            this.btnPrintClipboard.setHandler(btn -> {
                BlockPos pos = moduleView.getBuildingView().getPosition();
                PacketDistributor.sendToServer(new RequestPortableTrackerPacket(pos));
            });
        }

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
                Button btnDispatch = rowPane.findPaneOfTypeByID("btnDispatch", Button.class);

                if (btnDelete != null) {
                    btnDelete.setHandler(btn -> {
                        BlockPos pos = moduleView.getBuildingView().getPosition();
                        PacketDistributor.sendToServer(new CancelFreightRequestPacket(pos, trackingId));
                    });
                }

                if (btnDispatch != null) {
                    boolean canForceSend = req.override != null && req.override.contains("[In Stock]");

                    if (req.isIncoming && canForceSend) {
                        btnDispatch.show();
                        btnDispatch.setEnabled(true);
                        btnDispatch.setText(net.minecraft.network.chat.Component.literal("§aSend"));

                        btnDispatch.setHandler(btn -> {
                            BlockPos pos = moduleView.getBuildingView().getPosition();
                            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.ogtenzohd.cclogistics.network.ForceDispatchPacket(pos, trackingId));

                            btnDispatch.setText(net.minecraft.network.chat.Component.literal("§7..."));
                            btnDispatch.setEnabled(false);
                        });
                    } else {
                        btnDispatch.hide();
                    }
                }

                if (itemText != null && statusText != null) {
                    String rawName = (req.itemName != null && !req.itemName.isEmpty()) ? req.itemName : "Loading...";
                    itemText.setText(Component.literal(req.amount + "x " + rawName).withStyle(net.minecraft.ChatFormatting.BLACK));

                    String symbol = "• ";

                    String statusStr = req.status.getDisplayName();

                    if (req.override != null && !req.override.isEmpty() && req.status != FreightTrackerModule.TrackStatus.COMPLETED) {
                        statusStr = "§7[" + req.override + "]";
                    }

                    if (statusStr.contains("No Stock")) {
                        symbol = "§8✖ ";
                    } else if (statusStr.contains("Delivered")) {
                        symbol = "§2✔ ";
                    } else if (statusStr.contains("Out for Delivery")) {
                        symbol = "§6➔ ";
                    } else if (statusStr.contains("Arrived at Depot")) {
                        symbol = "§9📦 ";
                    } else if (statusStr.contains("Transit")) {
                        symbol = "§3🚂 ";
                    } else if (statusStr.contains("Dispatched")) {
                        symbol = "§1📤 ";
                    } else if (statusStr.contains("Processing")) {
                        symbol = "§5⚙ ";
                    } else if (statusStr.contains("Awaiting")) {
                        symbol = "§4⏳ ";
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