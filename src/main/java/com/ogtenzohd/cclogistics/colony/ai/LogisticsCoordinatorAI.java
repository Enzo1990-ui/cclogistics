package com.ogtenzohd.cclogistics.colony.ai;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.ogtenzohd.cclogistics.colony.job.LogisticsCoordinatorJob;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class LogisticsCoordinatorAI extends AbstractEntityAIBasic<LogisticsCoordinatorJob, FreightDepotBuilding> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum State {
        IDLE, TO_TOWNHALL, AT_TOWNHALL, TO_WAREHOUSE, AT_WAREHOUSE, TO_DEPOT, AT_DEPOT
    }

    private State state = State.IDLE;
    private int delay = 0;
    private BlockPos currentTarget = null;
    private final java.util.List<ManifestEntry> clipboardCacheB = new java.util.ArrayList<>();
    private final java.util.List<PendingPickup> pendingPickups = new java.util.ArrayList<>();

    public LogisticsCoordinatorAI(LogisticsCoordinatorJob job) {
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
    
    private static class PendingPickup {
        String itemName; int count; long requestTime;
        PendingPickup(String item, int count, long time) { this.itemName = item; this.count = count; this.requestTime = time; }
    }
    
    public static class ManifestEntry {
        public final ItemStack item;
        public final String address;
        public final int amount;

        public ManifestEntry(ItemStack item, String address, int amount) {
            this.item = item;
            this.address = address;
            this.amount = amount;
        }
    }

    public void tick() {
        if (delay > 0) {
            delay--;
            return;
        }

        switch (state) {
            case IDLE:
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Waking up from IDLE. Preparing new shipping cycle. Target: TOWNHALL.");
                setHoldingClipboard(false);
                state = State.TO_TOWNHALL;
                break;
                
            case TO_TOWNHALL:
                com.minecolonies.api.colony.buildings.IBuilding townHall = job.getColony().getServerBuildingManager().getBuildings().values().stream()
                    .filter(b -> b.getSchematicName().contains("townhall"))
                    .findFirst().orElse(null);

                if (townHall != null) {
                    currentTarget = townHall.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                         if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Successfully navigated to TOWNHALL. Transitioning to AT_TOWNHALL.");
                         state = State.AT_TOWNHALL;
                         delay = 100; 
                         setHoldingClipboard(true); 
                    }
                } else {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.warn("[LogisticsAI] WARNING: TownHall building not found in Colony Registry! Aborting manifest pickup and skipping to WAREHOUSE.");
                    state = State.TO_WAREHOUSE;
                }
                break;

            case AT_TOWNHALL:
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Executing AT_TOWNHALL: Picking up shipping manifests...");
    
                com.minecolonies.api.colony.buildings.IBuilding depotB = job.getWorkBuilding();
                if (depotB instanceof FreightDepotBuilding) {
                    if (job.getColony().getWorld() != null) {
                        BlockEntity be = job.getColony().getWorld().getBlockEntity(depotB.getPosition());
                        if (be instanceof FreightDepotBlockEntity depotBE) {
                            java.util.List<ManifestEntry> inbox = depotBE.getAndClearCacheA();
                            this.clipboardCacheB.addAll(inbox);
            
                            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
                                LOGGER.info("[LogisticsAI] Copied " + inbox.size() + " active manifests from Depot CacheA to local ClipboardCacheB!");
                                for (ManifestEntry entry : inbox) {
                                    LOGGER.info("[LogisticsAI]   -> Acquired Manifest: Send " + entry.amount + "x " + entry.item.getHoverName().getString() + " to " + entry.address);
                                }
                            }
                        } 
                    }
                }
    
                int townhallSpeed = Math.max(20, 100 - getSkillLevel(Skill.Athletics));
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Townhall paperwork finished. Delay applied: " + townhallSpeed + " ticks. Moving to WAREHOUSE.");
                delay = townhallSpeed;
                setHoldingClipboard(false); 
                state = State.TO_WAREHOUSE;
                break;

            case TO_WAREHOUSE:
                com.minecolonies.api.colony.buildings.IBuilding warehouse = job.getColony().getServerBuildingManager().getBuildings().values().stream()
                    .filter(b -> b.getSchematicName().contains("warehouse"))
                    .findFirst().orElse(null);

                if (warehouse != null) {
                    currentTarget = warehouse.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Successfully navigated to WAREHOUSE. Transitioning to AT_WAREHOUSE.");
                        state = State.AT_WAREHOUSE;
                        delay = 100;
                        setHoldingClipboard(true); 
                        
                        long now = job.getColony().getWorld().getGameTime();
                        pendingPickups.removeIf(pickup -> now - pickup.requestTime > 12000);
                    }
                } else {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.warn("[LogisticsAI] WARNING: Warehouse not found in Colony Registry! Skipping excess scan, moving to DEPOT.");
                    state = State.TO_DEPOT;
                }
                break;

            case AT_WAREHOUSE:
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Executing AT_WAREHOUSE: Initializing deep scan of all Warehouse inventories...");
                IBuilding depot = job.getWorkBuilding();
                if (depot != null) {
                    
                    FreightDepotBlockEntity depotBE = null;
                    if (job.getColony().getWorld() != null) {
                        BlockEntity be = job.getColony().getWorld().getBlockEntity(depot.getPosition());
                        if (be instanceof FreightDepotBlockEntity fbe) depotBE = fbe;
                    }
                    Map<net.minecraft.world.item.Item, Integer> itemCounts = new HashMap<>();
                    Map<net.minecraft.world.item.Item, ItemStack> itemStash = new HashMap<>();
                    for (IBuilding building : job.getColony().getServerBuildingManager().getBuildings().values()) {
                        if (building.getSchematicName().contains("warehouse")) {
                            Map<ItemStorage, ItemStorage> buildingItems = InventoryUtils.getAllItemsForProviders(building);
                            for (ItemStorage storage : buildingItems.keySet()) {
                                net.minecraft.world.item.Item item = storage.getItemStack().getItem();
                                itemCounts.put(item, itemCounts.getOrDefault(item, 0) + storage.getAmount());
                                if (!itemStash.containsKey(item)) itemStash.put(item, storage.getItemStack().copy());
                            }
                        }
                    }
        
                    int threshold = CCLConfig.INSTANCE.warehouseExcessThreshold.get();
                    for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : itemCounts.entrySet()) {
                        ItemStack referenceStack = itemStash.get(entry.getKey());
                        String itemName = referenceStack.getHoverName().getString();
                        if (depotBE != null && depotBE.isItemProtected(referenceStack)) {
                            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
                                LOGGER.info("[LogisticsAI] Customs Protection Active: Skipping " + itemName + " (Imported recently).");
                            }
                            continue;
                        }

                        long now = job.getColony().getWorld().getGameTime();

                        int alreadyRequestedAmount = 0;
                        for (PendingPickup pickup : pendingPickups) {
                            if (pickup.itemName.equals(itemName)) alreadyRequestedAmount += pickup.count;
                        }
                        int trueExcess = entry.getValue() - threshold;
                        int unhandledExcess = trueExcess - alreadyRequestedAmount;
                        if (unhandledExcess > 0) {
                            int maxStackSize = referenceStack.getMaxStackSize();
                            int maxPackageCapacity = maxStackSize * 9;
                            if (alreadyRequestedAmount >= maxPackageCapacity) {
                                continue; 
                            }
                            int amountToRequestThisTrip = Math.min(unhandledExcess, maxStackSize);
                            int spaceLeftInPackage = maxPackageCapacity - alreadyRequestedAmount;
                            amountToRequestThisTrip = Math.min(amountToRequestThisTrip, spaceLeftInPackage);
                            if (amountToRequestThisTrip <= 0) continue;
                            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
                                LOGGER.info("[LogisticsAI] EXCESS DETECTED: " + itemName + " | Total in DB: " + entry.getValue() + " | Threshold: " + threshold + " | Pending Pickups: " + alreadyRequestedAmount + " | Actionable Excess: " + unhandledExcess);
                            }
                            ItemStack requestStack = referenceStack.copy();
                            requestStack.setCount(amountToRequestThisTrip);
                            IToken<?> newReq = job.getCitizen().createRequest(new Stack(requestStack));

                            if (newReq != null) {
                                pendingPickups.add(new PendingPickup(itemName, amountToRequestThisTrip, now));
                                FreightTrackerModule module = depot.getModule(FreightTrackerModule.class);
                                if (module != null) {
                                    module.updateRequest(itemName + "_export_" + now, itemName, amountToRequestThisTrip, FreightTrackerModule.TrackStatus.REQUESTED, "Export Pending", false);
                                }

                                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] -> Successfully spawned internal Request Token for " + amountToRequestThisTrip + "x " + itemName);
                                delay = 10;
                                return;
                            } else {
                                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.warn("[LogisticsAI] -> FAILED to spawn Request Token for " + itemName + "! Skipping to next item to prevent loop.");
                                continue;
                            }
                        }
                    }
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Warehouse scan finished. No actionable excess items remaining.");
                }
                setHoldingClipboard(false); 
                state = State.TO_DEPOT;
                break;

            case TO_DEPOT:
                com.minecolonies.api.colony.buildings.IBuilding depotWork = job.getWorkBuilding();
                if (depotWork != null) {
                    currentTarget = depotWork.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Successfully navigated to DEPOT. Transitioning to AT_DEPOT.");
                        state = State.AT_DEPOT;
                        delay = 200;
                        setHoldingClipboard(true); 
                    }
                }
                break;

            case AT_DEPOT:
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Executing AT_DEPOT: Processing ClipboardCacheB (Count: " + clipboardCacheB.size() + ")");
                if (!clipboardCacheB.isEmpty() && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE) {
            
                        for (ManifestEntry order : clipboardCacheB) {
                            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
                                LOGGER.info("[LogisticsAI]   -> Submitting to Logistics Bridge: " + order.amount + "x " + order.item.getHoverName().getString() + " destined for " + order.address);
                            }
                            com.ogtenzohd.cclogistics.util.LogisticsBridge.sendPackage(
                                depotBE.getStockTicker(), 
                                order.item, 
                                order.amount,
                                order.address, 
                                null
                            );
                        }
            
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
                            LOGGER.info("[LogisticsAI] SUCCESS: Pushed " + clipboardCacheB.size() + " manifests to the Create Network! Trains are moving!");
                        }
            
                        clipboardCacheB.clear();
                    } else {
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.warn("[LogisticsAI] ERROR: BlockEntity at Depot is NOT a FreightDepotBlockEntity! Bridge execution aborted.");
                    }
                } else if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
                    LOGGER.info("[LogisticsAI] ClipboardCacheB is empty. No packages to route this cycle.");
                }

                int baseDelay = CCLConfig.INSTANCE.coordinatorCooldown.get();
                if (CCLConfig.INSTANCE.enableSkillScaling.get()) {
                    int intel = getSkillLevel(Skill.Intelligence);
                    int reduction = intel * CCLConfig.INSTANCE.cooldownReductionPerIntel.get();
                    delay = Math.max(20, baseDelay - reduction);
                } else {
                    delay = baseDelay;
                }
                
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[LogisticsAI] Resting at Depot for " + delay + " ticks before next cycle.");
                state = State.IDLE;
                break;
        }
    }
    
    private ItemStack getClipboard() {
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("minecolonies:clipboard"));
        if (item == Items.AIR) {
            return new ItemStack(Items.BOOK);
        }
        return new ItemStack(item);
    }
    
    private void setHoldingClipboard(boolean holding) {
        job.getCitizen().getEntity().ifPresent(entity -> {
            if (holding) {
                entity.setItemInHand(InteractionHand.MAIN_HAND, getClipboard());
            } else {
                entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        });
    }

    private void playScribbleSound() {
        job.getCitizen().getEntity().ifPresent(entity -> {
            float pitch = 1.0F + (entity.level().random.nextFloat() - 0.5F) * 0.2F;
            entity.playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 0.5F, pitch);
        });
    }
    
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("State", state.ordinal());
        tag.putInt("Delay", delay);
        if (currentTarget != null) {
            tag.put("Target", NbtUtils.writeBlockPos(currentTarget));
        }
        
        net.minecraft.nbt.ListTag cacheBList = new net.minecraft.nbt.ListTag();
        for (ManifestEntry entry : clipboardCacheB) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("Item", entry.item.save(provider));
            entryTag.putString("Address", entry.address);
            entryTag.putInt("Amount", entry.amount);
            cacheBList.add(entryTag);
        }
        tag.put("ClipboardCacheB", cacheBList);
        net.minecraft.nbt.ListTag pickupsList = new net.minecraft.nbt.ListTag();
        for (PendingPickup pickup : pendingPickups) {
            CompoundTag pTag = new CompoundTag();
            pTag.putString("Item", pickup.itemName);
            pTag.putInt("Count", pickup.count);
            pTag.putLong("Time", pickup.requestTime);
            pickupsList.add(pTag);
        }
        tag.put("PendingPickups", pickupsList);
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
        if (tag.contains("ClipboardCacheB")) {
            clipboardCacheB.clear();
            net.minecraft.nbt.ListTag list = tag.getList("ClipboardCacheB", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                ItemStack item = ItemStack.parse(provider, entryTag.getCompound("Item")).orElse(ItemStack.EMPTY);
                String address = entryTag.getString("Address");
                int amount = entryTag.getInt("Amount");
                if (!item.isEmpty()) {
                    clipboardCacheB.add(new ManifestEntry(item, address, amount));
                }
            }
        }
        if (tag.contains("PendingPickups")) {
            pendingPickups.clear();
            net.minecraft.nbt.ListTag list = tag.getList("PendingPickups", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag pTag = list.getCompound(i);
                pendingPickups.add(new PendingPickup(
                    pTag.getString("Item"),
                    pTag.getInt("Count"),
                    pTag.getLong("Time")
                ));
            }
        }
    }
    
    private void moveTo(BlockPos pos) {
        if (!job.getCitizen().getEntity().isPresent()) {
            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.warn("[LogisticsAI] Citizen Entity not present during moveTo!");
            return;
        }
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
        return job.getCitizen().getEntity().map(e -> e.blockPosition().closerThan(pos, 3.0)).orElseGet(() -> {
            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.warn("[LogisticsAI] Citizen Entity not present during isAt!");
            return false;
        });
    }
}