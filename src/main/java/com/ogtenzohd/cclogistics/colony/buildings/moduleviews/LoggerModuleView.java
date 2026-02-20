package com.ogtenzohd.cclogistics.colony.buildings.moduleviews;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.ogtenzohd.cclogistics.colony.buildings.gui.LoggerWindow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LoggerModuleView extends AbstractBuildingModuleView {

    private final List<String> incomingLogs = new ArrayList<>();
    private final List<String> outgoingLogs = new ArrayList<>();

    public LoggerModuleView() {
        super();
    }

    @Override
    public BOWindow getWindow() {
        return new LoggerWindow(this);
    }

    public List<String> getIncomingLogs() {
        return incomingLogs;
    }

    public List<String> getOutgoingLogs() {
        return outgoingLogs;
    }
    
    public int getBuildingLevel() {
        if (getBuildingView() != null) {
            return getBuildingView().getBuildingLevel();
        }
        return 0;
    }

    @Override
    public void deserialize(RegistryFriendlyByteBuf buf) {
        incomingLogs.clear();
        incomingLogs.addAll(buf.readList(b -> b.readUtf()));
        outgoingLogs.clear();
        outgoingLogs.addAll(buf.readList(b -> b.readUtf()));
    }

    @Override
    public Component getDesc() {
        return Component.literal("Logger");
    }
}