package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FreightDepotBuilding extends AbstractBuilding {

    private static final Logger LOGGER = LogUtils.getLogger();

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
	public String getBlueprintPath() {
        int level = getBuildingLevel() == 0 ? 1 : getBuildingLevel();
        return getSchematicName() + "/" + getSchematicName() + level + ".blueprint";
	}
	
	@Override
	public List<BlockPos> getContainers() {
		List<BlockPos> containers = new ArrayList<>(super.getContainers());
		containers.add(getLocation().getInDimensionLocation());
		return containers;
	}

    public void updateStructureData() {
        if (getColony().getWorld() == null) return;
        try {
            resolveStructureTags();
        } catch (Exception e) {
            LOGGER.error("[FreightDepotBuilding] Error resolving tags", e);
        }
    }

    private void resolveStructureTags() {
        AbstractTileEntityColonyBuilding abstractTE = getTileEntity();
        if (abstractTE == null) return;

        if (!(abstractTE instanceof TileEntityColonyBuilding)) return;
        
        TileEntityColonyBuilding te = (TileEntityColonyBuilding) abstractTE;
        Map<String, Set<BlockPos>> tagMap = te.getWorldTagNamePosMap();
        
        if (tagMap == null || tagMap.isEmpty()) return;

        this.inputChestPos = null;
        this.outputChestPos = null;

        if (tagMap.containsKey("freight_input")) {
            Set<BlockPos> positions = tagMap.get("freight_input");
            if (!positions.isEmpty()) {
                this.inputChestPos = positions.iterator().next();
            }
        }
        
        if (tagMap.containsKey("freight_output")) {
            Set<BlockPos> positions = tagMap.get("freight_output");
            if (!positions.isEmpty()) {
                this.outputChestPos = positions.iterator().next();
            }
        }
        
        BlockEntity be = getColony().getWorld().getBlockEntity(getLocation().getInDimensionLocation());
        if (be instanceof FreightDepotBlockEntity depotBE) {
            if (inputChestPos != null) depotBE.setImportPos(inputChestPos);
            if (outputChestPos != null) depotBE.setExportPos(outputChestPos);
        }
    }
}
