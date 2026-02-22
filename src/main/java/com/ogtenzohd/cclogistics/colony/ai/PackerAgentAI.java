package com.ogtenzohd.cclogistics.colony.ai;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.colony.job.PackerAgentJob;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;

public class PackerAgentAI extends AbstractEntityAIBasic<PackerAgentJob, FreightDepotBuilding> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum State {
        IDLE,
        TO_DEPOT_IMPORT,
        AT_DEPOT_IMPORT,
        TO_WAREHOUSE,
        AT_WAREHOUSE,
        TO_DEPOT_EXCESS,
        AT_DEPOT_EXCESS,
        TO_DEPOT_EXPORT,
        AT_DEPOT_EXPORT
    }

    private State state = State.IDLE;
    private int delay = 0;
    private BlockPos currentTarget = null;
    
    private List<ItemStack> holdingItems = new ArrayList<>();

    public PackerAgentAI(PackerAgentJob job) {
        super(job);
    }

    @Override
    public Class<FreightDepotBuilding> getExpectedBuildingClass() {
        return FreightDepotBuilding.class;
    }

    public void tick() {
        if (delay > 0) {
            delay--;
            return;
        }

        switch (state) {
            case IDLE:
                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Starting cycle, moving to DEPOT IMPORT");
                state = State.TO_DEPOT_IMPORT;
                break;
            
            case TO_DEPOT_IMPORT:
                com.minecolonies.api.colony.buildings.IBuilding depot = job.getWorkBuilding();
                if (depot != null) {
                    currentTarget = depot.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT_IMPORT;
                        delay = 20;
                    }
                }
                break;

            case AT_DEPOT_IMPORT:
                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE) {
                        IItemHandler importInv = depotBE.getImportInventory();
                        if (importInv != null) {
                            for (int i = 0; i < importInv.getSlots(); i++) {
                                ItemStack extracted = importInv.extractItem(i, 64, false);
                                if (!extracted.isEmpty()) {
                                    holdingItems.add(extracted);
                                    
                                    // Make the worker visually hold the package (or item) in their hand
                                    job.getCitizen().getEntity().ifPresent(entity -> {
                                        entity.setItemInHand(InteractionHand.MAIN_HAND, extracted.copy());
                                    });

                                    if (isPackage(extracted)) {
                                        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Picked up package, moving to warehouse: " + extracted.getHoverName().getString());
                                    }
                                    
                                    state = State.TO_WAREHOUSE;
                                    return;
                                }
                            }
                        }
                    }
                }
                
                state = State.TO_DEPOT_EXCESS; 
                break;

            case TO_WAREHOUSE:
                com.minecolonies.api.colony.buildings.IBuilding warehouse = job.getColony().getServerBuildingManager().getBuildings().values().stream()
                    .filter(b -> b.getSchematicName().contains("warehouse"))
                    .findFirst().orElse(null);

                if (warehouse != null) {
                    currentTarget = warehouse.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_WAREHOUSE;
                        delay = 20;
                    }
                } else {
                    state = State.TO_DEPOT_IMPORT; 
                }
                break;

            case AT_WAREHOUSE:
                if (!holdingItems.isEmpty()) {
                    if (currentTarget != null && job.getColony().getWorld() != null) {
                        BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                        if (be != null) {
                            IItemHandler handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
                            if (handler != null) {
                                List<ItemStack> remainingList = new ArrayList<>();
                                
                                for (ItemStack stack : holdingItems) {
                                    // If it's a package, unpack it NOW at the warehouse
                                    if (isPackage(stack)) {
                                        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Unpacking package at warehouse.");
                                        List<ItemStack> contents = unpack(stack);
                                        for (ItemStack unpackedItem : contents) {
                                            ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, unpackedItem, false);
                                            if (!remaining.isEmpty()) {
                                                remainingList.add(remaining); // Keep what didn't fit
                                            }
                                        }
                                    } else {
                                        // Standard item, just insert it
                                        ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, stack, false);
                                        if (!remaining.isEmpty()) {
                                            remainingList.add(remaining);
                                        }
                                    }
                                }
                                holdingItems = remainingList;
                            }
                        }
                    }
                }
                
                // Clear the worker's hand if they successfully dropped off the items
                if (holdingItems.isEmpty()) {
                    job.getCitizen().getEntity().ifPresent(entity -> {
                        entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    });
                }
                
                state = State.TO_DEPOT_IMPORT; 
                break;

            case TO_DEPOT_EXCESS:
                if (!holdingItems.isEmpty()) {
                    for (ItemStack s : holdingItems) {
                         if (job.getCitizen().getEntity().isPresent()) {
                             Containers.dropItemStack(job.getColony().getWorld(), job.getCitizen().getEntity().get().getX(), job.getCitizen().getEntity().get().getY(), job.getCitizen().getEntity().get().getZ(), s);
                         }
                    }
                    holdingItems.clear();
                    job.getCitizen().getEntity().ifPresent(entity -> entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
                }

                com.minecolonies.api.colony.buildings.IBuilding depotExcess = job.getWorkBuilding();
                if (depotExcess != null) {
                    currentTarget = depotExcess.getPosition(); 
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT_EXCESS;
                        delay = 20;
                    }
                }
                break;

            case AT_DEPOT_EXCESS:
                com.minecolonies.api.colony.buildings.IBuilding workBuilding = job.getWorkBuilding();
                
                String targetAddress = "Colony_Storage";
                if (workBuilding != null && job.getColony().getWorld() != null) {
                    BlockEntity mainBE = job.getColony().getWorld().getBlockEntity(workBuilding.getPosition());
                    if (mainBE instanceof FreightDepotBlockEntity fbe) {
                        targetAddress = fbe.getCityTarget();
                    }
                }

                if (workBuilding != null) {
                    for (BlockPos containerPos : workBuilding.getContainers()) {
                        
                        IItemHandler inv = job.getColony().getWorld().getCapability(Capabilities.ItemHandler.BLOCK, containerPos, null);
                        
                        if (inv == null) {
                             BlockEntity be = job.getColony().getWorld().getBlockEntity(containerPos);
                             if (be instanceof FreightDepotBlockEntity fbe) {
                                 inv = fbe.getBuildingInventory();
                             }
                        }

                        if (inv != null) {
                            for (int s = 0; s < inv.getSlots(); s++) {
                                ItemStack check = inv.extractItem(s, 64, true);
                                if (!check.isEmpty()) {
                                    ItemStack extracted = inv.extractItem(s, 64, false);
                                    
                                    if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Found Excess Item in container " + containerPos + ": " + extracted.getHoverName().getString());
                                    
                                    ItemStack pkg = pack(extracted, targetAddress);
                                    
                                    if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Packed into: " + pkg.getHoverName().getString() + " for: " + targetAddress);
                                    holdingItems.add(pkg);
                                    
                                    // Visually hold the newly made export package
                                    job.getCitizen().getEntity().ifPresent(entity -> {
                                        entity.setItemInHand(InteractionHand.MAIN_HAND, pkg.copy());
                                    });
                                    
                                    state = State.TO_DEPOT_EXPORT;
                                    return;
                                }
                            }
                        }
                    }
                }
                
                delay = 100;
                state = State.IDLE;
                break;

            case TO_DEPOT_EXPORT:
                com.minecolonies.api.colony.buildings.IBuilding depotExport = job.getWorkBuilding();
                if (depotExport != null) {
                    currentTarget = depotExport.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT_EXPORT;
                        delay = 20;
                    }
                }
                break;

            case AT_DEPOT_EXPORT:
                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE) {
                        IItemHandler exportInv = depotBE.getExportInventory();
                        if (exportInv != null && !holdingItems.isEmpty()) {
                            List<ItemStack> remainingList = new ArrayList<>();
                            for (ItemStack stack : holdingItems) {
                                ItemStack remaining = ItemHandlerHelper.insertItemStacked(exportInv, stack, false);
                                
                                int packagesSent = stack.getCount() - remaining.getCount();
                                if (packagesSent > 0) {
                                    String logName = stack.getHoverName().getString();
                                    int logCount = packagesSent;

                                    if (isPackage(stack)) {
                                        List<ItemStack> contents = unpack(stack);
                                        if (!contents.isEmpty()) {
                                            ItemStack inner = contents.get(0);
                                            logName = inner.getHoverName().getString();
                                            logCount = inner.getCount() * packagesSent;
                                        }
                                    }
                                    
                                    depotBE.addOutgoingLog("Exported: " + logName + " x" + logCount, 100);
                                }

                                if (!remaining.isEmpty()) {
                                    remainingList.add(remaining);
                                }
                            }
                            holdingItems = remainingList;
                            
                            // Clear hand if all packages exported successfully
                            if (holdingItems.isEmpty()) {
                                job.getCitizen().getEntity().ifPresent(entity -> {
                                    entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                                });
                            }
                        }
                    }
                }
                state = State.IDLE;
                break;
        }
    }

    private boolean isPackage(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("create") && (id.getPath().contains("package") || id.getPath().contains("box"));
    }
    
    private List<ItemStack> unpack(ItemStack stack) {
        List<ItemStack> contents = new ArrayList<>();
        HolderLookup.Provider registryAccess = job.getColony().getWorld().registryAccess();
        
        try {
            Tag tag = stack.save(registryAccess);
            if (tag instanceof CompoundTag root) {
                CompoundTag components = root.getCompound("components");
                
                if (components.contains("create:package_contents")) {
                    ListTag itemList = components.getList("create:package_contents", Tag.TAG_COMPOUND);
                    for (int i = 0; i < itemList.size(); i++) {
                        CompoundTag entry = itemList.getCompound(i);
                        if (entry.contains("item")) {
                            CompoundTag itemTag = entry.getCompound("item");
                            ItemStack parsed = ItemStack.parse(registryAccess, itemTag).orElse(ItemStack.EMPTY);
                            if (!parsed.isEmpty()) {
                                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("   -> Unpacked: " + parsed.getHoverName().getString() + " x" + parsed.getCount());
                                contents.add(parsed);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.error("[Unpack] Error parsing Create package", e);
        }
        
        if (contents.isEmpty()) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("Items")) {
                     ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
                     for (int i = 0; i < list.size(); i++) contents.add(ItemStack.parse(registryAccess, list.getCompound(i)).orElse(ItemStack.EMPTY));
                } else if (tag.contains("Contents")) {
                     ListTag list = tag.getList("Contents", Tag.TAG_COMPOUND);
                     for (int i = 0; i < list.size(); i++) contents.add(ItemStack.parse(registryAccess, list.getCompound(i)).orElse(ItemStack.EMPTY));
                }
            }
        }

        return contents;
    }
    
    private ItemStack pack(ItemStack content, String targetAddress) {
        HolderLookup.Provider registryAccess = job.getColony().getWorld().registryAccess();

        ItemStack pkg = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("create:cardboard_package_12x10")));
        if (pkg.isEmpty()) {
             if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.error("[PackerAI] CRITICAL: Could not find 'create:cardboard_package_12x10'.");
             return ItemStack.EMPTY; 
        }

        Tag rawTag = pkg.save(registryAccess);
        if (!(rawTag instanceof CompoundTag root)) {
            return ItemStack.EMPTY;
        }

        CompoundTag components;
        if (root.contains("components")) {
            components = root.getCompound("components");
        } else {
            components = new CompoundTag();
            root.put("components", components);
        }

        ListTag contentsList = new ListTag();
        CompoundTag entry = new CompoundTag();
        
        entry.put("item", content.save(registryAccess));
        
        entry.putInt("slot", 0); 
        
        contentsList.add(entry);
        components.put("create:package_contents", contentsList);

        if (targetAddress != null && !targetAddress.isEmpty()) {
            components.putString("create:package_address", targetAddress);
        }

        return ItemStack.parse(registryAccess, root).orElse(ItemStack.EMPTY);
    }

    public void write(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("State", state.ordinal());
        tag.putInt("Delay", delay);
        if (currentTarget != null) {
            tag.put("Target", NbtUtils.writeBlockPos(currentTarget));
        }
        ListTag list = new ListTag();
        for (ItemStack s : holdingItems) list.add(s.save(provider));
        tag.put("HoldingItems", list);
    }

    public void read(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("State")) {
            state = State.values()[tag.getInt("State")];
        }
        if (tag.contains("Delay")) {
            delay = tag.getInt("Delay");
        }
        if (tag.contains("Target")) {
            currentTarget = NbtUtils.readBlockPos(tag, "Target").orElse(null);
        }
        if (tag.contains("HoldingItems")) {
            holdingItems.clear();
            ListTag list = tag.getList("HoldingItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) holdingItems.add(ItemStack.parse(provider, list.getCompound(i)).orElse(ItemStack.EMPTY));
        }
    }
    
    private void moveTo(BlockPos pos) {
        job.getCitizen().getEntity().ifPresent(entity -> {
            entity.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0);
        });
    }
    
    private boolean isAt(BlockPos pos) {
        return job.getCitizen().getEntity().map(e -> e.blockPosition().closerThan(pos, 3.0)).orElse(false);
    }
}