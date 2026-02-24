package com.ogtenzohd.cclogistics.colony.ai;

import com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic;
import com.minecolonies.api.entity.citizen.Skill;
import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.colony.buildings.ForemenHutBuilding;
import com.ogtenzohd.cclogistics.colony.job.FreightInspectorJob;
import com.ogtenzohd.cclogistics.config.CCLConfig;
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
    
    private int getSkillLevel(Skill skill) {
        if (job.getCitizen() == null || job.getCitizen().getCitizenSkillHandler() == null) return 1;
        return job.getCitizen().getCitizenSkillHandler().getLevel(skill);
    }

    private String applySpellingMistakes(String logLine, int intelligence) {
        if (!CCLConfig.INSTANCE.enableSkillScaling.get()) return logLine;
        if (intelligence >= 20) return logLine;

        int baseChance = CCLConfig.INSTANCE.baseSpellingMistakeChance.get();
        if (baseChance == 0) return logLine;

        net.minecraft.util.RandomSource rand = job.getColony().getWorld().random;
        
        return com.ogtenzohd.cclogistics.util.TypoGenerator.generateSpellingMistake(logLine, intelligence, baseChance, rand);
    }

    @Override
    public Class<ForemenHutBuilding> getExpectedBuildingClass() {
        return ForemenHutBuilding.class;
    }

    public void tick() {
        if (delay > 0) {
            if (currentTarget != null && (state == State.AT_DEPOT || state == State.AT_HUT)) {
                boolean isNear = job.getCitizen().getEntity().map(e -> e.blockPosition().closerThan(currentTarget, 5.0)).orElse(false);
                if (!isNear) {
                    delay = 0;
                    state = State.IDLE;
                    setHoldingClipboard(false);
                    return;
                }
            }

            delay--;
            
            if (state == State.AT_DEPOT && delay % 20 == 0) {
                playScribbleSound();
            }
            
            if (state == State.AT_HUT) {
                if (delay == 120 || delay == 70 || delay == 20) {
                    setHoldingClipboard(false);
                    playPageFlipSound();
                } 
                else if (delay == 100 || delay == 50) {
                    setHoldingClipboard(true);
                }
            }
            
            return;
        }

        switch (state) {
            case IDLE:
                setHoldingClipboard(false); 
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
                        setHoldingClipboard(true); 
                    }
                } else {
                    delay = 200;
                }
                break;

            case AT_DEPOT:
                int intel = getSkillLevel(Skill.Intelligence);
                
                if (currentTarget != null && job.getColony().getWorld() != null) {
                    BlockEntity be = job.getColony().getWorld().getBlockEntity(currentTarget);
                    if (be instanceof FreightDepotBlockEntity depotBE) {
                        
                        // Protect Incoming Logs
                        List<String> in = depotBE.collectIncomingLogs();
                        for (String s : in) {
                            String[] parts = s.split(";", 2);
                            if (parts.length == 2) {
                                // Re-attach prefix after scrambling the message!
                                bufferedIncoming.add(parts[0] + ";" + applySpellingMistakes(parts[1], intel));
                            } else {
                                bufferedIncoming.add(applySpellingMistakes(s, intel));
                            }
                        }
                        
                        // Protect Outgoing Logs
                        List<String> out = depotBE.collectOutgoingLogs();
                        for (String s : out) {
                            String[] parts = s.split(";", 2);
                            if (parts.length == 2) {
                                bufferedOutgoing.add(parts[0] + ";" + applySpellingMistakes(parts[1], intel));
                            } else {
                                bufferedOutgoing.add(applySpellingMistakes(s, intel));
                            }
                        }
                    }
                }
                
                // Calculate typing speed based on config and intelligence
                int baseDelay = 80;
                if (CCLConfig.INSTANCE.enableSkillScaling.get()) {
                    int reduction = intel * CCLConfig.INSTANCE.cooldownReductionPerIntel.get();
                    delay = Math.max(10, baseDelay - reduction);
                } else {
                    delay = baseDelay;
                }
                
                state = State.TO_HUT;
                break;

            case TO_HUT:
                com.minecolonies.api.colony.buildings.IBuilding hut = job.getWorkBuilding();
                if (hut != null) {
                    currentTarget = hut.getPosition();
                    moveTo(currentTarget);
                    if (isAt(currentTarget)) {
                        state = State.AT_HUT;
                        delay = 150; 
                        setHoldingClipboard(true); 
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
                
                setHoldingClipboard(false); 
                state = State.IDLE;
                delay = 600; 
                break;
        }
    }
    
    private ItemStack getClipboard() {
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("minecolonies:clipboard"));
        if (item == Items.AIR) {
            return new ItemStack(Items.BOOK);
        }
        return new ItemStack(item);
    }
    
    private void setHoldingClipboard(boolean holding) {
        job.getCitizen().getEntity().ifPresent(entity -> {
            if (holding) {
                entity.setItemInHand(InteractionHand.MAIN_HAND, getClipboard());
            } else {
                entity.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        });
    }

    private void playScribbleSound() {
        job.getCitizen().getEntity().ifPresent(entity -> {
            float pitch = 1.0F + (entity.level().random.nextFloat() - 0.5F) * 0.2F;
            entity.playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 0.5F, pitch);
        });
    }

    private void playPageFlipSound() {
        job.getCitizen().getEntity().ifPresent(entity -> {
            float pitch = 0.9F + (entity.level().random.nextFloat() * 0.2F);
            entity.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, pitch);
        });
    }
    
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
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

    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
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
            double baseSpeed = 1.0;
            if (CCLConfig.INSTANCE.enableSkillScaling.get()) {
                int athletics = getSkillLevel(Skill.Athletics);
                baseSpeed += athletics * CCLConfig.INSTANCE.speedBoostPerAthletics.get();
            }
            entity.getNavigation().moveTo(pos.getX(), pos.getY(), pos.getZ(), baseSpeed);
        });
    }
    
    private boolean isAt(BlockPos pos) {
        return job.getCitizen().getEntity().map(e -> e.blockPosition().closerThan(pos, 3.0)).orElse(false);
    }
}