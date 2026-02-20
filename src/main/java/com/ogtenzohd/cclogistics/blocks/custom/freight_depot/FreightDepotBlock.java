package com.ogtenzohd.cclogistics.blocks.custom.freight_depot;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.ogtenzohd.cclogistics.colony.CCLColonyRegistries;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FreightDepotBlock extends AbstractBlockHut<FreightDepotBlock> {

    public FreightDepotBlock(Properties properties) {
        super(properties);
    }

    @Override
    public String getHutName() {
        return "freight_depot";
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath(CCLRegistration.MODID, "freight_depot");
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return CCLColonyRegistries.FREIGHT_DEPOT_BUILDING_ENTRY;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FreightDepotBlockEntity(pos, state);
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == CCLRegistration.FREIGHT_DEPOT_BE.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<FreightDepotBlockEntity>) (l, p, s, be) -> be.tick();
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FreightDepotBlockEntity depot) {
                player.openMenu(depot, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}