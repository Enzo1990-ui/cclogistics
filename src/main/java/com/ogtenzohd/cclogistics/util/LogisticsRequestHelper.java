package com.ogtenzohd.cclogistics.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
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

        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.BRIDGE)) {
            LOGGER.info("[CCL-TRACE] === Starting Request Processing Cycle ===");
        }

        Map<String, IRequest<?>> liveRequests = new HashMap<>();
        IRequestManager manager = colony.getRequestManager();

        try {
            if (manager instanceof IStandardRequestManager stdManager) {
                var requestStore = stdManager.getRequestIdentitiesDataStore();
                Collection<IRequest<?>> allRequests = requestStore.getIdentities().values();

                for (IRequest<?> req : allRequests) {
                    if (req.getId() != null) {
                        liveRequests.put(req.getId().toString(), req);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[CCLogistics] API Request Hook Failed!", e);
            return;
        }

        liveRequests.entrySet().removeIf(entry -> {
            IRequest<?> req = entry.getValue();
            if (req == null) return true;

            String reqClass = req.getRequester().getClass().getSimpleName().toLowerCase();
            if (reqClass.contains("warehouse") || reqClass.contains("logisticscoordinator")) {
                return true;
            }

            return false;
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
                if (req.canBeDelivered()) continue;
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
                                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.BRIDGE)) {
                                    LOGGER.info("[CCL-TRACE] Request upgraded to EXPRESS for {}!", requesterBuilding.getBuildingDisplayName());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("[CCL-TRACE] Failed to parse Express routing", e);
                }

                String type = req.getClass().getSimpleName();
                if (type.contains("DeliveryRequest")) {
                    ItemStack deliveryItem = ItemStack.EMPTY;
                    int delAmount = 1;

                    Object inner = req.getRequest();
                    if (inner instanceof IDeliverable deliverableReq) {
                        deliveryItem = deliverableReq.getResult();
                        delAmount = deliverableReq.getCount();
                    }

                    if (trackerUpdater != null) trackerUpdater.track(id, !deliveryItem.isEmpty() ? deliveryItem.getHoverName().getString() : "Transit Package", delAmount, FreightTrackerModule.TrackStatus.DELIVERING, null);
                    continue;
                }

                Object innerReq = req.getRequest();
                if (!(innerReq instanceof IDeliverable deliverable)) {
                    continue;
                }

                int amountNeeded = deliverable.getCount();
                if (amountNeeded <= 0) amountNeeded = 1;
                ItemStack itemToSend = ItemStack.EMPTY;
                for (BigItemStack bis : networkInv) {
                    if (bis.stack.isEmpty()) continue;

                    if (deliverable.matches(bis.stack)) {
                        itemToSend = bis.stack.copy();
                        itemToSend.setCount(1);
                        break;
                    }
                }

                if (itemToSend.isEmpty()) {
                    String missingName = "Requested Item";
                    if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.Stack stackReq) {
                        if (!stackReq.getStack().isEmpty()) {
                            missingName = stackReq.getStack().getHoverName().getString();
                        }
                    }
                    else if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.RequestTag tagReq) {
                        String tagPath = tagReq.getTag().location().getPath();

                        String formattedName = java.util.Arrays.stream(tagPath.split("_"))
                                .map(word -> word.isEmpty() ? word : word.substring(0, 1).toUpperCase() + word.substring(1))
                                .collect(java.util.stream.Collectors.joining(" "));

                        missingName = "Any " + formattedName;
                    }
                    else if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.StackList stackList) {
                        if (!stackList.getStacks().isEmpty()) {
                            missingName = stackList.getStacks().get(0).getHoverName().getString();
                        } else if (stackList.getDescription() != null && !stackList.getDescription().isEmpty()) {
                            missingName = stackList.getDescription();
                        }
                    }
                    else if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.Tool) {
                        missingName = "Requested Tool";
                    }
                    else if (deliverable instanceof com.minecolonies.api.colony.requestsystem.requestable.Food) {
                        missingName = "Food / Edibles";
                    }
                    else if (!deliverable.getResult().isEmpty()) {
                        missingName = deliverable.getResult().getHoverName().getString();
                    }

                    if (trackerUpdater != null) {
                        trackerUpdater.track(id, missingName, amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, null);
                    }
                    continue;
                }

                if (canColonyCraft(itemToSend) && deliverable.canBeResolvedByBuilding()) continue;
                int exactStockAvailable = 0;
                for (BigItemStack bis : networkInv) {
                    if (!bis.stack.isEmpty() && ItemStack.isSameItemSameComponents(bis.stack, itemToSend)) {
                        exactStockAvailable += bis.count;
                    }
                }

                int maxPackageCapacity = itemToSend.getMaxStackSize() * 9;
                if (amountNeeded > maxPackageCapacity) amountNeeded = maxPackageCapacity;

                if (sentRequestIds != null && sentRequestIds.contains(id)) {
                    continue;
                }

                if (exactStockAvailable >= amountNeeded) {
                    int remainingNeeded = amountNeeded;
                    for (BigItemStack bis : networkInv) {
                        if (bis.stack.isEmpty() || remainingNeeded <= 0 || bis.count <= 0) continue;

                        if (ItemStack.isSameItemSameComponents(bis.stack, itemToSend)) {
                            int toTake = Math.min(remainingNeeded, bis.count);

                            if (orderCacher != null) {
                                orderCacher.cacheOrder(bis.stack.copy(), toTake, targetAddress, id);
                            } else {
                                boolean success = LogisticsBridge.sendPackage(ticker, bis.stack.copy(), toTake, targetAddress, null);
                                if (success) {
                                    if (onImportSuccess != null) onImportSuccess.accept(bis.stack.copy());
                                    if (auditLogger != null) auditLogger.accept("IN;Imported " + toTake + "x " + bis.stack.getHoverName().getString());
                                }
                            }
                            bis.count -= toTake;
                            remainingNeeded -= toTake;
                        }
                    }

                    if (sentRequestIds != null) sentRequestIds.add(id);
                    if (orderCacher != null) {
                        if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, "Awaiting Coordinator");
                    } else {
                        if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, "Requested");
                    }

                } else {
                    String msg = (exactStockAvailable == 0) ? "No Stock" : (amountNeeded - exactStockAvailable) + " Missing";
                    if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, msg);
                }

            } catch (Exception e) {
                LOGGER.error("Failed to process request ID: {}", id, e);
            }
        }
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
        } catch (Exception ignored) {
        }
        return false;
    }
}