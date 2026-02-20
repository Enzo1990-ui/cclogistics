package com.ogtenzohd.cclogistics.util;

import com.ogtenzohd.cclogistics.accessor.IAutomatedTicker;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity; // Import the Link BE
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler; 
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

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
                LOGGER.warn("[Bridge] StockTicker has no frequency ID!");
                return new BigItemStack(wanted, 0);
            }

            Collection<LogisticallyLinkedBehaviour> networkLinks = LogisticallyLinkedBehaviour.getAllPresent(link.freqId, false);
            
            if (networkLinks == null || networkLinks.isEmpty()) {
                LOGGER.warn("[Bridge] Network has no links for freqId: " + link.freqId);
                return new BigItemStack(wanted, 0);
            }

            long totalCount = 0;
            Set<BlockPos> visitedPackagers = new HashSet<>();

            for (LogisticallyLinkedBehaviour behaviour : networkLinks) {
                if (behaviour == null || behaviour.blockEntity == null) continue;
                
                if (behaviour.blockEntity instanceof PackagerBlockEntity packager) {
                    if (visitedPackagers.add(packager.getBlockPos())) {
                        totalCount += countInPackager(packager, wanted);
                    }
                }
                
                else if (behaviour.blockEntity instanceof PackagerLinkBlockEntity linkBE) {
                    PackagerBlockEntity foundPackager = findConnectedPackager(linkBE);
                    if (foundPackager != null) {
                         if (visitedPackagers.add(foundPackager.getBlockPos())) {
                            totalCount += countInPackager(foundPackager, wanted);
                        }
                    }
                }
            }
            return new BigItemStack(wanted, (int) totalCount);

        } catch (Exception e) {
            LOGGER.error("[Bridge] Crash in checkStock", e);
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

    private static long countInPackager(PackagerBlockEntity packager, ItemStack wanted) {
        long count = 0;
        if (packager.targetInventory != null) {
            try {
                Object rawInv = packager.targetInventory.getInventory();
                IItemHandler handler = null;

                if (rawInv instanceof IItemHandler direct) {
                    handler = direct;
                } else if (rawInv instanceof BlockEntity be) {
                    Direction targetSide = packager.getBlockState().getValue(PackagerBlock.FACING).getOpposite();
                    handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, targetSide);
                }

                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack inSlot = handler.getStackInSlot(i);
                        if (!inSlot.isEmpty() && isSameItem(inSlot, wanted)) {
                            count += inSlot.getCount();
                        }
                    }
                }
            } catch (Exception e) {}
        }
        return count;
    }

    public static List<BigItemStack> getNetworkInventory(StockTickerBlockEntity ticker) {
        List<BigItemStack> allItems = new ArrayList<>();
        Map<String, BigItemStack> consolidated = new HashMap<>();

        try {
            LogisticallyLinkedBehaviour link = ticker.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
            if (link == null || link.freqId == null) return allItems;

            Collection<LogisticallyLinkedBehaviour> networkLinks = LogisticallyLinkedBehaviour.getAllPresent(link.freqId, false);
            Set<BlockPos> visitedPackagers = new HashSet<>();

            if (networkLinks != null) {
                for (LogisticallyLinkedBehaviour behaviour : networkLinks) {
                    if (behaviour == null || behaviour.blockEntity == null) continue;
                    
                    PackagerBlockEntity packagerToScan = null;

                    if (behaviour.blockEntity instanceof PackagerBlockEntity p) {
                        packagerToScan = p;
                    } else if (behaviour.blockEntity instanceof PackagerLinkBlockEntity linkBE) {
                        packagerToScan = findConnectedPackager(linkBE);
                    }

                    if (packagerToScan != null && visitedPackagers.add(packagerToScan.getBlockPos())) {
                         if (packagerToScan.targetInventory != null) {
                            try {
                                Object rawInv = packagerToScan.targetInventory.getInventory();
                                IItemHandler handler = null;

                                if (rawInv instanceof IItemHandler direct) {
                                    handler = direct;
                                } else if (rawInv instanceof BlockEntity be) {
                                    Direction targetSide = packagerToScan.getBlockState().getValue(PackagerBlock.FACING).getOpposite();
                                    handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, targetSide);
                                }

                                if (handler != null) {
                                    for (int i = 0; i < handler.getSlots(); i++) {
                                        ItemStack s = handler.getStackInSlot(i);
                                        if (!s.isEmpty()) {
                                            String key = s.getDescriptionId(); 
                                            if (consolidated.containsKey(key)) {
                                                consolidated.get(key).count += s.getCount();
                                            } else {
                                                consolidated.put(key, new BigItemStack(s, s.getCount()));
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    }
                }
            }
        } catch (Exception e) {}
        
        allItems.addAll(consolidated.values());
        return allItems;
    }

    private static boolean isSameItem(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && a.getItem() == b.getItem();
    }

    public static boolean sendPackage(StockTickerBlockEntity ticker, Object token, String address, PackageOrder dummyOrder) {
        LOGGER.info("[Bridge] sendPackage invoked for Address: " + address);
        
        if (!(token instanceof ItemStack stack) || stack.isEmpty()) {
            LOGGER.warn("[Bridge] Token is invalid or empty ItemStack");
            return false;
        }

        LOGGER.info("[Bridge] Item: " + stack.getHoverName().getString() + " x" + stack.getCount());

        ItemStack typeStack = stack.copy();
        typeStack.setCount(1);
        
        BigItemStack bigStack = new BigItemStack(typeStack, stack.getCount());

        PackageOrder realOrder = new PackageOrder(Collections.singletonList(bigStack));

        PackageOrderWithCrafts wrapped = new PackageOrderWithCrafts(realOrder, Collections.emptyList());

        try {
            LOGGER.info("[Bridge] Forwarding to StockTicker Mixin...");
            send(ticker, wrapped, address);
            return true;
        } catch (Exception e) {
            LOGGER.error("[Bridge] Send failed", e);
            return false;
        }
    }

    public static void send(Object ticker, Object order, String address) {
        if (ticker instanceof IAutomatedTicker automatedTicker) {
            if (order instanceof PackageOrderWithCrafts packageOrder) {
                automatedTicker.cclogistics$automatedRequest(packageOrder, address);
            } else {
                 LOGGER.warn("[Bridge] Order is not PackageOrderWithCrafts! Type: " + order.getClass().getName());
            }
        } else {
             LOGGER.warn("[Bridge] Ticker does not implement IAutomatedTicker mixin!");
        }
    }
}
