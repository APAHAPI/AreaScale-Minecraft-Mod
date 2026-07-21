package com.areascale.client;

import com.areascale.network.SetSelectionPointPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * No background dim, no title bar - just real, clickable buttons for the six coordinate
 * values, so the player keeps a clear view of the wireframe selection box updating live
 * behind it. Left-click a value to +1, right-click to -1 (shift for +/-10). The game keeps
 * running (not a pause screen).
 */
public class CoordinateEditScreen extends Screen {
    private static final int NORMAL_STEP = 1;
    private static final int FAST_STEP = 10;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int HINT_COLOR = 0xFF888888;
    private static final int PANEL_COLOR = 0xC0000000;
    private static final String[] AXIS_LABELS = {"X", "Y", "Z"};
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 14;
    private static final int PANEL_MARGIN = 4;

    private final int[] pos1 = new int[3];
    private final int[] pos2 = new int[3];

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

        int baseX = 8;
        int baseY = 20;
        for (int axis = 0; axis < 3; axis++) {
            int y = baseY + axis * (BUTTON_HEIGHT + 2);
            addRenderableWidget(new StepButton(baseX, y, pos1, axis, true));
            addRenderableWidget(new StepButton(baseX + BUTTON_WIDTH + 10, y, pos2, axis, false));
        }

        sendUpdate(true);
        sendUpdate(false);
    }

    /**
     * The game's actual entry point is the final Screen#renderWithTooltip, which calls
     * renderBackground() UNCONDITIONALLY before render() - that's what was drawing the
     * panorama/blur/menu-background regardless of what render() itself did. Overriding
     * render() alone can never suppress it; this is the method that actually controls it.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty - no panorama, no blur, no menu-background texture. The world
        // stays fully visible and sharp behind this screen.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int hintY = 20 + 3 * (BUTTON_HEIGHT + 2) + 4;
        int panelRight = 8 + BUTTON_WIDTH + 10 + BUTTON_WIDTH + PANEL_MARGIN;
        int panelBottom = hintY + font.lineHeight + PANEL_MARGIN;
        // Drawn before the buttons/text so it sits behind them - fill() immediately followed
        // by text()/widget rendering in the same extractRenderState() call is the same order
        // vanilla itself uses for backdrop-behind-text, so this reliably stays behind
        // everything drawn after it.
        graphics.fill(PANEL_MARGIN, PANEL_MARGIN, panelRight, panelBottom, PANEL_COLOR);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.text(font, "Pos 1", 8, 8, LABEL_COLOR, true);
        graphics.text(font, "Pos 2", 8 + BUTTON_WIDTH + 10, 8, LABEL_COLOR, true);

        graphics.text(font,
            "Left-click +1, right-click -1 (shift: +/-10) - Esc to close",
            8, hintY, HINT_COLOR, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    /**
     * A real vanilla Button so it's unmistakably visible/clickable (matching what the button
     * background sprite renders), but with left/right click both wired up to step the value -
     * vanilla Button only ever fires onPress for left-click by default, so mouseClicked is
     * overridden directly here instead of relying on onPress/isValidClickButton.
     */
    private final class StepButton extends Button {
        private final int[] target;
        private final int axis;

        StepButton(int x, int y, int[] target, int axis, boolean isPos1) {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, labelFor(target, axis), b -> {
            }, DEFAULT_NARRATION);
            this.target = target;
            this.axis = axis;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(graphics);
            extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR));
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!active || !visible || (event.button() != 0 && event.button() != 1) || !isMouseOver(event.x(), event.y())) {
                return false;
            }
            int step = event.hasShiftDown() ? FAST_STEP : NORMAL_STEP;
            target[axis] += event.button() == 0 ? step : -step;
            setMessage(labelFor(target, axis));
            sendUpdate(target == pos1);
            playDownSound(minecraft.getSoundManager());
            return true;
        }

        private static Component labelFor(int[] target, int axis) {
            return Component.literal(AXIS_LABELS[axis] + ": " + target[axis]);
        }
    }
}
