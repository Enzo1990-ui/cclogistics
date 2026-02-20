package com.ogtenzohd.cclogistics.registration;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlock;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.menu.FreightDepotMenu;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlock;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.menu.LogisticsControllerMenu;
import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.ForemenHutBlock;
import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.ForemenHutBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.menu.ForemenHutMenu;
import com.ogtenzohd.cclogistics.items.LogisticsLinkerItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = CreateColonyLogistics.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CCLRegistration {

    public static final String MODID = CreateColonyLogistics.MODID;

    // --- REGISTRIES ---
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);

    // --- BLOCKS ---
    public static final DeferredHolder<Block, LogisticsControllerBlock> LOGISTICS_CONTROLLER_BLOCK = BLOCKS.register("logistics_controller",
            () -> new LogisticsControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredHolder<Block, FreightDepotBlock> FREIGHT_DEPOT_BLOCK = BLOCKS.register("freight_depot",
            () -> new FreightDepotBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)));

    public static final DeferredHolder<Block, ForemenHutBlock> FOREMEN_HUT_BLOCK = BLOCKS.register("foremens_hut",
            () -> new ForemenHutBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)));

    // --- ITEMS ---
    public static final DeferredHolder<Item, BlockItem> LOGISTICS_CONTROLLER_ITEM = ITEMS.register("logistics_controller",
            () -> new BlockItem(LOGISTICS_CONTROLLER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> FREIGHT_DEPOT_ITEM = ITEMS.register("freight_depot",
            () -> new BlockItem(FREIGHT_DEPOT_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> FOREMEN_HUT_ITEM = ITEMS.register("foremens_hut",
            () -> new BlockItem(FOREMEN_HUT_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, LogisticsLinkerItem> LOGISTICS_LINKER = ITEMS.register("logistics_linker",
            () -> new LogisticsLinkerItem(new Item.Properties().stacksTo(1)));

    // --- BLOCK ENTITIES ---
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogisticsControllerBlockEntity>> LOGISTICS_CONTROLLER_BE =
            BLOCK_ENTITIES.register("logistics_controller", () ->
                    BlockEntityType.Builder.of(LogisticsControllerBlockEntity::new, LOGISTICS_CONTROLLER_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FreightDepotBlockEntity>> FREIGHT_DEPOT_BE =
            BLOCK_ENTITIES.register("freight_depot", () ->
                    BlockEntityType.Builder.of(FreightDepotBlockEntity::new, FREIGHT_DEPOT_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForemenHutBlockEntity>> FOREMEN_HUT_BE =
            BLOCK_ENTITIES.register("foremens_hut", () ->
                    BlockEntityType.Builder.of(ForemenHutBlockEntity::new, FOREMEN_HUT_BLOCK.get()).build(null));

    // --- MENUS ---
    public static final DeferredHolder<MenuType<?>, MenuType<FreightDepotMenu>> FREIGHT_DEPOT_MENU =
            MENU_TYPES.register("freight_depot", () -> IMenuTypeExtension.create(FreightDepotMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LogisticsControllerMenu>> LOGISTICS_CONTROLLER_MENU =
            MENU_TYPES.register("logistics_controller", () -> IMenuTypeExtension.create(LogisticsControllerMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ForemenHutMenu>> FOREMEN_HUT_MENU =
            MENU_TYPES.register("foremens_hut", () -> IMenuTypeExtension.create(ForemenHutMenu::new));

    // --- CREATIVE TAB ---
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CCL_TAB = CREATIVE_TABS.register("ccl_tab", () -> CreativeModeTab.builder()
            .title(Component.literal("Create: Colony Logistics"))
            .icon(() -> LOGISTICS_CONTROLLER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(LOGISTICS_CONTROLLER_ITEM.get());
                output.accept(FREIGHT_DEPOT_ITEM.get());
                output.accept(FOREMEN_HUT_ITEM.get());
                output.accept(LOGISTICS_LINKER.get());
            }).build());

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENU_TYPES.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }
    
    // --- CAPABILITY REGISTRATION ---
    @SubscribeEvent
	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        CCLRegistration.FREIGHT_DEPOT_BE.get(),
        (blockEntity, context) -> blockEntity.getBuildingInventory()
    );
}
}
