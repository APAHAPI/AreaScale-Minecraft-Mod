package com.areascale.block;

import com.areascale.AreaScaleMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Manual overrides for the display platform's hollowing check (see
 * DisplayPlatformBlockEntity#occludes). By default a neighbor counts as "occluding" (safe to
 * hide the block behind it) purely by computed geometry: {@code canOcclude()} plus
 * {@code Block.isShapeFullBlock} on its actual collision shape - there's no hardcoded block
 * list. If that default is ever wrong for a specific block, add it to one of these two data
 * pack tags instead of guessing:
 *
 * - data/areascale/tags/block/never_occludes.json: this block will NEVER hide what's behind
 *   it, even if the geometry check thinks it's a full cube.
 * - data/areascale/tags/block/always_occludes.json: this block WILL always hide what's
 *   behind it, even if the geometry check thinks it isn't a full cube.
 *
 * Both start empty; add block ids under "values" in those files to override specific blocks.
 */
public final class ModBlockTags {
    public static final TagKey<Block> NEVER_OCCLUDES = TagKey.create(Registries.BLOCK, AreaScaleMod.id("never_occludes"));
    public static final TagKey<Block> ALWAYS_OCCLUDES = TagKey.create(Registries.BLOCK, AreaScaleMod.id("always_occludes"));

    private ModBlockTags() {
    }
}
