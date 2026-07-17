package com.areascale;

import com.areascale.block.ModBlockEntities;
import com.areascale.block.ModBlocks;
import com.areascale.command.AreaScaleCommand;
import com.areascale.network.ClearSelectionPayload;
import com.areascale.network.SetSelectionPointPayload;
import com.areascale.selection.SelectionManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AreaScaleMod implements ModInitializer {
    public static final String MOD_ID = "areascale";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.initialize();
        ModBlocks.initialize();
        ModBlockEntities.initialize();

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> {
                entries.accept(ModItems.SELECTION_WAND);
                entries.accept(ModItems.STRUCTURE_CAPSULE);
                entries.accept(ModBlocks.DISPLAY_PLATFORM);
                entries.accept(ModBlocks.STRUCTURE_PLACER);
            });

        PayloadTypeRegistry.playC2S().register(SetSelectionPointPayload.TYPE, SetSelectionPointPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClearSelectionPayload.TYPE, ClearSelectionPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetSelectionPointPayload.TYPE, (payload, context) ->
            context.server().execute(() ->
                SelectionManager.setPoint(context.player(), payload.point(), payload.pos())));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            AreaScaleCommand.register(dispatcher));

        LOGGER.info("Area Scale initialized.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
