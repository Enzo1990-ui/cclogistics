package com.ogtenzohd.cclogistics.client.gui;

import com.ldtteam.blockui.BOGuiGraphics;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.menu.FreightDepotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;

public class FreightDepotScreen extends AbstractContainerScreen<FreightDepotMenu> {

    private final FreightDepotWindow window;

    public FreightDepotScreen(FreightDepotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.window = new FreightDepotWindow(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        window.onOpened();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        final boolean oldFilteringValue = NeoForgeRenderTypes.enableTextTextureLinearFiltering;
        NeoForgeRenderTypes.enableTextTextureLinearFiltering = false;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos, this.topPos, 0);

        try {
            final BOGuiGraphics target = new BOGuiGraphics(minecraft, guiGraphics.pose(), guiGraphics.bufferSource());
            window.draw(target, mouseX - this.leftPos, mouseY - this.topPos);
            window.drawLast(target, mouseX - this.leftPos, mouseY - this.topPos);
        } finally {
            guiGraphics.pose().popPose();
            NeoForgeRenderTypes.enableTextTextureLinearFiltering = oldFilteringValue;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (window.click(mouseX - this.leftPos, mouseY - this.topPos)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (window.onMouseReleased(mouseX - this.leftPos, mouseY - this.topPos)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x000000, false);
		guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x000000, false);
	}
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (window.onMouseDrag(mouseX - this.leftPos, mouseY - this.topPos, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void onClose() {
        window.onClosed();
        super.onClose();
    }
}