package com.ogtenzohd.cclogistics.colony.ai;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.colony.job.LogisticsCoordinatorJob;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.crafting.ItemStorage;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;

public class LogisticsCoordinatorAI extends AbstractEntityAIBasic<LogisticsCoordinatorJob, FreightDepotBuilding> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum State {
        IDLE,
        TO_TOWNHALL,
        AT_TOWNHALL,
        TO_WAREHOUSE,
        AT_WAREHOUSE,
        TO_DEPOT,
        AT_DEPOT
    }

    private State state = State.IDLE;
    private int delay = 0;
    private BlockPos currentTarget = null;

    public LogisticsCoordinatorAI(LogisticsCoordinatorJob job) {
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
                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Starting cycle, moving to TOWNHALL");
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
                         if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Arrived at TOWNHALL");
                         state = State.AT_TOWNHALL;
                         delay = 100; 
                    }
                } else {
                    if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.warn("[LogisticsAI] No TownHall found! Skipping to WAREHOUSE");
                    state = State.TO_WAREHOUSE;
                }
                break;

            case AT_TOWNHALL:
                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Processing requests at Town Hall...");
                com.minecolonies.api.colony.buildings.IBuilding depotB = job.getWorkBuilding();
                if (depotB instanceof FreightDepotBuilding) {
                    if (job.getColony().getWorld() != null) {
                        BlockEntity be = job.getColony().getWorld().getBlockEntity(depotB.getPosition());
                        if (be instanceof FreightDepotBlockEntity depotBE) {
                            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Delegating request processing to Depot BlockEntity logic");
                            depotBE.coordinateLogistics();
                        } else {
                            LOGGER.error("[LogisticsAI] Failed to find Depot Block Entity at " + depotB.getPosition());
                        }
                    }
                } else {
                     LOGGER.error("[LogisticsAI] Worker is not assigned to a Freight Depot!");
                }
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
                        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Arrived at WAREHOUSE");
                        state = State.AT_WAREHOUSE;
                        delay = 100;
                    }
                } else {
                    if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.warn("[LogisticsAI] No Warehouse found! Skipping to DEPOT");
                    state = State.TO_DEPOT;
                }
                break;

            case AT_WAREHOUSE:
                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Scanning Warehouse inventory...");
                IBuilding depot = job.getWorkBuilding();
                if (depot != null) {
                    Map<ItemStorage, ItemStorage> allItems = new HashMap<>();
                    
                    for (IBuilding building : job.getColony().getServerBuildingManager().getBuildings().values()) {
                        if (building.getSchematicName().contains("warehouse")) {
                            Map<ItemStorage, ItemStorage> buildingItems = InventoryUtils.getAllItemsForProviders(building);
                            for (Map.Entry<ItemStorage, ItemStorage> entry : buildingItems.entrySet()) {
                                ItemStorage storage = entry.getKey();
                                ItemStorage existing = allItems.get(storage);
                                if (existing != null) {
                                    existing.setAmount(existing.getAmount() + storage.getAmount());
                                } else {
                                    allItems.put(storage, storage); 
                                }
                            }
                        }
                    }
                    
                    if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Scan complete. Checking for excess items (>128)...");
                    for (ItemStorage storage : allItems.keySet()) {
                        if (storage.getAmount() > 128) {
                            int excess = storage.getAmount() - 128;
                            if (excess > 64) excess = 64; 
                            
                            ItemStack stack = storage.getItemStack().copy();
                            stack.setCount(excess);
                            
                            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Found Excess: " + stack.getHoverName().getString() + " x" + excess + ". Creating Pickup Request.");
                            
                            job.getCitizen().createRequest(new Stack(stack));
                            
                            break; 
                        }
                    }
                }
                state = State.TO_DEPOT;
                break;

            case TO_DEPOT:
                com.minecolonies.api.colony.buildings.IBuilding depotWork = job.getWorkBuilding();
                if (depotWork != null) {
                    currentTarget = depotWork.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Arrived at DEPOT");
                        state = State.AT_DEPOT;
                        delay = 200;
                    }
                }
                break;

            case AT_DEPOT:
                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[LogisticsAI] Resting at Depot...");
                state = State.IDLE;
                delay = CCLConfig.INSTANCE.coordinatorCooldown.get();
                break;
        }
    }
    
    public void write(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("State", state.ordinal());
        tag.putInt("Delay", delay);
        if (currentTarget != null) {
            tag.put("Target", NbtUtils.writeBlockPos(currentTarget));
        }
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