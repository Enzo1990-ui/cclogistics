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

public class ExpressModule extends AbstractBuildingModule implements IPersistentModule {

    private final List<String> expressBuildings = new ArrayList<>();

    public ExpressModule() {
        super();
    }

    public String getSaveKey() {
        return "cclogistics_express";
    }

    public List<String> getExpressBuildings() {
        return expressBuildings;
    }

    public boolean isExpressEnabled(String buildingName) {
        return expressBuildings.contains(buildingName);
    }

    public void toggleBuilding(String buildingName, boolean enabled) {
        if (enabled) {
            if (!expressBuildings.contains(buildingName)) {
                expressBuildings.add(buildingName);
            }
        } else {
            expressBuildings.remove(buildingName);
        }
        setChanged();
    }

    private void setChanged() {
        if (getBuilding() != null) {
            getBuilding().markDirty();
        }
    }

    public List<String> getAvailableBuildings() {
        List<String> names = new ArrayList<>();
        if (getBuilding() != null && getBuilding().getColony() != null) {
            for (var b : getBuilding().getColony().getServerBuildingManager().getBuildings().values()) {
                String uniqueName = b.getBuildingDisplayName() + ";" + b.getID();

                if (!names.contains(uniqueName)) names.add(uniqueName);
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("ExpressBuildings")) {
            expressBuildings.clear();
            ListTag list = tag.getList("ExpressBuildings", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                expressBuildings.add(list.getString(i));
            }
        }
    }

    @Override
    public void serializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ListTag list = new ListTag();
        for (String s : expressBuildings) {
            list.add(StringTag.valueOf(s));
        }
        tag.put("ExpressBuildings", list);
    }

    @Override
    public void serializeToView(RegistryFriendlyByteBuf buf) {
        buf.writeCollection(expressBuildings, (b, s) -> b.writeUtf(s));
        buf.writeCollection(getAvailableBuildings(), (b, s) -> b.writeUtf(s));
    }
}