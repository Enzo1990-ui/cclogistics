package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IAssignmentModuleView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.function.Predicate;

public class FreightDepotBuildingView extends AbstractBuildingView implements IBuildingView {

    public FreightDepotBuildingView(IColonyView colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public <T extends IBuildingModuleView> T getModuleViewMatching(Class<T> clazz, Predicate<? super T> modulePredicate) {
        T result = super.getModuleViewMatching(clazz, modulePredicate);
        
        // Failsafe: Stop WindowHireWorker from crashing when swapping jobs directly
        if (result == null && IAssignmentModuleView.class.isAssignableFrom(clazz)) {
            List<WorkerBuildingModuleView> workers = getModuleViews(WorkerBuildingModuleView.class);
            if (!workers.isEmpty()) {
                // If Minecolonies gets confused and returns null, hand it our first valid worker module 
                // so the GUI can safely fire the citizen without crashing the game!
                return (T) workers.get(0);
            }
        }
        
        return result;
    }
}