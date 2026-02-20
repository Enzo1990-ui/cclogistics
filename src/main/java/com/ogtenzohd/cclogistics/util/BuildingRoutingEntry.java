package com.ogtenzohd.cclogistics.util;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record BuildingRoutingEntry(String address, String buildingId, boolean enabled) {

    public BuildingRoutingEntry(String address, String buildingId) {
        this(address, buildingId, true);
    }
    // -------------------------------------------------------------------

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Address", address);
        tag.putString("BuildingId", buildingId);
        tag.putBoolean("Enabled", enabled);
        return tag;
    }

    public static BuildingRoutingEntry deserialize(CompoundTag tag) {
        return new BuildingRoutingEntry(
            tag.getString("Address"),
            tag.getString("BuildingId"),
            !tag.contains("Enabled") || tag.getBoolean("Enabled")
        );
    }

    public static final StreamCodec<ByteBuf, BuildingRoutingEntry> STREAM_CODEC = StreamCodec.composite(
         ByteBufCodecs.STRING_UTF8, BuildingRoutingEntry::address,
         ByteBufCodecs.STRING_UTF8, BuildingRoutingEntry::buildingId,
         ByteBufCodecs.BOOL, BuildingRoutingEntry::enabled,
         BuildingRoutingEntry::new
    );
}