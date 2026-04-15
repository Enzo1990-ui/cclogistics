package com.ogtenzohd.cclogistics.blocks.custom.scanner;

import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashSet;
import java.util.Set;

public class FreightScannerBlockEntity extends BeltTunnelBlockEntity {

    // We keep track of UUIDs we've already scanned so it doesn't beep 50 times while the box is moving!
    private final Set<String> recentlyScanned = new HashSet<>();
    private int cooldown = 0;
    private BlockPos linkedDepot = null;

    public FreightScannerBlockEntity(BlockPos pos, BlockState state) {
        super(CCLRegistration.FREIGHT_SCANNER_BE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) return;

        // Memory cleanup so the scanner can scan new boxes eventually
        if (cooldown > 0) {
            cooldown--;
            if (cooldown == 0) {
                recentlyScanned.clear();
            }
        }

        // --- FIX: Read the inventory of the block directly below the scanner! ---
        IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);

        if (inventory != null) {
            // Scan everything currently sliding underneath us
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);

                if (isPackage(stack)) {
                    String trackingId = getTrackingId(stack);

                    if (trackingId != null && !recentlyScanned.contains(trackingId)) {
                        recentlyScanned.add(trackingId);
                        cooldown = 40; // Gives it a 2-second memory to avoid spam beeping

                        // PLAY THE BEEP SOUND! (High pitch UI click sounds exactly like a scanner)
                        level.playSound(null, getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0F, 2.0F);

                        if (linkedDepot != null && level.getBlockEntity(linkedDepot) instanceof com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity depotBE) {
                            // Ping the depot to update the database!
                            depotBE.updateTrackerByTrackingId(
                                    trackingId,
                                    com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule.TrackStatus.IN_TRANSIT,
                                    "En Route"
                            );
                        }
                    }
                }
            }
        }
    }

    // Checks if the item is a Create package
    private boolean isPackage(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String hover = stack.getHoverName().getString().toLowerCase();
        return hover.contains("package") || hover.contains("box");
    }

    // Safely extracts our custom UUID from the package
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