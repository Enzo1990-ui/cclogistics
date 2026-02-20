package com.ogtenzohd.cclogistics.mixin;

import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemVaultBlockEntity.class, priority = 1500)
public abstract class CCLItemVaultMixin extends SmartBlockEntity {

    public CCLItemVaultMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "getControllerBE", at = @At("HEAD"), cancellable = true, remap = false)
    private void cclogistics$safeGetController(CallbackInfoReturnable<ItemVaultBlockEntity> cir) {
        if (cclogistics$isFakeLevel()) {
            cir.setReturnValue((ItemVaultBlockEntity) (Object) this);
        }
    }

    @Inject(method = "updateConnectivity", at = @At("HEAD"), cancellable = true, remap = true)
    private void cclogistics$preventFakeLevelCrash(CallbackInfo ci) {
        if (cclogistics$isFakeLevel()) {
            ci.cancel();
        }
    }

    @Inject(method = "initCapability", at = @At("HEAD"), cancellable = true, remap = false)
    private void cclogistics$stopInitCapability(CallbackInfo ci) {
        if (cclogistics$isFakeLevel()) {
            ci.cancel();
        }
    }

    private boolean cclogistics$isFakeLevel() {
        Level level = this.getLevel();
        if (level == null) return false;
        String name = level.getClass().getName();
        return name.contains("FakeLevel") || 
               name.contains("com.ldtteam.blockui") || 
               name.contains("BlueprintBlockAccess");
    }
}