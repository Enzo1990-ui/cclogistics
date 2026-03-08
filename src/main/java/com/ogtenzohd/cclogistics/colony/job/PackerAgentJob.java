package com.ogtenzohd.cclogistics.colony.job;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.ogtenzohd.cclogistics.colony.ai.PackerAgentAI;
import com.ogtenzohd.cclogistics.colony.CCLColonyRegistries;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class PackerAgentJob extends AbstractJob<PackerAgentAI, PackerAgentJob> {

    //THE ROLES -- Bigger Brains
    public enum PackerRole {
        UNPACKING_IMPORT, PACKING_EXPORT, UNLOADING_TRAIN, LOADING_TRAIN, IDLE
    }
    
    private PackerRole currentRole = PackerRole.UNPACKING_IMPORT;

    public PackerAgentJob(ICitizenData citizenData) {
        super(citizenData);
        this.setRegistryEntry(CCLColonyRegistries.PACKER_AGENT_JOB_ENTRY);
    }

    public PackerAgentJob(ICitizenData citizenData, JobEntry entry) {
        super(citizenData);
        this.setRegistryEntry(entry);
    }

    public PackerRole getRole() { 
        return currentRole; 
    }

    public void setRole(PackerRole role) { 
        if (this.currentRole != role) {
            this.currentRole = role;
            if (this.getWorkerAI() != null) {
                this.getWorkerAI().resetStateForNewRole(); 
            }
        }
    }

    public ResourceLocation getModel() {
        return super.getModel();
    }
    
    @Override
    @NotNull
    public PackerAgentAI generateAI() {
        return new PackerAgentAI(this);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = super.serializeNBT(provider);
        tag.putInt("PackerRole", currentRole.ordinal()); // Save Role
        if (getWorkerAI() != null) {
            getWorkerAI().writeData(tag, provider);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        super.deserializeNBT(provider, nbt);
        if (nbt.contains("PackerRole")) {
            currentRole = PackerRole.values()[nbt.getInt("PackerRole")]; // Load Role
        }
        if (getWorkerAI() != null) {
            getWorkerAI().readData(nbt, provider);
        }
    }

    @Override 
    public String getNameTagDescription() { 
        return "Packer"; 
    }
}