package com.areascale.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Per-player preference: what the confirm-scale keybind pre-fills in chat. Purely a personal shorthand - the server still only recognizes whatever command literal(s) it actually registered. */
public class AreaScaleClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("areascale-client-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("areascale-client.json");

    private static AreaScaleClientConfig instance;

    public String chatAlias = "areascale";

    public static synchronized AreaScaleClientConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AreaScaleClientConfig load() {
        if (Files.exists(PATH)) {
            try {
                String json = Files.readString(PATH, StandardCharsets.UTF_8);
                AreaScaleClientConfig loaded = GSON.fromJson(json, AreaScaleClientConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Failed to read config/areascale-client.json, using defaults", e);
            }
        }
        AreaScaleClientConfig config = new AreaScaleClientConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write config/areascale-client.json", e);
        }
    }
}
