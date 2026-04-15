package com.ogtenzohd.cclogistics.compat;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class FreightDepotDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        List<MutableComponent> lines = new ArrayList<>();
        BlockEntity be = context.getSourceBlockEntity();

        if (be instanceof FreightDepotBlockEntity depot) {
            IBuilding building = depot.getBuilding();
            if (building != null) {
                FreightTrackerModule module = building.getModule(FreightTrackerModule.class);
                if (module != null && module.getRequests() != null) {
                    lines.add(Component.literal("== LIVE MANIFEST =="));

                    for (FreightTrackerModule.TrackedReq req : module.getRequests().values()) {
                        if (req.status == FreightTrackerModule.TrackStatus.COMPLETED) continue;

                        String dir = req.isIncoming ? "IN " : "OUT";
                        String text = String.format("[%s] %dx %s - %s", dir, req.amount, req.itemName, formatStatus(req.status));

                        lines.add(Component.literal(text));
                    }
                }
            }
        }

        if (lines.isEmpty() || lines.size() == 1) {
            lines.add(Component.literal("No Active Freight"));
        }

        return lines;
    }

    private String formatStatus(FreightTrackerModule.TrackStatus status) {
        if (status == null) return "Unknown";
        switch(status) {
            case REQUESTED: return "Requested";
            case ACCEPTED: return "Dispatched";
            case DELIVERING: return "En Route";
            case NO_STOCK: return "No Stock";
            case IN_TRANSIT: return "In Transit";
            default: return status.name();
        }
    }

    @Override
    public int getPassiveRefreshTicks() {
        return 40;
    }

    @Override
    protected String getTranslationKey() {
        return "freight_manifest";
    }
}