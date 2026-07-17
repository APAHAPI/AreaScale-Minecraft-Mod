package com.areascale.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.areascale.structure.StructureData;
import com.areascale.structure.StructureDataStorage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Carries only a small, fixed-size summary in its {@code minecraft:custom_data} component -
 * a UUID pointing into {@link StructureDataStorage} plus dimensions/scale/block count for the
 * tooltip and icon. The actual (potentially huge) captured block/entity data never lives on
 * the item itself.
 *
 * This is deliberate: item data components get synced to clients as part of ordinary
 * inventory/container packets, which have real size limits. Embedding the full capture
 * directly here used to work for small structures but broke down catastrophically at scale
 * (100k+ blocks produced a payload big enough to fail packet decoding and disconnect the
 * player, even in singleplayer). Keeping the item tiny and the bulk data in world-level saved
 * data sidesteps that regardless of capture size.
 */
public class StructureCapsuleItem extends Item {
    public StructureCapsuleItem(Properties properties) {
        super(properties);
    }

    /** A structure's small, network-safe summary: everything needed for the tooltip/icon, plus the reference to find the real data. */
    public record CapsuleSummary(UUID structureId, int sizeX, int sizeY, int sizeZ, float scale, int blockCount) {
        void write(CompoundTag tag) {
            tag.putString("structure_id", structureId.toString());
            tag.putInt("size_x", sizeX);
            tag.putInt("size_y", sizeY);
            tag.putInt("size_z", sizeZ);
            tag.putFloat("scale", scale);
            tag.putInt("block_count", blockCount);
        }

        static Optional<CapsuleSummary> read(CompoundTag tag) {
            Optional<String> idString = tag.getString("structure_id");
            if (idString.isEmpty()) {
                return Optional.empty();
            }
            UUID id;
            try {
                id = UUID.fromString(idString.get());
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            return Optional.of(new CapsuleSummary(id,
                tag.getIntOr("size_x", 0), tag.getIntOr("size_y", 0), tag.getIntOr("size_z", 0),
                tag.getFloatOr("scale", 1.0f), tag.getIntOr("block_count", 0)));
        }
    }

    /** Stores newly-captured data and creates a fresh capsule referencing it. */
    public static ItemStack create(Item item, ServerLevel level, StructureData data) {
        UUID id = StructureDataStorage.get(level).store(data);
        return createReferencing(item, id, data.sizeX(), data.sizeY(), data.sizeZ(), data.scale(), data.blockCount());
    }

    /** Creates a capsule that reuses an already-stored structure (e.g. picking a display back up) - no re-storing, no duplicate data. */
    public static ItemStack createReferencing(Item item, UUID structureId, int sizeX, int sizeY, int sizeZ, float scale, int blockCount) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        new CapsuleSummary(structureId, sizeX, sizeY, sizeZ, scale, blockCount).write(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
            new CustomModelData(List.of((float) blockCount), List.of(), List.of(), List.of()));
        return stack;
    }

    /** Reads just the small summary - safe to call client-side, no world access needed. */
    public static Optional<CapsuleSummary> readSummary(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        return CapsuleSummary.read(customData.copyTag());
    }

    /** Resolves the full captured structure data. Server-side only. */
    public static Optional<StructureData> readFull(ServerLevel level, ItemStack stack) {
        return readSummary(stack).flatMap(summary -> StructureDataStorage.get(level).get(summary.structureId()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
                                 Consumer<Component> tooltipAdder, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);

        Optional<CapsuleSummary> summary = readSummary(stack);
        if (summary.isEmpty()) {
            tooltipAdder.accept(Component.translatable("item.areascale.structure_capsule.empty"));
            return;
        }

        CapsuleSummary s = summary.get();
        tooltipAdder.accept(Component.translatable("item.areascale.structure_capsule.scale", trimFloat(s.scale())));
        tooltipAdder.accept(Component.translatable("item.areascale.structure_capsule.size", s.sizeX(), s.sizeY(), s.sizeZ()));
        tooltipAdder.accept(Component.translatable("item.areascale.structure_capsule.blocks", s.blockCount()));
    }

    private static String trimFloat(float value) {
        if (value == Math.round(value)) {
            return String.valueOf(Math.round(value));
        }
        return String.valueOf(value);
    }
}
