package com.ogtenzohd.cclogistics.blocks.custom.freight_depot.menu;

import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess; 
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class FreightDepotMenu extends AbstractContainerMenu {

    public final FreightDepotBlockEntity blockEntity;
    private final ContainerData data;

    public FreightDepotMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()), new SimpleContainerData(2));
    }

    public FreightDepotMenu(int id, Inventory inv, BlockEntity entity, ContainerData data) {
    super(CCLRegistration.FREIGHT_DEPOT_MENU.get(), id);
    this.blockEntity = (FreightDepotBlockEntity) entity;
    this.data = data;
    addDataSlots(data);
    
    IItemHandler depotInv = (this.blockEntity != null) 
        ? blockEntity.getBuildingInventory() 
        : new net.neoforged.neoforge.items.ItemStackHandler(27);

    for (int j = 0; j < 3; j++) {
        for (int i = 0; i < 9; i++) {
            this.addSlot(new SlotItemHandler(depotInv, i + j * 9, 8 + i * 18, 18 + j * 18));
        }
    }

    addPlayerSlots(inv, 8, 84); 
}

private void addPlayerSlots(Inventory playerInventory, int x, int y) {
    for (int row = 0; row < 3; ++row) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new net.minecraft.world.inventory.Slot(playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
        }
    }
    for (int col = 0; col < 9; ++col) {
        this.addSlot(new net.minecraft.world.inventory.Slot(playerInventory, col, x + col * 18, y + 58));
    }
}

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.getLevel() == null) return false;
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, CCLRegistration.FREIGHT_DEPOT_BLOCK.get());
    }

    @Override
public ItemStack quickMoveStack(Player playerIn, int index) {
    ItemStack itemstack = ItemStack.EMPTY;
    net.minecraft.world.inventory.Slot slot = this.slots.get(index);
    if (slot != null && slot.hasItem()) {
        ItemStack itemstack1 = slot.getItem();
        itemstack = itemstack1.copy();
        if (index < 27) {
            if (!this.moveItemStackTo(itemstack1, 27, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(itemstack1, 0, 27, false)) return ItemStack.EMPTY; // Player -> Depot

        if (itemstack1.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
    }
    return itemstack;
}
    
    public String getColonyName() { return blockEntity != null ? blockEntity.getColonyName() : ""; }
    public String getCityTarget() { return blockEntity != null ? blockEntity.getCityTarget() : ""; }
}