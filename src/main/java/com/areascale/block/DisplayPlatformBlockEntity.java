package com.areascale.block;

import java.util.Optional;
import java.util.UUID;

import com.areascale.ModItems;
import com.areascale.item.StructureCapsuleItem;
import com.areascale.structure.RotationUtil;
import com.areascale.structure.StructureData;
import com.areascale.structure.StructureDataStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Holds only a {@link UUID} reference into {@link StructureDataStorage} - never the bulk
 * captured data directly - so this block entity's own NBT (which does get synced to nearby
 * clients) stays tiny regardless of how large the displayed structure is. The block-display
 * entities are purely decorative (no collision) - see the README for why.
 *
 * The diorama is rotated around Y to match whichever way the player was facing when they
 * placed the capsule (see {@link #facing}). This does NOT fix beds/signs/banners/chests/etc
 * looking wrong in the diorama - that's a separate, unrelated vanilla rendering limitation
 * (see the README) that no amount of orientation handling changes.
 */
public class DisplayPlatformBlockEntity extends BlockEntity {
    // Generous margin for the display cleanup search radius (safe to over-search).
    private static final double MARGIN = 1.0;

    private UUID displayedId;
    private Direction facing = Direction.SOUTH;

    public DisplayPlatformBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISPLAY_PLATFORM, pos, state);
    }

    public boolean isOccupied() {
        return displayedId != null;
    }

    public void place(ServerLevel level, UUID structureId, StructureData data, Direction facing) {
        this.displayedId = structureId;
        this.facing = facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
        spawnDisplays(level, data);
        setChanged();
    }

    /** Despawns the current display and returns enough info to hand the player a capsule back (id + resolved data). */
    public Optional<DisplayedStructure> remove(ServerLevel level) {
        if (displayedId == null) {
            return Optional.empty();
        }
        Optional<StructureData> data = StructureDataStorage.get(level).get(displayedId);
        data.ifPresent(d -> despawnDisplays(level, d));
        DisplayedStructure result = data.map(d -> new DisplayedStructure(displayedId, d)).orElse(null);
        displayedId = null;
        setChanged();
        return Optional.ofNullable(result);
    }

    public record DisplayedStructure(UUID id, StructureData data) {
    }

    private void spawnDisplays(ServerLevel level, StructureData data) {
        BlockPos base = getBlockPos().above();
        float scale = data.scale();
        int steps = RotationUtil.steps(facing);
        Rotation rotation = RotationUtil.rotationFor(steps);
        RotationUtil.Footprint fp = RotationUtil.footprint(data.sizeX(), data.sizeZ(), steps);
        double centerX = fp.minX() + fp.sizeX() / 2.0;
        double centerZ = fp.minZ() + fp.sizeZ() / 2.0;

        // Purely a rendering-cost optimization: block-display entities are far more expensive
        // than real chunk-batched blocks, so skip anything that could never be seen anyway -
        // every one of its 6 neighbors is a full, opaque cube. This is a computed geometric
        // check (Block.isShapeFullBlock on the neighbor's actual collision shape), not a
        // hardcoded block list, so partial-shape blocks (snow layers, slabs, stairs, fences,
        // glass panes, etc.) are correctly never treated as occluding, and liquids are rendered
        // like anything else and culled by the same rule. See ModBlockTags for manual overrides.
        BlockState[] grid = buildDenseGrid(data);

        for (int i = 0; i < data.blockCount(); i++) {
            BlockState blockState = data.palette().get(data.blockPaletteIndex()[i]);
            int lx = data.blockX()[i];
            int ly = data.blockY()[i];
            int lz = data.blockZ()[i];

            if (isFullyHidden(data, grid, lx, ly, lz)) {
                continue;
            }

            double[] rotated = RotationUtil.rotateXZ(lx, lz, steps);
            double x = base.getX() + 0.5 + (rotated[0] - centerX) * scale;
            double y = base.getY() + ly * scale;
            double z = base.getZ() + 0.5 + (rotated[1] - centerZ) * scale;

            BlockState rotatedState = displayStateFor(blockState.rotate(rotation));

            CompoundTag tag = new CompoundTag();
            tag.put("block_state", NbtUtils.writeBlockState(rotatedState));

            CompoundTag transform = new CompoundTag();
            transform.put("scale", floatList(scale, scale, scale));
            transform.put("translation", floatList(0f, 0f, 0f));
            transform.put("left_rotation", floatList(0f, 0f, 0f, 1f));
            transform.put("right_rotation", floatList(0f, 0f, 0f, 1f));
            tag.put("transformation", transform);

            tag.put("Pos", doubleList(x, y, z));

            // NOTE: block-display entities render only through BlockRenderDispatcher's static
            // baked model (the same path used to draw a block in your hand), never through a
            // BlockEntityRenderer. Beds, chests, shulker boxes, standing/wall signs, banners
            // and decorated pots all get their real appearance from a BlockEntityRenderer, so
            // they show a crude/incorrect placeholder here no matter what data or rotation we
            // feed them - this is a vanilla engine limitation of Display.BlockDisplay, not a
            // data loss or orientation bug (verified: the full BlockState, including
            // facing/part/rotation, round-trips correctly through BlockState.CODEC). The
            // Structure Placer places actual blocks, so those render perfectly there.
            Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
            ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
            display.load(input);
            level.addFreshEntity(display);
        }
        // Captured entities (armor stands, mobs, etc.) are intentionally never spawned into the
        // diorama - there's no real floor for them to stand on here, so they'd just fall out or
        // wander off. Their data still lives in StructureData untouched, so the Structure Placer
        // reconstructs them correctly when the structure is placed for real.
    }

    /**
     * Water and lava can never actually render through a Display.BlockDisplay - their block's
     * getRenderShape() is hardcoded to RenderShape.INVISIBLE (real fluid rendering happens
     * through a separate mesher that looks at neighboring fluid levels in the world, which a
     * floating display entity doesn't have), so displaying the true block state here would just
     * show nothing at all. Stand in with translucent colored glass purely for this cosmetic
     * diorama - the underlying StructureData still stores the real water/lava, so the Structure
     * Placer places actual fluid there.
     */
    private static BlockState displayStateFor(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
        }
        if (state.is(Blocks.LAVA)) {
            return Blocks.STAINED_GLASS.pick(DyeColor.ORANGE).defaultBlockState();
        }
        return state;
    }

    private static BlockState[] buildDenseGrid(StructureData data) {
        BlockState[] grid = new BlockState[data.sizeX() * data.sizeY() * data.sizeZ()];
        for (int i = 0; i < data.blockCount(); i++) {
            grid[gridIndex(data, data.blockX()[i], data.blockY()[i], data.blockZ()[i])] =
                data.palette().get(data.blockPaletteIndex()[i]);
        }
        return grid;
    }

    private static int gridIndex(StructureData data, int x, int y, int z) {
        return (y * data.sizeZ() + z) * data.sizeX() + x;
    }

    /** True if every one of the 6 neighbors is present and a full opaque cube, so this block could never be seen from outside the structure. */
    private static boolean isFullyHidden(StructureData data, BlockState[] grid, int x, int y, int z) {
        return occludes(data, grid, x - 1, y, z)
            && occludes(data, grid, x + 1, y, z)
            && occludes(data, grid, x, y - 1, z)
            && occludes(data, grid, x, y + 1, z)
            && occludes(data, grid, x, y, z - 1)
            && occludes(data, grid, x, y, z + 1);
    }

    private static boolean occludes(StructureData data, BlockState[] grid, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= data.sizeX() || y >= data.sizeY() || z >= data.sizeZ()) {
            return false;
        }
        BlockState neighbor = grid[gridIndex(data, x, y, z)];
        if (neighbor == null) {
            return false;
        }
        if (neighbor.is(ModBlockTags.NEVER_OCCLUDES)) {
            return false;
        }
        if (neighbor.is(ModBlockTags.ALWAYS_OCCLUDES)) {
            return true;
        }
        if (!neighbor.canOcclude()) {
            return false;
        }
        VoxelShape shape = neighbor.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return Block.isShapeFullBlock(shape);
    }

    private void despawnDisplays(ServerLevel level, StructureData data) {
        for (Display.BlockDisplay display : level.getEntitiesOfClass(Display.BlockDisplay.class, displayBounds(data))) {
            display.discard();
        }
    }

    private AABB displayBounds(StructureData data) {
        BlockPos base = getBlockPos().above();
        double halfWidth = Math.max(data.sizeX(), data.sizeZ()) * data.scale() / 2.0 + MARGIN;
        double height = data.sizeY() * data.scale() + MARGIN;
        return new AABB(
            base.getX() - halfWidth, base.getY() - MARGIN, base.getZ() - halfWidth,
            base.getX() + halfWidth, base.getY() + height, base.getZ() + halfWidth
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (displayedId != null) {
            output.putString("displayed_structure_id", displayedId.toString());
        }
        output.putString("facing", facing.getSerializedName());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        displayedId = input.getString("displayed_structure_id").map(id -> {
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).orElse(null);
        facing = Direction.byName(input.getStringOr("facing", "south"));
        if (facing == null) {
            facing = Direction.SOUTH;
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            remove(serverLevel).ifPresent(displayed -> {
                StructureData data = displayed.data();
                ItemStack capsule = StructureCapsuleItem.createReferencing(ModItems.STRUCTURE_CAPSULE,
                    displayed.id(), data.sizeX(), data.sizeY(), data.sizeZ(), data.scale(), data.blockCount());
                Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, capsule);
            });
        }
        super.preRemoveSideEffects(pos, state);
    }
}
