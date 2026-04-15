package com.ogtenzohd.cclogistics.blocks.custom.scanner;

import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class FreightScannerBlock extends BeltTunnelBlock {

    public FreightScannerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends BeltTunnelBlockEntity> getBlockEntityType() {
        return CCLRegistration.FREIGHT_SCANNER_BE.get();
    }
}