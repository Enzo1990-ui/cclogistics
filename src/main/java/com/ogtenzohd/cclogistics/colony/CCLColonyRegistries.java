package com.ogtenzohd.cclogistics.colony;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.colony.buildings.ForemenHutBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.ForemenHutBuildingView;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuildingView;
import com.ogtenzohd.cclogistics.colony.job.FreightInspectorJob;
import com.ogtenzohd.cclogistics.colony.job.LogisticsCoordinatorJob;
import com.ogtenzohd.cclogistics.colony.job.PackerAgentJob;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.ogtenzohd.cclogistics.colony.job.ClientJobView;
import com.ogtenzohd.cclogistics.colony.buildings.modules.LogisticsModule;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.LogisticsModuleView;
import com.ogtenzohd.cclogistics.colony.buildings.modules.LoggerModule;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.LoggerModuleView;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = CreateColonyLogistics.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CCLColonyRegistries {

    public static final String JOB_LOGISTICS_COORDINATOR = "logistics_coordinator";
    public static final String JOB_FREIGHT_INSPECTOR = "freight_inspector";
    public static final String JOB_PACKER_AGENT = "packer_agent";
    public static final String BUILDING_FREIGHT_DEPOT = "freight_depot";
    public static final String BUILDING_FOREMEN_HUT = "foremens_hut";

    public static JobEntry LOGISTICS_JOB_ENTRY;
    public static JobEntry FREIGHT_INSPECTOR_JOB_ENTRY;
    public static JobEntry PACKER_AGENT_JOB_ENTRY;

    public static BuildingEntry FREIGHT_DEPOT_BUILDING_ENTRY;
    public static BuildingEntry FOREMEN_HUT_BUILDING_ENTRY;

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> LOGISTICS_COORDINATOR_WORK = 
        new BuildingEntry.ModuleProducer<>(
            "logistics_coordinator_work", 
            () -> new WorkerBuildingModule(LOGISTICS_JOB_ENTRY, Skill.Knowledge, Skill.Agility, false, b -> 1), 
            () -> WorkerBuildingModuleView::new
        );

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> FREIGHT_INSPECTOR_WORK = 
        new BuildingEntry.ModuleProducer<>(
            "freight_inspector_work", 
            () -> new WorkerBuildingModule(FREIGHT_INSPECTOR_JOB_ENTRY, Skill.Intelligence, Skill.Knowledge, false, b -> 1), 
            () -> WorkerBuildingModuleView::new
        );

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> PACKER_AGENT_WORK = 
        new BuildingEntry.ModuleProducer<>(
            "packer_agent_work", 
            () -> new WorkerBuildingModule(PACKER_AGENT_JOB_ENTRY, Skill.Stamina, Skill.Agility, false, 
                b -> b.getBuildingLevel() == 1 ? 2 : b.getBuildingLevel() == 2 ? 3 : b.getBuildingLevel() == 3 ? 5 : b.getBuildingLevel() == 4 ? 7 : 10), 
            () -> WorkerBuildingModuleView::new
        );

    public static final BuildingEntry.ModuleProducer<LogisticsModule, LogisticsModuleView> LOGISTICS_MODULE =
        new BuildingEntry.ModuleProducer<>(
            "freight_depot_logistics",
            () -> new LogisticsModule(),
            () -> LogisticsModuleView::new
        );

    public static final BuildingEntry.ModuleProducer<LoggerModule, LoggerModuleView> LOGGER_MODULE =
        new BuildingEntry.ModuleProducer<>(
            "foremens_hut_logger",
            () -> new LoggerModule(),
            () -> LoggerModuleView::new
        );
        
    private static final ResourceKey<Registry<JobEntry>> JOB_REGISTRY_KEY = 
        ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecolonies", "jobs"));
        
    private static final ResourceKey<Registry<BuildingEntry>> BUILDING_REGISTRY_KEY = 
        ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("minecolonies", "buildings"));

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(JOB_REGISTRY_KEY)) {
            registerJobs(event);
        }
        
        if (event.getRegistryKey().equals(BUILDING_REGISTRY_KEY)) {
            registerBuildings(event);
        }
    }

    private static void registerJobs(RegisterEvent event) {
        // 1. Logistics Coordinator
        ResourceLocation logLoc = ResourceLocation.fromNamespaceAndPath(CCLRegistration.MODID, JOB_LOGISTICS_COORDINATOR);
        LOGISTICS_JOB_ENTRY = new JobEntry.Builder()
            .setRegistryName(logLoc)
            .setJobProducer(LogisticsCoordinatorJob::new)
            .setJobViewProducer(() -> ClientJobView::new) 
            .createJobEntry();
        event.register(JOB_REGISTRY_KEY, logLoc, () -> LOGISTICS_JOB_ENTRY);

        // 2. Freight Inspector
        ResourceLocation fiLoc = ResourceLocation.fromNamespaceAndPath(CCLRegistration.MODID, JOB_FREIGHT_INSPECTOR);
        FREIGHT_INSPECTOR_JOB_ENTRY = new JobEntry.Builder()
            .setRegistryName(fiLoc)
            .setJobProducer(FreightInspectorJob::new)
            .setJobViewProducer(() -> ClientJobView::new)
            .createJobEntry();
        event.register(JOB_REGISTRY_KEY, fiLoc, () -> FREIGHT_INSPECTOR_JOB_ENTRY);

        // 3. Packer Agent
        ResourceLocation paLoc = ResourceLocation.fromNamespaceAndPath(CCLRegistration.MODID, JOB_PACKER_AGENT);
        PACKER_AGENT_JOB_ENTRY = new JobEntry.Builder()
            .setRegistryName(paLoc)
            .setJobProducer(PackerAgentJob::new)
            .setJobViewProducer(() -> ClientJobView::new)
            .createJobEntry();
        event.register(JOB_REGISTRY_KEY, paLoc, () -> PACKER_AGENT_JOB_ENTRY);
    }

    private static void registerBuildings(RegisterEvent event) {
        ResourceLocation fdLoc = ResourceLocation.fromNamespaceAndPath(CCLRegistration.MODID, BUILDING_FREIGHT_DEPOT);

        FREIGHT_DEPOT_BUILDING_ENTRY = new BuildingEntry.Builder()
            .setRegistryName(fdLoc)
            .setBuildingProducer(FreightDepotBuilding::new)
            .setBuildingViewProducer(() -> FreightDepotBuildingView::new)
            .setBuildingBlock(CCLRegistration.FREIGHT_DEPOT_BLOCK.get())
            
            // MODULES
            .addBuildingModuleProducer(LOGISTICS_COORDINATOR_WORK)
            .addBuildingModuleProducer(PACKER_AGENT_WORK)
            .addBuildingModuleProducer(BuildingModules.MIN_STOCK)
            .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
            .addBuildingModuleProducer(LOGISTICS_MODULE)
            
            .createBuildingEntry();

        event.register(BUILDING_REGISTRY_KEY, fdLoc, () -> FREIGHT_DEPOT_BUILDING_ENTRY);

        ResourceLocation fhLoc = ResourceLocation.fromNamespaceAndPath(CCLRegistration.MODID, BUILDING_FOREMEN_HUT);

        FOREMEN_HUT_BUILDING_ENTRY = new BuildingEntry.Builder()
            .setRegistryName(fhLoc)
            .setBuildingProducer(ForemenHutBuilding::new)
            .setBuildingViewProducer(() -> ForemenHutBuildingView::new)
            .setBuildingBlock(CCLRegistration.FOREMEN_HUT_BLOCK.get())
            .addBuildingModuleProducer(FREIGHT_INSPECTOR_WORK)
            .addBuildingModuleProducer(BuildingModules.STATS_MODULE)
            .addBuildingModuleProducer(LOGGER_MODULE)
            .createBuildingEntry();

        event.register(BUILDING_REGISTRY_KEY, fhLoc, () -> FOREMEN_HUT_BUILDING_ENTRY);
    }
}