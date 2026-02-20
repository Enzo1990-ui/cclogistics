package com.ogtenzohd.cclogistics.colony.job;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.colony.CCLColonyRegistries;
import com.ogtenzohd.cclogistics.colony.ai.FreightInspectorAI;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class FreightInspectorJob extends AbstractJob<FreightInspectorAI, FreightInspectorJob> {

    // Removed JobEntry field as AbstractJob might handle it or we use getJobRegistryEntry from registry
    // But overriding getJobRegistryEntry is final, so AbstractJob must know it.
    // AbstractJob usually gets it from the constructor or via setRegistryEntry.
    
    // Fix: Add constructor required by CCLColonyRegistries::setJobProducer(FreightInspectorJob::new)
    public FreightInspectorJob(ICitizenData citizenData) {
        super(citizenData);
        // We need to set the registry entry manually or rely on the system to call setRegistryEntry later.
        // However, if we look at the error, the producer passed to setJobProducer is Function<ICitizenData, Job>.
        // So this constructor matches that signature.
        this.setRegistryEntry(CCLColonyRegistries.FREIGHT_INSPECTOR_JOB_ENTRY);
    }

    // Keep the other constructor if needed for other purposes, but the producer uses the 1-arg one.
    public FreightInspectorJob(ICitizenData citizenData, JobEntry entry) {
        super(citizenData);
        setRegistryEntry(entry);
    }

    public ResourceLocation getModel() {
        return super.getModel();
    }
    
    @Override
    @NotNull
    public FreightInspectorAI generateAI() {
        return new FreightInspectorAI(this);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = super.serializeNBT(provider);
        if (getWorkerAI() != null) {
            getWorkerAI().write(tag, provider);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        super.deserializeNBT(provider, nbt);
        if (getWorkerAI() != null) {
            getWorkerAI().read(nbt, provider);
        }
    }
    
    @Override 
    public String getNameTagDescription() { 
        return "Inspector"; 
    }
}
