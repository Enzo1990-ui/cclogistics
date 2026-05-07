package com.ogtenzohd.cclogistics.registration;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.blocks.custom.BallastBlock;
import com.ogtenzohd.cclogistics.blocks.custom.LogisticsPathBlock;
import com.ogtenzohd.cclogistics.blocks.custom.TrackClearanceBlock;
import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.ForemenHutBlock;
import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.ForemenHutBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.menu.ForemenHutMenu;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlock;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.menu.FreightDepotMenu;
import com.ogtenzohd.cclogistics.blocks.custom.funnel.LogisticsFunnelBlock;
import com.ogtenzohd.cclogistics.blocks.custom.funnel.LogisticsFunnelBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlock;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.menu.LogisticsControllerMenu;
import com.ogtenzohd.cclogistics.blocks.custom.scanner.FreightScannerBlock;
import com.ogtenzohd.cclogistics.blocks.custom.scanner.FreightScannerBlockEntity;
import com.ogtenzohd.cclogistics.compat.FreightDepotDisplaySource;
import com.ogtenzohd.cclogistics.items.LogisticsLinkerItem;
import com.ogtenzohd.cclogistics.items.PortableTrackerItem;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = CreateColonyLogistics.MODID)
public class CCLRegistration {

    public static final String MODID = CreateColonyLogistics.MODID;

