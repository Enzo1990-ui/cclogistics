package com.ogtenzohd.cclogistics.colony.job;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.jobs.IJobView;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.HashSet;
import java.util.Set;

public class ClientJobView implements IJobView {
    private final Set<IToken<?>> asyncRequests = new HashSet<>();
    private final IColonyView colonyView;
    private JobEntry entry;

    public ClientJobView(final IColonyView iColonyView, final ICitizenDataView iCitizenDataView) {
        this.colonyView = iColonyView;
    }

    @Override
    public void deserialize(final RegistryFriendlyByteBuf buffer) {
        this.asyncRequests.clear();
        final int size = buffer.readInt();
        for (int i = 0; i < size; i++) {
            asyncRequests.add(StandardFactoryController.getInstance().deserialize(buffer));
        }
    }

    @Override
    public String getName() {
        if (this.entry == null) return "unknown";
        
        String translationKey = this.entry.getTranslationKey();
        int lastDotIndex = translationKey.lastIndexOf('.');
        if (lastDotIndex != -1) {
            return translationKey.substring(lastDotIndex + 1);
        }
        
        return translationKey;
    }

    @Override
    public JobEntry getEntry() {
        return entry;
    }

    @Override
    public void setEntry(final JobEntry entry) {
        this.entry = entry;
    }

    @Override
    public Set<IToken<?>> getAsyncRequests() {
        return asyncRequests;
    }
}