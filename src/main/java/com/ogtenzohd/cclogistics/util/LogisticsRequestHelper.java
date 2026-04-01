package com.ogtenzohd.cclogistics.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.core.colony.requestsystem.management.IStandardRequestManager;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.colony.buildings.modules.ExpressModule;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;

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
        void cacheOrder(ItemStack item, int amount, String targetAddress);
    }

    public static void processRequests(
            IColony colony,
            StockTickerBlockEntity ticker,
            Set<Object> activeRequestIds,
            Set<String> sentRequestIds,
            Function<IRequest<?>, String> addressResolver,
            List<String> auditLog,
            Consumer<ItemStack> onImportSuccess,
            TrackerUpdater trackerUpdater,
            OrderCacher orderCacher
    ) {
        if (colony == null || ticker == null) return;
        boolean FORCE_TRACE = false;

        if (FORCE_TRACE) LOGGER.info("[CCL-TRACE] === Starting Request Processing Cycle ===");

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
                    BlockPos requesterPos = null;
                    if (req.getRequester() != null) {
                        requesterPos = req.getRequester().getLocation().getInDimensionLocation();
                    }

                    if (requesterPos != null) {
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
                                    if (FORCE_TRACE) LOGGER.info("[CCL-TRACE] Request upgraded to EXPRESS for {}!", requesterBuilding.getBuildingDisplayName());
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
                    if (inner instanceof Stack stackReq) {
                        deliveryItem = stackReq.getStack();
                        delAmount = stackReq.getCount();
                    }

                    if (trackerUpdater != null) trackerUpdater.track(id, !deliveryItem.isEmpty() ? deliveryItem.getHoverName().getString() : "Transit Package", delAmount, FreightTrackerModule.TrackStatus.DELIVERING, null);
                    continue;
                }

                Object innerReq = req.getRequest();
                if (innerReq == null) continue;

                ItemStack itemToSend = ItemStack.EMPTY;
                int amountNeeded = 1;

                if (innerReq instanceof Tool toolReq) {
                    itemToSend = findToolInNetwork(toolReq, networkInv);
                    amountNeeded = 1;
                } else if (innerReq instanceof Stack stackReq) {
                    itemToSend = stackReq.getStack().copy();
                    amountNeeded = stackReq.getCount();
                } else if (innerReq instanceof Food foodReq) {
                    itemToSend = findFoodInNetwork(networkInv);
                    amountNeeded = 1;
                } else if (innerReq instanceof StackList stackListReq) {
                    if (!stackListReq.getStacks().isEmpty()) {
                        ItemStack firstValid = stackListReq.getStacks().get(0);
                        itemToSend = firstValid.copy();
                        amountNeeded = firstValid.getCount();
                    }
                } else if (innerReq instanceof Burnable) {
                    amountNeeded = 1;
                }

                if (amountNeeded <= 0) amountNeeded = 1;
                if (itemToSend.isEmpty() || canColonyCraft(colony, itemToSend)) continue;

                int maxPackageCapacity = itemToSend.getMaxStackSize() * 9;
                if (amountNeeded > maxPackageCapacity) amountNeeded = maxPackageCapacity;

                if (sentRequestIds != null && sentRequestIds.contains(id)) {
                    if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, "Dispatched");
                    continue;
                }

                int exactStockAvailable = 0;
                ItemStack safeItemToShip = null;

                for (BigItemStack bis : networkInv) {
                    if (bis.stack.isEmpty()) continue;
                    if (ItemStack.isSameItemSameComponents(bis.stack, itemToSend)) {
                        exactStockAvailable += bis.count;
                        safeItemToShip = bis.stack.copy();
                    }
                    else if (bis.stack.getItem() == itemToSend.getItem() &&
                            bis.stack.getHoverName().getString().equals(itemToSend.getHoverName().getString())) {
                        if (!itemToSend.getItem().toString().contains("domum_ornamentum")) {
                            exactStockAvailable += bis.count;
                            safeItemToShip = bis.stack.copy();
                        }
                    }
                }

                if (exactStockAvailable >= amountNeeded && safeItemToShip != null) {
                    if (orderCacher != null) {
                        if (sentRequestIds != null) sentRequestIds.add(id);
                        orderCacher.cacheOrder(safeItemToShip, amountNeeded, targetAddress);

                        if (trackerUpdater != null) trackerUpdater.track(id, safeItemToShip.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, "Pending Coordinator");
                    } else {
                        boolean success = LogisticsBridge.sendPackage(ticker, safeItemToShip, amountNeeded, targetAddress, null);

                        if (success) {
                            if (sentRequestIds != null) sentRequestIds.add(id);
                            if (trackerUpdater != null) trackerUpdater.track(id, safeItemToShip.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, "Dispatched");
                            if (onImportSuccess != null) onImportSuccess.accept(safeItemToShip);
                            if (auditLog != null) auditLog.add("IN;Imported " + amountNeeded + "x " + safeItemToShip.getHoverName().getString());
                        } else {
                            if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, "Create Network Routing Failed");
                        }
                    }
                } else {
                    String msg = (exactStockAvailable == 0) ? "No Stock" : (amountNeeded - exactStockAvailable) + " Missing";
                    if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, msg);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process request ID: " + id, e);
            }
        }
    }

    private static boolean canColonyCraft(IColony colony, ItemStack stack) {
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
        } catch (Exception e) {}
        return false;
    }

    private static ItemStack findToolInNetwork(Tool toolReq, List<BigItemStack> networkInv) {
        List<ItemStack> candidates = new ArrayList<>();
        for (BigItemStack bis : networkInv) {
            ItemStack stack = bis.stack;
            if (stack.isEmpty()) continue;
            if (toolReq.matches(stack)) {
                candidates.add(stack);
            }
        }
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        candidates.sort((a, b) -> Integer.compare(b.getMaxDamage(), a.getMaxDamage()));
        return candidates.get(0).copyWithCount(1);
    }

    private static ItemStack findFoodInNetwork(List<BigItemStack> networkInv) {
        for (BigItemStack bis : networkInv) {
            if (!bis.stack.isEmpty() && bis.stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                return bis.stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }
}