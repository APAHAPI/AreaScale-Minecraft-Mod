package com.areascale.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class PlayerSelection {
    private BlockPos pos1;
    private BlockPos pos2;
    private ResourceKey<Level> dimension;

    public void setPos1(BlockPos pos, ResourceKey<Level> dim) {
        if (dimension != null && dimension != dim) {
            pos2 = null;
        }
        this.pos1 = pos;
        this.dimension = dim;
    }

    public void setPos2(BlockPos pos, ResourceKey<Level> dim) {
        if (dimension != null && dimension != dim) {
            pos1 = null;
        }
        this.pos2 = pos;
        this.dimension = dim;
    }

    public void clear() {
        pos1 = null;
        pos2 = null;
        dimension = null;
    }

    public BlockPos getPos1() {
        return pos1;
    }

    public BlockPos getPos2() {
        return pos2;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null;
    }
}
