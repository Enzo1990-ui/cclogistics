package com.ogtenzohd.cclogistics.blocks.custom.freight_depot;

import com.ogtenzohd.cclogistics.blocks.SmartColonyBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.ogtenzohd.cclogistics.util.LogisticsRequestHelper;
import com.ogtenzohd.cclogistics.colony.ColonyLogisticsBehaviour;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.buildings.IBuilding;

import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.menu.FreightDepotMenu;

import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FreightDepotBlockEntity extends SmartColonyBlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private String colonyName = "Colony_1";
    private String cityTarget = "City_Storage";
    private BlockPos manualTickerPos = null;
    
    private final Set<Object> activeRequestIds = new HashSet<>();
    private final Set<Object> failedRequestIds = new HashSet<>();
    private final List<String> pendingIncomingLogs = new ArrayList<>();
    private final List<String> pendingOutgoingLogs = new ArrayList<>();
    private final Map<Item, Long> protectedImports = new HashMap<>();

    private BlockPos importVaultPos;
    private BlockPos exportVaultPos;

    private ColonyLogisticsBehaviour tickerBehaviour;

    private final ItemStackHandler inventory = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public FreightDepotBlockEntity(BlockPos pos, BlockState state) {
        super(CCLRegistration.FREIGHT_DEPOT_BE.get(), pos, state);
    }

    public IBuilding getBuilding() {
        if (level == null || level.isClientSide) return null;
        IColony colony = MinecoloniesAPIProxy.getInstance().getColonyManager().getIColony(level, worldPosition);
        if (colony != null) {
            return colony.getServerBuildingManager().getBuilding(worldPosition);
        }
        return null;
    }
    
    public IItemHandler getBuildingInventory() {
        return this.inventory;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tickerBehaviour = new ColonyLogisticsBehaviour(this);
        behaviours.add(tickerBehaviour);
    }
    
    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveCustomOnly(provider);
    }
    
    @Override
    public void tick() {
        super.tick(); 
        if (level != null && !level.isClientSide) {
            if (level.getGameTime() % 200 == 0) {
                updateStructureInfo();
            }
        }
    }

    private void updateStructureInfo() {
        IColony colony = com.minecolonies.api.colony.IColonyManager.getInstance().getIColony(level, worldPosition);
        if (colony != null) {
            IBuilding building = colony.getServerBuildingManager().getBuilding(worldPosition);
            if (building instanceof FreightDepotBuilding freightBuilding) {
                freightBuilding.updateStructureData();
            }
        }
    }
    
    public void setImportPos(BlockPos pos) { this.importVaultPos = pos; }
    public void setExportPos(BlockPos pos) { this.exportVaultPos = pos; }

    public IItemHandler getImportInventory() {
        if (importVaultPos == null) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, importVaultPos, null);
    }

    public IItemHandler getExportInventory() {
        if (exportVaultPos == null) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, exportVaultPos, null);
    }

    public void setTickerLink(BlockPos pos) {
        this.manualTickerPos = pos;
        setChanged();
        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[FreightDepotBE] Manually linked to Ticker at " + pos);
    }
    
    private StockTickerBlockEntity resolveTicker() {
        if (manualTickerPos != null && level != null && level.getBlockEntity(manualTickerPos) instanceof StockTickerBlockEntity be) {
            return be;
        }
        return null; 
    }

    public void coordinateLogistics() {
        if (level == null || level.isClientSide) return;

        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[FreightDepotBE] CoordinateLogistics called.");
        StockTickerBlockEntity ticker = resolveTicker();
        if (ticker == null) {
            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.warn("[FreightDepotBE] No Linked Stock Ticker! Cannot process requests.");
            return;
        }

        IColony colony = MinecoloniesAPIProxy.getInstance().getColonyManager().getIColony(level, worldPosition);
        if (colony == null) {
            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.warn("[FreightDepotBE] Not in a valid colony!");
            return;
        }

        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[FreightDepotBE] Ticker found at " + ticker.getBlockPos() + ". Processing Colony Requests...");
        
        LogisticsRequestHelper.processRequests(
            colony,
            ticker,
            this.activeRequestIds,
            this.failedRequestIds,
            (request) -> resolveAddress(request), 
            this.pendingIncomingLogs,
            (stack) -> this.protectItem(stack, 30)
        );
    }
	
    public void protectItem(ItemStack stack, int minutes) {
        if (stack.isEmpty() || level == null) return;
        long expireTime = level.getGameTime() + (minutes * 60 * 20); // Mins * Secs * Ticks
        protectedImports.put(stack.getItem(), expireTime);
        setChanged();
        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[FreightDepot] Protected " + stack.getHoverName().getString() + " from export for " + minutes + " mins.");
    }

    public boolean isItemProtected(ItemStack stack) {
        if (stack.isEmpty() || level == null) return false;
        
        Item item = stack.getItem();
        if (protectedImports.containsKey(item)) {
            long expiry = protectedImports.get(item);
            if (level.getGameTime() < expiry) {
                return true;
            } else {
                protectedImports.remove(item);
                setChanged();
                return false;
            }
        }
        return false;
    }

    private String resolveAddress(IRequest<?> request) {
        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("[FreightDepotBE] Resolving address for request: " + request);
        
        if (request.getRequester() != null && request.getRequester().getLocation().getInDimensionLocation().equals(worldPosition)) {
            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("   -> Skipped (Requester is self/Depot)");
            return null;
        }
        
        if (request.getRequest() instanceof Stack stack) {
             if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("   -> Item Requested: " + stack.getStack().getHoverName().getString());
        }
        
        if (this.colonyName != null && !this.colonyName.isEmpty()) {
            if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.info("   -> Routing Import Request to My Address: " + this.colonyName);
            return this.colonyName;
        }
        
        if (CCLConfig.INSTANCE.debugMode.get()) LOGGER.warn("   -> No Colony Name configured!");
        return null; 
    }


    public void addIncomingLog(String log, int limit) {
        if (pendingIncomingLogs.size() >= limit) {
            pendingIncomingLogs.remove(0);
        }
        pendingIncomingLogs.add(log);
        setChanged();
    }

    public void addOutgoingLog(String log, int limit) {
        if (pendingOutgoingLogs.size() >= limit) {
            pendingOutgoingLogs.remove(0);
        }
        pendingOutgoingLogs.add(log);
        setChanged();
    }

    public List<String> collectIncomingLogs() {
        List<String> clipboard = new ArrayList<>(this.pendingIncomingLogs);
        this.pendingIncomingLogs.clear(); 
        return clipboard;
    }

    public List<String> collectOutgoingLogs() {
        List<String> clipboard = new ArrayList<>(this.pendingOutgoingLogs);
        this.pendingOutgoingLogs.clear(); 
        return clipboard;
    }


    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cclogistics.freight_depot");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FreightDepotMenu(id, inv, this, new net.minecraft.world.inventory.SimpleContainerData(2));
    }
    
    public void setColonyName(String name) { this.colonyName = name; setChanged(); }
    public void setCityTarget(String target) { this.cityTarget = target; setChanged(); }
    
    public String getColonyName() { return colonyName; }
    public String getCityTarget() { return cityTarget; }


    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("ColonyName", colonyName);
        tag.putString("CityTarget", cityTarget);
        if (manualTickerPos != null) tag.put("TickerLink", NbtUtils.writeBlockPos(manualTickerPos));
        
        tag.put("Inventory", inventory.serializeNBT(provider));

        ListTag protectionList = new ListTag();
        for (Map.Entry<Item, Long> entry : protectedImports.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Item", BuiltInRegistries.ITEM.getKey(entry.getKey()).toString());
            entryTag.putLong("Expiry", entry.getValue());
            protectionList.add(entryTag);
        }
        tag.put("ProtectedItems", protectionList);
    }
    
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("ColonyName")) colonyName = tag.getString("ColonyName");
        if (tag.contains("CityTarget")) cityTarget = tag.getString("CityTarget");
        if (tag.contains("TickerLink")) manualTickerPos = NbtUtils.readBlockPos(tag, "TickerLink").orElse(null);
        
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(provider, tag.getCompound("Inventory"));
        }

        if (tag.contains("ProtectedItems")) {
            protectedImports.clear();
            ListTag list = tag.getList("ProtectedItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(entryTag.getString("Item")));
                long expiry = entryTag.getLong("Expiry");
                if (item != net.minecraft.world.item.Items.AIR) {
                    protectedImports.put(item, expiry);
                }
            }
        }
    }
}