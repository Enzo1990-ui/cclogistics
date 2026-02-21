package com.ogtenzohd.cclogistics.mixin;

import com.ogtenzohd.cclogistics.accessor.IAutomatedTicker;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StockTickerBlockEntity.class, remap = false)
public abstract class StockTickerMixin implements IAutomatedTicker {

    @Unique
    private static final Logger cclogistics$LOGGER = LogUtils.getLogger();


    @Override
    public void cclogistics$automatedRequest(PackageOrderWithCrafts orderWrapper, String address) {
        try {
            // Only log if debug mode is enabled so it doesn't spam the server console
            boolean isDebug = CCLConfig.INSTANCE.debugMode.get();
            
            if (isDebug) {
                cclogistics$LOGGER.info("[StockTickerMixin] Received Automated Request for Address: {}", address);
            }
            
            StockTickerBlockEntity ticker = (StockTickerBlockEntity) (Object) this;
            LogisticallyLinkedBehaviour link = ticker.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
            
            if (link == null || link.freqId == null) {
                if (isDebug) cclogistics$LOGGER.warn("[StockTickerMixin] Ticker at {} has no Frequency ID (Not Linked!)", ticker.getBlockPos());
                return;
            }

            if (isDebug) cclogistics$LOGGER.info("[StockTickerMixin] Broadcasting Package Request on Freq: {}", link.freqId);
            
            LogisticsManager.broadcastPackageRequest(
                link.freqId,
                LogisticallyLinkedBehaviour.RequestType.REDSTONE, 
                orderWrapper,
                null, 
                address
            );
            
            if (isDebug) cclogistics$LOGGER.info("[StockTickerMixin] Request Sent via Patched LogisticsManager!");
            
        } catch (Exception e) {
            cclogistics$LOGGER.error("[StockTickerMixin] Failed to process automated request for address: " + address, e);
        }
    }
}