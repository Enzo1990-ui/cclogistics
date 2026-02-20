package com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.menu;

import java.util.ArrayList;
import java.util.List;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlockEntity;
import com.ogtenzohd.cclogistics.util.BuildingRoutingEntry;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class LogisticsControllerMenu extends AbstractContainerMenu {
    public final LogisticsControllerBlockEntity blockEntity;
    private final ContainerData data;

    public LogisticsControllerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, (LogisticsControllerBlockEntity) inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(2));
    }

    public LogisticsControllerMenu(int id, Inventory inv, LogisticsControllerBlockEntity entity, ContainerData data) {
        super(CCLRegistration.LOGISTICS_CONTROLLER_MENU.get(), id);
        this.blockEntity = entity;
        this.data = data;
        
        checkContainerDataCount(data, 2);
        addDataSlots(data);
        
        if (entity != null && !inv.player.level().isClientSide) {
            entity.syncBuildingsToPlayer(inv.player);
        }
    }

    public List<BuildingRoutingEntry> getPackages() { 
        return blockEntity != null ? blockEntity.getPackages() : new ArrayList<>(); 
    }
    
    public List<String> getAvailableBuildings() { 
        return blockEntity != null ? blockEntity.getAvailableBuildings() : new ArrayList<>(); 
    }
    
    public LogisticsControllerBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        return ItemStack.EMPTY; 
    }

    @Override
    public boolean stillValid(Player player) {
        return true; 
    }
}