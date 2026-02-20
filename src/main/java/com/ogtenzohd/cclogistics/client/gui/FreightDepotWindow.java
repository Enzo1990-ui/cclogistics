package com.ogtenzohd.cclogistics.client.gui;

import com.ldtteam.blockui.views.BOWindow;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.menu.FreightDepotMenu;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.network.UpdateFreightDepotPacket;
import com.ogtenzohd.cclogistics.network.CCLPackets;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.Minecraft;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.TextField;

public class FreightDepotWindow extends BOWindow {

    private final FreightDepotMenu menu;

	public FreightDepotWindow(FreightDepotMenu menu, Inventory inventory, Component title) {
		super(ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/freight_depot_inventory.xml"));
		this.menu = menu;
	}
	
    public FreightDepotMenu getMenu() {
        return this.menu;
    }

    @Override
    public void onOpened() {
        if (getWindow() != null) {
            Button saveBtn = getWindow().findPaneOfTypeByID("saveBtn", Button.class);
            if (saveBtn != null) {
                saveBtn.setHandler(btn -> saveAndClose());
            }
        }
        updateUI();
    }

    private void updateUI() {
        if (getWindow() == null) return;
        TextField colonyInput = getWindow().findPaneOfTypeByID("colonyNameInput", TextField.class);
        if (colonyInput != null) colonyInput.setText(menu.getColonyName());
        TextField cityInput = getWindow().findPaneOfTypeByID("cityTargetInput", TextField.class);
        if (cityInput != null) cityInput.setText(menu.getCityTarget());
    }

    private void saveToServer() {
        if (getWindow() == null) return;
        String cName = "";
        String cTarget = "";
        TextField colonyInput = getWindow().findPaneOfTypeByID("colonyNameInput", TextField.class);
        if (colonyInput != null) cName = colonyInput.getText();
        TextField cityInput = getWindow().findPaneOfTypeByID("cityTargetInput", TextField.class);
        if (cityInput != null) cTarget = cityInput.getText();
        CCLPackets.sendToServer(new UpdateFreightDepotPacket(menu.blockEntity.getBlockPos(), cName, cTarget));
    }

    private void saveAndClose() {
        saveToServer();
        Minecraft.getInstance().setScreen(null);
    }
}