package com.ogtenzohd.cclogistics.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.colony.buildings.modules.ExpressModule;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class LogisticsRequestHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    @FunctionalInterface
    public interface TrackerUpdater {
        void track(String id, String itemName, int amount, FreightTrackerModule.TrackStatus status, String override);
    }

    @FunctionalInterface
    public interface OrderCacher {
        void cacheOrder(ItemStack item, int amount, String targetAddress, String requestId);
    }

    public static void processRequests(
            IColony colony,
            StockTickerBlockEntity ticker,
            Set<Object> activeRequestIds,
            Set<String> sentRequestIds,
            Function<IRequest<?>, String> addressResolver,
            Consumer<String> auditLogger,
            Consumer<ItemStack> onImportSuccess,
            TrackerUpdater trackerUpdater,
            OrderCacher orderCacher
    ) {
        if (colony == null || ticker == null) return;

        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] ===================================================");
        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] CYCLE START: Fetching requests for colony ID: " + colony.getID());

        Map<String, IRequest<?>> liveRequests = new HashMap<>();
        IRequestManager manager = colony.getRequestManager();

        try {
            if (manager instanceof IStandardRequestManager stdManager) {
                var requestStore = stdManager.getRequestIdentitiesDataStore();
                Collection<IRequest<?>> allRequests = requestStore.getIdentities().values();
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] Raw Requests found in Minecolonies API: " + allRequests.size());

                for (IRequest<?> req : allRequests) {
                    if (req.getId() != null) {
                        liveRequests.put(req.getId().toString(), req);
                    }
                }
            }
        } catch (Exception e) {
            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.error("[CCL-DEBUG] CRITICAL: API Request Hook Failed!", e);
            return;
        }

        liveRequests.entrySet().removeIf(entry -> {
            IRequest<?> req = entry.getValue();
            if (req == null) return true;
            String reqClass = req.getRequester().getClass().getSimpleName().toLowerCase();
            return reqClass.contains("warehouse") || reqClass.contains("logisticscoordinator");
        });

        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] Filtered Live Requests (Ignored Warehouse/Coordinator): " + liveRequests.size());

        if (activeRequestIds != null) {
            Set<Object> currentIds = new HashSet<>(liveRequests.keySet());
            for (Object oldId : new HashSet<>(activeRequestIds)) {
                if (!currentIds.contains(oldId)) {
                    if (trackerUpdater != null) trackerUpdater.track(oldId.toString(), "Request", 0, FreightTrackerModule.TrackStatus.COMPLETED, "Delivered");
                    activeRequestIds.remove(oldId);
                    if (sentRequestIds != null) sentRequestIds.remove(oldId.toString());
                }
            }
            activeRequestIds.addAll(currentIds);
        }

        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] Fetching Network Inventory Summary from Create/AE2...");
        List<BigItemStack> networkInv = LogisticsBridge.getNetworkInventory(ticker);
        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] Network Inventory fetched. Unique Item Types visible: " + networkInv.size());

        for (Map.Entry<String, IRequest<?>> entry : liveRequests.entrySet()) {
            IRequest<?> req = entry.getValue();
            String id = entry.getKey();

            try {
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] ---------------------------------------------------");
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] Processing Request ID: " + id);

                String targetAddress = addressResolver.apply(req);
                if (targetAddress == null) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> ABORT: Target Address is null.");
                    continue;
                }

                if (req.canBeDelivered()) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> ABORT: Request canBeDelivered() is true. Ignoring.");
                    continue;
                }

                try {
                    BlockPos requesterPos = req.getRequester().getLocation().getInDimensionLocation();
                    IBuilding requesterBuilding = colony.getServerBuildingManager().getBuilding(requesterPos);
                    if (requesterBuilding != null) {
                        ExpressModule expressModule = null;
                        for (var b : colony.getServerBuildingManager().getBuildings().values()) {
                            expressModule = b.getModule(ExpressModule.class);
                            if (expressModule != null) break;
                        }
                        if (expressModule != null) {
                            String checkString = requesterBuilding.getBuildingDisplayName() + ";" + requesterBuilding.getID();
                            if (expressModule.isExpressEnabled(checkString)) {
                                targetAddress = targetAddress + "-express;" + requesterPos.getX() + "," + requesterPos.getY() + "," + requesterPos.getZ();
                                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> ROUTING: Upgraded to EXPRESS Delivery: " + targetAddress);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.error("[CCL-DEBUG] Failed to parse Express routing", e);
                }

                String type = req.getClass().getSimpleName();
                if (type.contains("DeliveryRequest")) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> ABORT: Is a DeliveryRequest (Not a fetch request).");
                    continue;
                }

                Object innerReq = req.getRequest();
                if (!(innerReq instanceof IDeliverable deliverable)) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> ABORT: Inner request is not an IDeliverable.");
                    continue;
                }

                int amountNeeded = deliverable.getCount();
                if (amountNeeded <= 0) amountNeeded = 1;
                ItemStack itemToSend = ItemStack.EMPTY;

                ItemStack exactGuess = deliverable.getResult();
                if (exactGuess != null && !exactGuess.isEmpty() && deliverable.matches(exactGuess)) {
                    itemToSend = exactGuess.copy();
                    itemToSend.setCount(1);
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Token Fast-Path: Identified item as " + itemToSend.getHoverName().getString());
                } else {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Token Slow-Path: Iterating network inventory to find match...");
                    for (BigItemStack bis : networkInv) {
                        if (bis.stack.isEmpty()) continue;
                        if (deliverable.matches(bis.stack)) {
                            itemToSend = bis.stack.copy();
                            itemToSend.setCount(1);
                            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Token Slow-Path SUCCESS: Found match: " + itemToSend.getHoverName().getString());
                            break;
                        }
                    }
                }

                if (itemToSend.isEmpty()) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.warn("[CCL-DEBUG] -> ITEM NOT FOUND: Could not resolve a physical item for this request.");

                    if (exactGuess != null && !exactGuess.isEmpty() && canColonyCraft(exactGuess) && deliverable.canBeResolvedByBuilding()) {
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> The colony can craft this missing item natively. Silently aborting.");
                        continue;
                    }

                    String missingName = "Requested Item";
                    if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.Stack stackReq && !stackReq.getStack().isEmpty()) {
                        missingName = stackReq.getStack().getHoverName().getString();
                    } else if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.RequestTag tagReq) {
                        missingName = "Any " + tagReq.getTag().location().getPath();
                    } else if (deliverable instanceof Tool toolReq) {
                        missingName = "Requested Tool";
                        try {
                            if (toolReq.getEquipmentType() != null) missingName = "Any " + toolReq.getEquipmentType().toString();
                        } catch (Exception ignored) {}
                    } else if (deliverable instanceof Food) {
                        missingName = "Food / Edibles";
                    }

                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Updating Tracker: NO STOCK for " + missingName);
                    if (trackerUpdater != null) {
                        trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, null);
                    }
                    continue;
                }

                if (innerReq instanceof Food) {
                    amountNeeded = itemToSend.getMaxStackSize();
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Request is FOOD. Adjusted amountNeeded to full stack: " + amountNeeded);
                }

                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Target Item: " + itemToSend.getHoverName().getString() + " | Needed: " + amountNeeded);

                int exactStockAvailable = 0;
                boolean usedDirectStock = false;

                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Querying LogisticsBridge.checkStock() for direct AE2 reading...");
                BigItemStack stockCheck = LogisticsBridge.checkStock(ticker, itemToSend, null);

                if (stockCheck != null) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> LogisticsBridge.checkStock() returned: " + stockCheck.count);
                    if (stockCheck.count > 0) {
                        exactStockAvailable = stockCheck.count;
                        usedDirectStock = true;
                    }
                } else {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.warn("[CCL-DEBUG] -> LogisticsBridge.checkStock() returned NULL!");
                }

                if (!usedDirectStock) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Direct check failed or returned 0. Running fallback loop over truncated inventory...");
                    for (BigItemStack bis : networkInv) {
                        if (bis.stack.isEmpty()) continue;
                        if (ItemStack.isSameItemSameComponents(bis.stack, itemToSend)) {
                            exactStockAvailable += bis.count;
                        }
                    }
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Fallback loop concluded. Found: " + exactStockAvailable);
                }

                int maxPackageCapacity = itemToSend.getMaxStackSize() * 9;
                if (amountNeeded > maxPackageCapacity) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Amount needed (" + amountNeeded + ") exceeds package capacity. Capping to: " + maxPackageCapacity);
                    amountNeeded = maxPackageCapacity;
                }

                if (sentRequestIds != null && sentRequestIds.contains(id)) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> ABORT: Request ID already in sentRequestIds. Skipping.");
                    continue;
                }

                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Final Stock Assessment: Needed=" + amountNeeded + ", Available=" + exactStockAvailable);

                if (exactStockAvailable >= amountNeeded) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> SUCCESS: Sufficient stock found! Initiating shipment...");

                    if (orderCacher != null) {
                        orderCacher.cacheOrder(itemToSend.copy(), amountNeeded, targetAddress, id);
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Shipped via orderCacher.");
                    } else {
                        boolean success = LogisticsBridge.sendPackage(ticker, itemToSend.copy(), amountNeeded, targetAddress, null);
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Shipped via LogisticsBridge.sendPackage(). Success: " + success);
                        if (success) {
                            if (onImportSuccess != null) onImportSuccess.accept(itemToSend.copy());
                            if (auditLogger != null) auditLogger.accept("IN;Imported " + amountNeeded + "x " + itemToSend.getHoverName().getString());
                        }
                    }

                    if (sentRequestIds != null) sentRequestIds.add(id);
                    if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, orderCacher != null ? "Awaiting Coordinator" : "Requested");

                } else {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> FAILURE: Not enough stock.");

                    if (canColonyCraft(itemToSend) && deliverable.canBeResolvedByBuilding()) {
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> The colony can craft this item natively. Silently aborting to let Crafters handle it.");
                        continue;
                    }

                    String msg = (exactStockAvailable == 0) ? "No Stock" : (amountNeeded - exactStockAvailable) + " Missing";
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] -> Updating Tracker: " + msg);
                    if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, msg);
                }

            } catch (Exception e) {
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.error("[CCL-DEBUG] CRITICAL: Unhandled exception processing request ID: " + id, e);
            }
        }
        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] CYCLE COMPLETE.");
        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCL-DEBUG] ===================================================");
    }

    private static boolean canColonyCraft(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getMaxDamage() > 0 || !stack.isStackable()) return false;
        try {
            var recipes = com.minecolonies.api.colony.IColonyManager.getInstance().getRecipeManager().getRecipes();
            for (Object storageObj : recipes.values()) {
                if (storageObj instanceof com.minecolonies.api.crafting.IRecipeStorage storage) {
                    if (storage.getPrimaryOutput() != null && storage.getPrimaryOutput().getItem() == stack.getItem()) return true;
                    if (storage.getAlternateOutputs() != null) {
                        for (ItemStack alt : storage.getAlternateOutputs()) {
                            if (alt != null && alt.getItem() == stack.getItem()) return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}