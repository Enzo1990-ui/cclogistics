package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.api.colony.IColonyView;
import net.minecraft.core.BlockPos;

public class FreightDepotBuildingView extends AbstractBuildingView implements IBuildingView {

    public FreightDepotBuildingView(IColonyView colony, BlockPos pos) {
        super(colony, pos);
    }
}