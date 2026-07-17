package com.areascale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Draws an outline-only (no fill) box around the current selection every frame. */
public final class SelectionBoxRenderer {
    private static final float RED = 1.0f;
    private static final float GREEN = 0.85f;
    private static final float BLUE = 0.1f;
    private static final float ALPHA = 1.0f;

    private SelectionBoxRenderer() {
    }

    public static void render(WorldRenderContext context) {
        BlockPos p1 = ClientSelectionState.getPos1();
        BlockPos p2 = ClientSelectionState.getPos2();
        if (p1 == null && p2 == null) {
            return;
        }
        if (p2 == null) {
            p2 = p1;
        }
        if (p1 == null) {
            p1 = p2;
        }

        PoseStack matrixStack = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        if (matrixStack == null || !(consumers instanceof MultiBufferSource.BufferSource bufferSource)) {
            return;
        }

        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX()) + 1;
        int maxY = Math.max(p1.getY(), p2.getY()) + 1;
        int maxZ = Math.max(p1.getZ(), p2.getZ()) + 1;

        Vec3 camera = context.camera().getPosition();

        double x1 = minX - camera.x;
        double y1 = minY - camera.y;
        double z1 = minZ - camera.z;
        double x2 = maxX - camera.x;
        double y2 = maxY - camera.y;
        double z2 = maxZ - camera.z;

        matrixStack.pushPose();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = matrixStack.last();

        drawBoxEdges(buffer, pose, x1, y1, z1, x2, y2, z2);
        bufferSource.endBatch(RenderType.lines());

        matrixStack.popPose();
    }

    private static void drawBoxEdges(VertexConsumer buffer, PoseStack.Pose pose,
                                      double x1, double y1, double z1,
                                      double x2, double y2, double z2) {
        // Bottom face
        line(buffer, pose, x1, y1, z1, x2, y1, z1);
        line(buffer, pose, x2, y1, z1, x2, y1, z2);
        line(buffer, pose, x2, y1, z2, x1, y1, z2);
        line(buffer, pose, x1, y1, z2, x1, y1, z1);
        // Top face
        line(buffer, pose, x1, y2, z1, x2, y2, z1);
        line(buffer, pose, x2, y2, z1, x2, y2, z2);
        line(buffer, pose, x2, y2, z2, x1, y2, z2);
        line(buffer, pose, x1, y2, z2, x1, y2, z1);
        // Vertical edges
        line(buffer, pose, x1, y1, z1, x1, y2, z1);
        line(buffer, pose, x2, y1, z1, x2, y2, z1);
        line(buffer, pose, x2, y1, z2, x2, y2, z2);
        line(buffer, pose, x1, y1, z2, x1, y2, z2);
    }

    private static void line(VertexConsumer buffer, PoseStack.Pose pose,
                              double x1, double y1, double z1,
                              double x2, double y2, double z2) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0) {
            length = 1;
        }
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;

        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1)
            .setColor(RED, GREEN, BLUE, ALPHA)
            .setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2)
            .setColor(RED, GREEN, BLUE, ALPHA)
            .setNormal(pose, nx, ny, nz);
    }
}
