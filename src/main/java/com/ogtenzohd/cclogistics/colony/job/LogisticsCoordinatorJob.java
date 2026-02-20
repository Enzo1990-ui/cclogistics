package com.ogtenzohd.cclogistics.colony.job;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.core.colony.jobs.AbstractJob;
import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.colony.CCLColonyRegistries;
import com.ogtenzohd.cclogistics.colony.ai.LogisticsCoordinatorAI;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class LogisticsCoordinatorJob extends AbstractJob<LogisticsCoordinatorAI, LogisticsCoordinatorJob> {

    public LogisticsCoordinatorJob(ICitizenData citizenData) {
        super(citizenData);
        this.setRegistryEntry(CCLColonyRegistries.LOGISTICS_JOB_ENTRY);
    }

    public LogisticsCoordinatorJob(ICitizenData citizenData, JobEntry entry) {
        super(citizenData);
        this.setRegistryEntry(entry);
    }

    public ResourceLocation getModel() {
        return super.getModel();
    }

    @Override
    @NotNull
    public LogisticsCoordinatorAI generateAI() {
        return new LogisticsCoordinatorAI(this);
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
        return "Logistics"; 
    }
}
