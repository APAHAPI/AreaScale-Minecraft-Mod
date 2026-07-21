package com.areascale.client;

import com.areascale.AreaScaleMod;
import com.areascale.ModItems;
import com.areascale.network.ClearSelectionPayload;
import com.areascale.network.SetSelectionPointPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

public class AreaScaleModClient implements ClientModInitializer {
    private static final KeyMapping.Category KEY_CATEGORY =
        KeyMapping.Category.register(AreaScaleMod.id("main"));

    private static final KeyMapping EDIT_COORDINATES_KEY = new KeyMapping(
        "key.areascale.edit_coordinates",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_U,
        KEY_CATEGORY
    );

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(EDIT_COORDINATES_KEY);

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
                    client.player.sendOverlayMessage(Component.translatable("commands.areascale.select_first"));
                    continue;
                }
                client.gui.setScreen(new CoordinateEditScreen());
            }
        });

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(SelectionBoxRenderer::render);

        ClientPlayNetworking.registerGlobalReceiver(ClearSelectionPayload.TYPE, (payload, context) -> {
            ClientSelectionState.setPos1(null);
            ClientSelectionState.setPos2(null);
        });
    }

    private static boolean isWand(ItemStack stack) {
        return stack.getItem() == ModItems.SELECTION_WAND;
    }
}
