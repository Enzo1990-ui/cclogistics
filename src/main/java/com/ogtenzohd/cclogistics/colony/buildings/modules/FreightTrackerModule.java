package com.ogtenzohd.cclogistics.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FreightTrackerModule extends AbstractBuildingModule implements IPersistentModule {

    public enum TrackStatus {
        REQUESTED("§e[Requested]"),
        RECEIVED("§e[Ticker Received]"),
        ACCEPTED("§a[En Route]"),
        IN_TRANSIT("§b[Depot Received]"),
        DELIVERING("§d[Delivering]"),
        NO_STOCK("§c[Out of Stock]"),
        COMPLETED("§2[Complete]");

        public final String display;
        TrackStatus(String display) { this.display = display; }
    }

    public static class TrackedReq {
        public String itemName;
        public int amount;
        public TrackStatus status;
        public String override;
        public long timestamp;
        public boolean isIncoming = true; //
    }

    private final Map<String, TrackedReq> requests = new ConcurrentHashMap<>();
    private boolean isPurgedV2 = false;

    public FreightTrackerModule() {
        super();
    }

    public String getSaveKey() {
        return "cclogistics_tracker";
    }

    public Map<String, TrackedReq> getRequests() {
        return requests;
    }

    public void purgeOldLogs() {
        requests.clear();
        isPurgedV2 = true;
        setChanged();
    }

    public void removeRequest(String id) {
        requests.remove(id);
        setChanged();
    }

    public void updateRequest(String id, String name, int amt, TrackStatus stat, String override, boolean isIncoming) {
        TrackedReq r = requests.computeIfAbsent(id, k -> new TrackedReq());
        if (name != null && !name.equals("Unknown") && !name.equals("Request")) {
            r.itemName = name;
        } else if (r.itemName == null) {
            r.itemName = "Pending Item...";
        }

        if (amt > 0) r.amount = amt;

        if (r.status != TrackStatus.COMPLETED || stat == TrackStatus.COMPLETED) {
            r.status = stat;
        }
        r.override = override != null ? override : "";
        r.isIncoming = isIncoming;
        r.timestamp = System.currentTimeMillis();
        setChanged();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        boolean removed = requests.entrySet().removeIf(e ->
                (e.getValue().status == TrackStatus.COMPLETED)
                        && (now - e.getValue().timestamp > 120000));

        if (removed) setChanged();
    }

    private void setChanged() {
        if (getBuilding() != null) {
            getBuilding().markDirty();
        }
    }

    @Override
    public void serializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        tag.putBoolean("FormatPurgedV2", isPurgedV2);
        ListTag list = new ListTag();
        for (Map.Entry<String, TrackedReq> e : requests.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("ID", e.getKey());
            if (e.getValue().itemName != null) t.putString("Name", e.getValue().itemName);
            t.putInt("Amt", e.getValue().amount);
            t.putInt("Stat", e.getValue().status.ordinal());
            if (e.getValue().override != null) t.putString("Over", e.getValue().override);
            t.putLong("Time", e.getValue().timestamp);
            t.putBoolean("IsIncoming", e.getValue().isIncoming);
            list.add(t);
        }
        tag.put("TrackerV2", list);
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        if (!tag.getBoolean("FormatPurgedV2")) {
            purgeOldLogs();
            return;
        }
        isPurgedV2 = tag.getBoolean("FormatPurgedV2");
        if (tag.contains("TrackerV2")) {
            ListTag list = tag.getList("TrackerV2", Tag.TAG_COMPOUND);
            requests.clear();
            for (int i=0; i<list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                TrackedReq r = new TrackedReq();
                if (t.contains("Name")) r.itemName = t.getString("Name");
                r.amount = t.getInt("Amt");
                int statIdx = t.getInt("Stat");
                if (statIdx >= 0 && statIdx < TrackStatus.values().length) {
                    r.status = TrackStatus.values()[statIdx];
                } else {
                    r.status = TrackStatus.REQUESTED;
                }

                if (t.contains("Over")) r.override = t.getString("Over");
                r.timestamp = t.getLong("Time");
                if (t.contains("IsIncoming")) {
                    r.isIncoming = t.getBoolean("IsIncoming");
                } else {
                    r.isIncoming = true;
                }

                requests.put(t.getString("ID"), r);
            }
        }
    }

    @Override
    public void serializeToView(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requests.size());
        for (Map.Entry<String, TrackedReq> e : requests.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue().itemName != null ? e.getValue().itemName : "Unknown");
            buf.writeInt(e.getValue().amount);
            buf.writeInt(e.getValue().status.ordinal());
            buf.writeUtf(e.getValue().override != null ? e.getValue().override : "");
            buf.writeLong(e.getValue().timestamp);
            buf.writeBoolean(e.getValue().isIncoming);
        }
    }
}