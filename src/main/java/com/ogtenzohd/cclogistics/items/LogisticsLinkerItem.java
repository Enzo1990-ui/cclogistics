package com.ogtenzohd.cclogistics.items;

import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class LogisticsLinkerItem extends Item {

    public LogisticsLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var player = context.getPlayer();
        var stack = context.getItemInHand();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof StockTickerBlockEntity) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.put("LinkedTicker", NbtUtils.writeBlockPos(pos));
            });
            player.displayClientMessage(Component.literal("§aCaptured Ticker Position: " + pos.toShortString()), true);
            return InteractionResult.SUCCESS;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("LinkedTicker")) {
            CompoundTag tag = customData.copyTag();
            BlockPos tickerPos = NbtUtils.readBlockPos(tag, "LinkedTicker").orElse(null);

            if (tickerPos != null) {
                boolean success = false;
                
                if (be instanceof LogisticsControllerBlockEntity controller) {
                    controller.setTickerLink(tickerPos);
                    player.displayClientMessage(Component.literal("§aLinked Controller to Ticker at " + tickerPos.toShortString()), true);
                    success = true;
                } 
                else if (be instanceof FreightDepotBlockEntity depot) {
                    depot.setTickerLink(tickerPos);
                    player.displayClientMessage(Component.literal("§aLinked Depot to Ticker at " + tickerPos.toShortString()), true);
                    success = true;
                }
                
                if (success) return InteractionResult.SUCCESS;
            }
        }
        
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("LinkedTicker")) {
             CompoundTag tag = customData.copyTag();
             BlockPos pos = NbtUtils.readBlockPos(tag, "LinkedTicker").orElse(null);
             if (pos != null) {
                 tooltipComponents.add(Component.literal("§7Target: §e" + pos.toShortString()));
             }
        } else {
             tooltipComponents.add(Component.literal("§71. Right-click a §6Stock Ticker§7 to bind"));
             tooltipComponents.add(Component.literal("§72. Right-click a §bController/Depot§7 to link"));
        }
    }
}