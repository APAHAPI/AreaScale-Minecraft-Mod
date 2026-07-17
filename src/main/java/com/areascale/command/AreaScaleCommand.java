package com.areascale.command;

import java.util.Optional;
import java.util.UUID;

import com.areascale.ModItems;
import com.areascale.config.AreaScaleConfig;
import com.areascale.item.StructureCapsuleItem;
import com.areascale.selection.PlayerSelection;
import com.areascale.selection.SelectionManager;
import com.areascale.structure.StructureData;
import com.areascale.structure.StructureDataStorage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class AreaScaleCommand {
    private AreaScaleCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(build("areascale"));

        String alias = AreaScaleConfig.get().commandAlias;
        if (alias != null && !alias.isBlank() && !alias.equals("areascale")) {
            dispatcher.register(Commands.literal(alias).redirect(node));
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name)
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("expand")
                .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.001))
                    .executes(ctx -> capture(ctx, true))))
            .then(Commands.literal("shrink")
                .then(Commands.argument("factor", DoubleArgumentType.doubleArg(0.001))
                    .executes(ctx -> capture(ctx, false))))
            .then(Commands.literal("clear")
                .executes(AreaScaleCommand::clear))
            .then(Commands.literal("undo")
                .executes(AreaScaleCommand::undo));
    }

    private static int capture(CommandContext<CommandSourceStack> ctx, boolean expand) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        double factor = DoubleArgumentType.getDouble(ctx, "factor");
        if (factor <= 0) {
            ctx.getSource().sendFailure(Component.translatable("commands.areascale.invalid_factor"));
            return 0;
        }
        float scale = (float) (expand ? factor : 1.0 / factor);

        PlayerSelection selection = SelectionManager.get(player);
        if (!selection.isComplete()) {
            ctx.getSource().sendFailure(Component.translatable("commands.areascale.no_selection"));
            return 0;
        }
        if (selection.getDimension() != player.level().dimension()) {
            ctx.getSource().sendFailure(Component.translatable("commands.areascale.different_dimension"));
            return 0;
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
            ctx.getSource().sendFailure(Component.translatable("commands.areascale.empty_selection"));
            return 0;
        }

        long maxBlocks = AreaScaleConfig.get().maxCaptureBlocks;
        if (maxBlocks > 0 && data.blockCount() > maxBlocks) {
            ctx.getSource().sendFailure(Component.translatable("commands.areascale.too_large", maxBlocks, data.blockCount()));
            return 0;
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

        String factorText = trimFactor(factor);
        String mode = expand ? "expand" : "shrink";
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.areascale.captured",
            mode, factorText, data.sizeX(), data.sizeY(), data.sizeZ(), data.blockCount()), true);

        return 1;
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
        removeMatchingCapsule(player.getInventory(), e.structureId());
        StructureDataStorage.get(player.level()).remove(e.structureId());
        UndoManager.clear(player);

        ctx.getSource().sendSuccess(() -> Component.translatable("commands.areascale.undone"), true);
        return 1;
    }

    private static void removeMatchingCapsule(Inventory inventory, UUID structureId) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof com.areascale.item.StructureCapsuleItem)) {
                continue;
            }
            Optional<StructureCapsuleItem.CapsuleSummary> summary = StructureCapsuleItem.readSummary(stack);
            if (summary.isPresent() && summary.get().structureId().equals(structureId)) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static String trimFactor(double factor) {
        if (factor == Math.floor(factor)) {
            return String.valueOf((long) factor);
        }
        return String.valueOf(factor);
    }
}
