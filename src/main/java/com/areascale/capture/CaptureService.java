package com.areascale.capture;

import java.util.UUID;

import com.areascale.ModItems;
import com.areascale.command.UndoManager;
import com.areascale.config.AreaScaleConfig;
import com.areascale.item.StructureCapsuleItem;
import com.areascale.selection.PlayerSelection;
import com.areascale.selection.SelectionManager;
import com.areascale.structure.StructureData;
import com.areascale.structure.StructureDataStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The actual capture operation, shared by the /areascale command and the in-game Capture
 * Screen (see {@link com.areascale.network.CaptureRequestPayload}). Both routes have to behave
 * identically, so the command keeps no capture logic of its own - it just reports the result.
 */
public final class CaptureService {
    private CaptureService() {
    }

    /** Whether the capture happened, plus the exact message to show the player either way. */
    public record Result(boolean success, Component message) {
    }

    public static Result capture(ServerPlayer player, double factor, boolean expand) {
        if (!(factor > 0.0) || Double.isInfinite(factor)) {
            return new Result(false, Component.translatable("commands.areascale.invalid_factor"));
        }
        float scale = (float) (expand ? factor : 1.0 / factor);

        PlayerSelection selection = SelectionManager.get(player);
        if (!selection.isComplete()) {
            return new Result(false, Component.translatable("commands.areascale.no_selection"));
        }
        if (selection.getDimension() != player.level().dimension()) {
            return new Result(false, Component.translatable("commands.areascale.different_dimension"));
        }

        BlockPos p1 = selection.getPos1();
        BlockPos p2 = selection.getPos2();
        BlockPos min = new BlockPos(
            Math.min(p1.getX(), p2.getX()),
            Math.min(p1.getY(), p2.getY()),
            Math.min(p1.getZ(), p2.getZ()));
        BlockPos max = new BlockPos(
            Math.max(p1.getX(), p2.getX()),
            Math.max(p1.getY(), p2.getY()),
            Math.max(p1.getZ(), p2.getZ()));

        StructureData data = StructureData.capture(player.level(), min, max, scale);
        if (data.blockCount() == 0) {
            return new Result(false, Component.translatable("commands.areascale.empty_selection"));
        }

        long maxBlocks = AreaScaleConfig.get().maxCaptureBlocks;
        if (maxBlocks > 0 && data.blockCount() > maxBlocks) {
            return new Result(false, Component.translatable("commands.areascale.too_large", maxBlocks, data.blockCount()));
        }

        data.clearSource(player.level(), min);
        SelectionManager.clearAndNotify(player);

        UUID structureId = StructureDataStorage.get(player.level()).store(data);
        ItemStack capsule = StructureCapsuleItem.createReferencing(ModItems.STRUCTURE_CAPSULE,
            structureId, data.sizeX(), data.sizeY(), data.sizeZ(), data.scale(), data.blockCount());
        if (!player.getInventory().add(capsule)) {
            player.drop(capsule, false);
        }

        UndoManager.record(player, structureId, data, min);

        return new Result(true, Component.translatable("commands.areascale.captured",
            expand ? "expand" : "shrink", trimFactor(factor),
            data.sizeX(), data.sizeY(), data.sizeZ(), data.blockCount()));
    }

    public static String trimFactor(double factor) {
        if (factor == Math.floor(factor)) {
            return String.valueOf((long) factor);
        }
        return String.valueOf(factor);
    }
}
