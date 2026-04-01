package com.ogtenzohd.cclogistics.colony.buildings.gui;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.ExpressModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ExpressWindow extends AbstractModuleWindow<ExpressModuleView> {

    private final ScrollingList buildingList;
    private final ExpressModuleView moduleView;

    public ExpressWindow(ExpressModuleView moduleView) {
        super(moduleView, ResourceLocation.fromNamespaceAndPath("cclogistics", "gui/window/express_module.xml"));
        this.moduleView = moduleView;

        this.buildingList = window.findPaneOfTypeByID("buildingList", ScrollingList.class);
        updateList();
    }

    private void updateList() {
        if (buildingList == null || moduleView == null) return;

        List<String> bNames = new ArrayList<>(moduleView.getAvailableBuildings());

        bNames.sort((a, b) -> {
            String rawA = a.split(";")[0];
            String rawB = b.split(";")[0];
            String nameA = (rawA.contains(".") && !rawA.contains(" ")) ? Component.translatable(rawA).getString() : rawA;
            String nameB = (rawB.contains(".") && !rawB.contains(" ")) ? Component.translatable(rawB).getString() : rawB;
            return nameA.compareToIgnoreCase(nameB);
        });

        buildingList.setDataProvider(new ScrollingList.DataProvider() {
            @Override
            public int getElementCount() {
                return bNames.size();
            }

            @Override
            public void updateElement(int index, Pane rowPane) {
                if (index < 0 || index >= bNames.size()) return;

                String bName = bNames.get(index);
                boolean isExpress = moduleView.getExpressBuildings().contains(bName);

                String[] parts = bName.split(";");
                String rawName = parts[0];
                String idStr = parts.length > 1 ? " #" + parts[1] : "";

                Text buildingText = rowPane.findPaneOfTypeByID("buildingText", Text.class);
                if (buildingText != null) {
                    if (rawName.contains(".") && !rawName.contains(" ")) {
                        buildingText.setText(Component.translatable(rawName).append(Component.literal(idStr)));
                    } else {
                        buildingText.setText(Component.literal(rawName + idStr));
                    }
                }

                Button toggleBtn = rowPane.findPaneOfTypeByID("toggleBtn", Button.class);
                if (toggleBtn != null) {
                    toggleBtn.setText(Component.literal(isExpress ? "[X]" : "[ ]"));
                    toggleBtn.setHandler(btn -> {
                        boolean newState = !moduleView.getExpressBuildings().contains(bName);
                        moduleView.toggleBuilding(bName, newState);
                        toggleBtn.setText(Component.literal(newState ? "[X]" : "[ ]"));
                    });
                }
            }
        });
    }
}