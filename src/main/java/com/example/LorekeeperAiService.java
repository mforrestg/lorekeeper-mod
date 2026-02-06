package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;

public final class LorekeeperAiService {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final int MAX_NEWS_INPUT_CHARS = 6000;
    private static final int MAX_HISTORY_INPUT_CHARS = 8000;
    private static final Set<String> PENDING_NEWS = ConcurrentHashMap.newKeySet();
    private static final Set<String> PENDING_HISTORY = ConcurrentHashMap.newKeySet();

    private LorekeeperAiService() {}

    public static String getOrCreateWeeklySummary(MinecraftServer server, long weekNumber, List<LoreStorage.LoreEntry> entries) {
        LoreStorage storage = LoreStorage.get(server);
        String existing = storage.getAiNewsSummary(weekNumber);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        if (!isAiEnabled()) {
            return null;
        }
        if (entries.isEmpty()) {
            return null;
        }
        String prompt = buildWeeklyPrompt(weekNumber, entries);
        String summary = requestSummary(prompt, 600, modelForNews());
        if (summary != null && !summary.isBlank()) {
            storage.setAiNewsSummary(weekNumber, summary.trim());
            return summary.trim();
        }
        return null;
    }

    public static String getOrCreateWeeklySummaryNonBlocking(
        MinecraftServer server,
        long weekNumber,
        List<LoreStorage.LoreEntry> entries
    ) {
        LoreStorage storage = LoreStorage.get(server);
        String existing = storage.getAiNewsSummary(weekNumber);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        if (!isAiEnabled() || entries.isEmpty()) {
            return null;
        }
        String key = "news:" + weekNumber;
        if (!PENDING_NEWS.add(key)) {
            return null;
        }
        String prompt = buildWeeklyPrompt(weekNumber, entries);
        CompletableFuture
            .supplyAsync(() -> requestSummary(prompt, 600, modelForNews()))
            .thenAccept(summary -> server.execute(() -> {
                try {
                    if (summary != null && !summary.isBlank()) {
                        storage.setAiNewsSummary(weekNumber, summary.trim());
                    }
                } finally {
                    PENDING_NEWS.remove(key);
                }
            }));
        return null;
    }

    public static String getOrCreateHistorySummary(MinecraftServer server, List<LoreStorage.LoreEntry> entries) {
        LoreStorage storage = LoreStorage.get(server);
        long latestTimestamp = storage.getLatestEntryTimestamp();
        String existing = storage.getAiHistorySummary();
        if (!existing.isBlank() && storage.getAiHistoryTimestamp() == latestTimestamp) {
            return existing;
        }
        if (!isAiEnabled()) {
            return null;
        }
        if (entries.isEmpty()) {
            return null;
        }
        String prompt = buildHistoryPrompt(entries);
        String summary = requestSummary(prompt, 1200, modelForHistory());
        if (summary != null && !summary.isBlank()) {
            storage.setAiHistorySummary(summary.trim(), latestTimestamp);
            return summary.trim();
        }
        return null;
    }

    public static String getOrCreateHistorySummaryNonBlocking(MinecraftServer server, List<LoreStorage.LoreEntry> entries) {
        LoreStorage storage = LoreStorage.get(server);
        long latestTimestamp = storage.getLatestEntryTimestamp();
        String existing = storage.getAiHistorySummary();
        if (!existing.isBlank() && storage.getAiHistoryTimestamp() == latestTimestamp) {
            return existing;
        }
        if (!isAiEnabled() || entries.isEmpty()) {
            return null;
        }
        String key = "history:" + latestTimestamp;
        if (!PENDING_HISTORY.add(key)) {
            return null;
        }
        String prompt = buildHistoryPrompt(entries);
        CompletableFuture
            .supplyAsync(() -> requestSummary(prompt, 1200, modelForHistory()))
            .thenAccept(summary -> server.execute(() -> {
                try {
                    if (summary != null && !summary.isBlank()) {
                        storage.setAiHistorySummary(summary.trim(), latestTimestamp);
                    }
                } finally {
                    PENDING_HISTORY.remove(key);
                }
            }));
        return null;
    }

    public static String generateInterviewReaction(String playerName, String question, String answer) {
        if (!isAiEnabled()) {
            return null;
        }
        String prompt = buildInterviewPrompt(playerName, question, answer);
        return requestSummary(prompt, 80, modelForInterview());
    }

    private static boolean isAiEnabled() {
        if (LorekeeperMod.CONFIG == null || !LorekeeperMod.CONFIG.aiEnabled) {
            LorekeeperMod.LOGGER.info("AI disabled or config missing; using fallback responses.");
            return false;
        }
        return true;
    }

