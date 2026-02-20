package com.ogtenzohd.cclogistics.blocks.custom.logistics_controller;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding; 
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.ogtenzohd.cclogistics.network.CCLPackets; 
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.network.SyncBuildingsPacket; 
import com.ogtenzohd.cclogistics.util.BuildingRoutingEntry;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.logic.PackageRouting;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import com.ogtenzohd.cclogistics.util.LogisticsRequestHelper;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class LogisticsControllerBlockEntity extends SmartBlockEntity implements MenuProvider {

    private List<BuildingRoutingEntry> packages = new ArrayList<>();
    private BlockPos tickerLink = null;
    private int timer = 0;
    
    private List<String> clientAvailableBuildings = new ArrayList<>();

    private final HashSet<Object> activeRequestIds = new HashSet<>();
    private final HashSet<Object> failedRequestIds = new HashSet<>();

    protected final ContainerData data = new ContainerData() {
        @Override public int get(int pIndex) { return 0; }
        @Override public void set(int pIndex, int pValue) {}
        @Override public int getCount() { return 2; }
    };

    public LogisticsControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CCLRegistration.LOGISTICS_CONTROLLER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;
        
        timer++;
        if (timer >= 100) {
            timer = 0;
            performRequest();
        }
    }

    private void performRequest() {
        if (tickerLink == null) return;
        
        if (!(level.getBlockEntity(tickerLink) instanceof StockTickerBlockEntity ticker)) {
            return;
        }

        IColony colony = MinecoloniesAPIProxy.getInstance().getColonyManager().getIColony(level, worldPosition);
        
        if (colony == null) return;

        LogisticsRequestHelper.processRequests(
            colony,
            ticker,
            activeRequestIds,
            failedRequestIds,
            (request) -> PackageRouting.resolvePackageName(this.packages, request),
            null,
			null
        );
    }

    public void setPackages(List<BuildingRoutingEntry> packages) {
        this.packages = packages;
        setChanged();
    }
    
    public List<BuildingRoutingEntry> getPackages() {
        return this.packages;
    }

    public void setTickerLink(BlockPos pos) {
        this.tickerLink = pos;
        setChanged();
    }
    
    public List<String> getAvailableBuildings() {
        if (level.isClientSide) {
            return clientAvailableBuildings;
        }
        
        IColony colony = MinecoloniesAPIProxy.getInstance().getColonyManager().getIColony(level, worldPosition);
        if (colony != null) {
            return colony.getServerBuildingManager().getBuildings().values().stream()
                    .map(IBuilding::getBuildingDisplayName) 
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
    
    public void setClientAvailableBuildings(List<String> buildings) {
        this.clientAvailableBuildings = buildings;
    }

    public void syncBuildingsToPlayer(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            List<String> buildings = getAvailableBuildings();
            CCLPackets.sendToPlayer(new SyncBuildingsPacket(this.getBlockPos(), buildings), serverPlayer);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.write(tag, provider, clientPacket);
        if (tickerLink != null) {
            tag.put("TickerLink", NbtUtils.writeBlockPos(tickerLink));
        }
        ListTag list = new ListTag();
        for (BuildingRoutingEntry p : packages) list.add(p.serialize());
        tag.put("Packages", list);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket) {
        super.read(tag, provider, clientPacket);
        if (tag.contains("TickerLink")) {
            tickerLink = NbtUtils.readBlockPos(tag, "TickerLink").orElse(null);
        }
        if (tag.contains("Packages")) {
            packages.clear();
            ListTag list = tag.getList("Packages", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                packages.add(BuildingRoutingEntry.deserialize(list.getCompound(i)));
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.cclogistics.logistics_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.menu.LogisticsControllerMenu(id, inv, this, this.data);
    }
}