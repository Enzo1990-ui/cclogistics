package com.ogtenzohd.cclogistics.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.BigItemStack;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

public class LogisticsRequestHelper {
    private static Field dataStoreManagerField = null;
    private static Field storeMapField = null;

    @FunctionalInterface
    public interface IResultHandler {
        void handle(IRequest<?> request, ItemStack stack, String address);
    }

    @FunctionalInterface
    public interface IFailureHandler {
        void handle(IRequest<?> request, ItemStack stack, int available, int needed);
    }

    public static void processAllLogistics(IColony colony, InventorySummary summary, Function<IRequest<?>, String> addressResolver, IResultHandler successHandler, IFailureHandler failureHandler) {
        if (colony == null || summary == null) return;

        for (IRequest<?> request : getRequests(colony)) {
            ItemStack itemNeeded = extractItemFromRequest(request, summary);
            if (itemNeeded == null || itemNeeded.isEmpty()) continue;

            if (canColonyCraft(itemNeeded)) continue;

            String address = addressResolver.apply(request);
            if (address != null) {
                int available = summary.getCountOf(itemNeeded);
                int needed = itemNeeded.getCount();

                if (available >= needed) {
                    successHandler.handle(request, itemNeeded, address);
                } else {
                    failureHandler.handle(request, itemNeeded, available, needed);
                }
            }
        }
    }

    private static ItemStack extractItemFromRequest(IRequest<?> request, InventorySummary summary) {
        Object innerReq = request.getRequest();
        if (innerReq == null) return ItemStack.EMPTY;
        if (innerReq instanceof Stack stackReq) return stackReq.getStack();
        
        String reqType = request.getClass().getSimpleName();
        if (reqType.contains("ToolRequest")) return solveTieredRequest(request, summary, "getMiningLevel");
        if (reqType.contains("ArmorRequest")) return solveTieredRequest(request, summary, "getLevel");

        return huntForItemField(innerReq);
    }

    private static ItemStack solveTieredRequest(IRequest<?> request, InventorySummary summary, String methodName) {
        try {
            Object inner = request.getRequest();
            Object type = getFieldByName(inner, "equipmentType");
            if (type == null) type = getFieldByName(inner, "type");
            if (type == null) return ItemStack.EMPTY;

            int min = (int) Optional.ofNullable(getFieldByName(inner, "minLevel")).orElse(0);
            int max = (int) Optional.ofNullable(getFieldByName(inner, "maxLevel")).orElse(99);

            Method check = type.getClass().getMethod(methodName, ItemStack.class);
            final Object finalType = type;

            return summary.getStacks().stream()
                .map(bis -> bis.stack)
                .filter(s -> !s.isEmpty())
                .filter(s -> {
                    try {
                        int lvl = (int) check.invoke(finalType, s);
                        return lvl >= min && lvl <= max;
                    } catch (Exception e) { return false; }
                })
                .max(Comparator.comparingInt(s -> {
                    try { return (int) check.invoke(finalType, s); } catch (Exception e) { return 0; }
                }))
                .map(s -> s.copyWithCount(1))
                .orElse(ItemStack.EMPTY);
        } catch (Exception e) { return ItemStack.EMPTY; }
    }

    private static ItemStack huntForItemField(Object inner) {
        if (inner == null) return ItemStack.EMPTY;
        for (Field f : getAllFields(inner.getClass())) {
            try {
                f.setAccessible(true);
                Object val = f.get(inner);
                if (val instanceof Collection<?> coll) {
                    for (Object obj : coll) {
                        if (obj instanceof ItemStack s && !s.isEmpty()) return s;
                        if (obj instanceof Stack s) return s.getStack();
                        if (obj instanceof net.minecraft.world.item.Item i) return new ItemStack(i);
                    }
                }
                if (val instanceof ItemStack s && !s.isEmpty()) return s;
                if (val instanceof Stack s) return s.getStack();
            } catch (Exception ignored) {}
        }
        return ItemStack.EMPTY;
    }

    public static boolean canColonyCraft(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            var recipes = IColonyManager.getInstance().getRecipeManager().getRecipes();
            for (Object obj : recipes.values()) {
                if (obj instanceof IRecipeStorage storage) {
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

    public static Collection<IRequest<?>> getRequests(IColony colony) {
        try {
            IRequestManager manager = colony.getRequestManager();
            if (dataStoreManagerField == null) {
                dataStoreManagerField = manager.getClass().getDeclaredField("dataStoreManager");
                dataStoreManagerField.setAccessible(true);
            }
            Object dsm = dataStoreManagerField.get(manager);
            if (storeMapField == null) {
                storeMapField = dsm.getClass().getDeclaredField("storeMap");
                storeMapField.setAccessible(true);
            }
            Map<?, IRequest<?>> map = (Map<?, IRequest<?>>) storeMapField.get(dsm);
            return map != null ? map.values() : Collections.emptyList();
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private static Object getFieldByName(Object target, String name) {
        for (Field f : getAllFields(target.getClass())) {
            if (f.getName().equals(name)) {
                try { f.setAccessible(true); return f.get(target); } catch (Exception ignored) {}
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