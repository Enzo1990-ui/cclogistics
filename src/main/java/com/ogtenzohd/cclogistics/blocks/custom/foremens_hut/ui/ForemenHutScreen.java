package com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.ui;

import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.menu.ForemenHutMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class ForemenHutScreen extends AbstractContainerScreen<ForemenHutMenu> {

    public ForemenHutScreen(ForemenHutMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        int y = this.topPos + 10;
        guiGraphics.drawString(this.font, "Incoming Logs:", this.leftPos + 10, y, 0xFFFFFF, false);
        y += 10;
        
        List<String> incoming = this.menu.getIncomingLogs();
        for (String log : incoming) {
            guiGraphics.drawString(this.font, log, this.leftPos + 10, y, 0xAAAAAA, false);
            y += 10;
        }
        
        y += 10;
        guiGraphics.drawString(this.font, "Outgoing Logs:", this.leftPos + 10, y, 0xFFFFFF, false);
        y += 10;
        
        List<String> outgoing = this.menu.getOutgoingLogs();
        for (String log : outgoing) {
             guiGraphics.drawString(this.font, log, this.leftPos + 10, y, 0xAAAAAA, false);
             y += 10;
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
