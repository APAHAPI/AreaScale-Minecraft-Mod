package com.areascale.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A tiny personal-preference screen: set what the confirm-scale keybind types into chat for you. */
public class AreaScaleSettingsScreen extends Screen {
    private EditBox aliasBox;

    public AreaScaleSettingsScreen() {
        super(Component.translatable("areascale.settings.title"));
    }

    @Override
    protected void init() {
        aliasBox = new EditBox(font, width / 2 - 100, height / 2 - 30, 200, 20,
            Component.translatable("areascale.settings.alias"));
        aliasBox.setMaxLength(32);
        aliasBox.setValue(AreaScaleClientConfig.get().chatAlias);
        addRenderableWidget(aliasBox);

        addRenderableWidget(Button.builder(Component.translatable("areascale.settings.save"), button -> save())
            .bounds(width / 2 - 100, height / 2, 95, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(width / 2 + 5, height / 2, 95, 20)
            .build());

        setInitialFocus(aliasBox);
    }

    private void save() {
        String value = aliasBox.getValue().trim();
        if (!value.isEmpty()) {
            AreaScaleClientConfig config = AreaScaleClientConfig.get();
            config.chatAlias = value;
            config.save();
        }
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
