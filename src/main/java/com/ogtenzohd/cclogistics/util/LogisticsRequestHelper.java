package com.ogtenzohd.cclogistics.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.world.item.ItemStack;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;

public class LogisticsRequestHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Field F_IDENTITIES_MAP;

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
            Set<Object> failedRequestIds,
            Set<String> sentRequestIds,
            Function<IRequest<?>, String> addressResolver,
            List<String> auditLog,
            Consumer<ItemStack> onImportSuccess,
            TrackerUpdater trackerUpdater,
            OrderCacher orderCacher
    ) {
        if (colony == null || ticker == null) return;

        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[RequestHelper] Initiating Request Processing Cycle...");

        Map<String, IRequest<?>> liveRequests = new HashMap<>();
        Object manager = colony.getRequestManager();

        try {
            Field fManager = manager.getClass().getDeclaredField("dataStoreManager");
            fManager.setAccessible(true);
            Object dataStoreManager = fManager.get(manager);

            Field fStoreMap = dataStoreManager.getClass().getDeclaredField("storeMap");
            fStoreMap.setAccessible(true);
            Map<?, ?> storeMap = (Map<?, ?>) fStoreMap.get(dataStoreManager);

            for (Object store : storeMap.values()) {
                if (store.getClass().getSimpleName().equals("StandardRequestIdentitiesDataStore")) {
                    if (F_IDENTITIES_MAP == null) {
                        F_IDENTITIES_MAP = store.getClass().getDeclaredField("map");
                        F_IDENTITIES_MAP.setAccessible(true);
                    }
                    Map<?, ?> reqMap = (Map<?, ?>) F_IDENTITIES_MAP.get(store);
                    
                    for (Map.Entry<?, ?> entry : reqMap.entrySet()) {
                        if (entry.getValue() instanceof IRequest<?> req) {
                            liveRequests.put(entry.getKey().toString(), req);
                        }
                    }
                    break; 
                }
            }
        } catch (Exception e) {
            LOGGER.error("[CCLogistics] DataStore Hook Failed!", e);
            return;
        }

        if (activeRequestIds != null) {
            Set<Object> currentIds = new HashSet<>(liveRequests.keySet());
            for (Object oldId : new HashSet<>(activeRequestIds)) {
                if (!currentIds.contains(oldId)) {
                    if (trackerUpdater != null) {
                        trackerUpdater.track(oldId.toString(), "Request", 0, FreightTrackerModule.TrackStatus.COMPLETED, "Delivered");
                    }
                    activeRequestIds.remove(oldId);
                    if (sentRequestIds != null) sentRequestIds.remove(oldId.toString());
                }
            }
            activeRequestIds.addAll(currentIds);
        }
        List<BigItemStack> networkInv = LogisticsBridge.getNetworkInventory(ticker);
        Map<net.minecraft.world.item.Item, Integer> virtualStock = new HashMap<>();
        for (BigItemStack bis : networkInv) {
            if (!bis.stack.isEmpty()) {
                virtualStock.put(bis.stack.getItem(), virtualStock.getOrDefault(bis.stack.getItem(), 0) + bis.count);
            }
        }

        int count = 0;
        for (Map.Entry<String, IRequest<?>> entry : liveRequests.entrySet()) {
            if (count++ > 40) break;
            String id = entry.getKey();
            IRequest<?> req = entry.getValue();

            try {
                String targetAddress = addressResolver.apply(req);
                if (targetAddress == null) continue;

                String type = req.getClass().getSimpleName();
                
                if (type.contains("DeliveryRequest")) {
                    ItemStack deliveryItem = extractStackTargeted(req);
                    int delAmount = extractAmountTargeted(req);
                    if (delAmount <= 0) delAmount = 1; 
                    String name = !deliveryItem.isEmpty() ? deliveryItem.getHoverName().getString() : "Transit Package";
                    if (trackerUpdater != null) trackerUpdater.track(id, name, delAmount, FreightTrackerModule.TrackStatus.DELIVERING, null);
                    continue; 
                }
                
                Object innerReq = req.getRequest();
                if (innerReq == null) continue;

                ItemStack itemToSend = ItemStack.EMPTY;
                int amountNeeded = extractAmountTargeted(req);
                if (amountNeeded <= 0) amountNeeded = extractAmountTargeted(innerReq);
                if (amountNeeded <= 0) amountNeeded = 1; 

                if (type.contains("ToolRequest") || type.contains("ArmorRequest")) {
                    itemToSend = solveByTierTargeted(innerReq, networkInv);
                    amountNeeded = 1;
                } else if (type.contains("ItemStackRequest") || type.contains("MinStackRequest") || type.contains("ResourceRequest")) {
                    itemToSend = extractStackTargeted(innerReq);
                    if (amountNeeded <= 1 && !itemToSend.isEmpty()) amountNeeded = itemToSend.getCount();
                } else if (type.contains("FoodRequest")) {
                    itemToSend = solveFoodTargeted(networkInv);
                    amountNeeded = 1;
                }

                if (itemToSend.isEmpty()) continue;
                int maxPackageCapacity = itemToSend.getMaxStackSize() * 9;
                if (amountNeeded > maxPackageCapacity) amountNeeded = maxPackageCapacity;
                if (sentRequestIds != null && sentRequestIds.contains(id)) {
                    if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, "Dispatched");
                    continue;
                }

                int stock = virtualStock.getOrDefault(itemToSend.getItem(), 0);

                if (stock >= amountNeeded) {
                    if (orderCacher != null) {
                        boolean success = LogisticsBridge.sendPackage(ticker, itemToSend, amountNeeded, targetAddress, null);
                        
                        if (success) {
                            if (sentRequestIds != null) sentRequestIds.add(id);
                            virtualStock.put(itemToSend.getItem(), stock - amountNeeded);

                            orderCacher.cacheOrder(itemToSend, amountNeeded, targetAddress);
                            if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, null);
                            if (onImportSuccess != null) onImportSuccess.accept(itemToSend);
                            if (auditLog != null) auditLog.add("IN;Imported " + amountNeeded + "x " + itemToSend.getHoverName().getString());
                        } else {
                            String failMsg = "Create Network Routing Failed";
                            if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, failMsg);
                        }
                    }
                } else {
                    String msg = (stock == 0) ? "No Stock" : (amountNeeded - stock) + " Missing";
                    if (trackerUpdater != null) trackerUpdater.track(id, itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, msg);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process request ID: " + id, e);
            }
        }
    }

    private static ItemStack extractStackTargeted(Object obj) {
        return extractStackTargetedRecursive(obj, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static ItemStack extractStackTargetedRecursive(Object obj, Set<Object> visited) {
        if (obj == null || !visited.add(obj)) return ItemStack.EMPTY;

        if (obj instanceof ItemStack stack && !stack.isEmpty()) return stack.copy();
        if (obj instanceof net.minecraft.world.item.Item item) return new ItemStack(item);

        if (obj.getClass().getSimpleName().equals("Stack")) {
            try {
                Method m = obj.getClass().getMethod("getStack");
                ItemStack s = (ItemStack) m.invoke(obj);
                if (s != null && !s.isEmpty()) return s.copy();
            } catch (Exception e) {}
        }

        if (obj instanceof Collection<?> coll) {
            for (Object inner : coll) {
                ItemStack res = extractStackTargetedRecursive(inner, visited);
                if (!res.isEmpty()) return res;
            }
        }

        try {
            for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
                if (c.getName().startsWith("java.")) continue;
                for (Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    if (val == null) continue;

                    if (val instanceof ItemStack stack && !stack.isEmpty()) return stack.copy();
                    if (val instanceof net.minecraft.world.item.Item item) return new ItemStack(item);

                    if (val instanceof Collection<?> coll) {
                        for (Object inner : coll) {
                            ItemStack res = extractStackTargetedRecursive(inner, visited);
                            if (!res.isEmpty()) return res;
                        }
                    } else if (val.getClass().getName().contains("minecolonies")) {
                        ItemStack deep = extractStackTargetedRecursive(val, visited);
                        if (!deep.isEmpty()) return deep;
                    }
                }
            }
        } catch (Exception e) {}
        
        return ItemStack.EMPTY;
    }

    private static int extractAmountTargeted(Object obj) {
        return extractAmountRecursive(obj, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private static int extractAmountRecursive(Object obj, Set<Object> visited) {
        if (obj == null || !visited.add(obj)) return 0; 
        if (obj instanceof ItemStack stack && !stack.isEmpty()) return stack.getCount();
        if (obj.getClass().getSimpleName().equals("Stack")) {
            try {
                Method m = obj.getClass().getMethod("getCount");
                int c = (int) m.invoke(obj);
                if (c > 0) return c;
            } catch (Exception e) {}
        }

        if (obj instanceof Collection<?> coll) {
            for (Object inner : coll) {
                int val = extractAmountRecursive(inner, visited);
                if (val > 0) return val;
            }
        }

        try {
            for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
                if (c.getName().startsWith("java.")) continue;
                for (Field f : c.getDeclaredFields()) {
                    f.setAccessible(true);
                    String name = f.getName().toLowerCase();
                    if (f.getType() == int.class || f.getType() == Integer.class) {
                        if (name.equals("needed") || name.equals("amount") || name.equals("count") || name.equals("quantity")) {
                            int val = (int) f.get(obj);
                            if (val > 0) return val;
                        }
                    } else if (!f.getType().isPrimitive() && f.getType() != String.class) {
                        Object innerVal = f.get(obj);
                        if (innerVal instanceof Collection<?> coll) {
                            for (Object inner : coll) {
                                int res = extractAmountRecursive(inner, visited);
                                if (res > 0) return res;
                            }
                        } else if (innerVal != null && innerVal.getClass().getName().contains("minecolonies")) {
                            int res = extractAmountRecursive(innerVal, visited);
                            if (res > 0) return res;
                        }
                    }
                }
            }
        } catch (Exception e) {}
        return 0;
    }

    private static ItemStack solveByTierTargeted(Object innerReq, List<BigItemStack> networkInv) {
        for (BigItemStack bis : networkInv) {
            if (!bis.stack.isEmpty() && bis.stack.isDamageableItem()) {
                return bis.stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack solveFoodTargeted(List<BigItemStack> networkInv) {
        for (BigItemStack bis : networkInv) {
            if (!bis.stack.isEmpty() && bis.stack.has(net.minecraft.core.component.DataComponents.FOOD)) {
                return bis.stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    private static int getStockCount(List<BigItemStack> inv, ItemStack needed) {
        int count = 0;
        for (BigItemStack bis : inv) {
            if (bis.stack.getItem() == needed.getItem()) count += bis.count;
        }
        return count;
    }
}