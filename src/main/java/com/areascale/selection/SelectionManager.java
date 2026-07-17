package com.areascale.selection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.areascale.network.ClearSelectionPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Server-authoritative store of each player's current two selection corners. */
public final class SelectionManager {
    private static final Map<UUID, PlayerSelection> SELECTIONS = new ConcurrentHashMap<>();

    private SelectionManager() {
    }

    public static PlayerSelection get(ServerPlayer player) {
        return SELECTIONS.computeIfAbsent(player.getUUID(), id -> new PlayerSelection());
    }

    /** Clears the selection and tells the player's client to drop its rendered outline box. */
    public static void clearAndNotify(ServerPlayer player) {
        get(player).clear();
        ServerPlayNetworking.send(player, new ClearSelectionPayload());
    }

    public static void setPoint(ServerPlayer player, int point, BlockPos pos) {
        PlayerSelection selection = get(player);
        ResourceKey<Level> dimension = player.level().dimension();

        if (point == 1) {
            selection.setPos1(pos, dimension);
            player.displayClientMessage(
                Component.translatable("areascale.selection.pos1", pos.getX(), pos.getY(), pos.getZ()), true);
        } else {
            selection.setPos2(pos, dimension);
            player.displayClientMessage(
                Component.translatable("areascale.selection.pos2", pos.getX(), pos.getY(), pos.getZ()), true);
        }
    }
}
