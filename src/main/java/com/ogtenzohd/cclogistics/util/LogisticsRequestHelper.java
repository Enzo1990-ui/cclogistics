package com.ogtenzohd.cclogistics.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.crafting.IRecipeStorage;
import net.minecraft.world.item.ItemStack;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;

public class LogisticsRequestHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    @FunctionalInterface
    public interface TrackerUpdater {
        void track(String itemName, int amount, FreightTrackerModule.TrackStatus status, String override);
    }

    @SuppressWarnings("unchecked")
    public static void processRequests(
            IColony colony,
            StockTickerBlockEntity ticker,
            Set<Object> activeRequestIds,
            Set<Object> failedRequestIds,
            Function<IRequest<?>, String> addressResolver,
            List<String> auditLog,
            Consumer<ItemStack> onImportSuccess,
            TrackerUpdater trackerUpdater 
    ) {
        if (colony == null || ticker == null) return;

        List<IRequest<?>> allRequests = new ArrayList<>();
        Object manager = colony.getRequestManager();

        if (manager != null) {
            Object dataStoreManager = getFieldByName(manager, "dataStoreManager");
            if (dataStoreManager != null) {
                Object storeMapObj = getFieldByName(dataStoreManager, "storeMap");
                if (storeMapObj instanceof Map) {
                    Map<?, ?> storeMap = (Map<?, ?>) storeMapObj;
                    for (Object dataStore : storeMap.values()) {
                        if (dataStore == null) continue;
                        for (Field f : getAllFields(dataStore.getClass())) {
                            try {
                                f.setAccessible(true);
                                Object val = f.get(dataStore);
                                
                                if (val instanceof Collection<?> coll) {
                                    for (Object item : coll) {
                                        if (item instanceof IRequest) {
                                            allRequests.add((IRequest<?>) item);
                                        }
                                    }
                                } else if (val instanceof Map<?, ?> reqMap) {
                                    for (Object mapVal : reqMap.values()) {
                                        if (mapVal instanceof IRequest) {
                                            allRequests.add((IRequest<?>) mapVal);
                                        } else if (mapVal instanceof Collection<?> innerColl) {
                                            for (Object innerItem : innerColl) {
                                                if (innerItem instanceof IRequest) {
                                                    allRequests.add((IRequest<?>) innerItem);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }

        if (allRequests.isEmpty()) return;

        List<BigItemStack> networkInventory = LogisticsBridge.getNetworkInventory(ticker);
        
        int limit = 0;
        for (IRequest<?> request : allRequests) {
            if (limit++ > 50) break;

            try {
                String targetAddress = addressResolver.apply(request);
                if (targetAddress == null) continue;

                ItemStack itemToSend = ItemStack.EMPTY;
                int amountNeeded = 1; 
                String reqType = request.getClass().getSimpleName();

                if (request.getRequest() instanceof Stack itemReq) {
                    itemToSend = itemReq.getStack().copy(); 
                    amountNeeded = itemReq.getCount(); 
                } 
                else if (reqType.contains("ToolRequest")) {
                    itemToSend = solveRequestByTier(request, networkInventory, "getMiningLevel");
                }
                else if (reqType.contains("ArmorRequest")) {
                    itemToSend = solveRequestByTier(request, networkInventory, "getLevel");
                }
                else {
                    Object innerReq = request.getRequest();
                    if (innerReq != null) {
                        
                        try {
                            for (Method m : innerReq.getClass().getMethods()) {
                                if (m.getName().equals("getRepresentation") && m.getParameterCount() == 0) {
                                    Object rep = m.invoke(innerReq);
                                    if (rep instanceof List list && !list.isEmpty()) {
                                        Object first = list.get(0);
                                        if (first instanceof ItemStack st) {
                                            itemToSend = st.copy();
                                            amountNeeded = st.getCount();
                                        }
                                    }
                                    break;
                                }
                            }
                        } catch (Exception e) {}

                        if (itemToSend.isEmpty()) {
                            for (Field f : getAllFields(innerReq.getClass())) {
                                try {
                                    f.setAccessible(true);
                                    Object val = f.get(innerReq);
                                    
                                    if (val instanceof ItemStack stack && !stack.isEmpty()) {
                                        itemToSend = stack.copy();
                                        amountNeeded = stack.getCount();
                                        break;
                                    } else if (val instanceof Stack stackReq) {
                                        itemToSend = stackReq.getStack().copy();
                                        amountNeeded = stackReq.getCount();
                                        break;
                                    } else if (val instanceof Collection<?> coll) {
                                        for (Object obj : coll) {
                                            if (obj instanceof ItemStack stackObj && !stackObj.isEmpty()) {
                                                itemToSend = stackObj.copy();
                                                amountNeeded = stackObj.getCount();
                                                break;
                                            } else if (obj instanceof Stack stackObj) {
                                                itemToSend = stackObj.getStack().copy();
                                                amountNeeded = stackObj.getCount();
                                                break;
                                            } else if (obj instanceof net.minecraft.world.item.Item itemObj) {
                                                itemToSend = new ItemStack(itemObj, 1);
                                                amountNeeded = 1;
                                                break;
                                            }
                                        }
                                        if (!itemToSend.isEmpty()) break;
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                if (itemToSend.isEmpty()) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.warn("   -> FAILED: Extracted item is EMPTY! Could not parse request data.");
                    continue;
                }
                
                if (canColonyCraft(colony, itemToSend)) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("   -> SKIPPED: Colony can already craft " + itemToSend.getHoverName().getString() + ". Ignoring request.");
                    continue; 
                }

                if (trackerUpdater != null) {
                    trackerUpdater.track(itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.REQUESTED, null);
                }

                int currentStock = getStockCount(networkInventory, itemToSend);

                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) {
                    LOGGER.info("   -> Stock Check for: " + itemToSend.getHoverName().getString() + " | Needed: " + amountNeeded + " | Found in Create: " + currentStock);
                }

                if (currentStock >= amountNeeded) {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("   -> Stock verified! Sending to LogisticsBridge...");
                    if (LogisticsBridge.sendPackage(ticker, itemToSend, amountNeeded, targetAddress, null)) {
                        if (trackerUpdater != null) {
                            trackerUpdater.track(itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.ACCEPTED, null);
                        }
                        
                        String successMsg = "Received " + itemToSend.getHoverName().getString();
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.info("[CCLogistics] COMPLETION: Successfully imported " + amountNeeded + "x " + itemToSend.getHoverName().getString());
                        
                        if (auditLog != null) auditLog.add("IN;" + successMsg);
                        if (onImportSuccess != null) onImportSuccess.accept(itemToSend);
                    } else {
                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.error("   -> FAILED: LogisticsBridge.sendPackage returned false!");
                    }
                } else {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.warn("   -> FAILED: Not enough stock in the Create Network.");
                    String statusMessage = (currentStock == 0) ? "No Stock" : (amountNeeded - currentStock) + " Missing";

                    if (trackerUpdater != null) {
                        trackerUpdater.track(itemToSend.getHoverName().getString(), amountNeeded, FreightTrackerModule.TrackStatus.NO_STOCK, statusMessage);
                    }

                    if (auditLog != null) {
                        auditLog.add(itemToSend.getHoverName().getString() + ": " + statusMessage);
                        auditLog.add("MISS;" + itemToSend.getHoverName().getString() + ": " + statusMessage);
                    }
                }

            } catch (Exception e) {
                if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.LOGISTICS)) LOGGER.error("[CCLogistics] CRASH while processing individual request!", e);
            }
        }
    }

    private static int getStockCount(List<BigItemStack> networkInv, ItemStack needed) {
        int totalStock = 0;
        for (BigItemStack bis : networkInv) {
            if (!bis.stack.isEmpty() && !needed.isEmpty() && bis.stack.getItem() == needed.getItem()) {
                totalStock += bis.count;
            }
        }
        return totalStock;
    }

    private static ItemStack solveRequestByTier(IRequest<?> request, List<BigItemStack> inventory, String checkMethodName) {
        try {
            Object innerObj = request.getRequest(); 
            if (innerObj == null) return ItemStack.EMPTY;
            
            ItemStack representation = ItemStack.EMPTY;
            try {
                for (Method m : innerObj.getClass().getMethods()) {
                    if (m.getName().equals("getRepresentation") && m.getParameterCount() == 0) {
                        Object rep = m.invoke(innerObj);
                        if (rep instanceof List list && !list.isEmpty()) {
                            Object first = list.get(0);
                            if (first instanceof ItemStack st) representation = st.copy();
                        }
                        break;
                    }
                }
            } catch (Exception e) {}

            Object equipmentType = getFieldByName(innerObj, "equipmentType");
            if (equipmentType == null) equipmentType = getFieldByName(innerObj, "type");
            if (equipmentType == null) equipmentType = getFieldByName(innerObj, "toolType"); 
            
            if (equipmentType == null) {
                if (!representation.isEmpty() && getStockCount(inventory, representation) > 0) {
                    representation.setCount(1);
                    return representation;
                }
                return ItemStack.EMPTY;
            }

            int minLevel = 0;
            int maxLevel = 99;
            try { 
                Object minObj = getFieldByName(innerObj, "minLevel");
                if (minObj != null) minLevel = (int) minObj;
            } catch (Exception e) {}
            
            try { 
                Object maxObj = getFieldByName(innerObj, "maxLevel");
                if (maxObj != null) maxLevel = (int) maxObj;
            } catch (Exception e) {}

            Method levelCheckMethod = null;
            try { levelCheckMethod = equipmentType.getClass().getMethod(checkMethodName, ItemStack.class); } catch (Exception e) {}
            if (levelCheckMethod == null) try { levelCheckMethod = equipmentType.getClass().getMethod("getLevel", ItemStack.class); } catch (Exception e) {}
            if (levelCheckMethod == null) try { levelCheckMethod = equipmentType.getClass().getMethod("getMiningLevel", ItemStack.class); } catch (Exception e) {}

            List<ItemStack> candidates = new ArrayList<>();
            if (levelCheckMethod != null) {
                for (BigItemStack bis : inventory) {
                    ItemStack stack = bis.stack;
                    if (stack.isEmpty()) continue;
                    try {
                        int itemTier = (int) levelCheckMethod.invoke(equipmentType, stack);
                        if (itemTier >= minLevel && itemTier <= maxLevel) candidates.add(stack);
                    } catch (Exception e) {}
                }
            }

            if (candidates.isEmpty()) {
                if (!representation.isEmpty() && getStockCount(inventory, representation) > 0) {
                    representation.setCount(1);
                    return representation;
                }
                return ItemStack.EMPTY;
            }

            final Method finalMethod = levelCheckMethod;
            final Object finalEquipmentType = equipmentType;

            candidates.sort((a, b) -> {
                try {
                    int tierA = (int) finalMethod.invoke(finalEquipmentType, a);
                    int tierB = (int) finalMethod.invoke(finalEquipmentType, b);
                    return Integer.compare(tierB, tierA); 
                } catch (Exception e) { return 0; }
            });

            return candidates.get(0).copyWithCount(1);
        } catch (Exception e) { 
            return ItemStack.EMPTY; 
        }
    }

    private static boolean canColonyCraft(IColony colony, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getMaxDamage() > 0 || !stack.isStackable()) {
            return false;
        }
        
        try {
            var recipes = IColonyManager.getInstance().getRecipeManager().getRecipes();
            for (Object storageObj : recipes.values()) {
                if (storageObj instanceof IRecipeStorage storage) {
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

    private static Object getFieldByName(Object target, String name) {
        for (Field f : getAllFields(target.getClass())) {
            if (f.getName().equals(name)) {
                try {
                    f.setAccessible(true);
                    return f.get(target);
                } catch (Exception e) {}
            }
        }
        return null;
    }

    private static List<Field> getAllFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }
}