package com.areascale.network;

import com.areascale.AreaScaleMod;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent server -> client whenever the player's selection is cleared (explicit clear, or after a successful capture), so the client drops its rendered outline box. */
public record ClearSelectionPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClearSelectionPayload> TYPE =
        new CustomPacketPayload.Type<>(AreaScaleMod.id("clear_selection"));

    public static final StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ClearSelectionPayload> STREAM_CODEC =
        StreamCodec.unit(new ClearSelectionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
