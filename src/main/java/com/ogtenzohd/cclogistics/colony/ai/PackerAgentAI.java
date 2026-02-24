package com.ogtenzohd.cclogistics.colony.ai;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.api.entity.citizen.Skill;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
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
        IDLE, TO_DEPOT_IMPORT, AT_DEPOT_IMPORT, TO_WAREHOUSE, AT_WAREHOUSE,
        TO_DEPOT_EXCESS, AT_DEPOT_EXCESS, TO_DEPOT_EXPORT, AT_DEPOT_EXPORT
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
    
    private int getSkillLevel(Skill skill) {
        if (job.getCitizen() == null || job.getCitizen().getCitizenSkillHandler() == null) return 1;
        return job.getCitizen().getCitizenSkillHandler().getLevel(skill);
    }

    // --- TRACKING HELPER METHOD ---
    private void updateTracker(String itemName, int amount, FreightTrackerModule.TrackStatus status) {
        if (job.getWorkBuilding() != null) {
            FreightTrackerModule module = job.getWorkBuilding().getModule(FreightTrackerModule.class);
            if (module != null) {
                if (status == FreightTrackerModule.TrackStatus.COMPLETED) {
                    module.removeRequest(itemName);
                } else {
                    module.updateRequest(itemName, amount, status);
                }
            }
        }
    }

    public void tick() {
        if (!holdingItems.isEmpty()) {
            job.getCitizen().getEntity().ifPresent(entity -> {
                if (entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
                    entity.setItemInHand(InteractionHand.MAIN_HAND, holdingItems.get(0).copy());
                }
            });
        }

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
                            List<ItemStack> rawItemsToPack = new ArrayList<>();
                            
                            int maxCapacity = 2; 
                            if (CCLConfig.INSTANCE.enableSkillScaling.get()) {
                                maxCapacity += getSkillLevel(Skill.Strength) * CCLConfig.INSTANCE.packerCapacityPerStrength.get();
                            }
                            maxCapacity = Math.min(maxCapacity, 9);
                            
                            for (int i = 0; i < importInv.getSlots(); i++) {
                                if (rawItemsToPack.size() >= maxCapacity) break;
                                
                                ItemStack check = importInv.extractItem(i, 64, true);
                                if (!check.isEmpty()) {
                                    if (isPackage(check)) {
                                        if (!rawItemsToPack.isEmpty()) break;
                                        
                                        ItemStack extracted = importInv.extractItem(i, 64, false);
                                        holdingItems.add(extracted);
                                        job.getCitizen().getEntity().ifPresent(entity -> {
                                            entity.setItemInHand(InteractionHand.MAIN_HAND, extracted.copy());
                                        });
                                        
                                        // --- TRACKER: Package picked up! Mark as RECEIVED (Green) ---
                                        List<ItemStack> peekContents = unpack(extracted);
                                        for (ItemStack content : peekContents) {
                                            updateTracker(content.getHoverName().getString(), content.getCount(), FreightTrackerModule.TrackStatus.RECEIVED);
                                        }

                                        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Picked up package, moving to warehouse: " + extracted.getHoverName().getString());
                                        state = State.TO_WAREHOUSE;
                                        return;
                                    } else {
                                        rawItemsToPack.add(importInv.extractItem(i, 64, false));
                                    }
                                }
                            }
                            
                            if (!rawItemsToPack.isEmpty()) {
                                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Found " + rawItemsToPack.size() + " loose stacks in import vault. Packing them as a fallback!");
                                ItemStack fallbackPkg = pack(rawItemsToPack, ""); 
                                holdingItems.add(fallbackPkg);
                                job.getCitizen().getEntity().ifPresent(entity -> {
                                    entity.setItemInHand(InteractionHand.MAIN_HAND, fallbackPkg.copy());
                                });
                                state = State.TO_WAREHOUSE;
                                return;
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
                                    if (isPackage(stack)) {
                                        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Unpacking package at warehouse.");
                                        List<ItemStack> contents = unpack(stack);
                                        for (ItemStack unpackedItem : contents) {
                                            ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, unpackedItem, false);
                                            
                                            // --- TRACKER: Item has entered warehouse, remove it from list! ---
                                            int amountInserted = unpackedItem.getCount() - remaining.getCount();
                                            if (amountInserted > 0) {
                                                updateTracker(unpackedItem.getHoverName().getString(), amountInserted, FreightTrackerModule.TrackStatus.COMPLETED);
                                            }

                                            if (!remaining.isEmpty()) {
                                                remainingList.add(remaining); 
                                            }
                                        }
                                    } else {
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
                            List<ItemStack> toPack = new ArrayList<>();
                            
                            int maxCapacity = 2; 
                            if (CCLConfig.INSTANCE.enableSkillScaling.get()) {
                                maxCapacity += getSkillLevel(Skill.Strength) * CCLConfig.INSTANCE.packerCapacityPerStrength.get();
                            }
                            maxCapacity = Math.min(maxCapacity, 9);
                            
                            for (int s = 0; s < inv.getSlots(); s++) {
                                if (toPack.size() >= maxCapacity) break; 
                                
                                ItemStack check = inv.extractItem(s, 64, true);
                                if (!check.isEmpty()) {
                                    if (isPackage(check)) {
                                        if (!toPack.isEmpty()) break;
                                        
                                        ItemStack extracted = inv.extractItem(s, 64, false);
                                        holdingItems.add(extracted);
                                        job.getCitizen().getEntity().ifPresent(entity -> {
                                            entity.setItemInHand(InteractionHand.MAIN_HAND, extracted.copy());
                                        });
                                        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Found pre-boxed package in excess storage, exporting directly!");
                                        state = State.TO_DEPOT_EXPORT;
                                        return;
                                    } else {
                                        toPack.add(inv.extractItem(s, 64, false));
                                    }
                                }
                            }
                            
                            if (!toPack.isEmpty()) {
                                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Packing " + toPack.size() + " excess stacks from container " + containerPos);
                                
                                ItemStack pkg = pack(toPack, targetAddress);
                                
                                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[PackerAI] Packed massive bundle for: " + targetAddress);
                                holdingItems.add(pkg);
                                
                                job.getCitizen().getEntity().ifPresent(entity -> {
                                    entity.setItemInHand(InteractionHand.MAIN_HAND, pkg.copy());
                                });
                                
                                state = State.TO_DEPOT_EXPORT;
                                return;
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
                                    
                                    depotBE.addOutgoingLog("OUT;Exported: " + logName + " x" + logCount, 100);
                                }

                                if (!remaining.isEmpty()) {
                                    remainingList.add(remaining);
                                }
                            }
                            holdingItems = remainingList;
                            
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
        if (stack.isEmpty()) return false;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String namespace = id.getNamespace().toLowerCase();
            String path = id.getPath().toLowerCase();
            
            if ((namespace.equals("create") || namespace.equals("ccomunityboxes")) && 
                (path.contains("package") || path.contains("box"))) {
                return true;
            }
        }

        String hoverName = stack.getHoverName().getString().toLowerCase();
        if (hoverName.contains("cardboard package") || hoverName.equals("package") || hoverName.contains("create:package") || hoverName.contains("box")) {
            return true;
        }

        try {
            HolderLookup.Provider registryAccess = job.getColony().getWorld().registryAccess();
            Tag tag = stack.save(registryAccess);
            if (tag instanceof CompoundTag root) {
                if (root.contains("components")) {
                    CompoundTag components = root.getCompound("components");
                    if (components.contains("create:package_contents")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {}

        return false;
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
    
    private ItemStack pack(List<ItemStack> contents, String targetAddress) {
        HolderLookup.Provider registryAccess = job.getColony().getWorld().registryAccess();

        ItemStack pkg = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("create:cardboard_package_12x10")));
        if (pkg.isEmpty() || pkg.getItem() == Items.AIR) {
             pkg = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("create:package")));
             if (pkg.isEmpty() || pkg.getItem() == Items.AIR) return ItemStack.EMPTY; 
        }

        Tag rawTag = pkg.save(registryAccess);
        if (!(rawTag instanceof CompoundTag root)) return ItemStack.EMPTY;

        CompoundTag components = root.contains("components") ? root.getCompound("components") : new CompoundTag();
        root.put("components", components);

        ListTag contentsList = new ListTag();
        
        for (int i = 0; i < contents.size(); i++) {
            CompoundTag entry = new CompoundTag();
            entry.put("item", contents.get(i).save(registryAccess));
            entry.putInt("slot", i); 
            contentsList.add(entry);
        }
        
        components.put("create:package_contents", contentsList);

        if (targetAddress != null && !targetAddress.isEmpty()) {
            components.putString("create:package_address", targetAddress);
        }

        return ItemStack.parse(registryAccess, root).orElse(ItemStack.EMPTY);
    }

    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("State", state.ordinal());
        tag.putInt("Delay", delay);
        if (currentTarget != null) {
            tag.put("Target", NbtUtils.writeBlockPos(currentTarget));
        }
        
        ListTag list = new ListTag();
        for (ItemStack s : holdingItems) {
            list.add(s.save(provider));
        }
        tag.put("HoldingItems", list);
    }

    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
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
            for (int i = 0; i < list.size(); i++) {
                holdingItems.add(ItemStack.parse(provider, list.getCompound(i)).orElse(ItemStack.EMPTY));
            }
        }
    }
    
    private void moveTo(BlockPos pos) {
        job.getCitizen().getEntity().ifPresent(entity -> {
            double baseSpeed = 1.0;
            if (CCLConfig.INSTANCE.enableSkillScaling.get()) {
                int athletics = getSkillLevel(Skill.Athletics);
                baseSpeed += athletics * CCLConfig.INSTANCE.speedBoostPerAthletics.get();
            }
            entity.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), baseSpeed);
        });
    }
    
    private boolean isAt(BlockPos pos) {
        return job.getCitizen().getEntity().map(e -> e.blockPosition().closerThan(pos, 3.0)).orElse(false);
    }
}