package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.minecraft.core.BlockPos;

public class ForemenHutBuildingView extends AbstractBuildingView implements IBuildingView {

    public ForemenHutBuildingView(IColonyView colony, BlockPos pos) {
        super(colony, pos);
    }
}
