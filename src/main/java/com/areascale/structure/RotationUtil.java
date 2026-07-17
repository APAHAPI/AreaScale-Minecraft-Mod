package com.areascale.structure;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

/** Shared 90-degree-step horizontal rotation math, used by both the display platform (which centers the rotated footprint) and the structure placer (which pins a footprint corner to the anchor - see each caller). */
public final class RotationUtil {
    private RotationUtil() {
    }

    /** A rotated (but not yet scaled or re-centered) footprint, as local block-index bounds. */
    public record Footprint(int minX, int maxX, int minZ, int maxZ) {
        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }
    }

    /** How many 90-degree clockwise (compass) steps from SOUTH (the capture's baseline/identity orientation) to reach facing. */
    public static int steps(Direction facing) {
        int steps = 0;
        Direction d = Direction.SOUTH;
        while (d != facing && steps < 4) {
            d = d.getClockWise();
            steps++;
        }
        return steps;
    }

    public static Rotation rotationFor(int steps) {
        return switch (steps % 4) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static double[] rotateXZ(double x, double z, int steps) {
        return switch (steps % 4) {
            case 1 -> new double[]{-z, x};
            case 2 -> new double[]{-x, -z};
            case 3 -> new double[]{z, -x};
            default -> new double[]{x, z};
        };
    }

    public static Footprint footprint(int sizeX, int sizeZ, int steps) {
        double[] c0 = rotateXZ(0, 0, steps);
        double[] c1 = rotateXZ(sizeX - 1, 0, steps);
        double[] c2 = rotateXZ(0, sizeZ - 1, steps);
        double[] c3 = rotateXZ(sizeX - 1, sizeZ - 1, steps);
        int minX = (int) Math.min(Math.min(c0[0], c1[0]), Math.min(c2[0], c3[0]));
        int maxX = (int) Math.max(Math.max(c0[0], c1[0]), Math.max(c2[0], c3[0]));
        int minZ = (int) Math.min(Math.min(c0[1], c1[1]), Math.min(c2[1], c3[1]));
        int maxZ = (int) Math.max(Math.max(c0[1], c1[1]), Math.max(c2[1], c3[1]));
        return new Footprint(minX, maxX, minZ, maxZ);
    }
}
