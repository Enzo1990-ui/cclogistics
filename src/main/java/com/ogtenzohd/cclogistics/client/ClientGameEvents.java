package com.ogtenzohd.cclogistics.client;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = CreateColonyLogistics.MODID, value = Dist.CLIENT)
public class ClientGameEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.isPaused()) return;
        if (level.getGameTime() % 4 != 0) return;
        if (player.getMainHandItem().is(CCLRegistration.TRACK_CLEARANCE_ITEM.get()) ||
                player.getOffhandItem().is(CCLRegistration.TRACK_CLEARANCE_ITEM.get())) {

            BlockPos playerPos = player.blockPosition();
            int radius = 12;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos checkPos = playerPos.offset(x, y, z);
                        if (level.getBlockState(checkPos).is(CCLRegistration.TRACK_CLEARANCE_BLOCK.get())) {
                            level.addParticle(
                                    new BlockParticleOption(ParticleTypes.BLOCK_MARKER, Blocks.BARRIER.defaultBlockState()),
                                    checkPos.getX() + 0.5D,
                                    checkPos.getY() + 0.5D,
                                    checkPos.getZ() + 0.5D,
                                    0.0D, 0.0D, 0.0D
                            );
                        }
                    }
                }
            }
        }
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        String hover = stack.getHoverName().getString().toLowerCase();
        if (hover.contains("package") || hover.contains("box")) {
            try {
                net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (customData != null) {
                    CompoundTag tag = customData.copyTag();
                    if (tag.contains("cclogistics:tracking_id")) {
                        String fullId = tag.getString("cclogistics:tracking_id");
                        String shortId = fullId.length() > 6 ? fullId.substring(fullId.length() - 6).toUpperCase() : fullId.toUpperCase();
                        event.getToolTip().add(Component.empty());
                        event.getToolTip().add(Component.literal("⬛ CCL Shipping Label").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
                        event.getToolTip().add(Component.literal(" Tracking ID: *" + shortId).withStyle(ChatFormatting.GRAY));
                        event.getToolTip().add(Component.literal(" Status: Logged in Network").withStyle(ChatFormatting.DARK_GREEN));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
