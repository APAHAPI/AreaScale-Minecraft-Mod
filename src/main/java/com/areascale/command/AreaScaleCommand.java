package com.areascale.command;

import java.util.Optional;
import java.util.UUID;

import com.areascale.capture.CaptureService;
import com.areascale.item.StructureCapsuleItem;
import com.areascale.selection.SelectionManager;
import com.areascale.structure.StructureDataStorage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class AreaScaleCommand {
    private AreaScaleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("areascale")
            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .then(Commands.literal("expand")
                .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.001))
                    .executes(ctx -> capture(ctx, true))))
            .then(Commands.literal("shrink")
                .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.001))
                    .executes(ctx -> capture(ctx, false))))
            .then(Commands.literal("clear")
                .executes(AreaScaleCommand::clear))
            .then(Commands.literal("undo")
                .executes(AreaScaleCommand::undo)));
    }

    private static int capture(CommandContext<CommandSourceStack> ctx, boolean expand) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double factor = DoubleArgumentType.getDouble(ctx, "factor");

        // The capture itself lives in CaptureService so the in-game Capture Screen can run the
        // exact same operation; this just reports the outcome to the command source.
        CaptureService.Result result = CaptureService.capture(player, factor, expand);
        if (result.success()) {
            ctx.getSource().sendSuccess(result::message, true);
        } else {
            ctx.getSource().sendFailure(result.message());
        }
        return result.success() ? 1 : 0;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SelectionManager.clearAndNotify(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Selection cleared."), false);
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!player.isCreative()) {
            ctx.getSource().sendFailure(Component.translatable("commands.areascale.undo_creative_only"));
            return 0;
        }

        Optional<UndoManager.Entry> entry = UndoManager.get(player);
        if (entry.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("commands.areascale.nothing_to_undo"));
            return 0;
        }

        UndoManager.Entry e = entry.get();
        if (e.dimension() != player.level().dimension()) {
            ctx.getSource().sendFailure(Component.translatable("commands.areascale.different_dimension"));
            return 0;
        }

        e.data().place(player.level(), e.min());
        // Only free the world-level structure entry if the capsule was still sitting untouched
        // in the player's inventory - if it's gone, it's because the player already placed it
        // on a Display Platform or Structure Placer, which is still referencing this same id.
        // Freeing it out from under a live display leaves that display permanently stuck with
        // no way to resolve or remove it, so when in doubt, leak the registry entry instead.
        if (removeMatchingCapsule(player.getInventory(), e.structureId())) {
            StructureDataStorage.get(player.level()).remove(e.structureId());
        }
        UndoManager.clear(player);

        ctx.getSource().sendSuccess(() -> Component.translatable("commands.areascale.undone"), true);
        return 1;
    }

    private static boolean removeMatchingCapsule(Inventory inventory, UUID structureId) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof com.areascale.item.StructureCapsuleItem)) {
                continue;
            }
            Optional<StructureCapsuleItem.CapsuleSummary> summary = StructureCapsuleItem.readSummary(stack);
            if (summary.isPresent() && summary.get().structureId().equals(structureId)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
