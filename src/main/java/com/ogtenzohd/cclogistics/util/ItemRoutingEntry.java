package com.ogtenzohd.cclogistics.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public class ItemRoutingEntry {
    public String address;
    public ItemStack filterItem;

    public ItemRoutingEntry(String address, ItemStack filterItem) {
        this.address = address;
        this.filterItem = filterItem;
    }

    public static ItemRoutingEntry deserialize(CompoundTag tag, HolderLookup.Provider provider) {
        String addr = tag.getString("Address");
        ItemStack item = ItemStack.parse(provider, tag.getCompound("Item")).orElse(ItemStack.EMPTY);
        return new ItemRoutingEntry(addr, item);
    }

    public CompoundTag serialize(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Address", address);
        if (!filterItem.isEmpty()) {
            tag.put("Item", filterItem.save(provider));
        }
        return tag;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemRoutingEntry> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, p -> p.address,
        ItemStack.STREAM_CODEC, p -> p.filterItem,
        ItemRoutingEntry::new
    );
}