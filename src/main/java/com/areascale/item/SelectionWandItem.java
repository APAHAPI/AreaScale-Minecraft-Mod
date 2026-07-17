package com.areascale.item;

import net.minecraft.world.item.Item;

/**
 * Marker item used by {@code AttackBlockCallback} and {@code UseBlockCallback}
 * (see the client-side handlers) to recognize when the player is holding the wand.
 * All of its behavior is implemented via those events rather than item overrides,
 * since Minecraft dispatches left/right click on blocks to those callbacks first.
 */
public class SelectionWandItem extends Item {
    public SelectionWandItem(Properties properties) {
        super(properties);
    }
}
