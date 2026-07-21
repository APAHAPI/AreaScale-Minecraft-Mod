package com.areascale.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;

/**
 * A captured cuboid of blocks (plus block entity data and non-player entities),
 * stored sparsely (only non-air positions) with a deduplicated block-state
 * palette, plus the display scale the player chose. This never touches the
 * real block grid once captured, so the stored scale can be any positive
 * float - there's no rounding to whole blocks anywhere here.
 */
public record StructureData(
    int sizeX, int sizeY, int sizeZ,
    List<BlockState> palette,
    int[] blockX, int[] blockY, int[] blockZ, int[] blockPaletteIndex,
    List<CapturedBlockEntity> blockEntities,
    List<CapturedEntity> entities,
    float scale
) {
    private static final Codec<List<BlockState>> PALETTE_CODEC = BlockState.CODEC.listOf();

    /** A block entity's custom data (chest contents, spawner config, etc.), keyed by position relative to the capture's min corner. */
    public record CapturedBlockEntity(int x, int y, int z, CompoundTag data) {
    }

    /** A non-player entity (armor stand, etc.) captured inside the selection, position relative to the capture's min corner. */
    public record CapturedEntity(double relX, double relY, double relZ, Identifier entityType, CompoundTag data) {
    }

    public int blockCount() {
        return blockPaletteIndex.length;
    }

    public long sourceVolume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    public void write(CompoundTag tag) {
        tag.putInt("size_x", sizeX);
        tag.putInt("size_y", sizeY);
        tag.putInt("size_z", sizeZ);
        tag.putFloat("scale", scale);
        tag.store("palette", PALETTE_CODEC, palette);
        tag.putIntArray("block_x", blockX);
        tag.putIntArray("block_y", blockY);
        tag.putIntArray("block_z", blockZ);
        tag.putIntArray("block_palette_index", blockPaletteIndex);

        ListTag blockEntityList = new ListTag();
        for (CapturedBlockEntity be : blockEntities) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", be.x());
            entry.putInt("y", be.y());
            entry.putInt("z", be.z());
            entry.put("data", be.data());
            blockEntityList.add(entry);
        }
        tag.put("block_entities", blockEntityList);

        ListTag entityList = new ListTag();
        for (CapturedEntity captured : entities) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("x", captured.relX());
            entry.putDouble("y", captured.relY());
            entry.putDouble("z", captured.relZ());
            entry.putString("entity_type", captured.entityType().toString());
            entry.put("data", captured.data());
            entityList.add(entry);
        }
        tag.put("entities", entityList);
    }

    public static Optional<StructureData> read(CompoundTag tag) {
        List<BlockState> palette = tag.read("palette", PALETTE_CODEC).orElse(null);
        if (palette == null) {
            return Optional.empty();
        }

        int sizeX = tag.getIntOr("size_x", 0);
        int sizeY = tag.getIntOr("size_y", 0);
        int sizeZ = tag.getIntOr("size_z", 0);
        float scale = tag.getFloatOr("scale", 1.0f);
        int[] blockX = tag.getIntArray("block_x").orElse(new int[0]);
        int[] blockY = tag.getIntArray("block_y").orElse(new int[0]);
        int[] blockZ = tag.getIntArray("block_z").orElse(new int[0]);
        int[] blockPaletteIndex = tag.getIntArray("block_palette_index").orElse(new int[0]);

        List<CapturedBlockEntity> blockEntities = new ArrayList<>();
        for (CompoundTag entry : tag.getListOrEmpty("block_entities").compoundStream().toList()) {
            blockEntities.add(new CapturedBlockEntity(
                entry.getIntOr("x", 0), entry.getIntOr("y", 0), entry.getIntOr("z", 0),
                entry.getCompoundOrEmpty("data")));
        }

        List<CapturedEntity> entities = new ArrayList<>();
        for (CompoundTag entry : tag.getListOrEmpty("entities").compoundStream().toList()) {
            entities.add(new CapturedEntity(
                entry.getDoubleOr("x", 0), entry.getDoubleOr("y", 0), entry.getDoubleOr("z", 0),
                Identifier.parse(entry.getStringOr("entity_type", "minecraft:pig")),
                entry.getCompoundOrEmpty("data")));
        }

        return Optional.of(new StructureData(sizeX, sizeY, sizeZ, palette, blockX, blockY, blockZ, blockPaletteIndex,
            blockEntities, entities, scale));
    }

    /** Snapshots every non-air block, block entity, and non-player entity in [min, max] (inclusive). Does not modify the world. */
    public static StructureData capture(ServerLevel level, BlockPos min, BlockPos max, float scale) {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        IntArrayList blockX = new IntArrayList();
        IntArrayList blockY = new IntArrayList();
        IntArrayList blockZ = new IntArrayList();
        IntArrayList blockPaletteIndex = new IntArrayList();
        List<CapturedBlockEntity> blockEntities = new ArrayList<>();

        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    Integer index = paletteIndex.get(state);
                    if (index == null) {
                        index = palette.size();
                        palette.add(state);
                        paletteIndex.put(state, index);
                    }

                    blockX.add(x);
                    blockY.add(y);
                    blockZ.add(z);
                    blockPaletteIndex.add((int) index);

                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        CompoundTag data = blockEntity.saveCustomOnly(level.registryAccess());
                        blockEntities.add(new CapturedBlockEntity(x, y, z, data));
                    }
                }
            }
        }

        List<CapturedEntity> entities = new ArrayList<>();
        AABB region = new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        for (Entity entity : level.getEntities((Entity) null, region, e -> !(e instanceof Player))) {
            TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
            entity.saveWithoutId(output);
            entities.add(new CapturedEntity(
                entity.getX() - min.getX(), entity.getY() - min.getY(), entity.getZ() - min.getZ(),
                EntityType.getKey(entity.getType()),
                output.buildResult()));
        }

        return new StructureData(sizeX, sizeY, sizeZ, palette,
            blockX.toIntArray(), blockY.toIntArray(), blockZ.toIntArray(), blockPaletteIndex.toIntArray(),
            blockEntities, entities, scale);
    }

    /**
     * Clears every block in [min, min + (sizeX, sizeY, sizeZ)) to air and discards the
     * entities captured alongside it - used right after capture(). Uses
     * UPDATE_SKIP_ALL_SIDEEFFECTS so support-dependent blocks (torches, redstone dust,
     * grass, etc.) inside the selection don't notice their neighbor disappearing and
     * pop themselves off as a dropped item mid-clear (that was a real duplication bug:
     * the item ends up both in the capsule and on the ground).
     */
    public void clearSource(ServerLevel level, BlockPos min) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    level.setBlock(min.offset(x, y, z), air, flags);
                }
            }
        }

        AABB region = new AABB(min.getX(), min.getY(), min.getZ(),
            min.getX() + sizeX, min.getY() + sizeY, min.getZ() + sizeZ);
        for (Entity entity : level.getEntities((Entity) null, region, e -> !(e instanceof Player))) {
            entity.discard();
        }
    }

    public boolean canPlace(ServerLevel level, BlockPos anchor) {
        return canPlace(level, anchor, Direction.SOUTH);
    }

    /**
     * True if every world position the structure would occupy (at its original, unscaled
     * size - real placement always respects the 1 m grid, ignoring the diorama scale) is
     * currently air. {@code facing} rotates the structure around Y first, the same way
     * {@link #place} does - see there for how the corner is pinned to avoid a gap.
     */
    public boolean canPlace(ServerLevel level, BlockPos anchor, Direction facing) {
        int steps = RotationUtil.steps(facing);
        for (int i = 0; i < blockCount(); i++) {
            double[] r = RotationUtil.rotateXZ(blockX[i], blockZ[i], steps);
            BlockPos pos = anchor.offset((int) r[0], blockY[i], (int) r[1]);
            if (!level.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    public void place(ServerLevel level, BlockPos anchor) {
        place(level, anchor, Direction.SOUTH);
    }

    /**
     * Places the structure as real blocks/block entities/entities at its original, unscaled
     * size. Caller must have already checked {@link #canPlace}. Rotated around Y to match
     * facing (SOUTH = identity, matching how it was captured) - the rotation pivots around
     * the structure's own original (0,0,0) capture corner, which stays pinned exactly at
     * anchor for every facing (the rest of the structure sweeps around it, which can mean
     * negative offsets - that's expected). This is a true fixed-pivot rotation, not a
     * bounding-box recenter, so the placer's own position is always the corner, never the
     * middle, of the placed structure.
     *
     * Blocks are placed in the same bottom-up order they were captured in, so most
     * support-from-below attachments (torches, redstone dust, saplings, etc.) settle
     * correctly; support-from-the-side or from-above attachments (wall torches, vines) may
     * not - a known limitation of scan-order placement shared with most schematic-paste tools.
     */
    public void place(ServerLevel level, BlockPos anchor, Direction facing) {
        int steps = RotationUtil.steps(facing);
        Rotation rotation = RotationUtil.rotationFor(steps);

        for (int i = 0; i < blockCount(); i++) {
            double[] r = RotationUtil.rotateXZ(blockX[i], blockZ[i], steps);
            BlockPos pos = anchor.offset((int) r[0], blockY[i], (int) r[1]);
            BlockState state = palette.get(blockPaletteIndex[i]).rotate(rotation);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }

        for (CapturedBlockEntity captured : blockEntities) {
            double[] r = RotationUtil.rotateXZ(captured.x(), captured.z(), steps);
            BlockPos pos = anchor.offset((int) r[0], captured.y(), (int) r[1]);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), captured.data());
                blockEntity.loadCustomOnly(input);
            }
        }

        for (CapturedEntity captured : entities) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(captured.entityType()).orElse(null);
            if (type == null) {
                continue;
            }
            Entity entity = type.create(level, EntitySpawnReason.TRIGGERED);
            if (entity == null) {
                continue;
            }
            ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), captured.data());
            entity.load(input);

            double[] r = RotationUtil.rotateXZ(captured.relX(), captured.relZ(), steps);
            entity.setPos(anchor.getX() + r[0], anchor.getY() + captured.relY(), anchor.getZ() + r[1]);
            entity.setYRot(entity.getYRot() + steps * 90f);
            level.addFreshEntity(entity);
        }
    }
}
