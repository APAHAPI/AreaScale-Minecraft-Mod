package com.areascale.client;

import net.minecraft.core.BlockPos;

/** Client-local mirror of the selection, used purely for drawing the outline box. */
public final class ClientSelectionState {
    private static BlockPos pos1;
    private static BlockPos pos2;

    private ClientSelectionState() {
    }

    public static void setPos1(BlockPos pos) {
        pos1 = pos;
    }

    public static void setPos2(BlockPos pos) {
        pos2 = pos;
    }

    public static BlockPos getPos1() {
        return pos1;
    }

    public static BlockPos getPos2() {
        return pos2;
    }
}
