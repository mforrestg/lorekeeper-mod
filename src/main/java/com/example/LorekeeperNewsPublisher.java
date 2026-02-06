package com.example;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public final class LorekeeperNewsPublisher {
    private LorekeeperNewsPublisher() {}

    public static PublishResult publishWeeklyNews(MinecraftServer server, boolean announce) {
        long weekNumber = LoreStorage.getCurrentWeek(server);
        return publishWeeklyNews(server, weekNumber, announce, true);
    }

    public static PublishResult publishWeeklyNews(MinecraftServer server, boolean announce, boolean allowAiSync) {
        long weekNumber = LoreStorage.getCurrentWeek(server);
        return publishWeeklyNews(server, weekNumber, announce, allowAiSync);
    }

    public static PublishResult publishWeeklyNews(MinecraftServer server, long weekNumber, boolean announce) {
        return publishWeeklyNews(server, weekNumber, announce, true);
    }

    public static PublishResult publishWeeklyNews(
        MinecraftServer server,
        long weekNumber,
        boolean announce,
        boolean allowAiSync
    ) {
        LoreStorage storage = LoreStorage.get(server);
        boolean alreadyPublished = storage.hasNewsSnapshot(weekNumber);
        List<LoreStorage.LoreEntry> entries =
            storage.getOrCreateNewsSnapshot(weekNumber, LoreBooks.NEWS_ENTRY_LIMIT);
        String aiSummary = allowAiSync
            ? LorekeeperAiService.getOrCreateWeeklySummary(server, weekNumber, entries)
            : LorekeeperAiService.getOrCreateWeeklySummaryNonBlocking(server, weekNumber, entries);
        if (!alreadyPublished && announce) {
            String title = LoreBooks.NEWS_TITLE_PREFIX + weekNumber;
            server.getPlayerManager().broadcast(
                Text.literal("A new issue is available: " + title + "."),
                false
            );
        }
        return new PublishResult(weekNumber, entries, aiSummary, alreadyPublished);
    }

    public record PublishResult(
        long weekNumber,
        List<LoreStorage.LoreEntry> entries,
        String aiSummary,
        boolean alreadyPublished
    ) {}
}
