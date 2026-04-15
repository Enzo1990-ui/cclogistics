package com.ogtenzohd.cclogistics.network;

import com.ogtenzohd.cclogistics.registration.CCLRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestPortableTrackerPacket(BlockPos depotPos) implements CustomPacketPayload {

    public static final Type<RequestPortableTrackerPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cclogistics", "request_portable_tracker"));

    public static final StreamCodec<FriendlyByteBuf, RequestPortableTrackerPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestPortableTrackerPacket::depotPos,
            RequestPortableTrackerPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final RequestPortableTrackerPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack clipboard = new ItemStack(CCLRegistration.PORTABLE_TRACKER_ITEM.get());

                CustomData.update(DataComponents.CUSTOM_DATA, clipboard, tag -> {
                    tag.put("LinkedDepot", NbtUtils.writeBlockPos(payload.depotPos()));
                });
                if (!player.getInventory().add(clipboard)) {
                    player.drop(clipboard, false);
                }
            }
        });
    }
}