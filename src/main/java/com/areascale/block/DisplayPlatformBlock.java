package com.areascale.block;

import java.util.Optional;

import com.areascale.ModItems;
import com.areascale.item.StructureCapsuleItem;
import com.areascale.structure.StructureData;

import net.minecraft.core.BlockPos;
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

/** A low, thin pedestal. Right-click with a capsule to place its (purely decorative) display; right-click empty-handed to pick it back up. */
public class DisplayPlatformBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 2, 15);

    public DisplayPlatformBlock(Properties properties) {
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
        return new DisplayPlatformBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide || !(stack.getItem() instanceof StructureCapsuleItem)) {
            // Not our item - let the game fall through to useWithoutItem (e.g. empty-hand pickup).
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(pos) instanceof DisplayPlatformBlockEntity platform)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (platform.isOccupied()) {
            player.displayClientMessage(Component.translatable("block.areascale.display_platform.occupied"), true);
            return InteractionResult.FAIL;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        Optional<StructureCapsuleItem.CapsuleSummary> summary = StructureCapsuleItem.readSummary(stack);
        Optional<StructureData> data = summary.flatMap(s -> StructureCapsuleItem.readFull(serverLevel, stack));

        if (summary.isEmpty() || data.isEmpty()) {
            player.displayClientMessage(Component.translatable("block.areascale.display_platform.invalid_capsule"), true);
            return InteractionResult.FAIL;
        }

        platform.place(serverLevel, summary.get().structureId(), data.get(), player.getDirection());
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof DisplayPlatformBlockEntity platform)) {
            return InteractionResult.PASS;
        }

        return platform.remove((ServerLevel) level).<InteractionResult>map(displayed -> {
            StructureData data = displayed.data();
            ItemStack capsule = StructureCapsuleItem.createReferencing(ModItems.STRUCTURE_CAPSULE,
                displayed.id(), data.sizeX(), data.sizeY(), data.sizeZ(), data.scale(), data.blockCount());
            if (!player.getInventory().add(capsule)) {
                player.drop(capsule, false);
            }
            return InteractionResult.SUCCESS;
        }).orElse(InteractionResult.PASS);
    }
}
