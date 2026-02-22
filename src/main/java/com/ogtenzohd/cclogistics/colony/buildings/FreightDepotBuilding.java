package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import net.minecraft.core.BlockPos;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
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
    public List<BlockPos> getContainers() {
        List<BlockPos> containers = new ArrayList<>(super.getContainers());
        containers.add(getLocation().getInDimensionLocation());
        return containers;
    }

    @Override
    public void onUpgradeComplete(int newLevel) {
        super.onUpgradeComplete(newLevel);
        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[CCLogistics] Freight Depot successfully built/upgraded to level {}!", newLevel);
        updateStructureData();
        forceVaultConnections();
    }

    private void forceVaultConnections() { //** final fix for vault multiblock
        if (getColony().getWorld() == null) return;
        Level level = getColony().getWorld();

        var corners = this.getCorners();
        if (corners == null) return;

        BlockPos cornerA = corners.getA();
        BlockPos cornerB = corners.getB();

        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[CCLogistics] Scanning Freight Depot for Item Vaults to relink...");

        Map<BlockPos, BlockState> vaultsToReplace = new HashMap<>();

        for (BlockPos mutablePos : BlockPos.betweenClosed(cornerA, cornerB)) {
            BlockPos pos = mutablePos.immutable(); 
            BlockState state = level.getBlockState(pos);
            
            if (state.getBlock().toString().contains("item_vault")) {
                vaultsToReplace.put(pos, state);
            }
        }

        if (vaultsToReplace.isEmpty()) return;

        for (BlockPos pos : vaultsToReplace.keySet()) {
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); 
        }

        for (Map.Entry<BlockPos, BlockState> entry : vaultsToReplace.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        
        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[CCLogistics] Successfully wiped and reformed " + vaultsToReplace.size() + " Item Vault blocks!");
    }

    public void updateStructureData() {
        if (getColony().getWorld() == null) return;
        try {
            resolveStructureTags();
        } catch (Exception e) {
            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.error("[FreightDepotBuilding] Error resolving tags", e);
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