package com.ogtenzohd.cclogistics.items;

import com.ogtenzohd.cclogistics.blocks.custom.freight_depot.FreightDepotBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.logistics_controller.LogisticsControllerBlockEntity;
import com.ogtenzohd.cclogistics.blocks.custom.scanner.FreightScannerBlockEntity;
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

        if (be instanceof FreightDepotBlockEntity depot) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.put("LinkedDepot", NbtUtils.writeBlockPos(pos));
            });
            player.displayClientMessage(Component.literal("§aCaptured Freight Depot Position: " + pos.toShortString()), true);

            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains("LinkedTicker")) {
                BlockPos tickerPos = NbtUtils.readBlockPos(customData.copyTag(), "LinkedTicker").orElse(null);
                if (tickerPos != null) {
                    depot.setTickerLink(tickerPos);
                    player.displayClientMessage(Component.literal("§aLinked Depot to Ticker at " + tickerPos.toShortString()), true);
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (be instanceof LogisticsControllerBlockEntity controller) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains("LinkedTicker")) {
                BlockPos tickerPos = NbtUtils.readBlockPos(customData.copyTag(), "LinkedTicker").orElse(null);
                if (tickerPos != null) {
                    controller.setTickerLink(tickerPos);
                    player.displayClientMessage(Component.literal("§aLinked Controller to Ticker at " + tickerPos.toShortString()), true);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        if (be instanceof FreightScannerBlockEntity scanner) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains("LinkedDepot")) {
                BlockPos depotPos = NbtUtils.readBlockPos(customData.copyTag(), "LinkedDepot").orElse(null);
                if (depotPos != null) {
                    scanner.setLinkedDepot(depotPos);
                    player.displayClientMessage(Component.literal("§aLinked Scanner to Freight Depot at " + depotPos.toShortString()), true);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        if (be instanceof com.ogtenzohd.cclogistics.blocks.custom.funnel.LogisticsFunnelBlockEntity funnel) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains("LinkedDepot")) {
                BlockPos depotPos = NbtUtils.readBlockPos(customData.copyTag(), "LinkedDepot").orElse(null);
                if (depotPos != null) {
                    funnel.setLinkedDepot(depotPos);
                    player.displayClientMessage(Component.literal("§aLinked Funnel to Freight Depot at " + depotPos.toShortString()), true);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        boolean hasData = false;

        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("LinkedTicker")) {
                BlockPos pos = NbtUtils.readBlockPos(tag, "LinkedTicker").orElse(null);
                if (pos != null) {
                    tooltipComponents.add(Component.literal("§7Stored Ticker: §e" + pos.toShortString()));
                    hasData = true;
                }
            }
            if (tag.contains("LinkedDepot")) {
                BlockPos pos = NbtUtils.readBlockPos(tag, "LinkedDepot").orElse(null);
                if (pos != null) {
                    tooltipComponents.add(Component.literal("§7Stored Depot: §b" + pos.toShortString()));
                    hasData = true;
                }
            }
        }

        if (!hasData) {
            tooltipComponents.add(Component.literal("§71. Right-click a §6Stock Ticker§7 or §bDepot§7 to bind"));
            tooltipComponents.add(Component.literal("§72. Right-click a §bScanner§7 to link"));
        }
    }
}