package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.job.PackerAgentJob;
import com.ogtenzohd.cclogistics.colony.job.PackerAgentJob.PackerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FreightDepotBuilding extends AbstractBuilding {

    public BlockPos inputChestPos = null;
    public BlockPos outputChestPos = null;
    public Set<BlockPos> platformPositions = new java.util.HashSet<>();

    public FreightDepotBuilding(IColony colony, BlockPos pos) {
        super(colony, pos);
    }

    @Override
    public String getSchematicName() {
        return "freight_depot";
    }

    // BRAIN SWAP -- My little robots!
    public void initateBrainSwap(boolean trainPresent) {
        if (getColony() == null || getColony().getCitizenManager() == null) return;
        
        java.util.List<PackerAgentJob> workers = new java.util.ArrayList<>();
        
        for (com.minecolonies.api.colony.ICitizenData citizen : getColony().getCitizenManager().getCitizens()) {
            if (citizen.getWorkBuilding() == this && citizen.getJob() instanceof PackerAgentJob packerJob) {
                workers.add(packerJob);
            }
        }

        if (workers.isEmpty()) return;

        int total = workers.size();
        int i = 0;

        if (trainPresent) {
            //40% Unload, 40% Load, 10% Unpack, 10% Pack - Make this configurable in future
            int unloaders = Math.max(1, (int)(total * 0.4));
            int loaders = Math.max(1, (int)(total * 0.4));
            int unpackers = Math.max(1, (total - unloaders - loaders) / 2);

            for (PackerAgentJob worker : workers) {
                if (i < unloaders) worker.setRole(PackerRole.UNLOADING_TRAIN);
                else if (i < unloaders + loaders) worker.setRole(PackerRole.LOADING_TRAIN);
                else if (i < unloaders + loaders + unpackers) worker.setRole(PackerRole.UNPACKING_IMPORT);
                else worker.setRole(PackerRole.PACKING_EXPORT);
                i++;
            }
        } else {
            //50% Unpack, 50% Pack - Make this configurable in future
            int unpackers = Math.max(1, total / 2);
            for (PackerAgentJob worker : workers) {
                if (i < unpackers) worker.setRole(PackerRole.UNPACKING_IMPORT);
                else worker.setRole(PackerRole.PACKING_EXPORT);
                i++;
            }
        }
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

        if (tagMap.containsKey("freight_platform")) {
            this.platformPositions = tagMap.get("freight_platform");
        }
        
        BlockEntity be = getColony().getWorld().getBlockEntity(getLocation().getInDimensionLocation());
        if (be instanceof FreightDepotBlockEntity depotBE) {
            depotBE.setImportPos(inputChestPos);
            depotBE.setExportPos(outputChestPos);
        }
    }
}