    private static String buildWeeklyPrompt(long weekNumber, List<LoreStorage.LoreEntry> entries) {
        long startDay = weekNumber * 7L;
        long endDay = startDay + 6L;
        StringBuilder builder = new StringBuilder();
        builder.append("You are the Lorekeeper, a historian in a Minecraft server.\n");
        builder.append("Write a concise weekly newspaper summary for Week ").append(weekNumber)
            .append(" (Days ").append(startDay).append("-").append(endDay).append(").\n");
        builder.append("Tone: in-world, grounded, evocative. Avoid out-of-world references.\n");
        builder.append("Length: 2-6 short paragraphs. No bullet lists.\n");
        builder.append("Use player names when relevant.\n\n");
        builder.append("Lore entries:\n");
        builder.append(buildEntryList(entries, MAX_NEWS_INPUT_CHARS));
        return builder.toString();
    }

    private static String buildHistoryPrompt(List<LoreStorage.LoreEntry> entries) {
        LoreStorage.LoreEntry first = entries.get(0);
        LoreStorage.LoreEntry last = entries.get(entries.size() - 1);
        StringBuilder builder = new StringBuilder();
        builder.append("You are the Lorekeeper, a historian in a Minecraft server.\n");
        builder.append("Write a cohesive server history narrative based on the lore entries.\n");
        builder.append("Tone: archival, mythic but readable. Avoid out-of-world references.\n");
        builder.append("Length: 6-12 short paragraphs. No bullet lists.\n");
        builder.append("Use player names and locations when relevant.\n");
        builder.append("Coverage window: ")
            .append(DATE_FORMAT.format(Instant.ofEpochMilli(first.timestamp())))
            .append(" to ")
            .append(DATE_FORMAT.format(Instant.ofEpochMilli(last.timestamp())))
            .append(".\n\n");
        builder.append("Lore entries:\n");
        builder.append(buildEntryList(entries, MAX_HISTORY_INPUT_CHARS));
        return builder.toString();
    }

