package com.ogtenzohd.cclogistics.client;

import com.ogtenzohd.cclogistics.CreateColonyLogistics;
import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

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
}