    // --- REGISTRIES ---
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
    public static final ResourceKey<Registry<DisplaySource>> DISPLAY_SOURCE_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("create", "display_source"));
    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(DISPLAY_SOURCE_KEY, CreateColonyLogistics.MODID);
    public static final DeferredHolder<DisplaySource, FreightDepotDisplaySource> FREIGHT_MANIFEST =
            DISPLAY_SOURCES.register("freight_depot", FreightDepotDisplaySource::new);

    // --- BLOCKS ---
    public static final DeferredHolder<Block, LogisticsControllerBlock> LOGISTICS_CONTROLLER_BLOCK = BLOCKS.register("logistics_controller",
            () -> new LogisticsControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).requiresCorrectToolForDrops().noOcclusion()));

    public static final DeferredHolder<Block, FreightDepotBlock> FREIGHT_DEPOT_BLOCK = BLOCKS.register("freight_depot",
            () -> new FreightDepotBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)));

    public static final DeferredHolder<Block, ForemenHutBlock> FOREMEN_HUT_BLOCK = BLOCKS.register("foremens_hut",
            () -> new ForemenHutBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f)));

    public static final DeferredHolder<Block, TrackClearanceBlock> TRACK_CLEARANCE_BLOCK = BLOCKS.register("track_clearance",
            TrackClearanceBlock::new);

    public static final DeferredHolder<Block, LogisticsPathBlock> LOGISTICS_PATH = BLOCKS.register("logistics_path",
            () -> new LogisticsPathBlock(BlockBehaviour.Properties.of() .mapColor(net.minecraft.world.level.material.MapColor.STONE) .strength(1.5f) .sound(SoundType.STONE) .requiresCorrectToolForDrops() .speedFactor(1.3f).noOcclusion()));

    public static final DeferredHolder<Block, LogisticsPathBlock> BLACK_LOGISTICS_PATH = BLOCKS.register("black_logistics_path",
            () -> new LogisticsPathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f).sound(SoundType.STONE).speedFactor(1.3f).noOcclusion()));

    public static final DeferredHolder<Block, LogisticsPathBlock> WHITE_LOGISTICS_PATH = BLOCKS.register("white_logistics_path",
            () -> new LogisticsPathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.5f).sound(SoundType.STONE).speedFactor(1.3f).noOcclusion()));

    public static final DeferredHolder<Block, LogisticsPathBlock> MOSSY_LOGISTICS_PATH = BLOCKS.register("mossy_logistics_path",
            () -> new LogisticsPathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f).sound(SoundType.STONE).speedFactor(1.3f).noOcclusion()));

    public static final DeferredHolder<Block, LogisticsPathBlock> ILLUMINATED_PATH = BLOCKS.register("illuminated_path",
            () -> new LogisticsPathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.GLASS).speedFactor(1.3f).lightLevel(state -> 12).noOcclusion()));

    public static final DeferredHolder<Block, LogisticsPathBlock> ILLUMINATED_BLACK_PATH = BLOCKS.register("illuminated_black_path",
            () -> new LogisticsPathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f).sound(SoundType.GLASS).speedFactor(1.3f).lightLevel(state -> 12).noOcclusion()));

    public static final DeferredHolder<Block, LogisticsPathBlock> ILLUMINATED_WHITE_PATH = BLOCKS.register("illuminated_white_path",
            () -> new LogisticsPathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.5f).sound(SoundType.GLASS).speedFactor(1.3f).lightLevel(state -> 12).noOcclusion()));

    public static final DeferredHolder<Block, LogisticsPathBlock> ILLUMINATED_MOSSY_PATH = BLOCKS.register("illuminated_mossy_path",
            () -> new LogisticsPathBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.5f).sound(SoundType.GLASS).speedFactor(1.3f).lightLevel(state -> 12).noOcclusion()));

    public static final DeferredHolder<Block, BallastBlock> WHITE_BALLAST = registerColoredBallast("white", MapColor.SNOW);
    public static final DeferredHolder<Block, BallastBlock> BLUE_GRAY_BALLAST = registerColoredBallast("blue_gray", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredHolder<Block, BallastBlock> SANDY_BALLAST = registerColoredBallast("sandy", MapColor.COLOR_YELLOW);
    public static final DeferredHolder<Block, BallastBlock> PRISMA_BALLAST = registerColoredBallast("prisma", MapColor.COLOR_LIGHT_GREEN);
    public static final DeferredHolder<Block, BallastBlock> PINK_GRAY_BALLAST = registerColoredBallast("pink_gray", MapColor.COLOR_PINK);
    public static final DeferredHolder<Block, BallastBlock> GRAY_BALLAST = registerColoredBallast("gray", MapColor.COLOR_GRAY);
    public static final DeferredHolder<Block, BallastBlock> LIGHT_GRAY_BALLAST = registerColoredBallast("light_gray", MapColor.COLOR_LIGHT_GRAY);
    public static final DeferredHolder<Block, BallastBlock> DARK_GRAY_BALLAST = registerColoredBallast("dark_gray", MapColor.COLOR_CYAN);
    public static final DeferredHolder<Block, BallastBlock> PURPUR_BALLAST = registerColoredBallast("purpur", MapColor.COLOR_PURPLE);
    public static final DeferredHolder<Block, BallastBlock> BROWN_GRAY_BALLAST = registerColoredBallast("brown_gray", MapColor.COLOR_BROWN);
    public static final DeferredHolder<Block, BallastBlock> GREEN_GRAY_BALLAST = registerColoredBallast("green_gray", MapColor.COLOR_GREEN);
    public static final DeferredHolder<Block, BallastBlock> RED_BALLAST = registerColoredBallast("red", MapColor.COLOR_RED);
    public static final DeferredHolder<Block, BallastBlock> BLACK_BALLAST = registerColoredBallast("black", MapColor.COLOR_BLACK);
    public static final DeferredHolder<Block, BallastBlock> NETHER_BALLAST = registerColoredBallast("nether", MapColor.COLOR_RED);

    public static final DeferredHolder<Block, SlabBlock> WHITE_BALLAST_SLAB = registerColoredBallastSlab("white", MapColor.SNOW);
    public static final DeferredHolder<Block, SlabBlock> BLUE_GRAY_BALLAST_SLAB = registerColoredBallastSlab("blue_gray", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredHolder<Block, SlabBlock> SANDY_BALLAST_SLAB = registerColoredBallastSlab("sandy", MapColor.COLOR_YELLOW);
    public static final DeferredHolder<Block, SlabBlock> PRISMA_BALLAST_SLAB = registerColoredBallastSlab("prisma", MapColor.COLOR_LIGHT_GREEN);
    public static final DeferredHolder<Block, SlabBlock> PINK_GRAY_BALLAST_SLAB = registerColoredBallastSlab("pink_gray", MapColor.COLOR_PINK);
    public static final DeferredHolder<Block, SlabBlock> GRAY_BALLAST_SLAB = registerColoredBallastSlab("gray", MapColor.COLOR_GRAY);
    public static final DeferredHolder<Block, SlabBlock> LIGHT_GRAY_BALLAST_SLAB = registerColoredBallastSlab("light_gray", MapColor.COLOR_LIGHT_GRAY);
    public static final DeferredHolder<Block, SlabBlock> DARK_GRAY_BALLAST_SLAB = registerColoredBallastSlab("dark_gray", MapColor.COLOR_CYAN);
    public static final DeferredHolder<Block, SlabBlock> PURPUR_BALLAST_SLAB = registerColoredBallastSlab("purpur", MapColor.COLOR_PURPLE);
    public static final DeferredHolder<Block, SlabBlock> BROWN_GRAY_BALLAST_SLAB = registerColoredBallastSlab("brown_gray", MapColor.COLOR_BROWN);
    public static final DeferredHolder<Block, SlabBlock> GREEN_GRAY_BALLAST_SLAB = registerColoredBallastSlab("green_gray", MapColor.COLOR_GREEN);
    public static final DeferredHolder<Block, SlabBlock> RED_BALLAST_SLAB = registerColoredBallastSlab("red", MapColor.COLOR_RED);
    public static final DeferredHolder<Block, SlabBlock> BLACK_BALLAST_SLAB = registerColoredBallastSlab("black", MapColor.COLOR_BLACK);
    public static final DeferredHolder<Block, SlabBlock> NETHER_BALLAST_SLAB = registerColoredBallastSlab("nether", MapColor.COLOR_RED);

    public static final DeferredHolder<Block, FreightScannerBlock> FREIGHT_SCANNER_BLOCK = BLOCKS.register("freight_scanner",
            () -> new FreightScannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f).noOcclusion()));

    public static final DeferredHolder<Block, LogisticsFunnelBlock> LOGISTICS_FUNNEL_BLOCK = BLOCKS.register("logistics_funnel",
            () -> new LogisticsFunnelBlock(BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_YELLOW).strength(1.5f).noOcclusion()));

    // --- ITEMS ---
    public static final DeferredHolder<Item, BlockItem> LOGISTICS_CONTROLLER_ITEM = ITEMS.register("logistics_controller",
            () -> new BlockItem(LOGISTICS_CONTROLLER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> FREIGHT_DEPOT_ITEM = ITEMS.register("freight_depot",
            () -> new BlockItem(FREIGHT_DEPOT_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> FOREMEN_HUT_ITEM = ITEMS.register("foremens_hut",
            () -> new BlockItem(FOREMEN_HUT_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, LogisticsLinkerItem> LOGISTICS_LINKER = ITEMS.register("logistics_linker",
            () -> new LogisticsLinkerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, BlockItem> TRACK_CLEARANCE_ITEM = ITEMS.register("track_clearance",
            () -> new BlockItem(TRACK_CLEARANCE_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> LOGISTICS_PATH_ITEM = ITEMS.register("logistics_path",
            () -> new BlockItem(LOGISTICS_PATH.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> BLACK_LOGISTICS_PATH_ITEM = ITEMS.register("black_logistics_path",
            () -> new BlockItem(BLACK_LOGISTICS_PATH.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> WHITE_LOGISTICS_PATH_ITEM = ITEMS.register("white_logistics_path",
            () -> new BlockItem(WHITE_LOGISTICS_PATH.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> MOSSY_LOGISTICS_PATH_ITEM = ITEMS.register("mossy_logistics_path",
            () -> new BlockItem(MOSSY_LOGISTICS_PATH.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> ILLUMINATED_PATH_ITEM = ITEMS.register("illuminated_path",
            () -> new BlockItem(ILLUMINATED_PATH.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> ILLUMINATED_BLACK_PATH_ITEM = ITEMS.register("illuminated_black_path",
            () -> new BlockItem(ILLUMINATED_BLACK_PATH.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> ILLUMINATED_WHITE_PATH_ITEM = ITEMS.register("illuminated_white_path",
            () -> new BlockItem(ILLUMINATED_WHITE_PATH.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> ILLUMINATED_MOSSY_PATH_ITEM = ITEMS.register("illuminated_mossy_path",
            () -> new BlockItem(ILLUMINATED_MOSSY_PATH.get(), new Item.Properties()));

    public static final DeferredHolder<Item, Item> EDIBLE_BALLAST = ITEMS.register("edible_ballast",
            () -> new Item(new Item.Properties().stacksTo(1).food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().build())));

    public static final DeferredHolder<Item, BeltTunnelItem> FREIGHT_SCANNER_ITEM = ITEMS.register("freight_scanner",
            () -> new BeltTunnelItem(FREIGHT_SCANNER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> LOGISTICS_FUNNEL_ITEM = ITEMS.register("logistics_funnel",
            () -> new BlockItem(LOGISTICS_FUNNEL_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<Item, PortableTrackerItem> PORTABLE_TRACKER_ITEM = ITEMS.register("portable_tracker",
            () -> new PortableTrackerItem(new Item.Properties().stacksTo(1)));

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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FreightScannerBlockEntity>> FREIGHT_SCANNER_BE =
            BLOCK_ENTITIES.register("freight_scanner", () ->
                    BlockEntityType.Builder.of(FreightScannerBlockEntity::new, FREIGHT_SCANNER_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LogisticsFunnelBlockEntity>> LOGISTICS_FUNNEL_BE =
            BLOCK_ENTITIES.register("logistics_funnel", () ->
                    BlockEntityType.Builder.of(LogisticsFunnelBlockEntity::new, LOGISTICS_FUNNEL_BLOCK.get()).build(null));

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
                output.accept(TRACK_CLEARANCE_BLOCK.get());
                output.accept(WHITE_BALLAST.get());
                output.accept(SANDY_BALLAST.get());
                output.accept(GRAY_BALLAST.get());
                output.accept(LIGHT_GRAY_BALLAST.get());
                output.accept(DARK_GRAY_BALLAST.get());
                output.accept(BROWN_GRAY_BALLAST.get());
                output.accept(PRISMA_BALLAST.get());
                output.accept(NETHER_BALLAST.get());
                output.accept(BLUE_GRAY_BALLAST.get());
                output.accept(PINK_GRAY_BALLAST.get());
                output.accept(PURPUR_BALLAST.get());
                output.accept(RED_BALLAST.get());
                output.accept(BLACK_BALLAST.get());
                output.accept(GREEN_GRAY_BALLAST.get());
                output.accept(LOGISTICS_PATH_ITEM.get());
                output.accept(BLACK_LOGISTICS_PATH_ITEM.get());
                output.accept(WHITE_LOGISTICS_PATH_ITEM.get());
                output.accept(MOSSY_LOGISTICS_PATH_ITEM.get());
                output.accept(ILLUMINATED_PATH_ITEM.get());
                output.accept(ILLUMINATED_BLACK_PATH_ITEM.get());
                output.accept(ILLUMINATED_WHITE_PATH_ITEM.get());
                output.accept(ILLUMINATED_MOSSY_PATH_ITEM.get());
                output.accept(WHITE_BALLAST_SLAB.get());
                output.accept(BLUE_GRAY_BALLAST_SLAB.get());
                output.accept(SANDY_BALLAST_SLAB.get());
                output.accept(PRISMA_BALLAST_SLAB.get());
                output.accept(PINK_GRAY_BALLAST_SLAB.get());
                output.accept(GRAY_BALLAST_SLAB.get());
                output.accept(LIGHT_GRAY_BALLAST_SLAB.get());
                output.accept(DARK_GRAY_BALLAST_SLAB.get());
                output.accept(PURPUR_BALLAST_SLAB.get());
                output.accept(BROWN_GRAY_BALLAST_SLAB.get());
                output.accept(GREEN_GRAY_BALLAST_SLAB.get());
                output.accept(RED_BALLAST_SLAB.get());
                output.accept(BLACK_BALLAST_SLAB.get());
                output.accept(NETHER_BALLAST_SLAB.get());
                output.accept(FREIGHT_SCANNER_ITEM.get());
                output.accept(LOGISTICS_FUNNEL_ITEM.get());
            }).build());

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        ITEMS.register(eventBus);
        BLOCK_ENTITIES.register(eventBus);
        MENU_TYPES.register(eventBus);
        CREATIVE_TABS.register(eventBus);
        DISPLAY_SOURCES.register(eventBus);
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
    private static DeferredHolder<Block, BallastBlock> registerColoredBallast(String colorName, MapColor mapColor) {
        DeferredHolder<Block, BallastBlock> block = BLOCKS.register(colorName + "_ballast",
                () -> new BallastBlock(BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .strength(1.5f)
                        .sound(net.minecraft.world.level.block.SoundType.GRAVEL)));

        ITEMS.register(colorName + "_ballast",
                () -> new BlockItem(block.get(), new Item.Properties()));

        return block;
    }

    private static DeferredHolder<Block, SlabBlock> registerColoredBallastSlab(String colorName, MapColor mapColor) {
        DeferredHolder<Block, SlabBlock> block = BLOCKS.register(colorName + "_ballast_slab",
                () -> new SlabBlock(BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .strength(1.5f)
                        .sound(SoundType.GRAVEL)));

        ITEMS.register(colorName + "_ballast_slab",
                () -> new BlockItem(block.get(), new Item.Properties()));

        return block;
    }
}
