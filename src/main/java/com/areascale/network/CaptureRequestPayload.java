package com.areascale.network;

import com.areascale.AreaScaleMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Sent client -> server when the player confirms a capture from the in-game Capture Screen.
 * It carries exactly what the two command arguments would: the same factor and mode, so
 * "Shrink" + 4 is identical to typing /areascale shrink 4. This exists because commands can
 * be disabled entirely (singleplayer Hardcore), which would otherwise make the command the
 * only way to capture.
 */
public record CaptureRequestPayload(double factor, boolean expand) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CaptureRequestPayload> TYPE =
        new CustomPacketPayload.Type<>(AreaScaleMod.id("capture_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CaptureRequestPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, CaptureRequestPayload::factor,
        ByteBufCodecs.BOOL, CaptureRequestPayload::expand,
        CaptureRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
