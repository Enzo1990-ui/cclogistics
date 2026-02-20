package com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.logic;

import com.ogtenzohd.cclogistics.util.BuildingRoutingEntry;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.buildings.IBuilding;

import java.util.List;

public class PackageRouting {

    public static String resolvePackageName(List<BuildingRoutingEntry> packages, IRequest<?> request) {
        if (packages == null || packages.isEmpty()) {
            return null;
        }

        if (packages.size() == 1) {
            return packages.get(0).address();
        }

        if (request.getRequester() instanceof IBuilding building) {
            
            String buildingId = building.getBuildingType().toString(); 

            if (buildingId.contains(":")) {
                buildingId = buildingId.split(":")[1];
            }

            for (BuildingRoutingEntry entry : packages) {
                String ruleId = entry.buildingId();
                
                String simplifiedRule = ruleId.toLowerCase().replace(" ", "_");
                
                if (simplifiedRule.equalsIgnoreCase(buildingId)) {
                    return entry.address();
                }
            }
        }

        for (BuildingRoutingEntry entry : packages) {
            if (entry.buildingId().equalsIgnoreCase("default") || entry.buildingId().equalsIgnoreCase("generic")) {
                return entry.address();
            }
        }

        return null;
    }
}