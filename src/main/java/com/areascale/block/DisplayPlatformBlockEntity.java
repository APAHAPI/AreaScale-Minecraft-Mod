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
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
    // Generous margin for the entity/display cleanup search radius (safe to over-search).
    private static final double MARGIN = 1.0;
    // The barrier shell hugs the diorama's actual footprint tightly instead - a full 1-block
    // MARGIN here used to leave a very visible gap between the platform and the invisible
    // wall, especially for small/shrunk dioramas.
    private static final double BARRIER_MARGIN = 0.05;

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
        placeBarriers(level, footprintWorldBounds(data));
        setChanged();
    }

    /** Despawns the current display and returns enough info to hand the player a capsule back (id + resolved data). */
    public Optional<DisplayedStructure> remove(ServerLevel level) {
        if (displayedId == null) {
            return Optional.empty();
        }
        Optional<StructureData> data = StructureDataStorage.get(level).get(displayedId);
        data.ifPresent(d -> {
            despawnDisplays(level, d);
            removeBarriers(level, footprintWorldBounds(d));
        });
        DisplayedStructure result = data.map(d -> new DisplayedStructure(displayedId, d)).orElse(null);
        displayedId = null;
        setChanged();
        return Optional.ofNullable(result);
    }

    public record DisplayedStructure(UUID id, StructureData data) {
    }

    /** The structure's actual (rotation-aware) world-space bounding box, used for the barrier shell. */
    private AABB footprintWorldBounds(StructureData data) {
        BlockPos base = getBlockPos().above();
        RotationUtil.Footprint fp = RotationUtil.footprint(data.sizeX(), data.sizeZ(), RotationUtil.steps(facing));
        float scale = data.scale();
        double halfX = fp.sizeX() * scale / 2.0 + BARRIER_MARGIN;
        double halfZ = fp.sizeZ() * scale / 2.0 + BARRIER_MARGIN;
        return new AABB(
            base.getX() + 0.5 - halfX, base.getY() - BARRIER_MARGIN, base.getZ() + 0.5 - halfZ,
            base.getX() + 0.5 + halfX, base.getY() + data.sizeY() * scale + BARRIER_MARGIN, base.getZ() + 0.5 + halfZ
        );
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

            BlockState rotatedState = blockState.rotate(rotation);

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
            Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
            display.load(input);
            level.addFreshEntity(display);
        }

        for (StructureData.CapturedEntity captured : data.entities()) {
            spawnEntity(level, base, centerX, centerZ, scale, steps, captured);
        }
    }

    private void spawnEntity(ServerLevel level, BlockPos base, double centerX, double centerZ, float scale,
                              int steps, StructureData.CapturedEntity captured) {
        EntityType<?> type = EntityType.byString(captured.entityType().toString()).orElse(null);
        if (type == null) {
            return;
        }

        Entity entity = type.create(level, EntitySpawnReason.TRIGGERED);
        if (entity == null) {
            return;
        }

        ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), captured.data());
        entity.load(input);

        double[] rotated = RotationUtil.rotateXZ(captured.relX(), captured.relZ(), steps);
        double x = base.getX() + 0.5 + (rotated[0] - centerX) * scale;
        double y = base.getY() + captured.relY() * scale;
        double z = base.getZ() + 0.5 + (rotated[1] - centerZ) * scale;
        entity.setPos(x, y, z);
        entity.setYRot(entity.getYRot() + steps * 90f);

        if (entity instanceof LivingEntity living) {
            var scaleAttribute = living.getAttribute(Attributes.SCALE);
            if (scaleAttribute != null) {
                scaleAttribute.setBaseValue(scaleAttribute.getBaseValue() * scale);
            }
        }

        level.addFreshEntity(entity);
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
        if (data.entities().isEmpty()) {
            for (Display.BlockDisplay display : level.getEntitiesOfClass(Display.BlockDisplay.class, displayBounds(data))) {
                display.discard();
            }
            return;
        }
        // Also captured real entities (armor stands, etc.) reconstructed alongside the displays.
        for (Entity entity : level.getEntities((Entity) null, displayBounds(data), e -> !(e instanceof Player))) {
            entity.discard();
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

    private void placeBarriers(ServerLevel level, AABB bounds) {
        int minX = Mth.floor(bounds.minX);
        int maxX = Mth.ceil(bounds.maxX) - 1;
        int minY = Mth.floor(bounds.minY);
        int maxY = Mth.ceil(bounds.maxY) - 1;
        int minZ = Mth.floor(bounds.minZ);
        int maxZ = Mth.ceil(bounds.maxZ) - 1;
        BlockState barrier = Blocks.BARRIER.defaultBlockState();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, barrier, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private void removeBarriers(ServerLevel level, AABB bounds) {
        int minX = Mth.floor(bounds.minX);
        int maxX = Mth.ceil(bounds.maxX) - 1;
        int minY = Mth.floor(bounds.minY);
        int maxY = Mth.ceil(bounds.maxY) - 1;
        int minZ = Mth.floor(bounds.minZ);
        int maxZ = Mth.ceil(bounds.maxZ) - 1;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x != minX && x != maxX && y != minY && y != maxY && z != minZ && z != maxZ) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
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
