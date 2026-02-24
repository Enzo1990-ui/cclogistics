package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import java.util.Set;

public class FreightDepotBuilding extends AbstractBuilding {

    public BlockPos inputChestPos = null;
    public BlockPos outputChestPos = null;

    public FreightDepotBuilding(IColony colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public String getSchematicName() {
        return "freight_depot";
    }

    @Override
    public void onUpgradeComplete(int newLevel) {
        super.onUpgradeComplete(newLevel);
        updateStructureData();
        forceVaultConnections();
    }

    private void forceVaultConnections() {
        if (getColony().getWorld() == null) return;
        Level level = getColony().getWorld();
        var corners = this.getCorners();
        if (corners == null) return;

        for (BlockPos pos : BlockPos.betweenClosed(corners.getA(), corners.getB())) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock().toString().contains("item_vault")) {
                level.removeBlockEntity(pos);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos, state, 3);
            }
        }
    }

    public void updateStructureData() {
        AbstractTileEntityColonyBuilding abstractTE = getTileEntity();
        if (!(abstractTE instanceof TileEntityColonyBuilding te)) return;
        
        Map<String, Set<BlockPos>> tagMap = te.getWorldTagNamePosMap();
        if (tagMap == null) return;

        if (tagMap.containsKey("freight_input")) {
            this.inputChestPos = tagMap.get("freight_input").iterator().next();
        }
        
        if (tagMap.containsKey("freight_output")) {
            this.outputChestPos = tagMap.get("freight_output").iterator().next();
        }
        
        BlockEntity be = getColony().getWorld().getBlockEntity(getLocation().getInDimensionLocation());
        if (be instanceof FreightDepotBlockEntity depotBE) {
            depotBE.setImportPos(inputChestPos);
            depotBE.setExportPos(outputChestPos);
        }
    }
}