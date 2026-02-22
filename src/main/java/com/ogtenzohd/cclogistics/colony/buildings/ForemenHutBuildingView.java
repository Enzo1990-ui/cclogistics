package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ForemenHutBuildingView extends AbstractBuildingView implements IBuildingView {

    public ForemenHutBuildingView(IColonyView colony, BlockPos pos) {
        super(colony, pos);
		
		// ** Fix the crash for Description: MousePressed event for BO screen
        this.registerModule(new WorkerBuildingModuleView());
    }
	
	// ** Fix for the inventory Crash
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
}
