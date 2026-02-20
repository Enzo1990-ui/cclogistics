package com.ogtenzohd.cclogistics.mixin;

import com.ogtenzohd.cclogistics.accessor.IAutomatedTicker;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = StockTickerBlockEntity.class, remap = false)
public abstract class StockTickerMixin extends SmartBlockEntity implements IAutomatedTicker {

    private static final Logger LOGGER = LogUtils.getLogger();

    public StockTickerMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void cclogistics$automatedRequest(PackageOrderWithCrafts orderWrapper, String address) {
        try {
            LOGGER.info("[StockTickerMixin] Received Automated Request for Address: " + address);
            
            LogisticallyLinkedBehaviour link = this.getBehaviour(LogisticallyLinkedBehaviour.TYPE);
            if (link == null || link.freqId == null) {
                LOGGER.warn("[StockTickerMixin] Ticker has no Frequency ID (Not Linked!)");
                return;
            }

            LOGGER.info("[StockTickerMixin] Broadcasting Package Request on Freq: " + link.freqId);
            
            LogisticsManager.broadcastPackageRequest(
                link.freqId,
                LogisticallyLinkedBehaviour.RequestType.REDSTONE, 
                orderWrapper,
                null, 
                address
            );
            
            LOGGER.info("[StockTickerMixin] Request Sent via Patched LogisticsManager!");
        } catch (Exception e) {
            LOGGER.error("[StockTickerMixin] Mixin Error", e);
        }
    }
}
