package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

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
}