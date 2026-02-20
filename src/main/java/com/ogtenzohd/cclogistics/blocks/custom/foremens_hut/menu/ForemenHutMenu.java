package com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.menu;

import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.ForemenHutBlockEntity;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class ForemenHutMenu extends AbstractContainerMenu {

    public final ForemenHutBlockEntity blockEntity;

    public ForemenHutMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public ForemenHutMenu(int id, Inventory inv, BlockEntity entity) {
        super(CCLRegistration.FOREMEN_HUT_MENU.get(), id);
        this.blockEntity = (ForemenHutBlockEntity) entity;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()), player, CCLRegistration.FOREMEN_HUT_BLOCK.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
    
    public List<String> getIncomingLogs() {
        return blockEntity.getIncomingLogs();
    }
    
    public List<String> getOutgoingLogs() {
        return blockEntity.getOutgoingLogs();
    }
}
