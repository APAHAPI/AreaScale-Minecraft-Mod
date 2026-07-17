package com.areascale.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.areascale.structure.StructureData;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Single-level, per-player undo of the last /areascale expand|shrink capture. Creative-mode only. */
public final class UndoManager {
    public record Entry(UUID structureId, StructureData data, BlockPos min, ResourceKey<Level> dimension) {
    }

    private static final Map<UUID, Entry> LAST_CAPTURE = new ConcurrentHashMap<>();

    private UndoManager() {
    }

    public static void record(ServerPlayer player, UUID structureId, StructureData data, BlockPos min) {
        LAST_CAPTURE.put(player.getUUID(), new Entry(structureId, data, min, player.level().dimension()));
    }

    public static Optional<Entry> get(ServerPlayer player) {
        return Optional.ofNullable(LAST_CAPTURE.get(player.getUUID()));
    }

    public static void clear(ServerPlayer player) {
        LAST_CAPTURE.remove(player.getUUID());
    }
}
