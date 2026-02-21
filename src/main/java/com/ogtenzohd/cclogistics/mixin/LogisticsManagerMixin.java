package com.ogtenzohd.cclogistics.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.collect.Multimap;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;

@Mixin(value = LogisticsManager.class, remap = false)
public abstract class LogisticsManagerMixin {

    @Inject(method = "performPackageRequests", at = @At("HEAD"), cancellable = true)
    private static void performPackageRequests(Multimap<PackagerBlockEntity, PackagingRequest> requests, CallbackInfo ci) {
        Map<PackagerBlockEntity, Collection<PackagingRequest>> asMap = requests.asMap();

        for (Map.Entry<PackagerBlockEntity, Collection<PackagingRequest>> entry : asMap.entrySet()) {
            
            ArrayList<PackagingRequest> queuedRequests = new ArrayList<>(entry.getValue());
            PackagerBlockEntity packager = entry.getKey();

            if (!queuedRequests.isEmpty()) {
                packager.flashLink();
            }

            for (int i = 0; i < 100 && !queuedRequests.isEmpty(); i++) {
                packager.attemptToSend(queuedRequests);
            }

            packager.triggerStockCheck();
            packager.notifyUpdate();
        }
        
        ci.cancel(); 
    }
}