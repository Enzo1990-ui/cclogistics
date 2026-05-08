package com.ogtenzohd.cclogistics.blocks.custom.scanner;

import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FreightScannerBlockEntity extends BeltTunnelBlockEntity {

    private final Map<String, Integer> activeScans = new HashMap<>();
    private BlockPos linkedDepot = null;

    public FreightScannerBlockEntity(BlockPos pos, BlockState state) {
        super(CCLRegistration.FREIGHT_SCANNER_BE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) return;

        Iterator<Map.Entry<String, Integer>> it = activeScans.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft <= 0) {
                it.remove();
            } else {
                entry.setValue(ticksLeft);
            }
        }

        IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);

        if (inventory != null) {
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);

                if (isPackage(stack)) {
                    String trackingId = getTrackingId(stack);

                    if (trackingId != null && !activeScans.containsKey(trackingId)) {
                        activeScans.put(trackingId, 20);

                        level.playSound(null, getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0F, 2.0F);

                        if (linkedDepot != null && level.getBlockEntity(linkedDepot) instanceof com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity depotBE) {
                            depotBE.updateTrackerByTrackingId(
                                    trackingId,
                                    com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule.TrackStatus.ON_TRAIN,
                                    "Leaving Warehouse"
                            );
                        }
                    }
                }
            }
        }
    }

    private boolean isPackage(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String hover = stack.getHoverName().getString().toLowerCase();
        return hover.contains("package") || hover.contains("box");
    }

    private String getTrackingId(ItemStack stack) {
        try {
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("cclogistics:tracking_id")) {
                    return tag.getString("cclogistics:tracking_id");
                }
            }
        } catch (Exception ignored) {}
        if (level != null) {
            try {
                HolderLookup.Provider registryAccess = level.registryAccess();
                Tag tag = stack.save(registryAccess);
                if (tag instanceof CompoundTag root && root.contains("components")) {
                    CompoundTag comps = root.getCompound("components");
                    if (comps.contains("create:package_contents")) {
                        ListTag itemList = comps.getList("create:package_contents", Tag.TAG_COMPOUND);
                        for (int i = 0; i < itemList.size(); i++) {
                            CompoundTag entry = itemList.getCompound(i);
                            if (entry.contains("item")) {
                                ItemStack parsed = ItemStack.parse(registryAccess, entry.getCompound("item")).orElse(ItemStack.EMPTY);
                                if (!parsed.isEmpty()) {
                                    net.minecraft.world.item.component.CustomData innerData = parsed.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                                    if (innerData != null) {
                                        CompoundTag innerNbt = innerData.copyTag();
                                        if (innerNbt.contains("cclogistics:tracking_id")) {
                                            return innerNbt.getString("cclogistics:tracking_id");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void setLinkedDepot(BlockPos pos) {
        this.linkedDepot = pos;
        setChanged();
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (linkedDepot != null) {
            compound.put("LinkedDepot", net.minecraft.nbt.NbtUtils.writeBlockPos(linkedDepot));
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("LinkedDepot")) {
            linkedDepot = net.minecraft.nbt.NbtUtils.readBlockPos(compound, "LinkedDepot").orElse(null);
        }
    }
}