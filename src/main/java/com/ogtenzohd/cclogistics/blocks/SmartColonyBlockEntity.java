package com.ogtenzohd.cclogistics.blocks;

import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public abstract class SmartColonyBlockEntity extends TileEntityColonyBuilding {

    private final List<BlockEntityBehaviour> behaviours;
    private boolean initialized = false;
    public SmartColonyBlockEntity(BlockEntityType<? extends AbstractTileEntityColonyBuilding> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        behaviours = new ArrayList<>();
    }

    public abstract void addBehaviours(List<BlockEntityBehaviour> behaviours);

    public void initialize() {
        if (!initialized) {
            addBehaviours(behaviours);
            behaviours.forEach(BlockEntityBehaviour::initialize);
            initialized = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!initialized)
            initialize();
        
        behaviours.forEach(BlockEntityBehaviour::tick);
    }
    
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (!initialized) initialize();
        behaviours.forEach(b -> b.read(tag, provider, false));
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (!initialized) initialize();
        behaviours.forEach(b -> b.write(tag, provider, false));
    }
    
	@Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        if (!initialized) initialize();
        behaviours.forEach(b -> b.write(tag, provider, true));
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        if (!initialized) initialize();
        behaviours.forEach(b -> b.read(tag, provider, true));
    }
    
    @SuppressWarnings("unchecked")
    public <T extends BlockEntityBehaviour> T getBehaviour(BehaviourType<T> type) {
        if (!initialized) initialize();
        for (BlockEntityBehaviour behaviour : behaviours) {
            if (behaviour.getType() == type) {
                return (T) behaviour;
            }
        }
        return null;
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }
    
    public void setLazyTickRate(int rate) {
    }
}
