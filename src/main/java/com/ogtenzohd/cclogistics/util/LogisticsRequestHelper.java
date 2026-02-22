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
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.function.Consumer;

public class LogisticsRequestHelper {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("unchecked")
    public static void processRequests(
            IColony colony,
            StockTickerBlockEntity ticker,
            Set<Object> activeRequestIds,
            Set<Object> failedRequestIds,
            Function<IRequest<?>, String> addressResolver,
            List<String> auditLog,
            Consumer<ItemStack> onImportSuccess
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
                                if (val instanceof Collection) {
                                    Collection<?> coll = (Collection<?>) val;
                                    if (!coll.isEmpty() && coll.iterator().next() instanceof IRequest) {
                                        allRequests.addAll((Collection<IRequest<?>>) coll);
                                    }
                                } else if (val instanceof Map) {
                                    Map<?, ?> reqMap = (Map<?, ?>) val;
                                    if (!reqMap.isEmpty()) {
                                        Object firstVal = reqMap.values().iterator().next();
                                        if (firstVal instanceof IRequest) {
                                            allRequests.addAll((Collection<IRequest<?>>) reqMap.values());
                                        } else if (firstVal instanceof Collection) {
                                            for (Object listObj : reqMap.values()) {
                                                if (listObj instanceof Collection) {
                                                    for (Object item : (Collection<?>) listObj) {
                                                        if (item instanceof IRequest) allRequests.add((IRequest<?>) item);
                                                    }
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
        
        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[CCLogistics] Processing " + allRequests.size() + " active requests...");

        int limit = 0;
        for (IRequest<?> request : allRequests) {
            if (limit++ > 50) break;

            try {
                String targetAddress = addressResolver.apply(request);
                if (targetAddress == null) continue;

                ItemStack itemToSend = ItemStack.EMPTY;
                String reqType = request.getClass().getSimpleName();

                if (request.getRequest() instanceof Stack itemReq) {
                    itemToSend = itemReq.getStack().copy(); 
                    itemToSend.setCount(itemReq.getCount());
                } 
                else if (reqType.contains("ToolRequest")) {
                    itemToSend = solveRequestByTier(request, networkInventory, "getMiningLevel");
                }
                else if (reqType.contains("ArmorRequest")) {
                    itemToSend = solveRequestByTier(request, networkInventory, "getLevel");
                }

                if (itemToSend.isEmpty()) continue;

                // --- NEW CRAFTING CHECK ---
                // If the colony knows how to craft this item, skip it!
                // This forces Minecolonies to generate requests for the base materials instead.
                if (canColonyCraft(colony, itemToSend)) {
                    if (CCLConfig.INSTANCE.debugMode.get()) {
                        LOGGER.info("[CCLogistics] Skipped request for " + itemToSend.getHoverName().getString() + " because the colony knows how to craft it.");
                    }
                    continue; 
                }
                // --------------------------

                if (hasStock(networkInventory, itemToSend)) {
                    if (LogisticsBridge.sendPackage(ticker, itemToSend, targetAddress, null)) {
                        String msg = "Received " + itemToSend.getHoverName().getString();
                        LOGGER.info("[CCLogistics] Imported " + itemToSend.getHoverName().getString() + " for " + targetAddress);
                        if (auditLog != null) auditLog.add(msg);
                        
                        if (onImportSuccess != null) {
                            onImportSuccess.accept(itemToSend);
                        }
                    }
                }

            } catch (Exception e) {
                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.error("[CCLogistics] Error processing request", e);
            }
        }
    }

    private static Object getFieldByName(Object target, String name) {
        for (Field f : getAllFields(target.getClass())) {
            if (f.getName().equals(name)) {
                try {
                    f.setAccessible(true);
                    return f.get(target);
                } catch (Exception ignored) {}
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

    private static ItemStack solveRequestByTier(IRequest<?> request, List<BigItemStack> inventory, String checkMethodName) {
        try {
            Object innerObj = extractField(request, "requested");
            if (innerObj == null) return ItemStack.EMPTY;
            Object equipmentType = extractField(innerObj, "equipmentType");
            if (equipmentType == null) return ItemStack.EMPTY;

            int minLevel = (int) extractField(innerObj, "minLevel");
            int maxLevel = (int) extractField(innerObj, "maxLevel");

            Method levelCheckMethod = null;
            try { levelCheckMethod = equipmentType.getClass().getMethod(checkMethodName, ItemStack.class); } catch (Exception e) {}
            if (levelCheckMethod == null) try { levelCheckMethod = equipmentType.getClass().getMethod("getLevel", ItemStack.class); } catch (Exception e) {}
            if (levelCheckMethod == null) try { levelCheckMethod = equipmentType.getClass().getMethod("getMiningLevel", ItemStack.class); } catch (Exception e) {}

            if (levelCheckMethod == null) return ItemStack.EMPTY;

            List<ItemStack> candidates = new ArrayList<>();
            for (BigItemStack bis : inventory) {
                ItemStack stack = bis.stack;
                if (stack.isEmpty()) continue;
                try {
                    int itemTier = (int) levelCheckMethod.invoke(equipmentType, stack);
                    if (itemTier >= minLevel && itemTier <= maxLevel) candidates.add(stack);
                } catch (Exception ignored) {}
            }

            if (candidates.isEmpty()) return ItemStack.EMPTY;

            final Method finalMethod = levelCheckMethod;
            candidates.sort((a, b) -> {
                try {
                    int tierA = (int) finalMethod.invoke(equipmentType, a);
                    int tierB = (int) finalMethod.invoke(equipmentType, b);
                    return Integer.compare(tierB, tierA);
                } catch (Exception e) { return 0; }
            });

            return candidates.get(0).copyWithCount(1);
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private static boolean hasStock(List<BigItemStack> networkInv, ItemStack needed) {
        for (BigItemStack bis : networkInv) {
            if (ItemStack.isSameItemSameComponents(bis.stack, needed)) {
                return bis.count >= needed.getCount();
            }
        }
        return false;
    }
	
	private static boolean canColonyCraft(IColony colony, ItemStack stack) {
        if (stack.isEmpty()) return false;
        
        try {
            var recipes = IColonyManager.getInstance().getRecipeManager().getRecipes();
            
            for (Object storageObj : recipes.values()) {
                if (storageObj instanceof IRecipeStorage storage) {
                    
                    if (storage.getPrimaryOutput() != null && storage.getPrimaryOutput().getItem() == stack.getItem()) {
                        return true;
                    }
                    
                    if (storage.getAlternateOutputs() != null) {
                        for (ItemStack alt : storage.getAlternateOutputs()) {
                            if (alt != null && alt.getItem() == stack.getItem()) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (CCLConfig.INSTANCE.debugMode.get()) {
                LOGGER.warn("[CCLogistics] Could not check recipe manager for " + stack.getHoverName().getString());
            }
        }
        return false;
    }

    private static Object extractField(Object target, String fieldName) throws IllegalAccessException {
        for (Field f : getAllFields(target.getClass())) {
            if (f.getName().equals(fieldName)) {
                f.setAccessible(true);
                return f.get(target);
            }
        }
        return null;
    }
}