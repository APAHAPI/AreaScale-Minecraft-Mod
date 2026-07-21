package com.areascale.block;

import java.util.Optional;

import com.areascale.item.StructureCapsuleItem;
import com.areascale.structure.StructureData;
import com.areascale.structure.StructureDataStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Right-click with a Structure Capsule to preview the structure as a glowing ghost anchored
 * directly above this block, at its real, unscaled, 1-block-per-cell size. Right-click again
 * (still holding the capsule) to confirm and place it for real, consuming the capsule.
 * Right-click empty-handed while previewing cancels it.
 */
public class StructurePlacerBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 2, 15);

    public StructurePlacerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StructurePlacerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide() || !(stack.getItem() instanceof StructureCapsuleItem)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(pos) instanceof StructurePlacerBlockEntity placer)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        Optional<StructureCapsuleItem.CapsuleSummary> summary = StructureCapsuleItem.readSummary(stack);
        Optional<StructureData> maybeData = summary.flatMap(s -> StructureCapsuleItem.readFull(serverLevel, stack));
        if (summary.isEmpty() || maybeData.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("block.areascale.structure_placer.invalid_capsule"));
            return InteractionResult.FAIL;
        }

        StructureData data = maybeData.get();

        if (!placer.isPreviewing()) {
            placer.startPreview(serverLevel, data, player.getDirection());
            player.sendOverlayMessage(Component.translatable("block.areascale.structure_placer.previewing"));
            return InteractionResult.SUCCESS;
        }

        BlockPos anchor = placer.anchor();
        Direction facing = placer.facing();
        if (!data.canPlace(serverLevel, anchor, facing)) {
            player.sendOverlayMessage(Component.translatable("block.areascale.structure_placer.blocked"));
            return InteractionResult.FAIL;
        }

        placer.cancelPreview(serverLevel);
        data.place(serverLevel, anchor, facing);
        stack.shrink(1);
        // The capsule is fully consumed and turned into real blocks - free its entry so the
        // world's saved structure registry doesn't grow forever. Safe as long as no other
        // capsule/platform still references this id (see StructureDataStorage's docs).
        StructureDataStorage.get(serverLevel).remove(summary.get().structureId());
        player.sendOverlayMessage(Component.translatable("block.areascale.structure_placer.placed"));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof StructurePlacerBlockEntity placer) || !placer.isPreviewing()) {
            return InteractionResult.PASS;
        }

        placer.cancelPreview((ServerLevel) level);
        player.sendOverlayMessage(Component.translatable("block.areascale.structure_placer.cancelled"));
        return InteractionResult.SUCCESS;
    }
}
