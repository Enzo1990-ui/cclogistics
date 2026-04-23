package com.ogtenzohd.cclogistics.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class LogisticsModule extends AbstractBuildingModule implements IPersistentModule {

    private String colonyName = "";
    private String createTarget = "";
    private int playerStockTarget = 128;
    private boolean instantDispatch = false; // NEW

    public LogisticsModule() { super(); }

    public String getSaveKey() {
        return "cclogistics_module";
    }

    public void setChanged() {
        if (getBuilding() != null) {
            getBuilding().markDirty();
        }
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("colonyName")) {
            this.colonyName = tag.getString("colonyName");
        }
        if (tag.contains("createTarget")) {
            this.createTarget = tag.getString("createTarget");
        }
        if (tag.contains("playerStockTarget")) {
            this.playerStockTarget = tag.getInt("playerStockTarget");
        }
        if (tag.contains("instantDispatch")) {
            this.instantDispatch = tag.getBoolean("instantDispatch");
        }
    }

    @Override
    public void serializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        tag.putString("colonyName", this.colonyName);
        tag.putString("createTarget", this.createTarget);
        tag.putInt("playerStockTarget", this.playerStockTarget);
        tag.putBoolean("instantDispatch", this.instantDispatch);
    }

    @Override
    public void serializeToView(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.colonyName);
        buf.writeUtf(this.createTarget);
        buf.writeInt(this.playerStockTarget);
        buf.writeBoolean(this.instantDispatch);
    }

    public String getColonyName() { return colonyName; }
    public void setColonyName(String colonyName) {
        this.colonyName = colonyName;
        setChanged();
    }

    public String getCreateTarget() { return createTarget; }
    public void setCreateTarget(String createTarget) {
        this.createTarget = createTarget;
        setChanged();
    }

    public int getPlayerStockTarget() { return playerStockTarget; }
    public void setPlayerStockTarget(int target) {
        this.playerStockTarget = target;
        setChanged();
    }

    public boolean isInstantDispatch() { return instantDispatch; }
    public void setInstantDispatch(boolean instantDispatch) {
        this.instantDispatch = instantDispatch;
        setChanged();
    }
}