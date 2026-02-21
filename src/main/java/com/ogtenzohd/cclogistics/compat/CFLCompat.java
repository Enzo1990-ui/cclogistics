package com.ogtenzohd.cclogistics.compat;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.compat.CFLCompat;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class CFLCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized = false;
    public static boolean cflLoaded = false;

    private static Method bigGenericStackOfMethod;
    private static Method bigGenericStackGetMethod;
    private static Method genericOrderOrderMethod;
    private static Method broadcastPackageRequestMethod;

    public static void init() {
        if (initialized) return;
        cflLoaded = ModList.get().isLoaded("create_factory_logistics") || ModList.get().isLoaded("create_factory_abstractions");
        
        if (cflLoaded) {
            try {
                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[CCLogistics] Create Factory Logistics detected! Wiring up GenericLogisticsManager...");
                
                Class<?> bigGenericStackClass = Class.forName("ru.zznty.create_factory_abstractions.generic.support.BigGenericStack");
                bigGenericStackOfMethod = bigGenericStackClass.getMethod("of", BigItemStack.class);
                bigGenericStackGetMethod = bigGenericStackClass.getMethod("get");

                Class<?> genericOrderClass = Class.forName("ru.zznty.create_factory_abstractions.generic.support.GenericOrder");
                genericOrderOrderMethod = genericOrderClass.getMethod("order", List.class);

                Class<?> genericLogisticsManagerClass = Class.forName("ru.zznty.create_factory_abstractions.generic.support.GenericLogisticsManager");
                broadcastPackageRequestMethod = genericLogisticsManagerClass.getMethod("broadcastPackageRequest", 
                        UUID.class, 
                        LogisticallyLinkedBehaviour.RequestType.class, 
                        genericOrderClass, 
                        IdentifiedInventory.class, 
                        String.class);

                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[CCLogistics] CFL Reflection Wrapper initialized successfully!");
            } catch (Exception e) {
                if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.error("[CCLogistics] Failed to initialize CFL reflection! Falling back to native Create.", e);
                cflLoaded = false;
            }
        }
        initialized = true;
    }

    public static boolean sendCFLPackage(UUID networkFreqId, ItemStack stack, String address) {
        if (!cflLoaded) return false;
        
        try {
            ItemStack typeStack = stack.copy();
            typeStack.setCount(1);
            BigItemStack bigStack = new BigItemStack(typeStack, stack.getCount());

            Object bigGenericStack = bigGenericStackOfMethod.invoke(null, bigStack);
            Object genericStack = bigGenericStackGetMethod.invoke(bigGenericStack);

            Object genericOrder = genericOrderOrderMethod.invoke(null, Collections.singletonList(genericStack));

            broadcastPackageRequestMethod.invoke(null, 
                    networkFreqId, 
                    LogisticallyLinkedBehaviour.RequestType.REDSTONE, 
                    genericOrder, 
                    null, 
                    address);
            
            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[CCLogistics] Successfully pushed request to CFL GenericLogisticsManager for address: " + address);
            return true;
            
        } catch (Exception e) {
            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.error("[CCLogistics] CFL Package routing failed during reflection execution", e);
            return false;
        }
    }
}