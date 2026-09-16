package com.areascale;

import com.areascale.block.ModBlockEntities;
import com.areascale.block.ModBlocks;
import com.areascale.capture.CaptureService;
import com.areascale.command.AreaScaleCommand;
import com.areascale.network.CaptureRequestPayload;
import com.areascale.network.ClearSelectionPayload;
import com.areascale.network.SetSelectionPointPayload;
import com.areascale.selection.SelectionManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
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

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .register(entries -> {
                entries.accept(ModItems.SELECTION_WAND);
                entries.accept(ModItems.STRUCTURE_CAPSULE);
                entries.accept(ModBlocks.DISPLAY_PLATFORM);
                entries.accept(ModBlocks.STRUCTURE_PLACER);
            });

        PayloadTypeRegistry.serverboundPlay().register(SetSelectionPointPayload.TYPE, SetSelectionPointPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CaptureRequestPayload.TYPE, CaptureRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClearSelectionPayload.TYPE, ClearSelectionPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SetSelectionPointPayload.TYPE, (payload, context) ->
            context.server().execute(() ->
                SelectionManager.setPoint(context.player(), payload.point(), payload.pos())));

        ServerPlayNetworking.registerGlobalReceiver(CaptureRequestPayload.TYPE, (payload, context) ->
            context.server().execute(() -> handleCaptureRequest(context.player(), payload)));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            AreaScaleCommand.register(dispatcher));

        LOGGER.info("Area Scale initialized.");
    }

    /**
     * Runs a capture requested by the client-side Capture Screen. The /areascale command is the
     * normal, permission-checked route; this packet exists so the screen still works in
     * singleplayer Hardcore worlds, where commands are disabled outright and there is no op to
     * grant them to. On a dedicated server we keep the command's permission bar, so this can't be
     * used to capture without op - a singleplayer world is only ever the host's own world.
     */
    private static void handleCaptureRequest(ServerPlayer player, CaptureRequestPayload payload) {
        boolean permitted = player.level().getServer().isSingleplayer()
            || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        if (!permitted) {
            player.sendSystemMessage(Component.translatable("commands.areascale.not_permitted"));
            return;
        }

        player.sendSystemMessage(CaptureService.capture(player, payload.factor(), payload.expand()).message());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
