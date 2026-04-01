package com.ogtenzohd.cclogistics.colony.ai;

// WARNING COLONISTS WERE HARMED DURING THE MAKING OF THIS MOD!!!

import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.mojang.logging.LogUtils;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.FreightDepotBuilding;
import com.ogtenzohd.cclogistics.colony.buildings.modules.FreightTrackerModule;
import com.ogtenzohd.cclogistics.colony.job.PackerAgentJob;
import com.ogtenzohd.cclogistics.config.CCLConfig;
import com.ogtenzohd.cclogistics.util.HolidayManager;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class PackerAgentAI extends AbstractEntityAIBasic<PackerAgentJob, FreightDepotBuilding> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private enum State {
        IDLE,
        // Unpack Import
        TO_DEPOT_IMPORT, AT_DEPOT_IMPORT, TO_WAREHOUSE, AT_WAREHOUSE,
        // Pack Export
        TO_DEPOT_EXCESS, AT_DEPOT_EXCESS, TO_DEPOT_EXPORT, AT_DEPOT_EXPORT,
        // Train Unload
        TO_TRAIN_UNLOAD, AT_TRAIN_UNLOAD, TO_DEPOT_DROP, AT_DEPOT_DROP,
        // Train Load
        TO_DEPOT_PICKUP, AT_DEPOT_PICKUP, TO_TRAIN_LOAD, AT_TRAIN_LOAD,
        // Express Delivery
        TO_EXPRESS_DELIVERY, AT_EXPRESS_DELIVERY,
        // April Fools
        APRIL_FOOLS_RUNAWAY
    }

    private State state = State.IDLE;
    private int delay = 0;
    private BlockPos currentTarget = null;

    private List<ItemStack> holdingItems = new ArrayList<>();

    private net.minecraft.core.BlockPos prankTarget = null;
    private int prankTickTimer = 0;
    private int prankBoxesDropped = 0;
    private final java.util.List<net.minecraft.world.entity.Entity> droppedPrankBoxes = new java.util.ArrayList<>();

    private PackerAgentJob.PackerRole activeRole = null;

    public PackerAgentAI(PackerAgentJob job) {
        super(job);
    }

    @Override
    public Class<FreightDepotBuilding> getExpectedBuildingClass() {
        return FreightDepotBuilding.class;
    }

    public void resetStateForNewRole() {
        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
            LOGGER.info("[PackerAI-ENHANCED] Brain Swap requested role switch. Will smoothly transition after current task finishes.");
        }
    }

    private int getSkillLevel(Skill skill) {
        if (job.getCitizen() == null || job.getCitizen().getCitizenSkillHandler() == null) return 1;
        return job.getCitizen().getCitizenSkillHandler().getLevel(skill);
    }

    private void updateTracker(String itemName, int amount, FreightTrackerModule.TrackStatus status) {
        if (job.getWorkBuilding() != null) {
            FreightTrackerModule module = job.getWorkBuilding().getModule(FreightTrackerModule.class);
            if (module != null) {
                if (status == FreightTrackerModule.TrackStatus.COMPLETED) module.removeRequest(itemName);
                else module.updateRequest(itemName, amount, status);
            }
        }
    }

    private void updateTrackerByName(String itemName, FreightTrackerModule.TrackStatus status) {
        if (job.getWorkBuilding() != null) {
            FreightTrackerModule module = job.getWorkBuilding().getModule(FreightTrackerModule.class);
            if (module != null) {
                module.updateRequestByName(itemName, status);
            }
        }
    }

    private boolean checkHolidayEvents() {
        if (HolidayManager.IS_APRIL_FOOLS) {
            return handleAprilFoolsPrank();
        }

        return false;
    }

    private boolean handleAprilFoolsPrank() {
        if (job.getColony().getWorld().random.nextFloat() < 0.30f) {
            job.getCitizen().getEntity().ifPresent(entity -> {

                double ang = entity.getRandom().nextDouble() * Math.PI * 2;
                double dist = 6 + entity.getRandom().nextDouble() * 4;
                int tx = (int)(entity.getX() + Math.cos(ang) * dist);
                int tz = (int)(entity.getZ() + Math.sin(ang) * dist);
                int ty = entity.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, tx, tz);

                this.prankTarget = new net.minecraft.core.BlockPos(tx, ty, tz);

                this.prankTickTimer = 0;
                this.prankBoxesDropped = 0;
                this.droppedPrankBoxes.clear();

                entity.level().playSound(null, entity.blockPosition(), net.minecraft.sounds.SoundEvents.MULE_CHEST, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.5f);
            });

            state = State.APRIL_FOOLS_RUNAWAY;
            delay = 0;
            return true;
        }
        return false;
    }

    private void tickAprilFoolsRunaway() {
        job.getCitizen().getEntity().ifPresent(entity -> {
            net.minecraft.world.level.Level level = entity.level();
            prankTickTimer++;

            if (prankTarget != null) {
                moveTo(prankTarget);
            }

            if (prankTickTimer % 10 == 0 && prankBoxesDropped < 3) {
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    net.minecraft.world.entity.EntityType<?> packageType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("create:package"));

                    if (packageType != null) {
                        net.minecraft.world.entity.Entity customPackage = packageType.create(serverLevel);
                        if (customPackage != null) {
                            customPackage.setPos(entity.getX(), entity.getY() + 0.2, entity.getZ());
                            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
                            customPackage.saveWithoutId(nbt);

                            net.minecraft.nbt.CompoundTag boxTag = new net.minecraft.nbt.CompoundTag();
                            boxTag.putByte("count", (byte)1);
                            boxTag.putString("id", "create:cardboard_package_12x10");
                            nbt.put("Box", boxTag);

                            customPackage.load(nbt);
                            customPackage.setDeltaMovement((entity.getRandom().nextDouble() - 0.5) * 0.2, 0.2, (entity.getRandom().nextDouble() - 0.5) * 0.2);

                            serverLevel.addFreshEntity(customPackage);
                            droppedPrankBoxes.add(customPackage);
                            prankBoxesDropped++;

                            serverLevel.playSound(null, entity.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 0.5f);
                        }
                    }
                }
            }

            if (prankTickTimer > 50) {
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {

                    for (net.minecraft.world.entity.Entity box : droppedPrankBoxes) {
                        if (box.isRemoved()) continue;

                        serverLevel.playSound(null, box.blockPosition(), net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.5f);
                        serverLevel.playSound(null, box.blockPosition(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 2.0f);

                        for (int i = 0; i < 20; i++) {
                            double mx = (entity.getRandom().nextDouble() - 0.5) * 0.6;
                            double my = entity.getRandom().nextDouble() * 0.5;
                            double mz = (entity.getRandom().nextDouble() - 0.5) * 0.6;
                            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FIREWORK,
                                    box.getX(), box.getY() + 0.2, box.getZ(),
                                    0, mx, my, mz, 1.0D);
                        }
                        box.discard();
                    }
                }
                droppedPrankBoxes.clear();
                state = State.IDLE;
                delay = 40;
            }
        });
    }

    public void tick() {
        job.getCitizen().getEntity().ifPresent(entity -> {
            entity.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.RAIL, -1.0F);
        });
        if (activeRole == null) {
            activeRole = job.getRole();
            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
                LOGGER.info("[PackerAI-ENHANCED] Initializing Agent with Role: " + activeRole);
            }
        }

        if (activeRole != job.getRole() && holdingItems.isEmpty()) {
            if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) {
                LOGGER.info("[PackerAI-ENHANCED] Hands are empty! Seamlessly switching from " + activeRole + " to new role: " + job.getRole());
            }
            activeRole = job.getRole();
            this.state = State.IDLE;
            this.currentTarget = null;
            this.delay = 10;
        }

        if (!holdingItems.isEmpty()) {
            job.getCitizen().getEntity().ifPresent(entity -> {
                if (entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
                    entity.setItemInHand(InteractionHand.MAIN_HAND, holdingItems.getFirst().copy());
                }
            });
        } else {
            job.getCitizen().getEntity().ifPresent(entity -> {
                ItemStack handItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
                if (!handItem.isEmpty() && isPackage(handItem)) {
                    clearHand();
                }
            });
        }

        if (delay > 0) { delay--; return; }

        if (state == State.APRIL_FOOLS_RUNAWAY) {
            tickAprilFoolsRunaway();
            return;
        }

        switch (activeRole) {
            case UNPACKING_IMPORT: tickUnpackImport(); break;
            case PACKING_EXPORT:   tickPackExport(); break;
            case UNLOADING_TRAIN:  tickUnloadTrain(); break;
            case LOADING_TRAIN:    tickLoadTrain(); break;
            case GENERAL_DUTY:
                if (state == State.IDLE || state == State.TO_DEPOT_IMPORT || state == State.AT_DEPOT_IMPORT ||
                        state == State.TO_WAREHOUSE || state == State.AT_WAREHOUSE) {
                    tickUnpackImport();
                } else {
                    tickPackExport();
                }
                break;
            default: tickIdle();
                break;
        }
    }

    // =======================
    // ROLE: UNPACKING IMPORTS
    // =======================
    private void tickUnpackImport() {
        switch (state) {
            case IDLE:
            case TO_DEPOT_EXCESS: case TO_DEPOT_EXPORT: case TO_TRAIN_UNLOAD: case TO_DEPOT_PICKUP:
                state = State.TO_DEPOT_IMPORT;
                break;

            case TO_DEPOT_IMPORT:
                if (job.getWorkBuilding() != null) {
                    currentTarget = job.getWorkBuilding().getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT_IMPORT;
                        delay = 20;
                    }
                }
                break;

            case AT_DEPOT_IMPORT:
                if (checkHolidayEvents()) return;
                if (!holdingItems.isEmpty()) {
                    state = State.TO_WAREHOUSE;
                    return;
                }

                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE) {
                        IItemHandler importInv = depotBE.getImportInventory();
                        if (importInv != null) {
                            List<ItemStack> rawItemsToPack = new ArrayList<>();
                            int maxCapacity = Math.min(2 + (CCLConfig.INSTANCE.enableSkillScaling.get() ? getSkillLevel(Skill.Strength) * CCLConfig.INSTANCE.packerCapacityPerStrength.get() : 0), 9);

                            for (int i = 0; i < importInv.getSlots(); i++) {
                                if (rawItemsToPack.size() >= maxCapacity) break;
                                ItemStack check = importInv.extractItem(i, 64, true);
                                if (!check.isEmpty()) {
                                    if (isPackage(check)) {
                                        if (!rawItemsToPack.isEmpty()) break;
                                        ItemStack extracted = importInv.extractItem(i, 64, false);
                                        holdingItems.add(extracted);

                                        for (ItemStack content : unpack(extracted)) {
                                            updateTrackerByName(content.getHoverName().getString(), FreightTrackerModule.TrackStatus.IN_TRANSIT);
                                        }
                                        state = State.TO_WAREHOUSE;
                                        return;
                                    } else {
                                        rawItemsToPack.add(importInv.extractItem(i, 64, false));
                                    }
                                }
                            }
                            if (!rawItemsToPack.isEmpty()) {
                                holdingItems.add(pack(rawItemsToPack, ""));
                                for (ItemStack rawItem : rawItemsToPack) {
                                    updateTrackerByName(rawItem.getHoverName().getString(), FreightTrackerModule.TrackStatus.IN_TRANSIT);
                                }
                                state = State.TO_WAREHOUSE;
                                return;
                            }
                        }
                    }
                }

                if (activeRole == PackerAgentJob.PackerRole.GENERAL_DUTY) {
                    state = State.TO_DEPOT_EXCESS;
                } else {
                    state = State.IDLE;
                    delay = 40;
                }
                break;

            case TO_WAREHOUSE:
                com.minecolonies.api.colony.buildings.IBuilding warehouse = job.getColony().getServerBuildingManager().getBuildings().values().stream()
                        .filter(b -> b.getSchematicName().contains("warehouse")).findFirst().orElse(null);
                if (warehouse != null) {
                    currentTarget = warehouse.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_WAREHOUSE;
                        delay = 20;
                    }
                } else {
                    state = State.TO_DEPOT_IMPORT;
                }
                break;

            case AT_WAREHOUSE:
                if (!holdingItems.isEmpty() && currentTarget != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be != null) {
                        IItemHandler handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
                        if (handler != null) {
                            List<ItemStack> remainingList = new ArrayList<>();
                            for (ItemStack stack : holdingItems) {
                                if (isPackage(stack)) {
                                    for (ItemStack unpackedItem : unpack(stack)) {
                                        ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, unpackedItem, false);
                                        int amountInserted = unpackedItem.getCount() - remaining.getCount();
                                        if (amountInserted > 0) updateTracker(unpackedItem.getHoverName().getString(), amountInserted, FreightTrackerModule.TrackStatus.COMPLETED);
                                        if (!remaining.isEmpty()) remainingList.add(remaining);
                                    }
                                } else {
                                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, stack, false);
                                    if (!remaining.isEmpty()) remainingList.add(remaining);
                                }
                            }
                            holdingItems = remainingList;
                        }
                    }
                }

                if (holdingItems.isEmpty()) {
                    clearHand();
                    if (activeRole == PackerAgentJob.PackerRole.GENERAL_DUTY) {
                        state = State.TO_DEPOT_EXCESS;
                    } else {
                        state = State.IDLE;
                        delay = 40;
                    }
                } else {
                    delay = 60;
                }
                break;
        }
    }

    // =====================
    // ROLE: PACKING EXPORTS
    // =====================
    private void tickPackExport() {
        switch (state) {
            case IDLE:
            case TO_DEPOT_IMPORT: case TO_TRAIN_UNLOAD: case TO_DEPOT_PICKUP:
                state = State.TO_DEPOT_EXCESS;
                break;

            case TO_DEPOT_EXCESS:
                if (job.getWorkBuilding() != null) {
                    currentTarget = job.getWorkBuilding().getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT_EXCESS;
                        delay = 20;
                    }
                }
                break;

            case AT_DEPOT_EXCESS:
                if (checkHolidayEvents()) return;
                if (!holdingItems.isEmpty()) {
                    state = State.TO_DEPOT_EXPORT;
                    return;
                }

                com.minecolonies.api.colony.buildings.IBuilding workBuilding = job.getWorkBuilding();
                String targetAddress = "Colony_Storage";

                if (workBuilding != null && job.getColony().getWorld() != null) {
                    BlockEntity mainBE = job.getColony().getWorld().getBlockEntity(workBuilding.getPosition());
                    if (mainBE instanceof FreightDepotBlockEntity fbe) {
                        targetAddress = fbe.getCityTarget();
                    }

                    List<BlockPos> containers = workBuilding.getContainers();
                    for (BlockPos containerPos : containers) {
                        IItemHandler inv = job.getColony().getWorld().getCapability(Capabilities.ItemHandler.BLOCK, containerPos, null);
                        if (inv == null) {
                            BlockEntity be = job.getColony().getWorld().getBlockEntity(containerPos);
                            if (be instanceof FreightDepotBlockEntity fbe) {
                                inv = fbe.getBuildingInventory();
                            }
                        }

                        if (inv != null) {
                            List<ItemStack> toPack = new ArrayList<>();
                            int maxCapacity = Math.min(2 + (CCLConfig.INSTANCE.enableSkillScaling.get() ? getSkillLevel(Skill.Strength) * CCLConfig.INSTANCE.packerCapacityPerStrength.get() : 0), 9);

                            for (int s = 0; s < inv.getSlots(); s++) {
                                if (toPack.size() >= maxCapacity) break;

                                ItemStack check = inv.extractItem(s, 64, true);
                                if (!check.isEmpty()) {
                                    if (isPackage(check)) {
                                        if (!toPack.isEmpty()) break;

                                        ItemStack extracted = inv.extractItem(s, 64, false);
                                        holdingItems.add(extracted);
                                        job.getCitizen().getEntity().ifPresent(entity -> entity.setItemInHand(InteractionHand.MAIN_HAND, extracted.copy()));
                                        state = State.TO_DEPOT_EXPORT;
                                        return;
                                    } else {
                                        toPack.add(inv.extractItem(s, 64, false));
                                    }
                                }
                            }

                            if (!toPack.isEmpty()) {
                                ItemStack pkg = pack(toPack, targetAddress);
                                if (!pkg.isEmpty()) {
                                    holdingItems.add(pkg);
                                } else {
                                    holdingItems.addAll(toPack);
                                }

                                job.getCitizen().getEntity().ifPresent(entity -> entity.setItemInHand(InteractionHand.MAIN_HAND, holdingItems.getFirst().copy()));
                                state = State.TO_DEPOT_EXPORT;
                                return;
                            }
                        }
                    }
                }

                delay = 100;
                state = State.IDLE;
                break;

            case TO_DEPOT_EXPORT:
                if (job.getWorkBuilding() != null) {
                    currentTarget = job.getWorkBuilding().getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT_EXPORT;
                        delay = 20;
                    }
                }
                break;

            case AT_DEPOT_EXPORT:
                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE && depotBE.getExportInventory() != null && !holdingItems.isEmpty()) {
                        List<ItemStack> remainingList = new ArrayList<>();
                        for (ItemStack stack : holdingItems) {
                            ItemStack remaining = ItemHandlerHelper.insertItemStacked(depotBE.getExportInventory(), stack, false);
                            int packagesSent = stack.getCount() - remaining.getCount();
                            if (packagesSent > 0) depotBE.addOutgoingLog("OUT;Exported Package x" + packagesSent, 100);
                            if (!remaining.isEmpty()) remainingList.add(remaining);
                        }
                        holdingItems = remainingList;
                        if (holdingItems.isEmpty()) {
                            clearHand();
                        }
                    }
                }

                if (!holdingItems.isEmpty()) {
                    delay = 60;
                } else {
                    if (activeRole == PackerAgentJob.PackerRole.GENERAL_DUTY) {
                        state = State.IDLE;
                    } else {
                        state = State.TO_DEPOT_EXCESS;
                    }
                }
                break;
        }
    }

    // =====================
    // ROLE: UNLOADING TRAIN
    // =====================
    private void tickUnloadTrain() {
        switch (state) {
            case IDLE:
            case TO_DEPOT_IMPORT: case TO_DEPOT_EXCESS: case TO_DEPOT_PICKUP:
                state = State.TO_TRAIN_UNLOAD;
                break;

            case TO_TRAIN_UNLOAD:
                CarriageContraptionEntity parkedTrain = findParkedTrain();
                if (parkedTrain != null) {
                    currentTarget = findSafePlatformSpot(parkedTrain);
                    if (currentTarget != null) {
                        moveTo(currentTarget);
                        if (isAt(currentTarget)) {
                            state = State.AT_TRAIN_UNLOAD;
                            delay = 20;
                        }
                    } else {
                        delay = 40;
                    }
                } else {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[PackerAI] No train seen. Retreating to Depot.");
                    state = State.TO_DEPOT_DROP;
                    delay = 100;
                }
                break;

            case AT_TRAIN_UNLOAD:
                if (checkHolidayEvents()) return;
                CarriageContraptionEntity trainToUnload = findParkedTrain();
                if (trainToUnload == null) {
                    state = State.TO_DEPOT_DROP;
                    delay = 40;
                    break;
                }

                IItemHandler trainInv = getContraptionInventory(trainToUnload);
                if (trainInv == null) {
                    state = State.TO_DEPOT_DROP;
                    delay = 40;
                    break;
                }

                String myColonyName = "";
                if (job.getWorkBuilding() != null && job.getColony().getWorld() != null) {
                    BlockEntity mainBE = job.getColony().getWorld().getBlockEntity(job.getWorkBuilding().getPosition());
                    if (mainBE instanceof FreightDepotBlockEntity fbe) {
                        myColonyName = fbe.getColonyName();
                    }
                }

                boolean foundSomething = false;
                for (int i = 0; i < trainInv.getSlots(); i++) {
                    ItemStack check = trainInv.extractItem(i, 64, true);
                    if (!check.isEmpty() && isPackage(check)) {
                        String packageAddress = getPackageAddress(check);

                        if (packageAddress != null && packageAddress.equalsIgnoreCase(myColonyName)) {
                            holdingItems.add(trainInv.extractItem(i, 64, false));
                            foundSomething = true;
                            state = State.TO_DEPOT_DROP;
                            job.getCitizen().getEntity().ifPresent(entity -> {
                                entity.getLookControl().setLookAt(trainToUnload, 30.0F, 30.0F);
                                entity.swing(InteractionHand.MAIN_HAND);
                            });
                            break;
                        }
                        else if (packageAddress != null && packageAddress.toLowerCase().startsWith(myColonyName.toLowerCase() + "-express;")) {
                            String[] parts = packageAddress.split(";");
                            if (parts.length > 1) {
                                String[] coords = parts[1].split(",");
                                if (coords.length == 3) {
                                    try {
                                        int x = Integer.parseInt(coords[0]);
                                        int y = Integer.parseInt(coords[1]);
                                        int z = Integer.parseInt(coords[2]);
                                        this.currentTarget = new BlockPos(x, y, z);

                                        holdingItems.add(trainInv.extractItem(i, 64, false));
                                        foundSomething = true;
                                        state = State.TO_EXPRESS_DELIVERY;

                                        job.getCitizen().getEntity().ifPresent(entity -> {
                                            entity.getLookControl().setLookAt(trainToUnload, 30.0F, 30.0F);
                                            entity.swing(InteractionHand.MAIN_HAND);
                                        });
                                        if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.info("[PackerAI] Rushing EXPRESS package directly to: " + currentTarget);
                                        break;
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                    }
                }

                if (!foundSomething) {
                    state = State.TO_DEPOT_DROP;
                    delay = 100;
                }
                break;

            case TO_EXPRESS_DELIVERY:
                if (currentTarget != null) {
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_EXPRESS_DELIVERY;
                        delay = 20;
                    }
                } else {
                    state = State.TO_DEPOT_DROP;
                }
                break;

            case AT_EXPRESS_DELIVERY:
                if (checkHolidayEvents()) return;
                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be != null) {
                        IItemHandler handler = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
                        if (handler != null) {
                            List<ItemStack> remainingList = new ArrayList<>();
                            for (ItemStack stack : holdingItems) {
                                if (isPackage(stack)) {
                                    for (ItemStack unpackedItem : unpack(stack)) {
                                        ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, unpackedItem, false);
                                        int amountInserted = unpackedItem.getCount() - remaining.getCount();
                                        if (amountInserted > 0) updateTracker(unpackedItem.getHoverName().getString(), amountInserted, FreightTrackerModule.TrackStatus.COMPLETED);
                                        if (!remaining.isEmpty()) remainingList.add(remaining);
                                    }
                                }
                            }
                            holdingItems = remainingList;
                        }
                    }
                }

                if (holdingItems.isEmpty()) {
                    clearHand();
                    state = State.TO_TRAIN_UNLOAD;
                } else {
                    if (CCLConfig.INSTANCE.shouldDebug(CCLConfig.DebugLevel.CITIZENS)) LOGGER.warn("[PackerAI] Express target inventory is full. Re-routing back to Depot.");
                    state = State.TO_DEPOT_DROP;
                }
                break;

            case TO_DEPOT_DROP:
                if (job.getWorkBuilding() != null) {
                    currentTarget = job.getWorkBuilding().getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT_DROP;
                        delay = 10;
                    }
                }
                break;

            case AT_DEPOT_DROP:
                boolean wasActuallyUnloading = !holdingItems.isEmpty();

                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE && depotBE.getImportInventory() != null) {
                        List<ItemStack> remainingList = new ArrayList<>();
                        for (ItemStack stack : holdingItems) {
                            ItemStack remaining = ItemHandlerHelper.insertItemStacked(depotBE.getImportInventory(), stack, false);
                            int packagesDropped = stack.getCount() - remaining.getCount();
                            if (packagesDropped > 0) depotBE.addIncomingLog("IN;Train Unloaded Box", 100);
                            if (!remaining.isEmpty()) remainingList.add(remaining);
                        }
                        holdingItems = remainingList;
                        if (holdingItems.isEmpty()) {
                            clearHand();
                        }
                    }
                }

                if (wasActuallyUnloading) {
                    if (holdingItems.isEmpty()) {
                        state = State.TO_TRAIN_UNLOAD;
                    } else {
                        delay = 60;
                    }
                } else {
                    state = State.IDLE;
                }
                break;
        }
    }

    // ===================
    // ROLE: LOADING TRAIN
    // ===================
    private void tickLoadTrain() {
        switch (state) {
            case IDLE:
            case TO_DEPOT_IMPORT: case TO_DEPOT_EXCESS: case TO_TRAIN_UNLOAD:
                state = State.TO_DEPOT_PICKUP;
                break;

            case TO_DEPOT_PICKUP:
                if (job.getWorkBuilding() != null) {
                    currentTarget = job.getWorkBuilding().getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT_PICKUP;
                        delay = 10;
                    }
                }
                break;

            case AT_DEPOT_PICKUP:
                if (checkHolidayEvents()) return;
                if (!holdingItems.isEmpty()) {
                    state = State.TO_TRAIN_LOAD;
                    return;
                }

                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE && depotBE.getExportInventory() != null) {
                        IItemHandler exportInv = depotBE.getExportInventory();

                        for (int i = 0; i < exportInv.getSlots(); i++) {
                            ItemStack check = exportInv.extractItem(i, 64, true);
                            if (!check.isEmpty() && isPackage(check)) {
                                ItemStack extracted = exportInv.extractItem(i, 64, false);
                                holdingItems.add(extracted);
                                state = State.TO_TRAIN_LOAD;
                                return;
                            }
                        }
                    }
                }
                delay = 40;
                break;

            case TO_TRAIN_LOAD:
                CarriageContraptionEntity trainToLoad = findParkedTrain();
                if (trainToLoad != null) {
                    currentTarget = findSafePlatformSpot(trainToLoad);
                    if (currentTarget != null) {
                        moveTo(currentTarget);
                        if (isAt(currentTarget)) {
                            state = State.AT_TRAIN_LOAD;
                            delay = 20;
                        }
                    }
                } else {
                    state = State.TO_DEPOT_PICKUP;
                }
                break;

            case AT_TRAIN_LOAD:
                CarriageContraptionEntity parkedLoadTrain = findParkedTrain();
                IItemHandler loadTrainInv = getContraptionInventory(parkedLoadTrain);

                if (parkedLoadTrain != null && loadTrainInv != null) {
                    job.getCitizen().getEntity().ifPresent(entity -> {
                        entity.getLookControl().setLookAt(parkedLoadTrain, 30.0F, 30.0F);
                        entity.swing(InteractionHand.MAIN_HAND);
                    });

                    List<ItemStack> remainingList = new ArrayList<>();
                    for (ItemStack stack : holdingItems) {
                        ItemStack remaining = ItemHandlerHelper.insertItemStacked(loadTrainInv, stack, false);
                        if (!remaining.isEmpty()) remainingList.add(remaining);
                    }
                    holdingItems = remainingList;
                    if (holdingItems.isEmpty()) {
                        clearHand();
                    }
                }
                state = State.TO_DEPOT_PICKUP;
                break;
        }
    }

    private void tickIdle() {
        if (job.getWorkBuilding() != null) {
            if (delay <= 0) {
                moveTo(job.getWorkBuilding().getPosition().offset(job.getColony().getWorld().random.nextInt(5) - 2, 0, job.getColony().getWorld().random.nextInt(5) - 2));
                delay = 100;
            }
        }
    }

    // ====================
    // TRAIN HELPER METHODS
    // ====================

    private CarriageContraptionEntity findParkedTrain() {
        if (job.getColony().getWorld() == null || job.getWorkBuilding() == null) return null;

        AABB searchArea = new AABB(job.getWorkBuilding().getPosition()).inflate(10.0);
        List<CarriageContraptionEntity> carriages = job.getColony().getWorld().getEntitiesOfClass(CarriageContraptionEntity.class, searchArea);

        CarriageContraptionEntity bestCarriage = null;
        int maxSlots = 0;

        for (CarriageContraptionEntity carriage : carriages) {
            if (carriage.getDeltaMovement().lengthSqr() < 0.01) {
                IItemHandler inv = getContraptionInventory(carriage);
                if (inv != null && inv.getSlots() > maxSlots) {
                    maxSlots = inv.getSlots();
                    bestCarriage = carriage;
                }
            }
        }
        return bestCarriage;
    }

    private IItemHandler getContraptionInventory(CarriageContraptionEntity train) {
        if (train == null || train.getContraption() == null) return null;

        try {
            var manager = train.getContraption().getStorage();

            if (manager != null) {
                return ((com.ogtenzohd.cclogistics.mixin.ContraptionAccessor) manager).cclogistics$getItems();
            }
        } catch (Exception e) {
            LOGGER.error("[CCL-Train] Failed to access train inventory via Mixin Accessor", e);
        }

        return null;
    }

    private BlockPos findSafePlatformSpot(CarriageContraptionEntity train) {
        if (!job.getCitizen().getEntity().isPresent() || job.getWorkBuilding() == null) return null;

        net.minecraft.world.entity.LivingEntity entity = job.getCitizen().getEntity().get();
        AABB trainBox = train.getBoundingBox();
        AABB interactBox = trainBox.inflate(2.0, 1.0, 2.0);

        List<BlockPos> validSpots = new ArrayList<>();
        java.util.Set<BlockPos> platforms = null;
        if (job.getWorkBuilding() instanceof FreightDepotBuilding depot) {
            platforms = depot.platformPositions;
        }

        if (platforms != null && !platforms.isEmpty()) {
            for (BlockPos pos : platforms) {
                if (interactBox.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                    validSpots.add(pos.above());
                }
            }
            if (!validSpots.isEmpty()) {
                return validSpots.get(job.getColony().getWorld().random.nextInt(validSpots.size()));
            }
        }

        net.minecraft.world.level.Level level = job.getColony().getWorld();

        AABB dangerZone = trainBox.inflate(1.5, 1.0, 1.5);
        AABB searchZone = trainBox.inflate(3.5, 2.0, 3.5);

        List<BlockPos> dynamicSpots = new ArrayList<>();
        BlockPos workerPos = entity.blockPosition();
        BlockPos depotPos = job.getWorkBuilding().getPosition();

        net.minecraft.world.phys.Vec3 trainCenter = trainBox.getCenter();
        net.minecraft.world.phys.Vec3 toDepot = new net.minecraft.world.phys.Vec3(depotPos.getX() - trainCenter.x, 0, depotPos.getZ() - trainCenter.z).normalize();

        for (int x = (int)Math.floor(searchZone.minX); x <= Math.ceil(searchZone.maxX); x++) {
            for (int y = (int)Math.floor(searchZone.minY); y <= Math.ceil(searchZone.maxY); y++) {
                for (int z = (int)Math.floor(searchZone.minZ); z <= Math.ceil(searchZone.maxZ); z++) {

                    if (!dangerZone.contains(x + 0.5, y + 0.5, z + 0.5)) {
                        BlockPos checkPos = new BlockPos(x, y, z);
                        net.minecraft.world.phys.Vec3 toSpot = new net.minecraft.world.phys.Vec3(checkPos.getX() + 0.5 - trainCenter.x, 0, checkPos.getZ() + 0.5 - trainCenter.z).normalize();

                        if (toDepot.dot(toSpot) >= 0) {
                            if (level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty() &&
                                    !level.getBlockState(checkPos.below()).getCollisionShape(level, checkPos.below()).isEmpty()) {
                                dynamicSpots.add(checkPos);
                            }
                        }
                    }
                }
            }
        }

        if (!dynamicSpots.isEmpty()) {
            dynamicSpots.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(workerPos)));
            int poolSize = Math.min(5, dynamicSpots.size());
            return dynamicSpots.get(job.getColony().getWorld().random.nextInt(poolSize));
        }
        return job.getWorkBuilding().getPosition();
    }

    // =================================================================
    // DATA & UTILS -- Mod compatability, box logic, and colonist memory
    // =================================================================
    private void clearHand() {
        job.getCitizen().getEntity().ifPresent(entity -> entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
    }

    private boolean isPackage(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String namespace = id.getNamespace();
            String path = id.getPath().toLowerCase();
            if (namespace.equals("ccomunityboxes")) return true;
            if (namespace.equals("create") && (path.contains("package") || path.contains("box"))) return true;
        }
        String hover = stack.getHoverName().getString().toLowerCase();
        if (hover.contains("cardboard package") || hover.equals("package") || hover.contains("create:package") || hover.contains("box")) return true;
        try {
            Tag tag = stack.save(job.getColony().getWorld().registryAccess());
            if (tag instanceof CompoundTag root && root.contains("components") && root.getCompound("components").contains("create:package_contents")) return true;
        } catch (Exception e) {}
        return false;
    }

    private String getPackageAddress(ItemStack stack) {
        if (stack.isEmpty()) return "";
        try {
            HolderLookup.Provider registryAccess = job.getColony().getWorld().registryAccess();
            Tag tag = stack.save(registryAccess);
            if (tag instanceof CompoundTag root && root.contains("components")) {
                CompoundTag comps = root.getCompound("components");
                if (comps.contains("create:package_address")) {
                    return comps.getString("create:package_address").replace("\"", "");
                }
            }

            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag nbt = customData.copyTag();
                if (nbt.contains("create:package_address")) {
                    return nbt.getString("create:package_address").replace("\"", "");
                }
                if (nbt.contains("Address")) {
                    return nbt.getString("Address").replace("\"", "");
                }
            }
        } catch (Exception e) {}
        return "";
    }

    private List<ItemStack> unpack(ItemStack stack) {
        List<ItemStack> contents = new ArrayList<>();
        HolderLookup.Provider registryAccess = job.getColony().getWorld().registryAccess();
        try {
            Tag tag = stack.save(registryAccess);
            if (tag instanceof CompoundTag root && root.contains("components")) {
                CompoundTag comps = root.getCompound("components");
                if (comps.contains("create:package_contents")) {
                    ListTag itemList = comps.getList("create:package_contents", Tag.TAG_COMPOUND);
                    for (int i = 0; i < itemList.size(); i++) {
                        CompoundTag entry = itemList.getCompound(i);
                        if (entry.contains("item")) {
                            ItemStack parsed = ItemStack.parse(registryAccess, entry.getCompound("item")).orElse(ItemStack.EMPTY);
                            if (!parsed.isEmpty()) contents.add(parsed);
                        }
                    }
                }
            }
        } catch (Exception e) {}

        if (contents.isEmpty()) {
            net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("Items")) {
                    ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        ItemStack parsed = ItemStack.parse(registryAccess, list.getCompound(i)).orElse(ItemStack.EMPTY);
                        if (!parsed.isEmpty()) contents.add(parsed);
                    }
                } else if (tag.contains("Contents")) {
                    ListTag list = tag.getList("Contents", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        ItemStack parsed = ItemStack.parse(registryAccess, list.getCompound(i)).orElse(ItemStack.EMPTY);
                        if (!parsed.isEmpty()) contents.add(parsed);
                    }
                }
            }
        }
        return contents;
    }

    private ItemStack pack(List<ItemStack> contents, String targetAddress) {
        HolderLookup.Provider registryAccess = job.getColony().getWorld().registryAccess();
        ItemStack pkg = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("create:cardboard_package_12x10")));
        if (pkg.isEmpty() || pkg.getItem() == Items.AIR) pkg = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("create:package")));
        if (pkg.isEmpty() || pkg.getItem() == Items.AIR) return ItemStack.EMPTY;

        Tag rawTag = pkg.save(registryAccess);
        if (!(rawTag instanceof CompoundTag root)) return ItemStack.EMPTY;
        CompoundTag components = root.contains("components") ? root.getCompound("components") : new CompoundTag();
        root.put("components", components);

        ListTag contentsList = new ListTag();
        for (int i = 0; i < contents.size(); i++) {
            CompoundTag entry = new CompoundTag();
            entry.put("item", contents.get(i).save(registryAccess));
            entry.putInt("slot", i);
            contentsList.add(entry);
        }
        components.put("create:package_contents", contentsList);
        if (targetAddress != null && !targetAddress.isEmpty()) components.putString("create:package_address", targetAddress);

        return ItemStack.parse(registryAccess, root).orElse(ItemStack.EMPTY);
    }

    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("State", state.ordinal());
        tag.putInt("Delay", delay);
        if (currentTarget != null) tag.put("Target", NbtUtils.writeBlockPos(currentTarget));
        ListTag list = new ListTag();
        for (ItemStack s : holdingItems) list.add(s.save(provider));
        tag.put("HoldingItems", list);
        if (activeRole != null) tag.putInt("ActiveRole", activeRole.ordinal());
    }

    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("State")) state = State.values()[tag.getInt("State")];
        if (tag.contains("Delay")) delay = tag.getInt("Delay");
        if (tag.contains("Target")) currentTarget = NbtUtils.readBlockPos(tag, "Target").orElse(null);
        if (tag.contains("HoldingItems")) {
            holdingItems.clear();
            ListTag list = tag.getList("HoldingItems", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) holdingItems.add(ItemStack.parse(provider, list.getCompound(i)).orElse(ItemStack.EMPTY));
        }

        if (tag.contains("ActiveRole")) activeRole = PackerAgentJob.PackerRole.values()[tag.getInt("ActiveRole")];
    }

    private void moveTo(BlockPos pos) {
        job.getCitizen().getEntity().ifPresent(entity -> entity.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0 + (CCLConfig.INSTANCE.enableSkillScaling.get() ? getSkillLevel(Skill.Athletics) * CCLConfig.INSTANCE.speedBoostPerAthletics.get() : 0)));
    }

    private boolean isAt(BlockPos pos) {
        return job.getCitizen().getEntity().map(e -> e.blockPosition().closerThan(pos, 3.0)).orElse(false);
    }
}