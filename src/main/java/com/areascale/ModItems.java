package com.areascale;

import java.util.function.Function;

import com.areascale.item.SelectionWandItem;
import com.areascale.item.StructureCapsuleItem;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item SELECTION_WAND =
        register("selection_wand", SelectionWandItem::new, new Item.Properties().stacksTo(1));

    public static final Item STRUCTURE_CAPSULE =
        register("structure_capsule", StructureCapsuleItem::new, new Item.Properties().stacksTo(1));

    private ModItems() {
    }

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties settings) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AreaScaleMod.id(name));
        T item = factory.apply(settings.setId(key));
        Registry.register(BuiltInRegistries.ITEM, key, item);
        return item;
    }

    public static void initialize() {
        // Referencing this method forces the static fields above to load.
    }
}
