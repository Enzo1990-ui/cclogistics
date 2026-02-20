package com.ogtenzohd.cclogistics.blocks.custom.foremens_hut;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.ogtenzohd.cclogistics.colony.CCLColonyRegistries;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
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

public class ForemenHutBlock extends AbstractBlockHut<ForemenHutBlock> {

    public ForemenHutBlock(Properties properties) {
        super(properties);
    }

    @Override
    public String getHutName() {
        return "foremens_hut";
    }

    @Override
    public ResourceLocation getRegistryName() {
        return ResourceLocation.fromNamespaceAndPath(CCLRegistration.MODID, "foremens_hut");
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return CCLColonyRegistries.FOREMEN_HUT_BUILDING_ENTRY;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForemenHutBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == CCLRegistration.FOREMEN_HUT_BE.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<ForemenHutBlockEntity>) (l, p, s, be) -> be.tick();
        }
        return null;
    }
	
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof ForemenHutBlockEntity hutBE) {
                player.openMenu(hutBE, pos);
            } else {
                throw new IllegalStateException("Our Container provider is missing!");
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
