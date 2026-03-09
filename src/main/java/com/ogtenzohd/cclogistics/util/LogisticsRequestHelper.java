package com.ogtenzohd.cclogistics.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class LogisticsRequestHelper {
    private static Field dataStoreManagerField = null;
    private static Field storeMapField = null;

    public static Collection<IRequest<?>> getRequests(IColony colony) {
        try {
            IRequestManager manager = colony.getRequestManager();
            if (dataStoreManagerField == null) {
                dataStoreManagerField = manager.getClass().getDeclaredField("dataStoreManager");
                dataStoreManagerField.setAccessible(true);
            }
            Object dataStoreManager = dataStoreManagerField.get(manager);
            
            if (storeMapField == null) {
                storeMapField = dataStoreManager.getClass().getDeclaredField("storeMap");
                storeMapField.setAccessible(true);
            }
            
            Map<?, IRequest<?>> storeMap = (Map<?, IRequest<?>>) storeMapField.get(dataStoreManager);
            return storeMap != null ? storeMap.values() : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}