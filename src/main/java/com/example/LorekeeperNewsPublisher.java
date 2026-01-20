package com.example;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public final class LorekeeperNewsPublisher {
    private LorekeeperNewsPublisher() {}

    public static PublishResult publishWeeklyNews(MinecraftServer server, boolean announce) {
        LoreStorage storage = LoreStorage.get(server);
        long weekNumber = LoreStorage.getCurrentWeek(server);
        boolean alreadyPublished = storage.hasNewsSnapshot(weekNumber);
        List<LoreStorage.LoreEntry> entries =
            storage.getOrCreateNewsSnapshot(weekNumber, LoreBooks.NEWS_ENTRY_LIMIT);
        if (!alreadyPublished && announce) {
            String title = LoreBooks.NEWS_TITLE_PREFIX + weekNumber;
            server.getPlayerManager().broadcast(
                Text.literal("A new issue is available: " + title + "."),
                false
            );
        }
        return new PublishResult(weekNumber, entries, alreadyPublished);
    }

    public record PublishResult(long weekNumber, List<LoreStorage.LoreEntry> entries, boolean alreadyPublished) {}
}
