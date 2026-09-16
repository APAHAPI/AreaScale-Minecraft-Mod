package com.areascale.client;

import com.areascale.network.CaptureRequestPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The command-free way to capture: type a number, then pick Shrink or Expand. It sends the
 * exact request the command would ("Shrink" + 4 == /areascale shrink 4), so it also works in
 * singleplayer Hardcore worlds where commands are disabled. Keeps the mod's minimal look (no
 * dim, no blur, the world stays visible), with a light panel so the field stays readable.
 */
public class CaptureScreen extends Screen {
    private static final int PANEL_COLOR = 0xC0000000;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int ERROR_COLOR = 0xFFFF5555;

    private static final int PANEL_WIDTH = 224;
    private static final int PANEL_HEIGHT = 104;
    private static final int FIELD_WIDTH = 120;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;

    private EditBox factorField;
    private String error;

    public CaptureScreen() {
        super(Component.translatable("areascale.capture.title"));
    }

    @Override
    protected void init() {
        int fieldX = width / 2 - FIELD_WIDTH / 2;
        int fieldY = panelTop() + 34;

        factorField = new EditBox(font, fieldX, fieldY, FIELD_WIDTH, FIELD_HEIGHT,
            Component.translatable("areascale.capture.field"));
        factorField.setHint(Component.translatable("areascale.capture.field_hint"));
        factorField.setMaxLength(10);
        factorField.setValue("4");
        factorField.setResponder(value -> error = null);
        addRenderableWidget(factorField);
        setInitialFocus(factorField);

        int buttonY = fieldY + FIELD_HEIGHT + GAP;
        addRenderableWidget(Button.builder(Component.translatable("areascale.capture.shrink"), b -> submit(false))
            .bounds(width / 2 - BUTTON_WIDTH - GAP / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("areascale.capture.expand"), b -> submit(true))
            .bounds(width / 2 + GAP / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /**
     * The game's real entry point renders the background unconditionally before the screen's
     * own contents, so overriding this (not extractRenderState) is what keeps the world visible.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty - no dim, no blur.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = panelLeft();
        int top = panelTop();
        // Drawn before the widgets so it sits behind the field and buttons, matching the
        // backdrop-behind-text order vanilla itself uses.
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_COLOR);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        graphics.centeredText(font, title, width / 2, top + 10, TITLE_COLOR);
        if (error != null) {
            graphics.centeredText(font, Component.literal(error), width / 2, top + 88, ERROR_COLOR);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void submit(boolean expand) {
        Double factor = parseFactor();
        if (factor == null) {
            error = Component.translatable("areascale.capture.invalid").getString();
            return;
        }

        ClientPlayNetworking.send(new CaptureRequestPayload(factor, expand));
        onClose();
    }

    /** The typed value, or null if it isn't a usable positive number. */
    private Double parseFactor() {
        try {
            double factor = Double.parseDouble(factorField.getValue().trim());
            if (!(factor > 0.0) || Double.isInfinite(factor)) {
                return null;
            }
            return factor;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int panelLeft() {
        return width / 2 - PANEL_WIDTH / 2;
    }

    private int panelTop() {
        return height / 2 - PANEL_HEIGHT / 2;
    }
}
