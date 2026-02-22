package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.IAssignmentModuleView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Predicate;

public class ForemenHutBuildingView extends AbstractBuildingView implements IBuildingView {

    public ForemenHutBuildingView(IColonyView colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public void openGui(boolean shouldOpenInv) {
        if (shouldOpenInv) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("The Foremen's Hut does not have an inventory!"), 
                        true
                );
            }
            return; 
        }
        super.openGui(false);
    }

    @Override
    public <T extends IBuildingModuleView> T getModuleViewMatching(Class<T> clazz, Predicate<? super T> modulePredicate) {
        T result = super.getModuleViewMatching(clazz, modulePredicate);
        
        // Failsafe: Stop WindowHireWorker from crashing when swapping jobs directly
        if (result == null && IAssignmentModuleView.class.isAssignableFrom(clazz)) {
            List<WorkerBuildingModuleView> workers = getModuleViews(WorkerBuildingModuleView.class);
            if (!workers.isEmpty()) {
                return (T) workers.get(0);
            }
        }
        
        return result;
    }
}