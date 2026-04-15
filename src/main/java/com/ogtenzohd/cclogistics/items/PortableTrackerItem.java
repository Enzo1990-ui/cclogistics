package com.ogtenzohd.cclogistics.items;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.ogtenzohd.cclogistics.colony.buildings.gui.PortableTrackerWindow;
import com.ogtenzohd.cclogistics.colony.buildings.moduleviews.FreightTrackerModuleView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class PortableTrackerItem extends Item {

    public PortableTrackerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains("LinkedDepot")) {
                BlockPos depotPos = NbtUtils.readBlockPos(customData.copyTag(), "LinkedDepot").orElse(null);

                if (depotPos != null) {
                    IBuildingView buildingView = IColonyManager.getInstance().getBuildingView(level.dimension(), depotPos);

                    if (buildingView != null) {
                        List<FreightTrackerModuleView> views = buildingView.getModuleViews(FreightTrackerModuleView.class);

                        if (views != null && !views.isEmpty()) {
                            FreightTrackerModuleView view = views.get(0);

                            new PortableTrackerWindow(view).open();
                            return InteractionResultHolder.success(stack);
                        }
                    }
                    player.displayClientMessage(Component.literal("§cCould not connect to Freight Depot. Are you too far away?"), true);
                }
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("LinkedDepot")) {
            tooltipComponents.add(Component.literal("§7Right-Click to view Active Manifest"));
            tooltipComponents.add(Component.literal("§8Linked to Freight Depot"));
        } else {
            tooltipComponents.add(Component.literal("§cUnlinked Clipboard"));
        }
    }
}