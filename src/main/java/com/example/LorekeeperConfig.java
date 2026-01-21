package com.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class LorekeeperConfig {
    public static final String FILE_NAME = "lorekeeper.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean aiEnabled = false;
    public String aiProvider = "openai";
    public String apiBaseUrl = "https://api.openai.com/v1";
    public String apiKeyEnv = "OPENAI_API_KEY";
    public String model = "gpt-4o-mini";
    public String modelNews = "gpt-4o-mini";
    public String modelInterview = "gpt-4o-mini";
    public String modelHistory = "gpt-4o";
    public int requestTimeoutSeconds = 30;
    public boolean fallbackEnabled = false;
    public String fallbackProvider = "openai";
    public String fallbackApiBaseUrl = "";
    public String fallbackApiKeyEnv = "";
    public String fallbackModel = "";

    public static LorekeeperConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                LorekeeperConfig config = GSON.fromJson(json, LorekeeperConfig.class);
                return config != null ? config : new LorekeeperConfig();
            } catch (IOException e) {
                LorekeeperMod.LOGGER.warn("Failed to read config {}, using defaults.", path, e);
                return new LorekeeperConfig();
            }
        }

        LorekeeperConfig config = new LorekeeperConfig();
        config.save(path);
        return config;
    }

    private void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LorekeeperMod.LOGGER.warn("Failed to write config {}", path, e);
        }
    }
}
