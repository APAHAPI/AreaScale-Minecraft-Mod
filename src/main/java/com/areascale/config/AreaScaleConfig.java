package com.areascale.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side config, edited by hand at config/areascale.json. */
public class AreaScaleConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("areascale-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("areascale.json");

    private static AreaScaleConfig instance;

    /** Blocks a single /areascale capture may contain. 0 or negative means unlimited. */
    public long maxCaptureBlocks = 500_000L;

    public static synchronized AreaScaleConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static AreaScaleConfig load() {
        if (Files.exists(PATH)) {
            try {
                String json = Files.readString(PATH, StandardCharsets.UTF_8);
                AreaScaleConfig loaded = GSON.fromJson(json, AreaScaleConfig.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.error("Failed to read config/areascale.json, using defaults", e);
            }
        }
        AreaScaleConfig config = new AreaScaleConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write config/areascale.json", e);
        }
    }
}
