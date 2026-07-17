package com.areascale.block;

import com.areascale.AreaScaleMod;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<DisplayPlatformBlockEntity> DISPLAY_PLATFORM = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        AreaScaleMod.id("display_platform"),
        FabricBlockEntityTypeBuilder.create(DisplayPlatformBlockEntity::new, ModBlocks.DISPLAY_PLATFORM).build()
    );

    public static final BlockEntityType<StructurePlacerBlockEntity> STRUCTURE_PLACER = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        AreaScaleMod.id("structure_placer"),
        FabricBlockEntityTypeBuilder.create(StructurePlacerBlockEntity::new, ModBlocks.STRUCTURE_PLACER).build()
    );

    private ModBlockEntities() {
    }

    public static void initialize() {
    }
}
