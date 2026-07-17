package com.areascale.client;

import com.areascale.ModItems;
import com.areascale.network.ClearSelectionPayload;
import com.areascale.network.SetSelectionPointPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

public class AreaScaleModClient implements ClientModInitializer {
    private static final KeyMapping CONFIRM_SCALE_KEY = new KeyMapping(
        "key.areascale.confirm_scale",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_K,
        "key.categories.areascale"
    );

    private static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
        "key.areascale.open_settings",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,
        "key.categories.areascale"
    );

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(CONFIRM_SCALE_KEY);
        KeyBindingHelper.registerKeyBinding(OPEN_SETTINGS_KEY);

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!isWand(stack)) {
                return InteractionResult.PASS;
            }
            ClientSelectionState.setPos1(pos);
            ClientPlayNetworking.send(new SetSelectionPointPayload(1, pos));
            return InteractionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!isWand(stack)) {
                return InteractionResult.PASS;
            }
            ClientSelectionState.setPos2(hitResult.getBlockPos());
            ClientPlayNetworking.send(new SetSelectionPointPayload(2, hitResult.getBlockPos()));
            return InteractionResult.FAIL;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (CONFIRM_SCALE_KEY.consumeClick()) {
                if (client.player != null) {
                    String alias = AreaScaleClientConfig.get().chatAlias;
                    client.setScreen(new ChatScreen("/" + alias + " "));
                }
            }
            while (OPEN_SETTINGS_KEY.consumeClick()) {
                if (client.player != null) {
                    client.setScreen(new AreaScaleSettingsScreen());
                }
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(SelectionBoxRenderer::render);

        ClientPlayNetworking.registerGlobalReceiver(ClearSelectionPayload.TYPE, (payload, context) -> {
            ClientSelectionState.setPos1(null);
            ClientSelectionState.setPos2(null);
        });
    }

    private static boolean isWand(ItemStack stack) {
        return stack.getItem() == ModItems.SELECTION_WAND;
    }
}
