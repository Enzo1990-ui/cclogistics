package com.ogtenzohd.cclogistics.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public class FreightTrackerModule extends AbstractBuildingModule implements IPersistentModule {

    public enum TrackStatus {
        REQUESTED(ChatFormatting.BLUE, "Requested"),
        ACCEPTED(ChatFormatting.GOLD, "Accepted"),
        NO_STOCK(ChatFormatting.RED, "No Stock"),
        RECEIVED(ChatFormatting.GREEN, "Received"),
        COMPLETED(ChatFormatting.GRAY, "Completed");

        public final ChatFormatting color;
        public final String displayName;

        TrackStatus(ChatFormatting color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }
    }

    public static class TrackedRequest {
        public String itemName;
        public int amount;
        public TrackStatus status;
        public String statusOverride;

        public TrackedRequest(String itemName, int amount, TrackStatus status, String statusOverride) {
            this.itemName = itemName;
            this.amount = amount;
            this.status = status;
            this.statusOverride = statusOverride;
        }
		
        public TrackedRequest(String itemName, int amount, TrackStatus status) {
            this(itemName, amount, status, null);
        }

        public String getStatusDisplayText() {
            return (statusOverride != null && !statusOverride.isEmpty()) ? statusOverride : status.displayName;
        }
    }

    private final List<TrackedRequest> activeRequests = new ArrayList<>();

    public void updateRequest(String itemName, int amount, TrackStatus status, String override) {
        for (TrackedRequest req : activeRequests) {
            if (req.itemName.equals(itemName)) {
                req.status = status;
                req.amount = amount;
                req.statusOverride = override;
                markModuleDirty();
                return;
            }
        }
        activeRequests.add(new TrackedRequest(itemName, amount, status, override));
        markModuleDirty();
    }

    public void updateRequest(String itemName, int amount, TrackStatus status) {
        this.updateRequest(itemName, amount, status, null);
    }

    public void removeRequest(String itemName) {
        activeRequests.removeIf(req -> req.itemName.equals(itemName));
        markModuleDirty();
    }

    public List<TrackedRequest> getActiveRequests() {
        return activeRequests;
    }

    private void markModuleDirty() {
        if (getBuilding() != null) {
            getBuilding().markDirty();
        }
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("Requests")) {
            activeRequests.clear();
            ListTag list = tag.getList("Requests", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag reqTag = list.getCompound(i);
                activeRequests.add(new TrackedRequest(
                        reqTag.getString("Item"),
                        reqTag.getInt("Amount"),
                        TrackStatus.valueOf(reqTag.getString("Status")),
                        reqTag.contains("Override") ? reqTag.getString("Override") : null
                ));
            }
        }
    }

    @Override
    public void serializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        ListTag list = new ListTag();
        for (TrackedRequest req : activeRequests) {
            CompoundTag reqTag = new CompoundTag();
            reqTag.putString("Item", req.itemName);
            reqTag.putInt("Amount", req.amount);
            reqTag.putString("Status", req.status.name());
            if (req.statusOverride != null) reqTag.putString("Override", req.statusOverride);
            list.add(reqTag);
        }
        tag.put("Requests", list);
    }

    @Override
	public void serializeToView(RegistryFriendlyByteBuf buf) {
		buf.writeInt(activeRequests.size());
		for (TrackedRequest req : activeRequests) {
			buf.writeUtf(req.itemName);
			buf.writeInt(req.amount);
			buf.writeUtf(req.status.name());
			buf.writeUtf(req.statusOverride != null ? req.statusOverride : ""); 
		}
	}
}