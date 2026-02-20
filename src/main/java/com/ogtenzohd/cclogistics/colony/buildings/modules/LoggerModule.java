package com.ogtenzohd.cclogistics.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public class LoggerModule extends AbstractBuildingModule implements IPersistentModule {

    private final List<String> incomingLogs = new ArrayList<>();
    private final List<String> outgoingLogs = new ArrayList<>();

    public LoggerModule() {
        super();
    }

    public String getSaveKey() {
        return "cclogistics_logger";
    }

    public void addIncomingLog(String log, int limit) {
        if (incomingLogs.size() >= limit) {
            incomingLogs.remove(0);
        }
        incomingLogs.add(log);
        setChanged();
    }

    public void addOutgoingLog(String log, int limit) {
        if (outgoingLogs.size() >= limit) {
            outgoingLogs.remove(0);
        }
        outgoingLogs.add(log);
        setChanged();
    }

    public List<String> getIncomingLogs() {
        return incomingLogs;
    }

    public List<String> getOutgoingLogs() {
        return outgoingLogs;
    }

    private void setChanged() {
        if (getBuilding() != null) {
            getBuilding().markDirty();
        }
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
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
    public void serializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ListTag inList = new ListTag();
        for (String s : incomingLogs) inList.add(StringTag.valueOf(s));
        tag.put("IncomingLogs", inList);

        ListTag outList = new ListTag();
        for (String s : outgoingLogs) outList.add(StringTag.valueOf(s));
        tag.put("OutgoingLogs", outList);
    }

    @Override
    public void serializeToView(RegistryFriendlyByteBuf buf) {
        buf.writeCollection(incomingLogs, (b, s) -> b.writeUtf(s));
        buf.writeCollection(outgoingLogs, (b, s) -> b.writeUtf(s));
    }
}