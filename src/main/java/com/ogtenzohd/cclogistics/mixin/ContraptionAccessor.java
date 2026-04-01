package com.ogtenzohd.cclogistics.mixin;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MountedStorageManager.class)
public interface ContraptionAccessor {

    @Accessor("items")
    MountedItemStorageWrapper cclogistics$getItems();
}