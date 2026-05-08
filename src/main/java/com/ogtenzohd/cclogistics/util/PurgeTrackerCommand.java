package com.ogtenzohd.cclogistics.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.mojang.brigadier.CommandDispatcher;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class PurgeTrackerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cclogistics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("purge")
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            BlockPos playerPos = BlockPos.containing(source.getPosition());
                            IColony colony = IColonyManager.getInstance().getIColony(source.getLevel(), playerPos);

                            if (colony == null) {
                                source.sendFailure(Component.literal("You must be standing inside a Colony's borders to use this command!"));
                                return 0;
                            }

                            int purgedCount = 0;
                            for (IBuilding building : colony.getServerBuildingManager().getBuildings().values()) {
                                if (building instanceof FreightDepotBuilding) {
                                    FreightTrackerModule module = building.getModule(FreightTrackerModule.class);
                                    if (module != null) {
                                        module.purgeOldLogs();
                                        purgedCount++;
                                    }
                                }
                            }

                            if (purgedCount > 0) {
                                source.sendSuccess(() -> Component.literal("§a[CC Logistics] Successfully factory-reset the Freight Tracker for " + colony.getName() + "."), true);
                                return 1;
                            } else {
                                source.sendFailure(Component.literal("Colony " + colony.getName() + " does not have a Freight Depot!"));
                                return 0;
                            }
                        })
                )
        );
    }
}