    private static String buildInterviewPrompt(String playerName, String question, String answer) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are the Lorekeeper, interviewing a player in Minecraft.\n");
        builder.append("Provide a brief, in-world reaction (1-2 sentences). Do not ask a new question.\n");
        builder.append("Tone: curious, respectful, slightly mysterious.\n");
        builder.append("Player: ").append(playerName).append("\n");
        builder.append("Question: ").append(question).append("\n");
        builder.append("Answer: ").append(answer).append("\n");
        return builder.toString();
    }

    private static String buildEntryList(List<LoreStorage.LoreEntry> entries, int maxChars) {
        StringBuilder builder = new StringBuilder();
        int included = 0;
        for (LoreStorage.LoreEntry entry : entries) {
            String line = "- " + entry.author() + ": " + entry.text();
            if (builder.length() + line.length() + 1 > maxChars) {
                break;
            }
            builder.append(line).append('\n');
            included++;
        }
        int remaining = entries.size() - included;
        if (remaining > 0) {
            builder.append("(+ ").append(remaining).append(" more entries not shown)\n");
        }
        return builder.toString().trim();
    }

    private static String requestSummary(String prompt, int maxTokens, String modelOverride) {
        AiProvider primary = buildPrimaryProvider();
        String response = requestSummary(primary, prompt, maxTokens, modelOverride);
        if (response != null) {
            return response;
        }
        if (LorekeeperMod.CONFIG != null && LorekeeperMod.CONFIG.fallbackEnabled) {
            AiProvider fallback = buildFallbackProvider();
            return requestSummary(fallback, prompt, maxTokens, modelOverride);
        }
        return null;
    }

    private static AiProvider buildPrimaryProvider() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        String baseUrl = config == null ? "https://api.openai.com/v1" : config.apiBaseUrl;
        String apiKeyEnv = config == null ? "OPENAI_API_KEY" : config.apiKeyEnv;
        String model = config == null ? "gpt-4o-mini" : config.model;
        String provider = config == null ? "openai" : config.aiProvider;
        String apiKey = readEnv(apiKeyEnv);
        if (apiKey == null || apiKey.isBlank()) {
            LorekeeperMod.LOGGER.warn("AI API key missing for env var {}", apiKeyEnv);
        }
        return new AiProvider(provider, baseUrl, apiKey, model);
    }

    private static AiProvider buildFallbackProvider() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        if (config == null) {
            return null;
        }
        String apiKey = readEnv(config.fallbackApiKeyEnv);
        if (apiKey == null || apiKey.isBlank()) {
            LorekeeperMod.LOGGER.warn("AI fallback API key missing for env var {}", config.fallbackApiKeyEnv);
        }
        return new AiProvider(
            config.fallbackProvider,
            config.fallbackApiBaseUrl,
            apiKey,
            config.fallbackModel
        );
    }

    private static String readEnv(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return System.getenv(name.trim());
    }

    private static String requestSummary(
        AiProvider provider,
        String prompt,
        int maxTokens,
        String modelOverride
    ) {
        if (provider == null || provider.apiKey() == null || provider.apiKey().isBlank()) {
            return null;
        }
        String baseUrl = provider.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            LorekeeperMod.LOGGER.warn("AI base URL missing for provider {}", provider.providerType());
            return null;
        }
        String model = modelOverride;
        if (model == null || model.isBlank()) {
            model = provider.model();
        }
        if (model == null || model.isBlank()) {
            model = "gpt-4o-mini";
        }
        String providerType = provider.providerType();

        if (isChatProvider(providerType)) {
            return requestChatCompletions(baseUrl, provider.apiKey(), model, prompt, maxTokens);
        }
        String response = requestResponses(baseUrl, provider.apiKey(), model, prompt, maxTokens);
        if (response != null) {
            return response;
        }
        return requestChatCompletions(baseUrl, provider.apiKey(), model, prompt, maxTokens);
    }

    private static JsonObject message(String role, String content) {
        JsonObject obj = new JsonObject();
        obj.addProperty("role", role);
        obj.addProperty("content", content);
        return obj;
    }

    private static String modelForNews() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        if (config != null) {
            if (config.modelNews != null && !config.modelNews.isBlank()) {
                return config.modelNews;
            }
            if (config.model != null && !config.model.isBlank()) {
                return config.model;
            }
        }
        return "gpt-4o-mini";
    }

    private static String modelForInterview() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        if (config != null) {
            if (config.modelInterview != null && !config.modelInterview.isBlank()) {
                return config.modelInterview;
            }
            if (config.model != null && !config.model.isBlank()) {
                return config.model;
            }
        }
        return "gpt-4o-mini";
    }

    private static String modelForHistory() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        if (config != null) {
            if (config.modelHistory != null && !config.modelHistory.isBlank()) {
                return config.modelHistory;
            }
            if (config.model != null && !config.model.isBlank()) {
                return config.model;
            }
        }
        return "gpt-4o";
    }

    private static boolean isChatProvider(String providerType) {
        if (providerType == null) {
            return false;
        }
        String normalized = providerType.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("chat");
    }

    private static String requestResponses(
        String baseUrl,
        String apiKey,
        String model,
        String prompt,
        int maxTokens
    ) {
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "responses" : baseUrl + "/responses";
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray input = new JsonArray();
        input.add(message("system", "You are a concise, in-world historian for a Minecraft server."));
        input.add(message("user", prompt));
        body.add("input", input);
        body.addProperty("max_output_tokens", maxTokens);
        body.addProperty("temperature", 0.7);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(LorekeeperMod.CONFIG != null ? LorekeeperMod.CONFIG.requestTimeoutSeconds : 30))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        return sendRequest(request);
    }

    private static String requestChatCompletions(
        String baseUrl,
        String apiKey,
        String model,
        String prompt,
        int maxTokens
    ) {
        String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray messages = new JsonArray();
        messages.add(message("system", "You are a concise, in-world historian for a Minecraft server."));
        messages.add(message("user", prompt));
        body.add("messages", messages);
        body.addProperty("max_tokens", maxTokens);
        body.addProperty("temperature", 0.7);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(LorekeeperMod.CONFIG != null ? LorekeeperMod.CONFIG.requestTimeoutSeconds : 30))
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

        return sendRequest(request);
    }

    private static String sendRequest(HttpRequest request) {
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LorekeeperMod.LOGGER.warn("AI request failed with status {}", response.statusCode());
            return null;
        }
        return extractText(response.body());
    } catch (Exception e) {
        LorekeeperMod.LOGGER.warn("AI request failed", e);
        return null;
    }
    }

    private static String extractText(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonElement outputText = root.get("output_text");
            if (outputText != null && outputText.isJsonPrimitive()) {
                return outputText.getAsString();
            }

            JsonElement output = root.get("output");
            if (output != null && output.isJsonArray()) {
                JsonArray outputArray = output.getAsJsonArray();
                for (JsonElement item : outputArray) {
                    if (!item.isJsonObject()) {
                        continue;
                    }
                    JsonElement content = item.getAsJsonObject().get("content");
                    if (content != null && content.isJsonArray()) {
                        for (JsonElement contentItem : content.getAsJsonArray()) {
                            if (!contentItem.isJsonObject()) {
                                continue;
                            }
                            JsonObject contentObj = contentItem.getAsJsonObject();
                            String type = contentObj.has("type") ? contentObj.get("type").getAsString() : "";
                            if ("output_text".equals(type) && contentObj.has("text")) {
                                return contentObj.get("text").getAsString();
                            }
                            if (contentObj.has("text")) {
                                return contentObj.get("text").getAsString();
                            }
                        }
                    }
                }
            }

            JsonElement choices = root.get("choices");
            if (choices != null && choices.isJsonArray()) {
                JsonArray choiceArray = choices.getAsJsonArray();
                if (!choiceArray.isEmpty()) {
                    JsonObject first = choiceArray.get(0).getAsJsonObject();
                    if (first.has("message")) {
                        JsonObject message = first.getAsJsonObject("message");
                        if (message.has("content")) {
                            return message.get("content").getAsString();
                        }
                    }
                    if (first.has("text")) {
                        return first.get("text").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            LorekeeperMod.LOGGER.warn("Failed to parse AI response", e);
        }
        return null;
    }

    private record AiProvider(String providerType, String baseUrl, String apiKey, String model) {}
}
