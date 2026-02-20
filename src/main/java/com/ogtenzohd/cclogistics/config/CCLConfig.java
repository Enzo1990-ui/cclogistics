package com.ogtenzohd.cclogistics.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class CCLConfig {
    public static final ModConfigSpec SPEC;
    public static final CCLConfig INSTANCE;

    public final ModConfigSpec.BooleanValue debugMode;
    public final ModConfigSpec.IntValue coordinatorCooldown;
	public final ModConfigSpec.IntValue warehouseExcessThreshold;
	
    static {
        Pair<CCLConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(CCLConfig::new);
        SPEC = specPair.getRight();
        INSTANCE = specPair.getLeft();
    }

    public CCLConfig(ModConfigSpec.Builder builder) {
        builder.push("General");

        debugMode = builder
                .comment("Enable verbose debug logging for Logistics Coordinator and Freight Depot.")
                .define("debugMode", false);

        coordinatorCooldown = builder
                .comment("The interval (in ticks) between Logistics Coordinator request checks. Default: 1200 (60 seconds). Min: 200, Max: 12000.")
                .defineInRange("coordinatorCooldown", 1200, 200, 12000);

		warehouseExcessThreshold = builder
                .comment("The number of items to keep in the Warehouse before the Coordinator flags them as excess for export. Default: 128.")
                .defineInRange("warehouseExcessThreshold", 128, 1, 100000);
				
        builder.pop();
    }
}
