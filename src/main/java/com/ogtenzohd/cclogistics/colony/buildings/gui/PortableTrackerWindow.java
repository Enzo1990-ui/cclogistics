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
    private final Button tabAll;
    private final Button tabNoStock;
    private final Button tabTransit;
    private boolean viewingIncoming = true;
    private int activeTab = 0;

    public PortableTrackerWindow(FreightTrackerModuleView moduleView) {
        super(ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/freight_tracker_module.xml"));
        this.moduleView = moduleView;

        this.requestList = this.findPaneOfTypeByID("requestList", ScrollingList.class);
        this.btnIncoming = this.findPaneOfTypeByID("btnIncoming", Button.class);
        this.btnOutgoing = this.findPaneOfTypeByID("btnOutgoing", Button.class);
        this.tabAll = this.findPaneOfTypeByID("tabAll", Button.class);
        this.tabNoStock = this.findPaneOfTypeByID("tabNoStock", Button.class);
        this.tabTransit = this.findPaneOfTypeByID("tabTransit", Button.class);

        Button btnPrintClipboard = this.findPaneOfTypeByID("btnPrintClipboard", Button.class);
        if (btnPrintClipboard != null) {
            btnPrintClipboard.hide();
        }

        if (this.btnIncoming != null) this.btnIncoming.setHandler(this::onIncomingClicked);
        if (this.btnOutgoing != null) this.btnOutgoing.setHandler(this::onOutgoingClicked);
        if (this.tabAll != null) this.tabAll.setHandler(btn -> { activeTab = 0; updateButtonStates(); updateList(); });
        if (this.tabNoStock != null) this.tabNoStock.setHandler(btn -> { activeTab = 1; updateButtonStates(); updateList(); });
        if (this.tabTransit != null) this.tabTransit.setHandler(btn -> { activeTab = 2; updateButtonStates(); updateList(); });

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
        if (tabAll != null) tabAll.setEnabled(activeTab != 0);
        if (tabNoStock != null) tabNoStock.setEnabled(activeTab != 1);
        if (tabTransit != null) tabTransit.setEnabled(activeTab != 2);
    }

    private void updateList() {
        if (requestList == null) return;
        List<Map.Entry<String, FreightTrackerModule.TrackedReq>> requests = new ArrayList<>();

        for (Map.Entry<String, FreightTrackerModule.TrackedReq> entry : moduleView.requests.entrySet()) {
            FreightTrackerModule.TrackedReq req = entry.getValue();

            if (req.override != null && (req.override.contains("[In AE2]") || req.override.equals("Colony Crafting"))) {
                continue;
            }

            if (req.isIncoming != this.viewingIncoming) continue;
            if (activeTab == 1 && req.status != FreightTrackerModule.TrackStatus.NO_STOCK) continue;
            if (activeTab == 2 && req.status != FreightTrackerModule.TrackStatus.ON_TRAIN
                    && req.status != FreightTrackerModule.TrackStatus.DELIVERING
                    && req.status != FreightTrackerModule.TrackStatus.DISPATCHED) continue;

            requests.add(entry);
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
                Text requesterText = rowPane.findPaneOfTypeByID("requesterText", Text.class);
                Text statusText = rowPane.findPaneOfTypeByID("statusText", Text.class);

                Button btnDelete = rowPane.findPaneOfTypeByID("btnDelete", Button.class);
                Button btnDispatch = rowPane.findPaneOfTypeByID("btnDispatch", Button.class);

                if (btnDelete != null) {
                    btnDelete.setHandler(btn -> {
                        BlockPos pos = moduleView.getBuildingView().getPosition();
                        PacketDistributor.sendToServer(new CancelFreightRequestPacket(pos, trackingId));
                    });
                }

                String rawStatus = (req.override != null && !req.override.isEmpty()) ? req.override : req.status.getDisplayName();
                String cleanStatus = rawStatus;
                String requesterBuilding = "Colony Request";

                if (rawStatus.contains("|")) {
                    String[] parts = rawStatus.split("\\|");
                    cleanStatus = parts[0];
                    if (parts.length > 1) requesterBuilding = parts[1];
                }

                if (btnDispatch != null) {
                    boolean canForceSend = cleanStatus.contains("[In Stock]");

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

                    if (requesterText != null) {
                        requesterText.setText(Component.literal("§8To: " + requesterBuilding));
                    }

                    String symbol = "• ";
                    String finalDisplayStatus = req.status.getDisplayName();

                    if (!cleanStatus.isEmpty() && req.status != FreightTrackerModule.TrackStatus.COMPLETED) {
                        finalDisplayStatus = "§7[" + cleanStatus + "]";
                    }

                    if (finalDisplayStatus.contains("No Stock")) {
                        symbol = "§8✖ ";
                    } else if (finalDisplayStatus.contains("Delivered")) {
                        symbol = "§2✔ ";
                    } else if (finalDisplayStatus.contains("Out for Delivery")) {
                        symbol = "§6➔ ";
                    } else if (finalDisplayStatus.contains("Arrived at Depot")) {
                        symbol = "§9📦 ";
                    } else if (finalDisplayStatus.contains("Transit")) {
                        symbol = "§3🚂 ";
                    } else if (finalDisplayStatus.contains("Dispatched")) {
                        symbol = "§1📤 ";
                    } else if (finalDisplayStatus.contains("Processing")) {
                        symbol = "§5⚙ ";
                    } else if (finalDisplayStatus.contains("Awaiting")) {
                        symbol = "§4⏳ ";
                    }

                    statusText.setText(Component.literal(symbol + finalDisplayStatus));
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