package com.ogtenzohd.cclogistics.colony.ai;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.ForemenHutBuilding;
import com.ogtenzohd.cclogistics.colony.job.FreightInspectorJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

public class FreightInspectorAI extends AbstractEntityAIBasic<FreightInspectorJob, ForemenHutBuilding> {

    private enum State {
        IDLE,
        TO_DEPOT,
        AT_DEPOT,
        TO_HUT,
        AT_HUT
    }
	
	private static final Logger LOGGER = LogUtils.getLogger();

    private State state = State.IDLE;
    private int delay = 0;
    private BlockPos currentTarget = null;
    private List<String> bufferedIncoming = new ArrayList<>();
    private List<String> bufferedOutgoing = new ArrayList<>();

    public FreightInspectorAI(FreightInspectorJob job) {
        super(job);
    }

    @Override
    public Class<ForemenHutBuilding> getExpectedBuildingClass() {
        return ForemenHutBuilding.class;
    }

    public void tick() {
        if (delay > 0) {
            // If they are supposed to be inspecting, but get recalled > 5 blocks away, reset them!
            if (currentTarget != null && (state == State.AT_DEPOT || state == State.AT_HUT)) {
                boolean isNear = job.getCitizen().getEntity().map(e -> e.blockPosition().closerThan(currentTarget, 5.0)).orElse(false);
                if (!isNear) {
                    delay = 0;
                    state = State.IDLE;
                    setHoldingClipboard(false);
                    return;
                }
            }
            // ---------------------------

            delay--;
            
            // -- ANIMATION SEQUENCES --
            
            // 1. At Depot: Play scribbling sound every 20 ticks
            if (state == State.AT_DEPOT && delay % 20 == 0) {
                playScribbleSound();
            }
            
            // 2. At Hut: Paging sequence (Total delay starts at 150)
            if (state == State.AT_HUT) {
                // Put away and flip page (at 120, 70, and 20 ticks remaining)
                if (delay == 120 || delay == 70 || delay == 20) {
                    setHoldingClipboard(false);
                    playPageFlipSound();
                } 
                // Pull clipboard back out to read (at 100 and 50 ticks remaining)
                else if (delay == 100 || delay == 50) {
                    setHoldingClipboard(true);
                }
            }
            
            return;
        }

        switch (state) {
            case IDLE:
                setHoldingClipboard(false); // Ensure hands are empty
                state = State.TO_DEPOT;
                break;

            case TO_DEPOT:
                com.minecolonies.api.colony.buildings.IBuilding depot = job.getColony().getServerBuildingManager().getBuildings().values().stream()
                    .filter(b -> b.getSchematicName().contains("freight_depot"))
                    .findFirst().orElse(null);

                if (depot != null) {
                    currentTarget = depot.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_DEPOT;
                        delay = 60; 
                        setHoldingClipboard(true); // Pull out clipboard to inspect depot
                    }
                } else {
                    delay = 200;
                }
                break;

            case AT_DEPOT:
                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE) {
                        List<String> in = depotBE.collectIncomingLogs();
                        List<String> out = depotBE.collectOutgoingLogs();
                        bufferedIncoming.addAll(in);
                        bufferedOutgoing.addAll(out);
                    }
                }
                
                setHoldingClipboard(false); // Put clipboard away to walk
                state = State.TO_HUT;
                break;

            case TO_HUT:
                com.minecolonies.api.colony.buildings.IBuilding hut = job.getWorkBuilding();
                if (hut != null) {
                    currentTarget = hut.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_HUT;
                        delay = 150; // Set to 150 to allow for 3 page-flip cycles
                        setHoldingClipboard(true); // Pull out clipboard to log data
                    }
                }
                break;

            case AT_HUT:
                com.minecolonies.api.colony.buildings.IBuilding hutB = job.getWorkBuilding();
                if (hutB instanceof ForemenHutBuilding foremenHut) {
                    for (String s : bufferedIncoming) foremenHut.addIncomingLog(s);
                    for (String s : bufferedOutgoing) foremenHut.addOutgoingLog(s);
                    
                    if (job.getColony().getWorld() != null) {
                        BlockEntity be = job.getColony().getWorld().getBlockEntity(hutB.getPosition());
                        if (be instanceof com.ogtenzohd.cclogistics.blocks.custom.foremens_hut.ForemenHutBlockEntity hutBE) {
                            for (String s : bufferedIncoming) hutBE.addIncomingLog(s, 100);
                            for (String s : bufferedOutgoing) hutBE.addOutgoingLog(s, 100);
                        }
                    }

                    bufferedIncoming.clear();
                    bufferedOutgoing.clear();
                }
                
                setHoldingClipboard(false); // Finished inspecting, put it away
                state = State.IDLE;
                delay = 600; 
                break;
        }
    }
    
    /**
     * Attempts to find Minecolonies' clipboard item, falling back to a vanilla book.
     */
    private ItemStack getClipboard() {
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("minecolonies:clipboard"));
        if (item == Items.AIR) {
            return new ItemStack(Items.BOOK);
        }
        return new ItemStack(item);
    }
    
    /**
     * Makes the entity visually hold or put away the clipboard.
     */
    private void setHoldingClipboard(boolean holding) {
        job.getCitizen().getEntity().ifPresent(entity -> {
            if (holding) {
                entity.setItemInHand(InteractionHand.MAIN_HAND, getClipboard());
            } else {
                entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        });
    }

    /**
     * Plays a cartographer scribbling sound.
     */
    private void playScribbleSound() {
        job.getCitizen().getEntity().ifPresent(entity -> {
            float pitch = 1.0F + (entity.level().random.nextFloat() - 0.5F) * 0.2F;
            entity.playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 0.5F, pitch);
        });
    }

    /**
     * Plays a page-flipping sound.
     */
    private void playPageFlipSound() {
        job.getCitizen().getEntity().ifPresent(entity -> {
            float pitch = 0.9F + (entity.level().random.nextFloat() * 0.2F);
            entity.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, pitch);
        });
    }
    
    public void write(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("State", state.ordinal());
        tag.putInt("Delay", delay);
        if (currentTarget != null) {
            tag.put("Target", NbtUtils.writeBlockPos(currentTarget));
        }
        
        ListTag inList = new ListTag();
        for (String s : bufferedIncoming) inList.add(StringTag.valueOf(s));
        tag.put("BufferedIncoming", inList);

        ListTag outList = new ListTag();
        for (String s : bufferedOutgoing) outList.add(StringTag.valueOf(s));
        tag.put("BufferedOutgoing", outList);
    }

    public void read(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("State")) {
            state = State.values()[tag.getInt("State")];
        }
        if (tag.contains("Delay")) {
            delay = tag.getInt("Delay");
        }
        if (tag.contains("Target")) {
            currentTarget = NbtUtils.readBlockPos(tag, "Target").orElse(null);
        }
        
        if (tag.contains("BufferedIncoming")) {
            bufferedIncoming.clear();
            ListTag list = tag.getList("BufferedIncoming", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) bufferedIncoming.add(list.getString(i));
        }

        if (tag.contains("BufferedOutgoing")) {
            bufferedOutgoing.clear();
            ListTag list = tag.getList("BufferedOutgoing", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) bufferedOutgoing.add(list.getString(i));
        }
    }
	
    private void moveTo(BlockPos pos) {
        job.getCitizen().getEntity().ifPresent(entity -> {
            entity.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), 1.0);
        });
    }
    
    private boolean isAt(BlockPos pos) {
        return job.getCitizen().getEntity().map(e -> e.blockPosition().closerThan(pos, 3.0)).orElse(false);
    }
}