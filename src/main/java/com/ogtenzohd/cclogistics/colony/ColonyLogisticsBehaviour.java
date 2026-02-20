package com.ogtenzohd.cclogistics.colony.behaviour;

import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public class ColonyLogisticsBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<ColonyLogisticsBehaviour> TYPE = new BehaviourType<>();

    private final FreightDepotBlockEntity depot;
    private UUID freqId;
    
    public ColonyLogisticsBehaviour(FreightDepotBlockEntity te) {
        super(null); // Pass null because we manually handle the TE reference
        this.depot = te;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public void setNetworkId(UUID id) {
        this.freqId = id;
        depot.setChanged();
        // Force a block update to sync data to client
        if (depot.getLevel() != null) {
            depot.getLevel().sendBlockUpdated(depot.getBlockPos(), depot.getBlockState(), depot.getBlockState(), 3);
        }
    }

    public UUID getNetworkId() {
        return freqId;
    }

    // --- UPDATED FOR 1.21 (Added HolderLookup.Provider) ---

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(nbt, provider, clientPacket);
        if (freqId != null)
            nbt.putUUID("NetworkID", freqId);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(nbt, provider, clientPacket);
        if (nbt.contains("NetworkID"))
            freqId = nbt.getUUID("NetworkID");
    }
}