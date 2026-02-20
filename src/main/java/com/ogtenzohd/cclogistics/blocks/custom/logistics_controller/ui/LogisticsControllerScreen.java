package com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.ui;

import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.menu.LogisticsControllerMenu;
import com.ogtenzohd.cclogistics.util.BuildingRoutingEntry;
import com.ogtenzohd.cclogistics.network.CCLPackets;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.network.ConfigureRequesterPacket;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

public class LogisticsControllerScreen extends AbstractContainerScreen<LogisticsControllerMenu> implements MenuAccess<LogisticsControllerMenu> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CCLRegistration.MODID, "textures/gui/requester_gui.png");
    private static final int GUI_WIDTH = 255, GUI_HEIGHT = 256; 
    private static final int ROW_START_Y = 53, ROW_HEIGHT = 22, ROWS_PER_PAGE = 4; 
    private static final int NAME_BOX_X = 9, NAME_BOX_W = 105;
    private static final int SCROLL_X = 118, SCROLL_W = 104;
    private static final int BUTTON_COL_X = 226; 

    private List<BuildingRoutingEntry> packages;
    private List<EditBox> nameFields = new ArrayList<>();
    private List<SelectionScrollInput> idScrolls = new ArrayList<>();
    private int currentPage = 0;
    private Button confirmButton; 
    private IconButton addButton;

    public LogisticsControllerScreen(LogisticsControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.packages = new ArrayList<>(menu.getPackages());
        this.imageWidth = GUI_WIDTH; this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 1000; 
        this.inventoryLabelY = 1000; 

        if (packages == null) packages = new ArrayList<>();
        if (packages.isEmpty()) packages.add(new BuildingRoutingEntry("", "Default", true)); 

        int maxPages = Math.max(1, (int) Math.ceil((double) packages.size() / ROWS_PER_PAGE));
        if (currentPage >= maxPages) currentPage = maxPages - 1;
        if (currentPage < 0) currentPage = 0;

        int startIdx = currentPage * ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + ROWS_PER_PAGE, packages.size());

        this.clearWidgets();
        this.nameFields.clear();
        this.idScrolls.clear();

        for (int i = 0; i < (endIdx - startIdx); i++) {
            createRow(leftPos, topPos + ROW_START_Y + (i * ROW_HEIGHT), startIdx + i);
        }

        
        confirmButton = Button.builder(Component.literal("Save Selection"), b -> onSave())
                .bounds(leftPos + (GUI_WIDTH / 2) - 50, topPos + 180, 100, 20).build();
        addRenderableWidget(confirmButton);
        
        addButton = new IconButton(leftPos + BUTTON_COL_X, topPos + 180, AllIcons.I_ADD);
        addButton.withCallback(() -> { 
            saveLocalState();
            packages.add(new BuildingRoutingEntry("", "Any", true));
            currentPage = (packages.size() - 1) / ROWS_PER_PAGE;
            rebuildUI(); 
        });
        addRenderableWidget(addButton);

        if (maxPages > 1) {
            Button prevBtn = Button.builder(Component.literal("<"), b -> { 
                    saveLocalState(); 
                    currentPage--; 
                    rebuildUI(); 
                })
                .bounds(leftPos + 15, topPos + 180, 20, 20).build();
            prevBtn.active = (currentPage > 0);
            addRenderableWidget(prevBtn);

            Button nextBtn = Button.builder(Component.literal(">"), b -> { 
                    saveLocalState(); 
                    currentPage++; 
                    rebuildUI(); 
                })
                .bounds(leftPos + 40, topPos + 180, 20, 20).build();
            nextBtn.active = (currentPage < maxPages - 1);
            addRenderableWidget(nextBtn);
        }
    }

    private void createRow(int xBase, int y, int index) {
        BuildingRoutingEntry entry = packages.get(index);
        
        EditBox nameBox = new EditBox(font, xBase + NAME_BOX_X, y, NAME_BOX_W, 20, Component.literal("Name"));
        nameBox.setValue(entry.address());
        nameBox.setBordered(true);
        addRenderableWidget(nameBox);
        nameFields.add(nameBox);

        SelectionScrollInput scroll = new SelectionScrollInput(xBase + SCROLL_X, y, SCROLL_W, 20);
        List<String> options = new ArrayList<>(menu.getAvailableBuildings());
        
        if (packages.size() == 1 || options.isEmpty()) {
            options.clear();
            options.add("All Requests"); 
        } else {
            if (!entry.buildingId().isEmpty() && !options.contains(entry.buildingId())) {
                options.add(0, entry.buildingId());
            }
            if (!options.contains("Any")) options.add(0, "Any");
        }

        List<Component> comps = new ArrayList<>();
        for (String s : options) {
            if (s.contains(".") && !s.contains(" ")) {
                comps.add(Component.translatable(s));
            } else {
                comps.add(Component.literal(s));
            }
        }
        
        int stateIdx = options.indexOf(entry.buildingId());
        if (stateIdx == -1) stateIdx = 0; 
        scroll.forOptions(comps).setState(stateIdx);
        
        addRenderableWidget(scroll);
        idScrolls.add(scroll);
        
        if (packages.size() > 1) {
            IconButton del = new IconButton(xBase + BUTTON_COL_X, y, AllIcons.I_TRASH);
            del.withCallback(() -> { 
                saveLocalState();
                packages.remove(index); 
                rebuildUI(); 
            });
            addRenderableWidget(del);
        }
    }

    private void saveLocalState() {
        if (nameFields.isEmpty()) return;
        int startIdx = currentPage * ROWS_PER_PAGE;
        
        for (int i = 0; i < nameFields.size(); i++) {
            if (startIdx + i >= packages.size()) break;
            
            String name = nameFields.get(i).getValue();
            String id = "Any";
            
            if (i < idScrolls.size()) {
                SelectionScrollInput scroll = idScrolls.get(i);
                List<String> options = new ArrayList<>(menu.getAvailableBuildings());
                
                if (packages.size() == 1 || options.isEmpty()) {
                    id = "Default"; 
                } else {
                    if (!options.contains("Any")) options.add(0, "Any");
                    String oldId = packages.get(startIdx + i).buildingId();
                    if (!oldId.isEmpty() && !options.contains(oldId)) options.add(0, oldId);
                    
                    if (scroll.getState() < options.size()) {
                        id = options.get(scroll.getState());
                    }
                }
            }
            packages.set(startIdx + i, new BuildingRoutingEntry(name, id, true));
        }
    }

    private void onSave() {
        saveLocalState();
        if (menu.getBlockEntity() != null) {
            CCLPackets.sendToServer(new ConfigureRequesterPacket(menu.getBlockEntity().getBlockPos(), this.packages));
        }
        this.onClose();
    }

    private void rebuildUI() { 
        this.init(); 
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        if (scrollY != 0) {
            int maxPages = Math.max(1, (int) Math.ceil((double) packages.size() / ROWS_PER_PAGE));
            if (maxPages <= 1) return false;

            saveLocalState(); 

            if (scrollY < 0) { 
                if (currentPage < maxPages - 1) {
                    currentPage++;
                    rebuildUI();
                    return true;
                }
            } else { 
                if (currentPage > 0) {
                    currentPage--;
                    rebuildUI();
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override 
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) { 
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        
        int startIdx = currentPage * ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + ROWS_PER_PAGE, packages.size());
        
        for (int i = 0; i < (endIdx - startIdx); i++) {
            int rowY = topPos + ROW_START_Y + (i * ROW_HEIGHT);
            int boxX = leftPos + SCROLL_X;
            graphics.fill(boxX, rowY, boxX + SCROLL_W, rowY + 20, 0xFF000000); 
            graphics.fill(boxX + 1, rowY + 1, boxX + SCROLL_W - 1, rowY + 19, 0xFF303030); 
        }
    }

    @Override 
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "Logistics Controller", 80, 6, 0xFFFFFFFF, false);
        graphics.drawString(font, "Package Name", 10, 42, 0xFFFFFFFF, false);
        graphics.drawString(font, "Target Building", 119, 42, 0xFFFFFFFF, false);

        int startIdx = currentPage * ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + ROWS_PER_PAGE, packages.size());
        
        int maxPages = Math.max(1, (int) Math.ceil((double) packages.size() / ROWS_PER_PAGE));
        if (maxPages > 1) {
            graphics.drawString(font, "Page " + (currentPage + 1) + "/" + maxPages, 180, 6, 0x404040, false);
        }
        
        for (int i = 0; i < (endIdx - startIdx); i++) {
            int rowY = ROW_START_Y + (i * ROW_HEIGHT) + 6;
            graphics.drawString(font, "->", 112, rowY, 0x000000, false);
        }
    }

    @Override 
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (EditBox box : nameFields) {
            if (box.isFocused() && box.isVisible() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
                return box.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}