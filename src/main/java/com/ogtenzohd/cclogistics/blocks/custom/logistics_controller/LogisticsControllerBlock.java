package com.ogtenzohd.cclogistics.blocks.custom.logistics_controller;

import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class LogisticsControllerBlock extends Block implements IBE<LogisticsControllerBlockEntity>, EntityBlock, IWrenchable {

    public LogisticsControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    if (!level.isClientSide) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof LogisticsControllerBlockEntity controller) {
            player.openMenu(controller, pos);
        }
    }
    return InteractionResult.SUCCESS;
}

    @Override
    public Class<LogisticsControllerBlockEntity> getBlockEntityClass() {
        return LogisticsControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LogisticsControllerBlockEntity> getBlockEntityType() {
        return CCLRegistration.LOGISTICS_CONTROLLER_BE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogisticsControllerBlockEntity(pos, state);
    }
}