package com.areascale.block;

import java.util.function.Function;

import com.areascale.AreaScaleMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final Block DISPLAY_PLATFORM = register(
        "display_platform",
        DisplayPlatformBlock::new,
        BlockBehaviour.Properties.of().strength(1.0f, 6.0f).noOcclusion(),
        true
    );

    public static final Block STRUCTURE_PLACER = register(
        "structure_placer",
        StructurePlacerBlock::new,
        BlockBehaviour.Properties.of().strength(1.0f, 6.0f).noOcclusion(),
        true
    );

    private ModBlocks() {
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                   BlockBehaviour.Properties properties, boolean withItem) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, AreaScaleMod.id(name));
        Block block = factory.apply(properties.setId(key));
        Registry.register(BuiltInRegistries.BLOCK, key, block);

        if (withItem) {
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, AreaScaleMod.id(name));
            Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        }

        return block;
    }

    public static void initialize() {
    }
}
