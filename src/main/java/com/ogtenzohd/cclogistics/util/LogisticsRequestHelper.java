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

        try {
            if (manager instanceof IStandardRequestManager stdManager) {
                var requestStore = stdManager.getRequestIdentitiesDataStore();
                for (IRequest<?> req : requestStore.getIdentities().values()) {
                    if (req.getId() != null) {
                        liveRequests.put(req.getId().toString(), req);
                    }
                }
            }
        } catch (Exception e) { return; }

        liveRequests.entrySet().removeIf(entry -> {
            IRequest<?> req = entry.getValue();
            if (req == null) return true;
            String reqClass = req.getRequester().getClass().getSimpleName().toLowerCase();
            return reqClass.contains("warehouse") || reqClass.contains("logisticscoordinator");
        });

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
                if (!itemToSend.isEmpty()) {
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
                if (req.canBeDelivered()) {
                    if (trackerUpdater != null) {
                        String courierMessage = getCourierStatus(colony, req.getId());
                        trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.DELIVERING, courierMessage);
                    }
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
                            }
                        }
                    }
                } catch (Exception ignored) {}

                if (req.getClass().getSimpleName().contains("DeliveryRequest")) continue;
                if (sentRequestIds != null && sentRequestIds.contains(id)) continue;

                boolean forceDispatch = forcedDispatchIds != null && forcedDispatchIds.contains(id);
                boolean colonyCanResolve = false;
                boolean activelyCrafting = req.hasChildren();

                if (!forceDispatch) {
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
                        trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.PROCESSING, overrideMsg);
                    }
                    continue;
                }
                if (itemToSend.isEmpty()) {
                    if (trackerUpdater != null) trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, "No Stock");
                    continue;
                }

                if (exactStockAvailable >= amountNeeded) {
                    if (orderCacher != null) {
                        orderCacher.cacheOrder(itemToSend.copy(), amountNeeded, targetAddress, id);
                    } else {
                        ItemStack finalItem = itemToSend.copy();
                        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, finalItem, tag -> {
                            tag.putString("cclogistics:tracking_id", id);
                        });

                        boolean success = LogisticsBridge.sendPackage(ticker, finalItem, amountNeeded, targetAddress, null);
                        if (success) {
                            if (onImportSuccess != null) onImportSuccess.accept(finalItem);
                            if (auditLogger != null) auditLogger.accept("IN;Imported " + amountNeeded + "x " + finalItem.getHoverName().getString());
                        }
                    }

                    if (sentRequestIds != null) sentRequestIds.add(id);
                    if (trackerUpdater != null) {
                        FreightTrackerModule.TrackStatus sentStatus = orderCacher != null ? FreightTrackerModule.TrackStatus.AWAITING_COORDINATOR : FreightTrackerModule.TrackStatus.DISPATCHED;
                        trackerUpdater.track(id, missingName, amountNeeded, sentStatus, orderCacher != null ? "Awaiting Coordinator" : "Dispatched");
                    }

                } else {
                    String msg = (exactStockAvailable == 0) ? "No Stock" : (amountNeeded - exactStockAvailable) + " Missing";
                    if (trackerUpdater != null) trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, msg);
                }

            } catch (Exception e) {
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.error("[CCL-DEBUG] Unhandled exception processing request", e);
            }
        }
    }

    private static String getCourierStatus(IColony colony, com.minecolonies.api.colony.requestsystem.token.IToken<?> requestToken) {
        try {
            IRequestManager manager = colony.getRequestManager();
            com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver<?> resolver = manager.getResolverForRequest(requestToken);

            if (resolver != null) {
                com.minecolonies.api.colony.requestsystem.token.IToken<?> courierToken = resolver.getId();
                com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore deliveryStore =
                        manager.getDataStoreManager().get(
                                courierToken,
                                com.google.common.reflect.TypeToken.of(com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore.class)
                        );

                if (deliveryStore != null) {
                    if (deliveryStore.getOngoingDeliveries() != null && deliveryStore.getOngoingDeliveries().contains(requestToken)) {
                        return "Courier is delivering now!";
                    }
                    if (deliveryStore.getQueue() != null) {
                        int stops = 0;
                        for (com.minecolonies.api.colony.requestsystem.token.IToken<?> queuedToken : deliveryStore.getQueue()) {
                            stops++;
                            if (queuedToken.equals(requestToken)) {
                                return "Courier En Route (" + stops + (stops == 1 ? " stop away)" : " stops away)");
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Courier En Route";
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