package com.ogtenzohd.cclogistics.colony.buildings;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.modules.LoggerModule;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class ForemenHutBuilding extends AbstractBuilding {

    private static final Logger LOGGER = LogUtils.getLogger();

    public ForemenHutBuilding(IColony colony, BlockPos pos) {
        super(colony, pos);
    }

	
    @Override
    public String getSchematicName() {
        return "foremens_hut";
    }

    public void addIncomingLog(String message) {
        LoggerModule module = getModule(LoggerModule.class);
        if (module != null) {
            module.addIncomingLog(message, getLogCapacity());
        }
    }

    public void addOutgoingLog(String message) {
        LoggerModule module = getModule(LoggerModule.class);
        if (module != null) {
            module.addOutgoingLog(message, getLogCapacity());
        }
    }

    private int getLogCapacity() {
        int level = getBuildingLevel();
        return switch (level) {
            case 1 -> 10;
            case 2 -> 20;
            case 3 -> 20;
            case 4 -> 40;
            case 5 -> 100;
            default -> 10;
        };
    }
}