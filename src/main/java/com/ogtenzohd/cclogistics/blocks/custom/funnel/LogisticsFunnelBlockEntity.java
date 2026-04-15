package com.ogtenzohd.cclogistics.blocks.custom.funnel;

import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;

public class LogisticsFunnelBlockEntity extends FunnelBlockEntity {

    private BlockPos linkedDepot = null;

    public LogisticsFunnelBlockEntity(BlockPos pos, BlockState state) {
        super(CCLRegistration.LOGISTICS_FUNNEL_BE.get(), pos, state);
    }

    public void setLinkedDepot(BlockPos pos) {
        this.linkedDepot = pos;
        setChanged();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) return;

        // Scan the packager every half-second to catch packages right before they drop
        if (level.getGameTime() % 10 != 0) return;

        Direction facing = getBlockState().getValue(FunnelBlock.FACING);
        Direction pullDirection = facing.getOpposite(); // The block behind the funnel
        BlockPos sourcePos = worldPosition.relative(pullDirection);

        IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, facing);
        if (inventory != null) {
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);

                if (isPackage(stack) && getTrackingId(stack) == null) {
                    // 1. Generate a unique 6-character alphanumeric ID
                    String newId = "PKG-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

                    // 2. Stamp it onto the package's NBT
                    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                        tag.putString("cclogistics:tracking_id", newId);
                    });

                    // 3. Extract the real contents to update the database
                    if (linkedDepot != null && level.getBlockEntity(linkedDepot) instanceof FreightDepotBlockEntity depotBE) {
                        String itemName = getPrimaryItemName(stack);
                        int amount = getPrimaryItemAmount(stack);

                        // Ping the Depot to log it as Dispatched!
                        depotBE.updateTracker(
                                newId,
                                itemName,
                                amount,
                                com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule.TrackStatus.ACCEPTED,
                                "Dispatched"
                        );
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
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("cclogistics:tracking_id")) {
                    return tag.getString("cclogistics:tracking_id");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getPrimaryItemName(ItemStack stack) {
        try {
            HolderLookup.Provider registryAccess = level.registryAccess();
            Tag tag = stack.save(registryAccess);
            if (tag instanceof CompoundTag root && root.contains("components")) {
                CompoundTag comps = root.getCompound("components");
                if (comps.contains("create:package_contents")) {
                    ListTag itemList = comps.getList("create:package_contents", Tag.TAG_COMPOUND);
                    if (!itemList.isEmpty()) {
                        CompoundTag firstItem = itemList.getCompound(0);
                        if (firstItem.contains("item")) {
                            ItemStack parsed = ItemStack.parse(registryAccess, firstItem.getCompound("item")).orElse(ItemStack.EMPTY);
                            if (!parsed.isEmpty()) return parsed.getHoverName().getString();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Mixed Goods";
    }

    private int getPrimaryItemAmount(ItemStack stack) {
        int total = 0;
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
                            if (!parsed.isEmpty()) total += parsed.getCount();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return total > 0 ? total : 1;
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