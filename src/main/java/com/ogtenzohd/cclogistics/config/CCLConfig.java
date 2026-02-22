package com.ogtenzohd.cclogistics.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class CCLConfig {
    public static final ModConfigSpec SPEC;
    public static final CCLConfig INSTANCE;

    public final ModConfigSpec.BooleanValue debugMode;
    public final ModConfigSpec.IntValue coordinatorCooldown;
    public final ModConfigSpec.IntValue warehouseExcessThreshold;
    
    // how many packers can the building have
    public final ModConfigSpec.IntValue packersLevel1;
    public final ModConfigSpec.IntValue packersLevel2;
    public final ModConfigSpec.IntValue packersLevel3;
    public final ModConfigSpec.IntValue packersLevel4;
    public final ModConfigSpec.IntValue packersLevel5;
	
    static {
        Pair<CCLConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(CCLConfig::new);
        SPEC = specPair.getRight();
        INSTANCE = specPair.getLeft();
    }

    public CCLConfig(ModConfigSpec.Builder builder) {
        builder.push("Debug");
        debugMode = builder
                .comment("Enable verbose debug logging for Logistics Coordinator and Freight Depot.")
                .define("debugMode", false);
        builder.pop();
        
        builder.push("General");
        coordinatorCooldown = builder
                .comment("The interval (in ticks) between Logistics Coordinator request checks. Default: 1200 (60 seconds). Min: 200, Max: 12000.")
                .defineInRange("coordinatorCooldown", 1200, 200, 12000);

        warehouseExcessThreshold = builder
                .comment("The number of items to keep in the Warehouse before the Coordinator flags them as excess for export. Default: 128.")
                .defineInRange("warehouseExcessThreshold", 128, 1, 100000);
        
        builder.push("Packer Agents Capacity");
        
        packersLevel1 = builder
                .comment("Max Packer Agents for a Level 1 Freight Depot. Default: 2")
                .defineInRange("packersLevel1", 2, 1, 50);
                
        packersLevel2 = builder
                .comment("Max Packer Agents for a Level 2 Freight Depot. Default: 3")
                .defineInRange("packersLevel2", 3, 1, 50);
                
        packersLevel3 = builder
                .comment("Max Packer Agents for a Level 3 Freight Depot. Default: 5")
                .defineInRange("packersLevel3", 5, 1, 50);
                
        packersLevel4 = builder
                .comment("Max Packer Agents for a Level 4 Freight Depot. Default: 7")
                .defineInRange("packersLevel4", 7, 1, 50);
                
        packersLevel5 = builder
                .comment("Max Packer Agents for a Level 5 Freight Depot. Default: 10")
                .defineInRange("packersLevel5", 10, 1, 50);

        builder.pop();
    }
}