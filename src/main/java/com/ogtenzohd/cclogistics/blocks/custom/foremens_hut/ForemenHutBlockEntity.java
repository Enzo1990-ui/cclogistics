package com.ogtenzohd.cclogistics.blocks.custom.foremens_hut;

import com.ogtenzohd.cclogistics.blocks.SmartColonyBlockEntity;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ForemenHutBlockEntity extends SmartColonyBlockEntity implements MenuProvider {

    private final List<String> incomingLogs = new ArrayList<>();
    private final List<String> outgoingLogs = new ArrayList<>();

    public ForemenHutBlockEntity(BlockPos pos, BlockState state) {
        super(CCLRegistration.FOREMEN_HUT_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public void addIncomingLog(String log, int maxLogs) {
        if (incomingLogs.size() >= maxLogs) {
            incomingLogs.remove(0);
        }
        incomingLogs.add(log);
        setChanged();
        
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public void addOutgoingLog(String log, int maxLogs) {
        if (outgoingLogs.size() >= maxLogs) {
            outgoingLogs.remove(0);
        }
        outgoingLogs.add(log);
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    public List<String> getIncomingLogs() {
        return incomingLogs;
    }

    public List<String> getOutgoingLogs() {
        return outgoingLogs;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        
        ListTag inList = new ListTag();
        for (String s : incomingLogs) inList.add(StringTag.valueOf(s));
        tag.put("IncomingLogs", inList);

        ListTag outList = new ListTag();
        for (String s : outgoingLogs) outList.add(StringTag.valueOf(s));
        tag.put("OutgoingLogs", outList);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        
        if (tag.contains("IncomingLogs")) {
            incomingLogs.clear();
            ListTag list = tag.getList("IncomingLogs", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) incomingLogs.add(list.getString(i));
        }

        if (tag.contains("OutgoingLogs")) {
            outgoingLogs.clear();
            ListTag list = tag.getList("OutgoingLogs", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) outgoingLogs.add(list.getString(i));
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cclogistics.foremens_hut");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return new com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.menu.ForemenHutMenu(i, inventory, this);
    }
}
