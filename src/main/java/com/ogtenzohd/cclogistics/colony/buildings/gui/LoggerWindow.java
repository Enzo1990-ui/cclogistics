package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.Pane; 
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.LoggerModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class LoggerWindow extends AbstractModuleWindow<LoggerModuleView> {

    private final ScrollingList logList;
    private final Button allBtn;
    private final Button inBtn;
    private final Button outBtn;
    private final LoggerModuleView moduleView; 

    public LoggerWindow(LoggerModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/logger_module.xml"));
        this.moduleView = moduleView;

        this.logList = window.findPaneOfTypeByID("logList", ScrollingList.class);
        this.allBtn = window.findPaneOfTypeByID("allBtn", Button.class);
        this.inBtn = window.findPaneOfTypeByID("inBtn", Button.class);
        this.outBtn = window.findPaneOfTypeByID("outBtn", Button.class);

        int level = moduleView.getBuildingLevel();

        if (allBtn != null) {
            allBtn.setHandler(btn -> updateList("all"));
            allBtn.setEnabled(false); 
        }
        
        if (inBtn != null) {
            inBtn.setHandler(btn -> updateList("in"));
            if (level < 3 && inBtn != null) inBtn.setVisible(false); 
        }
        
        if (outBtn != null) {
            outBtn.setHandler(btn -> updateList("out"));
            if (level < 4 && outBtn != null) outBtn.setVisible(false); 
        }
        
        updateList("all");
    }

    private void updateList(String type) {
        if (logList == null) return;
        
        List<String> logs = new ArrayList<>();
        
        if (type.equals("all")) {
            logs.addAll(moduleView.getIncomingLogs());
            logs.addAll(moduleView.getOutgoingLogs());
        } else if (type.equals("in")) {
            logs.addAll(moduleView.getIncomingLogs());
        } else if (type.equals("out")) {
            logs.addAll(moduleView.getOutgoingLogs());
        }

        if (allBtn != null) allBtn.setEnabled(!type.equals("all"));
        if (inBtn != null) inBtn.setEnabled(!type.equals("in"));
        if (outBtn != null) outBtn.setEnabled(!type.equals("out"));

        logList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return logs.size();
            }

            @Override
            public void updateElement(int index, Pane rowPane) {
                if (index < 0 || index >= logs.size()) return;
                String log = logs.get(index);
                Text text = rowPane.findPaneOfTypeByID("logText", Text.class);
                if (text != null) {
                    text.setText(Component.literal(log));
                }
            }
        });
    }
}