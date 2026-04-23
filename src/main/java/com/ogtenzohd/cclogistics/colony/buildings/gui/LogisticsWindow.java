package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.LogisticsModuleView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class LogisticsWindow extends AbstractModuleWindow<LogisticsModuleView> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private boolean isInstantEnabled;

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

        Button instantDispatchBtn = window.findPaneOfTypeByID("instantReqBtn", Button.class);
        IColony colony = null;
        if (Minecraft.getInstance().level != null && moduleView.getBuildingView() != null) {
            colony = MinecoloniesAPIProxy.getInstance().getColonyManager().getIColony(
                    Minecraft.getInstance().level,
                    moduleView.getBuildingView().getPosition()
            );
        }

        boolean hasResearch = false;
        if (colony != null) {
            ResourceLocation tabletResearch = ResourceLocation.fromNamespaceAndPath("cclogistics", "cclogistics/stock_tablets");
            hasResearch = colony.getResearchManager().getResearchTree().isComplete(tabletResearch);
        }

        isInstantEnabled = moduleView.isInstantDispatch();

        if (instantDispatchBtn != null) {
            if (!hasResearch) {
                instantDispatchBtn.setEnabled(false);
                instantDispatchBtn.setText(Component.literal("Requires Stock Tablets"));
            } else {
                instantDispatchBtn.setEnabled(true);
                instantDispatchBtn.setText(Component.literal("Instant Dispatch: " + (isInstantEnabled ? "ON" : "OFF")));

                registerButton("instantReqBtn", btn -> {
                    isInstantEnabled = !isInstantEnabled;
                    btn.setText(Component.literal("Instant Dispatch: " + (isInstantEnabled ? "ON" : "OFF")));
                    LOGGER.info("[LogisticsWindow] Toggled Instant Dispatch to: " + isInstantEnabled);
                });
            }
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
            LOGGER.info("   -> Colony: " + newName + ", City: " + newTarget + ", Stock Target: " + newStockTarget + ", Instant Dispatch: " + isInstantEnabled);
            moduleView.updateData(newName, newTarget, newStockTarget, isInstantEnabled);
        });
    }
}