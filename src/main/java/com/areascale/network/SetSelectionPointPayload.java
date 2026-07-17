package com.areascale.network;

import com.areascale.AreaScaleMod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent client -> server when the player clicks a block with the Selection Wand.
 * {@code point} is 1 for the first corner (left-click / attack) or 2 for the
 * second corner (right-click / use).
 */
public record SetSelectionPointPayload(int point, BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetSelectionPointPayload> TYPE =
        new CustomPacketPayload.Type<>(AreaScaleMod.id("set_selection_point"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSelectionPointPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SetSelectionPointPayload::point,
        BlockPos.STREAM_CODEC, SetSelectionPointPayload::pos,
        SetSelectionPointPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
