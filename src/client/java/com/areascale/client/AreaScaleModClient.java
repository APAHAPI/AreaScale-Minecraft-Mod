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
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

public class AreaScaleModClient implements ClientModInitializer {
    private static final KeyMapping EDIT_COORDINATES_KEY = new KeyMapping(
        "key.areascale.edit_coordinates",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_U,
        "key.categories.areascale"
    );

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(EDIT_COORDINATES_KEY);

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
            while (EDIT_COORDINATES_KEY.consumeClick()) {
                if (client.player == null) {
                    continue;
                }
                if (ClientSelectionState.getPos1() == null || ClientSelectionState.getPos2() == null) {
                    client.player.displayClientMessage(Component.translatable("commands.areascale.select_first"), true);
                    continue;
                }
                client.setScreen(new CoordinateEditScreen());
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
