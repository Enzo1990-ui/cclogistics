package com.ogtenzohd.cclogistics.blocks.custom.funnel;

import com.simibubi.create.content.logistics.funnel.BrassFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class LogisticsFunnelBlock extends BrassFunnelBlock {

    public LogisticsFunnelBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends FunnelBlockEntity> getBlockEntityType() {
        return CCLRegistration.LOGISTICS_FUNNEL_BE.get();
    }
}