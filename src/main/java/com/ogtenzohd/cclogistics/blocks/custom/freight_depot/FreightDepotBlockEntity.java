package com.ogtenzohd.cclogistics.blocks.custom.freight_depot;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.blocks.SmartColonyBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.menu.FreightDepotMenu;
import com.ogtenzohd.cclogistics.colony.ColonyLogisticsBehaviour;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.ogtenzohd.cclogistics.util.LogisticsRequestHelper;
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

import java.util.*;

public class FreightDepotBlockEntity extends SmartColonyBlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private String colonyName = "Colony_1";
    private String cityTarget = "City_Storage";
    private boolean isTrainParked = false;
    private long lastTrainCheck = 0;
    private BlockPos manualTickerPos = null;
    
    private final Set<Object> activeRequestIds = new HashSet<>();
    private final Set<Object> failedRequestIds = new HashSet<>();
    private final List<String> pendingIncomingLogs = new ArrayList<>();
    private final List<String> pendingOutgoingLogs = new ArrayList<>();
    private final Map<Item, Long> protectedImports = new HashMap<>();
	private final Set<String> sentRequestIds = new HashSet<>();
    
    private final List<com.ogtenzohd.cclogistics.colony.ai.LogisticsCoordinatorAI.ManifestEntry> cacheA = new ArrayList<>();

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
            
            if (level.getGameTime() % 100 == 0) {
                coordinateLogistics();
            }
            
            if (level.getGameTime() % 200 == 0) {
                updateStructureInfo();
            }
            
            if (level.getGameTime() - lastTrainCheck >= 20) {
                lastTrainCheck = level.getGameTime();
                boolean trainFound = checkForParkedTrain();
                
                if (trainFound != isTrainParked) {
                    isTrainParked = trainFound;
                    IBuilding b = getBuilding();
                    if (b instanceof FreightDepotBuilding fdb) {
                        fdb.initateBrainSwap(isTrainParked);
                    }
                }
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
    
    public BlockPos getImportPos() { return importVaultPos; }
    public BlockPos getExportPos() { return exportVaultPos; }

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
    }
    
    private StockTickerBlockEntity resolveTicker() {
        if (manualTickerPos != null && level != null && level.getBlockEntity(manualTickerPos) instanceof StockTickerBlockEntity be) {
            return be;
        }
        return null; 
    }

    public void coordinateLogistics() {
        if (level == null || level.isClientSide) return;

        StockTickerBlockEntity ticker = resolveTicker();
        if (ticker == null) return;

        IColony colony = MinecoloniesAPIProxy.getInstance().getColonyManager().getIColony(level, worldPosition);
        if (colony == null) return;

        LogisticsRequestHelper.processRequests(
            colony,
            ticker,
            this.activeRequestIds,
            this.failedRequestIds,
            this.sentRequestIds,
            (request) -> resolveAddress(request), 
            this.pendingIncomingLogs,
            (stack) -> this.protectItem(stack, 30),
            this::updateTracker,
            this::cacheManifestOrder
        );
    }

    public void updateTracker(String id, String itemName, int amount, com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule.TrackStatus status, String override) {
        IBuilding b = getBuilding();
        if (b != null) {
            com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule module = b.getModule(com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule.class);
            if (module != null) {
                if (status == com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule.TrackStatus.COMPLETED) {
                    module.removeRequest(id);
                } else {
                    module.updateRequest(id, itemName, amount, status, override);
                }
            }
        }
    }
    
    public void protectItem(ItemStack stack, int minutes) {
        if (stack.isEmpty() || level == null) return;
        long expireTime = level.getGameTime() + (minutes * 60 * 20); 
        protectedImports.put(stack.getItem(), expireTime);
        setChanged();
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
        if (request.getRequester() != null) {
            
            if (request.getRequester().getLocation().getInDimensionLocation().equals(worldPosition)) {
                return null; 
            }
            
            if (request.getRequester() instanceof com.minecolonies.api.colony.ICitizenData citizen) {
                String jobName = "";
                if (citizen.getJob() != null) {
                     jobName = citizen.getJob().getClass().getSimpleName().toLowerCase();
                }
                if (jobName.contains("logisticscoordinator") || 
                    jobName.contains("packeragent") || 
                    jobName.contains("freightinspector")) {
                    return null; 
                }
                
                if (citizen.getWorkBuilding() != null && citizen.getWorkBuilding().getPosition().equals(worldPosition)) {
                    return null;
                }
            }

            String reqClass = request.getRequester().getClass().getSimpleName().toLowerCase();
            if (reqClass.contains("logisticscoordinator") || 
                reqClass.contains("packeragent") || 
                reqClass.contains("freightinspector") || 
                reqClass.contains("freightdepot")) {
                return null;
            }
        }
        
        if (this.colonyName != null && !this.colonyName.isEmpty()) {
            return this.colonyName;
        }
        
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
    
    private boolean checkForParkedTrain() {
        if (level == null) return false;
        
        net.minecraft.world.phys.AABB searchArea = new net.minecraft.world.phys.AABB(worldPosition).inflate(10.0);
        
        java.util.List<com.simibubi.create.content.trains.entity.CarriageContraptionEntity> trains = 
            level.getEntitiesOfClass(com.simibubi.create.content.trains.entity.CarriageContraptionEntity.class, searchArea);
        
        for (var train : trains) {
            if (train.getDeltaMovement().lengthSqr() < 0.01) {
                return true; 
            }
        }
        return false;
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

    public List<com.ogtenzohd.cclogistics.colony.ai.LogisticsCoordinatorAI.ManifestEntry> getAndClearCacheA() {
        List<com.ogtenzohd.cclogistics.colony.ai.LogisticsCoordinatorAI.ManifestEntry> copy = new ArrayList<>(this.cacheA);
        this.cacheA.clear();
        setChanged();
        return copy;
    }

    public StockTickerBlockEntity getStockTicker() {
        return resolveTicker();
    }

    public void cacheManifestOrder(ItemStack item, int amount, String targetAddress) {
        if (item.isEmpty() || amount <= 0) return;
        ItemStack cachedStack = item.copy();
        cachedStack.setCount(1);
        this.cacheA.add(new com.ogtenzohd.cclogistics.colony.ai.LogisticsCoordinatorAI.ManifestEntry(cachedStack, targetAddress, amount));
        setChanged();
    }

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
        
        ListTag cacheAList = new ListTag();
        for (com.ogtenzohd.cclogistics.colony.ai.LogisticsCoordinatorAI.ManifestEntry entry : cacheA) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("Item", entry.item.save(provider));
            entryTag.putString("Address", entry.address);
            entryTag.putInt("Amount", entry.amount);
            cacheAList.add(entryTag);
        }
        tag.put("CacheA", cacheAList);
		ListTag sentIds = new ListTag();
        for (String id : sentRequestIds) {
            CompoundTag t = new CompoundTag();
            t.putString("id", id);
            sentIds.add(t);
        }
        tag.put("SentRequestIds", sentIds);
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
        
        if (tag.contains("CacheA")) {
            cacheA.clear();
            ListTag list = tag.getList("CacheA", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                ItemStack item = ItemStack.parse(provider, entryTag.getCompound("Item")).orElse(ItemStack.EMPTY);
                String address = entryTag.getString("Address");
                int amount = entryTag.getInt("Amount");
                if (!item.isEmpty()) {
                    cacheA.add(new com.ogtenzohd.cclogistics.colony.ai.LogisticsCoordinatorAI.ManifestEntry(item, address, amount));
                }
            }
        }
		if (tag.contains("SentRequestIds")) {
            sentRequestIds.clear();
            ListTag list = tag.getList("SentRequestIds", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                sentRequestIds.add(list.getCompound(i).getString("id"));
            }
        }
    }
}