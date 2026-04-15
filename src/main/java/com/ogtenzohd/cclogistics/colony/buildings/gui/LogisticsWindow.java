package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.LogisticsModuleView;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

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
        TextField stockInput = window.findPaneOfTypeByID("stockTargetInput", TextField.class);
        if (stockInput != null) {
            stockInput.setText(String.valueOf(moduleView.getPlayerStockTarget()));
        }

        registerButton("saveBtn", btn -> {
            String newName = colonyInput != null ? colonyInput.getText() : "";
            String newTarget = cityInput != null ? cityInput.getText() : "";

            int newStockTarget = 128;
            if (stockInput != null && !stockInput.getText().isEmpty()) {
                try {
                    newStockTarget = Integer.parseInt(stockInput.getText().trim());
                    if (newStockTarget < 1) newStockTarget = 1;
                } catch (NumberFormatException e) {
                    LOGGER.warn("[LogisticsWindow] Invalid stock target entered by player. Defaulting to 128 to prevent crash.");
                }
            }

            LOGGER.info("[LogisticsWindow] Saving Data...");
            LOGGER.info("   -> Colony: " + newName + ", City: " + newTarget + ", Stock Target: " + newStockTarget);
            moduleView.updateData(newName, newTarget, newStockTarget);
        });
    }
}