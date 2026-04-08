package com.ogtenzohd.cclogistics.blocks.custom;

import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TrackClearanceBlock extends Block {

    public TrackClearanceBlock() {
        super(BlockBehaviour.Properties.of()
                .noCollission()
                .noOcclusion()
                .destroyTime(0.2f));
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        if (pContext instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player player) {

            if (player.getMainHandItem().is(CCLRegistration.TRACK_CLEARANCE_ITEM.get()) ||
                    player.getOffhandItem().is(CCLRegistration.TRACK_CLEARANCE_ITEM.get())) {

                return Shapes.block();
            }
        }
        return Shapes.empty();
    }
}