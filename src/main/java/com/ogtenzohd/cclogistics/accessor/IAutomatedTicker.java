package com.ogtenzohd.cclogistics.accessor;

import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

public interface IAutomatedTicker {
    void cclogistics$automatedRequest(PackageOrderWithCrafts order, String address);
}