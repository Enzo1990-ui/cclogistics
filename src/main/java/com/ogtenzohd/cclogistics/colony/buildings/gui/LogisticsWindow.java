package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.LogisticsModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class LogisticsWindow extends AbstractModuleWindow<LogisticsModuleView> {

    private static final Logger LOGGER = LogUtils.getLogger();

    public LogisticsWindow(LogisticsModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/freight_depot.xml"));
        moduleView.syncFromBlockEntity();
        TextField colonyInput = window.findPaneOfTypeByID("colonyNameInput", TextField.class);
        if (colonyInput != null) {
            colonyInput.setText(moduleView.getColonyName());
        }
        TextField cityInput = window.findPaneOfTypeByID("cityTargetInput", TextField.class);
        if (cityInput != null) {
            cityInput.setText(moduleView.getCreateTarget());
        }
        registerButton("saveBtn", btn -> {
            String newName = colonyInput != null ? colonyInput.getText() : "";
            String newTarget = cityInput != null ? cityInput.getText() : "";
            LOGGER.info("[LogisticsWindow] Saving Data...");
            LOGGER.info("   -> Colony: " + newName + ", City: " + newTarget);
            moduleView.updateData(newName, newTarget);
        });
    }
}
