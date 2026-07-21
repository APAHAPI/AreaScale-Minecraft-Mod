package com.areascale.block;

import com.areascale.structure.RotationUtil;
import com.areascale.structure.StructureData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;

/**
 * Not persisted across reloads on purpose - a preview is a purely transient, in-session
 * state. If the server restarts mid-preview, any orphaned ghost displays are cleared the
 * next time this placer is right-clicked (its own bounding box is re-swept before starting
 * a fresh preview) or when the block is broken.
 *
 * The preview (and the real placement in {@link StructureData#place}) rotates around Y to
 * match the player's facing at preview-start time, pivoting so a corner of the structure is
 * always pinned to this block - never centered - so there's no gap between the placer and
 * the structure regardless of rotation.
 */
public class StructurePlacerBlockEntity extends BlockEntity {
    private static final double MARGIN = 1.0;
    private static final int GHOST_GLOW_COLOR = 0x55D6FF;

    private boolean previewing;
    private StructureData previewedData;
    private Direction facing = Direction.SOUTH;

    public StructurePlacerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STRUCTURE_PLACER, pos, state);
    }

    public boolean isPreviewing() {
        return previewing;
    }

    public BlockPos anchor() {
        return getBlockPos().above();
    }

    public Direction facing() {
        return facing;
    }

    public void startPreview(ServerLevel level, StructureData data, Direction facing) {
        this.facing = facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
        clearGhostsInBounds(level, data);
        spawnGhosts(level, data);
        previewing = true;
        previewedData = data;
    }

    public void cancelPreview(ServerLevel level) {
        if (previewedData != null) {
            clearGhostsInBounds(level, previewedData);
        }
        previewing = false;
        previewedData = null;
    }

    private void spawnGhosts(ServerLevel level, StructureData data) {
        BlockPos anchor = anchor();
        int steps = RotationUtil.steps(facing);
        Rotation rotation = RotationUtil.rotationFor(steps);

        for (int i = 0; i < data.blockCount(); i++) {
            BlockState blockState = data.palette().get(data.blockPaletteIndex()[i]).rotate(rotation);
            double[] r = RotationUtil.rotateXZ(data.blockX()[i], data.blockZ()[i], steps);
            BlockPos pos = anchor.offset((int) r[0], data.blockY()[i], (int) r[1]);

            CompoundTag tag = new CompoundTag();
            tag.put("block_state", NbtUtils.writeBlockState(blockState));

            CompoundTag transform = new CompoundTag();
            transform.put("scale", floatList(1f, 1f, 1f));
            transform.put("translation", floatList(0f, 0f, 0f));
            transform.put("left_rotation", floatList(0f, 0f, 0f, 1f));
            transform.put("right_rotation", floatList(0f, 0f, 0f, 1f));
            tag.put("transformation", transform);

            tag.put("Pos", doubleList(pos.getX(), pos.getY(), pos.getZ()));
            tag.putBoolean("Glowing", true);
            tag.putInt("glow_color_override", GHOST_GLOW_COLOR);

            Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
            ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
            display.load(input);
            level.addFreshEntity(display);
        }
    }

    private void clearGhostsInBounds(ServerLevel level, StructureData data) {
        for (Display.BlockDisplay display : level.getEntitiesOfClass(Display.BlockDisplay.class, bounds(data))) {
            display.discard();
        }
    }

    /**
     * True fixed-pivot rotation (see the class doc) can sweep the structure into negative
     * local offsets from the anchor depending on facing, so this uses the footprint's actual
     * min/max rather than assuming the structure always extends in the +X/+Z direction.
     */
    private AABB bounds(StructureData data) {
        BlockPos anchor = anchor();
        int steps = RotationUtil.steps(facing);
        RotationUtil.Footprint fp = RotationUtil.footprint(data.sizeX(), data.sizeZ(), steps);
        return new AABB(
            anchor.getX() + fp.minX() - MARGIN, anchor.getY() - MARGIN, anchor.getZ() + fp.minZ() - MARGIN,
            anchor.getX() + fp.maxX() + 1 + MARGIN, anchor.getY() + data.sizeY() + MARGIN, anchor.getZ() + fp.maxZ() + 1 + MARGIN
        );
    }

    private static ListTag floatList(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    private static ListTag doubleList(double... values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (previewing && level instanceof ServerLevel serverLevel) {
            cancelPreview(serverLevel);
        }
        super.preRemoveSideEffects(pos, state);
    }
}
