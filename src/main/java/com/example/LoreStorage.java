package com.example;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class LoreStorage extends PersistentState {
    public static final int MAX_ENTRIES = 1000;

    private static final Codec<LoreEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("text").forGetter(LoreEntry::text),
        Codec.STRING.fieldOf("author").forGetter(LoreEntry::author),
        Codec.LONG.fieldOf("timestamp").forGetter(LoreEntry::timestamp)
    ).apply(instance, LoreEntry::new));

    private static final Codec<DailyNews> DAILY_NEWS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.LONG.fieldOf("day").forGetter(DailyNews::day),
        ENTRY_CODEC.listOf().fieldOf("entries").forGetter(DailyNews::entries)
    ).apply(instance, DailyNews::new));

    private static final Codec<LoreStorage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ENTRY_CODEC.listOf().fieldOf("entries").forGetter(LoreStorage::getEntries),
        DAILY_NEWS_CODEC.listOf().optionalFieldOf("news_by_day", List.of()).forGetter(LoreStorage::getNewsList),
        Codec.INT.optionalFieldOf("issue", 0).forGetter(LoreStorage::getIssueCounter)
    ).apply(instance, LoreStorage::new));

    public static final PersistentStateType<LoreStorage> STATE_TYPE = new PersistentStateType<>(
        "lorekeeper_lore",
        LoreStorage::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final List<LoreEntry> entries;
    private final Map<Long, List<LoreEntry>> newsByDay;
    private int issueCounter;

    public LoreStorage() {
        this(new ArrayList<>(), List.of(), 0);
    }

    public LoreStorage(List<LoreEntry> entries, List<DailyNews> newsByDay, int issueCounter) {
        this.entries = new ArrayList<>(entries);
        this.newsByDay = new LinkedHashMap<>();
        for (DailyNews news : newsByDay) {
            this.newsByDay.put(news.day, new ArrayList<>(news.entries));
        }
        this.issueCounter = issueCounter;
    }

    public static LoreStorage get(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        return world.getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public static long getCurrentDay(MinecraftServer server) {
        return server.getOverworld().getTimeOfDay() / 24000L;
    }

    public static long getCurrentWeek(MinecraftServer server) {
        return getCurrentDay(server) / 7L;
    }

    public void addEntry(LoreEntry entry) {
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        markDirty();
    }

    public void addEntryText(String text, String author, long timestamp) {
        addEntry(new LoreEntry(text, author, timestamp));
    }

    public void addEntryTextChunked(String text, String author, long timestamp, int maxChunkLength) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return;
        }
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxChunkLength, normalized.length());
            if (end < normalized.length()) {
                int split = findSplitPoint(normalized, start, end);
                if (split > start) {
                    end = split;
                }
            }
            addEntry(new LoreEntry(normalized.substring(start, end).trim(), author, timestamp));
            start = end;
        }
    }

    public List<LoreEntry> getLatestEntries(int limit) {
        int start = Math.max(entries.size() - limit, 0);
        return new ArrayList<>(entries.subList(start, entries.size()));
    }

    public List<LoreEntry> getOrCreateNewsSnapshot(long day, int limit) {
        List<LoreEntry> existing = newsByDay.get(day);
        if (existing != null) {
            return new ArrayList<>(existing);
        }
        List<LoreEntry> snapshot = getLatestEntries(limit);
        newsByDay.put(day, new ArrayList<>(snapshot));
        markDirty();
        return new ArrayList<>(snapshot);
    }

    public List<LoreEntry> getNewsSnapshot(long day) {
        List<LoreEntry> existing = newsByDay.get(day);
        return existing == null ? null : new ArrayList<>(existing);
    }

    public boolean hasNewsSnapshot(long day) {
        return newsByDay.containsKey(day);
    }

    public List<LoreEntry> getAllEntries() {
        return new ArrayList<>(entries);
    }

    public int getEntryCount() {
        return entries.size();
    }

    public LoreEntry removeEntryAt(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        LoreEntry removed = entries.remove(index);
        for (List<LoreEntry> snapshot : newsByDay.values()) {
            snapshot.removeIf(entry -> entry.equals(removed));
        }
        markDirty();
        return removed;
    }

    public void clearAll() {
        entries.clear();
        newsByDay.clear();
        issueCounter = 0;
        markDirty();
    }

    private List<LoreEntry> getEntries() {
        return entries;
    }

    private List<DailyNews> getNewsList() {
        List<DailyNews> list = new ArrayList<>(newsByDay.size());
        for (Map.Entry<Long, List<LoreEntry>> entry : newsByDay.entrySet()) {
            list.add(new DailyNews(entry.getKey(), new ArrayList<>(entry.getValue())));
        }
        return list;
    }

    private static int findSplitPoint(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return end;
    }

    private int getIssueCounter() {
        return issueCounter;
    }

    public record LoreEntry(String text, String author, long timestamp) {}

    public record DailyNews(long day, List<LoreEntry> entries) {}
}
