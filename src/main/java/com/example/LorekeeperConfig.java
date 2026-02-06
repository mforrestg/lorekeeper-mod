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

    public static final class EncounterTriggerConfig {
        public double baseChance;
        public double maxChance;
        public double firstTimeBonus;

        public EncounterTriggerConfig() {}

        public EncounterTriggerConfig(double baseChance, double maxChance, double firstTimeBonus) {
            this.baseChance = baseChance;
            this.maxChance = maxChance;
            this.firstTimeBonus = firstTimeBonus;
        }
    }

    public boolean aiEnabled = false;
    public String aiProvider = "openai";
    public String apiBaseUrl = "https://api.openai.com/v1";
    public String apiKeyEnv = "OPENAI_API_KEY";
    public String model = "gpt-4o-mini";
    public String modelNews = "gpt-4o-mini";
    public String modelInterview = "gpt-4o-mini";
    public String modelHistory = "gpt-4o";
    public int requestTimeoutSeconds = 30;
    public int interviewTimeoutSeconds = 120;
    public boolean interviewPrivateResponses = true;
    public boolean interviewAllowOptOut = true;
    public boolean fallbackEnabled = false;
    public String fallbackProvider = "openai";
    public String fallbackApiBaseUrl = "";
    public String fallbackApiKeyEnv = "";
    public String fallbackModel = "";

    public double encounterNearbyRadius = 16.0;
    public int encounterCooldownSeconds = 180;
    public int combatCooldownSeconds = 15;
    public int chunkStayMinutes = 5;
    public int lorekeeperDespawnMinutes = 10;
    public double luckRampStep = 0.05;
    public double firstEncounterBonus = 0.20;
    public int maxSettlementChunks = 48;
    public java.util.Map<String, EncounterTriggerConfig> encounterTriggers = defaultEncounterTriggers();

    public int newsPrice = 1;
    public int historyPrice = 5;
    public int submissionReward = 10;
    public int tradeMaxUses = 12;
    public float tradePriceMultiplier = 0.05f;
    public double lorekeeperMaxWanderDistance = 12.0;
    public double lorekeeperReturnSpeed = 0.4;
    public double lorekeeperBaseSpeed = 0.35;

    private static java.util.Map<String, EncounterTriggerConfig> defaultEncounterTriggers() {
        java.util.Map<String, EncounterTriggerConfig> map = new java.util.LinkedHashMap<>();
        map.put("crafting_table_placed", new EncounterTriggerConfig(0.25, 0.85, 0.10));
        map.put("enchanting_table_placed", new EncounterTriggerConfig(0.25, 0.85, 0.15));
        map.put("anvil_placed", new EncounterTriggerConfig(0.20, 0.80, 0.10));
        map.put("brewing_stand_placed", new EncounterTriggerConfig(0.20, 0.80, 0.10));
        map.put("settlement_placed", new EncounterTriggerConfig(0.15, 0.70, 0.05));
        map.put("enchanting_table_used", new EncounterTriggerConfig(0.20, 0.80, 0.15));
        map.put("anvil_used", new EncounterTriggerConfig(0.20, 0.80, 0.10));
        map.put("brewing_stand_used", new EncounterTriggerConfig(0.20, 0.80, 0.10));
        map.put("enter_nether", new EncounterTriggerConfig(0.40, 0.90, 0.20));
        map.put("enter_end", new EncounterTriggerConfig(0.50, 0.90, 0.25));
        map.put("equip_diamond_pickaxe", new EncounterTriggerConfig(0.30, 0.85, 0.20));
        map.put("equip_netherite", new EncounterTriggerConfig(0.35, 0.90, 0.25));
        map.put("chunk_stay", new EncounterTriggerConfig(0.20, 0.75, 0.10));
        return map;
    }

    public static LorekeeperConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                LorekeeperConfig config = GSON.fromJson(json, LorekeeperConfig.class);
                if (config == null) {
                    return new LorekeeperConfig();
                }
                if (config.encounterTriggers == null) {
                    config.encounterTriggers = defaultEncounterTriggers();
                }
                return config;
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
