package com.areascale.structure;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Holds the actual (potentially large) captured block/entity data server-side, keyed by a
 * UUID. Structure Capsule items only ever carry that UUID plus a small fixed-size summary
 * (dimensions, scale, block count) for their tooltip/icon - never the bulk data itself.
 *
 * This exists because embedding the full captured data directly in an ItemStack's NBT breaks
 * down at scale: item data gets synced to clients as part of ordinary inventory/container
 * packets, which have real size limits. A capture of 100k+ blocks produced a payload large
 * enough to fail decoding on the client ("Failed to decode packet
 * 'clientbound/minecraft:container_set_content'") and disconnect the player - this happened
 * even in singleplayer, because Minecraft's integrated server and client still communicate
 * over the same client-server protocol internally. Keeping the bulk data out of anything
 * that gets network-synced (the item, and previously the platform/placer block entities)
 * fixes this regardless of how large a capture gets.
 */
public class StructureDataStorage extends SavedData {
    public static final SavedDataType<StructureDataStorage> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("areascale", "areascale_structures"),
        StructureDataStorage::new,
        codec(),
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<UUID, StructureData> structures = new HashMap<>();

    /** Always uses the overworld's storage so captures/platforms/placers work consistently across dimensions. */
    public static StructureDataStorage get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public UUID store(StructureData data) {
        UUID id = UUID.randomUUID();
        structures.put(id, data);
        setDirty();
        return id;
    }

    public void store(UUID id, StructureData data) {
        structures.put(id, data);
        setDirty();
    }

    public Optional<StructureData> get(UUID id) {
        return Optional.ofNullable(structures.get(id));
    }

    public void remove(UUID id) {
        if (structures.remove(id) != null) {
            setDirty();
        }
    }

    private static Codec<StructureDataStorage> codec() {
        Codec<Map<String, CompoundTag>> rawCodec = Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC);
        return rawCodec.xmap(
            raw -> {
                StructureDataStorage storage = new StructureDataStorage();
                raw.forEach((key, tag) -> StructureData.read(tag)
                    .ifPresent(data -> storage.structures.put(UUID.fromString(key), data)));
                return storage;
            },
            storage -> {
                Map<String, CompoundTag> raw = new HashMap<>();
                storage.structures.forEach((id, data) -> {
                    CompoundTag tag = new CompoundTag();
                    data.write(tag);
                    raw.put(id.toString(), tag);
                });
                return raw;
            }
        );
    }
}
