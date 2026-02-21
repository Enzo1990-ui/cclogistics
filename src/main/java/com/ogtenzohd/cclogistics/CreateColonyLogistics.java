package com.ogtenzohd.cclogistics;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import com.ogtenzohd.cclogistics.network.CCLPackets;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModLoadingContext;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.minecraft.sounds.SoundEvent;
import com.minecolonies.api.sounds.EventType;
import com.minecolonies.api.sounds.ModSoundEvents;
import com.minecolonies.api.util.Tuple;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

@Mod(CreateColonyLogistics.MODID)
public class CreateColonyLogistics {
    public static final String MODID = "cclogistics";
    
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateColonyLogistics(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("[CCLogistics] Mod Construction Starting...");
        
        modContainer.registerConfig(ModConfig.Type.COMMON, CCLConfig.SPEC);
        
        CCLRegistration.register(modEventBus);
        
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(CCLPackets::register); 
        
        LOGGER.info("[CCLogistics] Mod Construction Complete.");
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[CCLogistics] Common Setup Starting...");
        
        event.enqueueWork(() -> {

            Map<String, Map<EventType, List<Tuple<SoundEvent, SoundEvent>>>> sounds = ModSoundEvents.CITIZEN_SOUND_EVENTS;
            
            String[] myJobs = {"logistics_coordinator", "freight_inspector", "packer_agent"};
            
            var baseSoundMap = sounds.get("deliveryman");
            
            if (baseSoundMap != null) {
                LOGGER.info("[CCLogistics] Registering Citizen Sounds...");
                for (String jobName : myJobs) {
                    if (!sounds.containsKey(jobName)) {
                        sounds.put(jobName, baseSoundMap);
                    }
                }
            } else {
                LOGGER.warn("[CCLogistics] Could not find base 'deliveryman' sounds!");
            }
        });
        
        LOGGER.info("[CCLogistics] Common Setup Complete.");
    }
}
