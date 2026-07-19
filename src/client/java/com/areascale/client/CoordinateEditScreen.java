package com.areascale.client;

import java.util.ArrayList;
import java.util.List;

import com.areascale.network.SetSelectionPointPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * "Menu hidden" by design: no background dim, no buttons, no title bar - just the six
 * coordinate values themselves, left-click to +1 / right-click to -1 (shift for +/-10),
 * so the player keeps a clear, unobstructed view of the wireframe selection box updating
 * live behind it. The game keeps running (not a pause screen).
 */
public class CoordinateEditScreen extends Screen {
    private static final int NORMAL_STEP = 1;
    private static final int FAST_STEP = 10;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int LABEL_COLOR = 0xAAAAAA;
    private static final int HINT_COLOR = 0x888888;
    private static final String[] AXIS_LABELS = {"X", "Y", "Z"};

    private final int[] pos1 = new int[3];
    private final int[] pos2 = new int[3];
    private final List<Hotspot> hotspots = new ArrayList<>();

    private record Hotspot(int x, int y, int width, int height, boolean isPos1, int axis) {
    }

    public CoordinateEditScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        BlockPos existingPos1 = ClientSelectionState.getPos1();
        BlockPos existingPos2 = ClientSelectionState.getPos2();
        BlockPos fallback = minecraft != null && minecraft.player != null
            ? minecraft.player.blockPosition()
            : BlockPos.ZERO;

        writeTo(pos1, existingPos1 != null ? existingPos1 : fallback);
        writeTo(pos2, existingPos2 != null ? existingPos2 : fallback);

        hotspots.clear();
        int baseX = 8;
        int baseY = 8;
        for (int axis = 0; axis < 3; axis++) {
            hotspots.add(new Hotspot(baseX, baseY + axis * 12, 64, 11, true, axis));
            hotspots.add(new Hotspot(baseX + 74, baseY + axis * 12, 64, 11, false, axis));
        }

        sendUpdate(true);
        sendUpdate(false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately not calling renderBackground()/super.render() - the world stays
        // fully visible behind this, no dark overlay, no widgets.
        graphics.drawString(font, "Pos 1", 8, 0, LABEL_COLOR);
        graphics.drawString(font, "Pos 2", 82, 0, LABEL_COLOR);

        for (int axis = 0; axis < 3; axis++) {
            int y = 8 + axis * 12;
            graphics.drawString(font, AXIS_LABELS[axis] + ": " + pos1[axis], 8, y, TEXT_COLOR);
            graphics.drawString(font, AXIS_LABELS[axis] + ": " + pos2[axis], 82, y, TEXT_COLOR);
        }

        graphics.drawString(font,
            "Left-click +1, right-click -1 (shift: +/-10) - Esc to close",
            8, 8 + 3 * 12 + 4, HINT_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            for (Hotspot spot : hotspots) {
                if (mouseX >= spot.x() && mouseX <= spot.x() + spot.width()
                    && mouseY >= spot.y() && mouseY <= spot.y() + spot.height()) {
                    int step = hasShiftDown() ? FAST_STEP : NORMAL_STEP;
                    int delta = button == 0 ? step : -step;
                    int[] target = spot.isPos1() ? pos1 : pos2;
                    target[spot.axis()] += delta;
                    sendUpdate(spot.isPos1());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendUpdate(boolean isPos1) {
        int[] values = isPos1 ? pos1 : pos2;
        BlockPos pos = new BlockPos(values[0], values[1], values[2]);
        if (isPos1) {
            ClientSelectionState.setPos1(pos);
        } else {
            ClientSelectionState.setPos2(pos);
        }
        ClientPlayNetworking.send(new SetSelectionPointPayload(isPos1 ? 1 : 2, pos));
    }

    private static void writeTo(int[] target, BlockPos pos) {
        target[0] = pos.getX();
        target[1] = pos.getY();
        target[2] = pos.getZ();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
