package com.ogtenzohd.cclogistics.util;

import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.accessor.IAutomatedTicker;
import com.ogtenzohd.cclogistics.compat.CFLCompat;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.*;

public class LogisticsBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static BigItemStack checkStock(StockTickerBlockEntity ticker, Object token, PackageOrder order) {
        if (!(token instanceof ItemStack wanted) || wanted.isEmpty()) {
            return new BigItemStack(ItemStack.EMPTY, 0);
        }

        try {
            LogisticallyLinkedBehaviour link = ticker.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
            if (link == null || link.freqId == null) {
                LOGGER.warn("[Bridge-CheckStock] StockTicker has no frequency ID!");
                return new BigItemStack(wanted, 0);
            }

            Collection<LogisticallyLinkedBehaviour> networkLinks = LogisticallyLinkedBehaviour.getAllPresent(link.freqId, false);

            if (networkLinks == null || networkLinks.isEmpty()) {
                LOGGER.warn("[Bridge-CheckStock] Network has no links for freqId: " + link.freqId);
                return new BigItemStack(wanted, 0);
            }

            long totalCount = 0;
            for (LogisticallyLinkedBehaviour behaviour : networkLinks) {
                if (behaviour == null) continue;
                com.simibubi.create.content.logistics.packager.InventorySummary summary = behaviour.getSummary(null);

                if (summary != null) {
                    totalCount += summary.getCountOf(wanted);
                }
            }

            return new BigItemStack(wanted, (int) totalCount);

        } catch (Exception e) {
            LOGGER.error("[Bridge-CheckStock] Crash in checkStock", e);
            return new BigItemStack(wanted, 0);
        }
    }

    private static PackagerBlockEntity findConnectedPackager(PackagerLinkBlockEntity linkBE) {
        Level level = linkBE.getLevel();
        BlockPos linkPos = linkBE.getBlockPos();

        for (Direction d : Direction.values()) {
            BlockEntity neighbor = level.getBlockEntity(linkPos.relative(d));
            if (neighbor instanceof PackagerBlockEntity packager) {
                return packager;
            }
        }
        return null;
    }

    public static List<BigItemStack> getNetworkInventory(StockTickerBlockEntity ticker) {
        List<BigItemStack> allItems = new ArrayList<>();
        try {
            LogisticallyLinkedBehaviour link = ticker.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
            if (link == null || link.freqId == null) return allItems;

            Collection<LogisticallyLinkedBehaviour> networkLinks = LogisticallyLinkedBehaviour.getAllPresent(link.freqId, false);
            if (networkLinks == null || networkLinks.isEmpty()) return allItems;

            for (LogisticallyLinkedBehaviour behaviour : networkLinks) {
                if (behaviour == null) continue;
                com.simibubi.create.content.logistics.packager.InventorySummary summary = behaviour.getSummary(null);

                if (summary != null) {
                    for (var entry : summary.getStacks()) {
                        if (entry.stack != null && !entry.stack.isEmpty() && entry.count > 0) {
                            allItems.add(new BigItemStack(entry.stack.copy(), entry.count));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Bridge-GetNetwork] CRASH", e);
        }
        return allItems;
    }

    public static boolean sendPackage(StockTickerBlockEntity ticker, Object token, int countNeeded, String address, PackageOrder dummyOrder) {
        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.BRIDGE)) LOGGER.info("[Bridge-Send] sendPackage invoked for Address: " + address);

        if (!(token instanceof ItemStack stack) || stack.isEmpty()) {
            LOGGER.error("[Bridge-Send] FAILED: Token is invalid or empty!");
            return false;
        }

        LogisticallyLinkedBehaviour link = ticker.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
        if (link == null || link.freqId == null) {
            LOGGER.error("[Bridge-Send] FAILED: StockTicker is not linked to a network!");
            return false;
        }

        ItemStack cleanStack = stack.copy();

        if (net.neoforged.fml.ModList.get().isLoaded("createstockbridge")) {
            wakeUpStockBridges(link.freqId, cleanStack, countNeeded, address);
        }

        CFLCompat.init();
        if (CFLCompat.cflLoaded) {
            boolean cakLoaded = net.neoforged.fml.ModList.get().isLoaded("createappliedkinetics");
            LogisticallyLinkedBehaviour.RequestType reqType = cakLoaded ?
                    LogisticallyLinkedBehaviour.RequestType.PLAYER :
                    LogisticallyLinkedBehaviour.RequestType.REDSTONE;

            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.BRIDGE)) {
                LOGGER.info("[Bridge-Send] CFL Detected! Routing via GenericLogisticsManager as " + reqType.name());
            }
            return CFLCompat.sendCFLPackage(link.freqId, cleanStack, countNeeded, address, reqType);
        }

        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.BRIDGE)) {
            LOGGER.info("[Bridge-Send] Native Create Detected. Forwarding to StockTicker Mixin...");
        }

        ItemStack typeStack = stack.copy();
        typeStack.setCount(1);
        typeStack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);

        BigItemStack bigStack = new BigItemStack(typeStack, countNeeded);
        PackageOrder realOrder = new PackageOrder(Collections.singletonList(bigStack));
        PackageOrderWithCrafts wrapped = new PackageOrderWithCrafts(realOrder, Collections.emptyList());

        try {
            send(ticker, wrapped, address);
            return true;
        } catch (Exception e) {
            LOGGER.error("[Bridge-Send] Send failed with exception", e);
            return false;
        }
    }

    private static void wakeUpStockBridges(java.util.UUID freqId, ItemStack itemToShip, int amount, String address) {
        try {
            Collection<LogisticallyLinkedBehaviour> networkLinks = LogisticallyLinkedBehaviour.getAllPresent(freqId, false);
            if (networkLinks == null || networkLinks.isEmpty()) return;

            for (LogisticallyLinkedBehaviour behaviour : networkLinks) {
                if (behaviour == null || behaviour.blockEntity == null) continue;

                PackagerBlockEntity packager = null;
                if (behaviour.blockEntity instanceof PackagerBlockEntity p) {
                    packager = p;
                } else if (behaviour.blockEntity instanceof PackagerLinkBlockEntity linkBE) {
                    packager = findConnectedPackager(linkBE);
                }

                if (packager != null && packager.targetInventory != null) {
                    Object rawInv = packager.targetInventory.getInventory();
                    if (rawInv == null) continue;

                    if (rawInv.getClass().getName().contains("BridgeInventory")) {
                        Method getBlockEntity = rawInv.getClass().getMethod("getBlockEntity");
                        getBlockEntity.setAccessible(true);
                        Object bridgeEntity = getBlockEntity.invoke(rawInv);

                        com.simibubi.create.content.logistics.packager.PackagingRequest dummyRequest =
                                new com.simibubi.create.content.logistics.packager.PackagingRequest(
                                        itemToShip.copy(),
                                        new MutableInt(amount),
                                        address,
                                        0,
                                        new MutableBoolean(true),
                                        new MutableInt(1),
                                        0,
                                        null
                                );

                        Method pullMethod = bridgeEntity.getClass().getMethod("pull", com.simibubi.create.content.logistics.packager.PackagingRequest.class);
                        pullMethod.setAccessible(true);
                        pullMethod.invoke(bridgeEntity, dummyRequest);

                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.BRIDGE)) LOGGER.info("[Bridge-Compat] Successfully woke up a Stock Bridge for item: " + itemToShip.getHoverName().getString());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[Bridge-Compat] Failed to send wake-up call to Stock Bridge.", e);
        }
    }

    public static void send(Object ticker, Object order, String address) {
        if (ticker instanceof IAutomatedTicker automatedTicker) {
            if (order instanceof PackageOrderWithCrafts packageOrder) {
                automatedTicker.cclogistics$automatedRequest(packageOrder, address);
            } else {
                LOGGER.error("[Bridge-Send] FAILED: Order is not PackageOrderWithCrafts! Type: " + order.getClass().getName());
            }
        } else {
            LOGGER.error("[Bridge-Send] FAILED: Ticker does not implement IAutomatedTicker mixin! Are mixins loading properly?");
        }
    }
}