package com.ogtenzohd.cclogistics.colony.buildings;

import com.google.common.collect.ImmutableCollection;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.job.PackerAgentJob;
import com.ogtenzohd.cclogistics.colony.job.PackerAgentJob.PackerRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Set;

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
            int unloaders = Math.max(1, (int)(total * 0.9));
            int loaders = Math.max(1, (int)(total * 0.1));

            for (PackerAgentJob worker : workers) {
                if (i < unloaders) {
                    worker.setRole(PackerRole.UNLOADING_TRAIN);
                } else if (i < unloaders + loaders) {
                    worker.setRole(PackerRole.LOADING_TRAIN);
                } else {
                    worker.setRole(i % 2 == 0 ? PackerRole.UNPACKING_IMPORT : PackerRole.PACKING_EXPORT);
                }
                i++;
            }
        } else {
            for (PackerAgentJob worker : workers) {
                worker.setRole(PackerRole.GENERAL_DUTY);
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

    @Override
    public ImmutableCollection<IRequestResolver<?>> createResolvers() {
        return super.createResolvers();
    }
}