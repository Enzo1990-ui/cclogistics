package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.function.Predicate;

public class FreightDepotBuildingView extends AbstractBuildingView implements IBuildingView {

    public FreightDepotBuildingView(IColonyView colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public <T extends IBuildingModuleView> T getModuleViewMatching(Class<T> clazz, Predicate<? super T> modulePredicate) {
        List<T> modules = getModuleViews(clazz);
        for (T module : modules) {
            if (modulePredicate.test(module)) {
                return module;
            }
        }
        return super.getModuleViewMatching(clazz, modulePredicate);
    }
}