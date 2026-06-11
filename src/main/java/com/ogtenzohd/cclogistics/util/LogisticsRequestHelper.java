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
import net.minecraft.world.item.Items;
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
            IColony colony, StockTickerBlockEntity ticker, Set<Object> activeRequestIds, Set<String> sentRequestIds,
            Function<IRequest<?>, String> addressResolver, Consumer<String> auditLogger, Consumer<ItemStack> onImportSuccess,
            TrackerUpdater trackerUpdater, OrderCacher orderCacher
    ) {
        processRequests(colony, ticker, activeRequestIds, sentRequestIds, null, addressResolver, auditLogger, onImportSuccess, trackerUpdater, orderCacher);
    }

    public static void processRequests(
            IColony colony, StockTickerBlockEntity ticker, Set<Object> activeRequestIds, Set<String> sentRequestIds,
            Set<String> forcedDispatchIds,
            Function<IRequest<?>, String> addressResolver, Consumer<String> auditLogger, Consumer<ItemStack> onImportSuccess,
            TrackerUpdater trackerUpdater, OrderCacher orderCacher
    ) {
        if (colony == null || ticker == null) return;

        Map<String, IRequest<?>> liveRequests = new HashMap<>();
        IRequestManager manager = colony.getRequestManager();
        Set<com.minecolonies.api.colony.requestsystem.token.IToken<?>> clipboardTokens = new HashSet<>();

        try {
            if (manager.getPlayerResolver() != null) {
                clipboardTokens.addAll(manager.getPlayerResolver().getAllAssignedRequests());
            }
            if (manager instanceof IStandardRequestManager stdManager) {
                var requestStore = stdManager.getRequestIdentitiesDataStore();
                for (Map.Entry<com.minecolonies.api.colony.requestsystem.token.IToken<?>, IRequest<?>> entry : requestStore.getIdentities().entrySet()) {
                    IRequest<?> req = entry.getValue();
                    if (req != null && req.getId() != null) {
                        String state = req.getState().name();
                        if (state.equals("COMPLETED") || state.equals("CANCELLED") || state.equals("FAILED")) continue;
                        liveRequests.put(req.getId().toString(), req);
                    }
                }
            }
        } catch (Exception e) { return; }

        liveRequests.entrySet().removeIf(entry -> {
            IRequest<?> req = entry.getValue();
            if (req == null || req.getRequester() == null) return true;
            if (req.getShortDisplayString().getString().contains("Recipe:[")) return true;

            String reqClass = req.getRequester().getClass().getSimpleName().toLowerCase();
            String typeClass = req.getType().getRawType().getSimpleName().toLowerCase();
            boolean isFreightDepot = false;
            if (req.getRequester() instanceof IBuilding b) {
                String bName = b.getBuildingDisplayName().toLowerCase();
                if (bName.contains("freight") || bName.contains("depot")) {
                    isFreightDepot = true;
                }
            }

            return reqClass.contains("warehouse") ||
                    reqClass.contains("logisticscoordinator") ||
                    reqClass.contains("freight") ||
                    typeClass.contains("delivery") ||
                    isFreightDepot;
        });

        if (forcedDispatchIds != null && !forcedDispatchIds.isEmpty()) {
            liveRequests.keySet().retainAll(forcedDispatchIds);
        }

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

        List<BigItemStack> networkInv = LogisticsBridge.getNetworkInventory(ticker);

        for (Map.Entry<String, IRequest<?>> entry : liveRequests.entrySet()) {
            IRequest<?> req = entry.getValue();
            String id = entry.getKey();

            try {
                String targetAddress = addressResolver.apply(req);
                if (targetAddress == null) continue;

                Object innerReq = req.getRequest();
                if (!(innerReq instanceof IDeliverable deliverable)) continue;

                int amountNeeded = deliverable.getCount();
                if (amountNeeded <= 0) amountNeeded = 1;
                ItemStack itemToSend = ItemStack.EMPTY;
                ItemStack exactGuess = deliverable.getResult();

                if (exactGuess != null && !exactGuess.isEmpty() && deliverable.matches(exactGuess)) {
                    itemToSend = exactGuess.copy();
                    itemToSend.setCount(1);
                } else {
                    for (BigItemStack bis : networkInv) {
                        if (bis.stack.isEmpty()) continue;
                        if (deliverable.matches(bis.stack)) {
                            itemToSend = bis.stack.copy();
                            itemToSend.setCount(1);
                            break;
                        }
                    }
                }

                if (innerReq instanceof Food && !itemToSend.isEmpty()) {
                    amountNeeded = itemToSend.getMaxStackSize();
                }
                String missingName = "Unknown Item";
                if (!itemToSend.isEmpty() && itemToSend.getItem() != Items.AIR) {
                    missingName = itemToSend.getHoverName().getString();
                } else if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.Stack stackReq && !stackReq.getStack().isEmpty()) {
                    missingName = stackReq.getStack().getHoverName().getString();
                } else if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.RequestTag tagReq) {
                    String path = tagReq.getTag().location().getPath().replace("_", " ");
                    missingName = "Any " + path.substring(0, 1).toUpperCase() + path.substring(1);
                } else if (deliverable instanceof Tool toolReq) {
                    try {
                        if (toolReq.getEquipmentType() != null) {
                            String tName = toolReq.getEquipmentType().toString();
                            missingName = (tName.equalsIgnoreCase("any") || tName.equalsIgnoreCase("none")) ? "Any Tool" : "Any " + tName.substring(0, 1).toUpperCase() + tName.substring(1).toLowerCase();
                        }
                    } catch (Exception ignored) {}
                } else if (deliverable instanceof Food) {
                    missingName = "Food / Edibles";
                } else {
                    missingName = req.getShortDisplayString().getString().replaceAll("^[0-9\\-*\\s]+", "");
                }
                int exactStockAvailable = 0;
                if (!itemToSend.isEmpty()) {
                    BigItemStack stockCheck = LogisticsBridge.checkStock(ticker, itemToSend, null);
                    if (stockCheck != null && stockCheck.count > 0) {
                        exactStockAvailable = stockCheck.count;
                    } else {
                        for (BigItemStack bis : networkInv) {
                            if (bis.stack.isEmpty()) continue;
                            if (ItemStack.isSameItemSameComponents(bis.stack, itemToSend)) {
                                exactStockAvailable += bis.count;
                            }
                        }
                    }
                }

                int maxPackageCapacity = itemToSend.isEmpty() ? 64 * 9 : itemToSend.getMaxStackSize() * 9;
                if (amountNeeded > maxPackageCapacity) amountNeeded = maxPackageCapacity;
                String requesterName = "Colony Worker";
                try {
                    BlockPos requesterPos = req.getRequester().getLocation().getInDimensionLocation();
                    IBuilding requesterBuilding = colony.getServerBuildingManager().getBuilding(requesterPos);

                    if (req.getRequester() instanceof IBuilding br) {
                        requesterBuilding = br;
                    }

                    if (requesterBuilding != null) {
                        String raw = requesterBuilding.getBuildingDisplayName();
                        if (raw.contains(".")) raw = raw.substring(raw.lastIndexOf('.') + 1);
                        requesterName = raw.substring(0, 1).toUpperCase() + raw.substring(1).toLowerCase();

                        ExpressModule expressModule = null;
                        for (var b : colony.getServerBuildingManager().getBuildings().values()) {
                            expressModule = b.getModule(ExpressModule.class);
                            if (expressModule != null) break;
                        }
                        if (expressModule != null) {
                            String checkString = requesterBuilding.getBuildingDisplayName() + ";" + requesterBuilding.getID();
                            if (expressModule.isExpressEnabled(checkString)) {
                                targetAddress = targetAddress + "-express;" + requesterPos.getX() + "," + requesterPos.getY() + "," + requesterPos.getZ();
                            }
                        }
                    }
                } catch (Exception ignored) {}
                boolean isWarehouseHandling = false;
                try {
                    var resolver = manager.getResolverForRequest(req.getId());
                    if (resolver != null && resolver.getClass().getSimpleName().contains("Warehouse")) {
                        isWarehouseHandling = true;
                    }
                } catch (Exception ignored) {}

                boolean isShipped = sentRequestIds != null && sentRequestIds.contains(id);
                if (req.canBeDelivered() || req.getState().name().contains("FOLLOWUP") || isShipped || isWarehouseHandling) {
                    if (trackerUpdater != null) {
                        String courierMessage = getCourierStatus(colony, req.getId(), isShipped);
                        trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.DELIVERING, courierMessage + "|" + requesterName);
                    }
                    continue;
                }
                boolean isStuck = clipboardTokens.contains(req.getId());
                boolean forceDispatch = forcedDispatchIds != null && forcedDispatchIds.contains(id);
                boolean colonyCanResolve = false;
                boolean activelyCrafting = req.hasChildren();

                if (!forceDispatch && !isStuck) {
                    if (activelyCrafting) {
                        colonyCanResolve = true;
                    } else {
                        boolean allowedToBeCrafted = true;
                        if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.Stack stackReq) {
                            allowedToBeCrafted = stackReq.canBeResolvedByBuilding();
                        }
                        if (allowedToBeCrafted) {
                            colonyCanResolve = canColonyCraft(colony, deliverable, missingName);
                        }
                    }
                }

                if (colonyCanResolve) {
                    if (trackerUpdater != null) {
                        String overrideMsg = (exactStockAvailable >= amountNeeded) ? "Colony Crafting [In Stock]" : "Colony Crafting";
                        trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.PROCESSING, overrideMsg + "|" + requesterName);
                    }
                    continue;
                }
                if (itemToSend.isEmpty() || exactStockAvailable == 0) {
                    String msg = isStuck ? "§4Stuck! No Stock" : "No Stock";
                    if (trackerUpdater != null) trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, msg + "|" + requesterName);
                    continue;
                }

                if (exactStockAvailable >= amountNeeded) {
                    if (orderCacher != null) {
                        orderCacher.cacheOrder(itemToSend.copy(), amountNeeded, targetAddress, id);
                    } else {
                        ItemStack finalItem = itemToSend.copy();

                        boolean success = LogisticsBridge.sendPackage(ticker, finalItem, amountNeeded, targetAddress, null);
                        if (success) {
                            if (onImportSuccess != null) onImportSuccess.accept(finalItem);
                            if (auditLogger != null) auditLogger.accept("IN;Imported " + amountNeeded + "x " + finalItem.getHoverName().getString());
                        }
                    }

                    if (sentRequestIds != null) sentRequestIds.add(id);
                    if (trackerUpdater != null) {
                        FreightTrackerModule.TrackStatus sentStatus = orderCacher != null ? FreightTrackerModule.TrackStatus.AWAITING_COORDINATOR : FreightTrackerModule.TrackStatus.DISPATCHED;
                        trackerUpdater.track(id, missingName, amountNeeded, sentStatus, orderCacher != null ? "Awaiting Coordinator" : "Dispatched|" + requesterName);
                    }

                } else {
                    String msg = isStuck ? "§4Stuck! " + (amountNeeded - exactStockAvailable) + " Missing" : (amountNeeded - exactStockAvailable) + " Missing";
                    if (trackerUpdater != null) trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, msg + "|" + requesterName);
                }

            } catch (Exception e) {
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.error("[CCL-DEBUG] Unhandled exception processing request", e);
            }
        }
    }

    private static String getCourierStatus(IColony colony, com.minecolonies.api.colony.requestsystem.token.IToken<?> requestToken, boolean isShipped) {
        try {
            IRequestManager manager = colony.getRequestManager();
            com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver<?> resolver = manager.getResolverForRequest(requestToken);

            if (resolver != null) {
                if (resolver.getClass().getSimpleName().contains("Warehouse")) {
                    return "§3Colony Warehouse";
                }

                com.minecolonies.api.colony.requestsystem.token.IToken<?> courierToken = resolver.getId();
                var deliveryStore = manager.getDataStoreManager().get(
                        courierToken,
                        com.google.common.reflect.TypeToken.of(com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore.class)
                );

                if (deliveryStore != null) {
                    if (deliveryStore.getOngoingDeliveries() != null && deliveryStore.getOngoingDeliveries().contains(requestToken)) {
                        return "§2In Transit (With Courier)";
                    }
                    if (deliveryStore.getQueue() != null) {
                        for (com.minecolonies.api.colony.requestsystem.token.IToken<?> queuedToken : deliveryStore.getQueue()) {
                            if (queuedToken.equals(requestToken)) {
                                return isShipped ? "§1Waiting at Depot/Warehouse" : "Awaiting Dispatch";
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return isShipped ? "§1Waiting at Depot/Warehouse" : "Processing";
    }

    private static boolean canColonyCraft(IColony colony, IDeliverable deliverable, String missingName) {
        if (colony == null || deliverable == null) return false;

        try {
            var recipes = com.minecolonies.api.colony.IColonyManager.getInstance().getRecipeManager().getRecipes();
            for (Object storageObj : recipes.values()) {
                if (storageObj instanceof com.minecolonies.api.crafting.IRecipeStorage storage) {
                    ItemStack primary = storage.getPrimaryOutput();
                    if (primary != null && !primary.isEmpty()) {
                        if (deliverable.matches(primary)) return true;
                    }
                    if (storage.getAlternateOutputs() != null) {
                        for (ItemStack alt : storage.getAlternateOutputs()) {
                            if (alt != null && !alt.isEmpty() && deliverable.matches(alt)) return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}