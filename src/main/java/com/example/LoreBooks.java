package com.example;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

public final class LoreBooks {
    public static final int NEWS_ENTRY_LIMIT = 10;
    public static final int MAX_LINE_LENGTH = 32;
    public static final int MAX_LINES_PER_PAGE = 14;
    public static final int MAX_PAGES = 100;
    public static final String NEWS_TITLE_PREFIX = "Lorekeeper Gazette — Week ";
    public static final String LOREKEEPER_AUTHOR = "Lorekeeper";
    private static final String LOREKEEPER_MARKER_KEY = "LorekeeperIssued";

    private static final DateTimeFormatter NEWS_HEADER_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter NEWS_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private LoreBooks() {}

    public static ItemStack createNewsBook(List<LoreStorage.LoreEntry> entries, long weekNumber) {
        return createNewsBookResult(entries, weekNumber, null).book();
    }

    public static ItemStack createNewsBook(List<LoreStorage.LoreEntry> entries, long weekNumber, String summary) {
        return createNewsBookResult(entries, weekNumber, summary).book();
    }

    public static BookResult createNewsBookResult(List<LoreStorage.LoreEntry> entries, long weekNumber, String summary) {
        String content = buildNewsText(entries, weekNumber, summary);
        return buildBookResult(buildNewsTitle(weekNumber), LOREKEEPER_AUTHOR, content);
    }

    public static ItemStack createHistoryBook(List<LoreStorage.LoreEntry> entries) {
        return createHistoryBookResult(entries, null).book();
    }

    public static ItemStack createHistoryBook(List<LoreStorage.LoreEntry> entries, String summary) {
        return createHistoryBookResult(entries, summary).book();
    }

    public static BookResult createHistoryBookResult(List<LoreStorage.LoreEntry> entries, String summary) {
        String content = buildHistoryText(entries, summary);
        return buildBookResult("Lorekeeper Archive", LOREKEEPER_AUTHOR, content);
    }

    public static String formatNewsHeader(long weekNumber) {
        return "Lorekeeper Gazette — Week " + weekNumber + " (" + NEWS_HEADER_FORMAT.format(Instant.now()) + ")";
    }

    public static String formatEntry(LoreStorage.LoreEntry entry) {
        String stamp = NEWS_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()));
        StringBuilder builder = new StringBuilder();
        builder.append("* ").append(entry.text()).append("\n  — ").append(entry.author()).append(", ").append(stamp);
        String location = formatLocation(entry);
        if (!location.isEmpty()) {
            builder.append(" (").append(location).append(")");
        }
        if (entry.eventType() != null && !entry.eventType().isBlank()
            && !LoreStorage.DEFAULT_EVENT_TYPE.equals(entry.eventType())) {
            builder.append(" [").append(entry.eventType()).append("]");
        }
        if (entry.tags() != null && !entry.tags().isEmpty()) {
            builder.append(" {").append(String.join(", ", entry.tags())).append("}");
        }
        return builder.toString();
    }

    public static BookSubmission extractWrittenBook(ItemStack stack) {
        if (!stack.isOf(Items.WRITTEN_BOOK)) {
            return null;
        }
        WrittenBookContentComponent content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (content == null) {
            return null;
        }
        String title = content.title().raw();
        String author = content.author();
        StringBuilder text = new StringBuilder();
        List<Text> pages = content.getPages(false);
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(pages.get(i).getString());
        }
        return new BookSubmission(title, author, text.toString());
    }

    private static ItemStack buildBook(String title, String author, String content) {
        return buildBookResult(title, author, content).book();
    }

    private static BookResult buildBookResult(String title, String author, String content) {
        List<RawFilteredPair<Text>> pages = buildPages(content);
        boolean truncated = false;
        if (pages.size() > MAX_PAGES) {
            pages = pages.subList(0, MAX_PAGES);
            truncated = true;
        }
        WrittenBookContentComponent bookContent = new WrittenBookContentComponent(
            RawFilteredPair.of(title),
            author,
            0,
            pages,
            true
        );
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, bookContent);
        markLorekeeperBook(book);
        return new BookResult(book, truncated, pages.size(), MAX_PAGES);
    }

    public static boolean isLorekeeperBook(ItemStack stack) {
        if (!stack.isOf(Items.WRITTEN_BOOK)) {
            return false;
        }
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData != null && customData.copyNbt().getBoolean(LOREKEEPER_MARKER_KEY).orElse(false)) {
            return true;
        }
        WrittenBookContentComponent content = stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
        if (content == null) {
            return false;
        }
        String title = content.title().raw();
        String author = content.author();
        if (LOREKEEPER_AUTHOR.equals(author) && title != null) {
            return title.equals("Lorekeeper Archive") || title.startsWith(NEWS_TITLE_PREFIX);
        }
        return false;
    }

    private static void markLorekeeperBook(ItemStack book) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, book, nbt -> nbt.putBoolean(LOREKEEPER_MARKER_KEY, true));
    }

    private static String buildNewsTitle(long weekNumber) {
        return NEWS_TITLE_PREFIX + weekNumber;
    }

    private static String buildNewsText(List<LoreStorage.LoreEntry> entries, long weekNumber, String summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("THE LOREKEEPER GAZETTE").append("\n");
        builder.append("Week ").append(weekNumber).append(" — ")
            .append(NEWS_HEADER_FORMAT.format(Instant.now())).append("\n");
        builder.append("----------------------------------------").append("\n\n");
        if (summary != null && !summary.isBlank()) {
            builder.append("Weekly Summary").append("\n\n");
            builder.append(summary.trim()).append("\n");
            return builder.toString().trim();
        }
        builder.append("Headlines").append("\n\n");
        if (entries.isEmpty()) {
            builder.append("No lore recorded yet.").append("\n");
            return builder.toString().trim();
        }
        for (LoreStorage.LoreEntry entry : entries) {
            builder.append(formatEntry(entry)).append("\n\n");
        }
        return builder.toString().trim();
    }

    private static String buildHistoryText(List<LoreStorage.LoreEntry> entries, String summary) {
        StringBuilder builder = new StringBuilder();
        builder.append("THE LOREKEEPER ARCHIVE").append("\n");
        builder.append("Compiled ").append(NEWS_HEADER_FORMAT.format(Instant.now())).append("\n");
        builder.append("----------------------------------------").append("\n\n");
        if (summary != null && !summary.isBlank()) {
            builder.append(summary.trim()).append("\n");
            return builder.toString().trim();
        }
        if (entries.isEmpty()) {
            builder.append("No lore recorded yet.").append("\n");
            return builder.toString().trim();
        }
        for (LoreStorage.LoreEntry entry : entries) {
            builder.append(formatEntry(entry)).append("\n\n");
        }
        return builder.toString().trim();
    }

    private static List<RawFilteredPair<Text>> buildPages(String content) {
        List<String> lines = wrapLines(content, MAX_LINE_LENGTH);
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            if (lineCount == MAX_LINES_PER_PAGE) {
                pages.add(RawFilteredPair.of(Text.literal(page.toString())));
                page.setLength(0);
                lineCount = 0;
            }
            if (lineCount > 0) {
                page.append('\n');
            }
            page.append(line);
            lineCount++;
        }

        if (page.length() > 0) {
            pages.add(RawFilteredPair.of(Text.literal(page.toString())));
        }

        return pages;
    }

    private static List<String> wrapLines(String content, int maxLineLength) {
        List<String> lines = new ArrayList<>();
        for (String rawLine : content.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                lines.add("");
                continue;
            }

            StringBuilder current = new StringBuilder();
            for (String word : rawLine.split("\\s+")) {
                if (current.length() == 0) {
                    appendWordWithSplit(lines, current, word, maxLineLength);
                    continue;
                }

                if (current.length() + 1 + word.length() <= maxLineLength) {
                    current.append(' ').append(word);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    appendWordWithSplit(lines, current, word, maxLineLength);
                }
            }

            if (current.length() > 0) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    private static String formatLocation(LoreStorage.LoreEntry entry) {
        String dimension = entry.dimension();
        if (dimension == null || dimension.isBlank()) {
            return "";
        }
        String prettyDimension = prettyDimensionName(dimension);
        return prettyDimension + " @ " + entry.x() + "," + entry.y() + "," + entry.z();
    }

    private static String prettyDimensionName(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "";
        }
        String trimmed = dimension.trim();
        if ("minecraft:overworld".equals(trimmed)) {
            return "Overworld";
        }
        if ("minecraft:the_nether".equals(trimmed)) {
            return "Nether";
        }
        if ("minecraft:the_end".equals(trimmed)) {
            return "The End";
        }
        String name = trimmed;
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && colon + 1 < trimmed.length()) {
            name = trimmed.substring(colon + 1);
        }
        name = name.replace('_', ' ');
        if (name.isEmpty()) {
            return trimmed;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void appendWordWithSplit(
        List<String> lines,
        StringBuilder current,
        String word,
        int maxLineLength
    ) {
        int start = 0;
        while (start < word.length()) {
            int remaining = word.length() - start;
            int take = Math.min(remaining, maxLineLength - current.length());
            if (take <= 0) {
                lines.add(current.toString());
                current.setLength(0);
                take = Math.min(remaining, maxLineLength);
            }
            current.append(word, start, start + take);
            start += take;
            if (start < word.length()) {
                lines.add(current.toString());
                current.setLength(0);
            }
        }
    }

    public record BookSubmission(String title, String author, String content) {}

    public record BookResult(ItemStack book, boolean truncated, int pageCount, int maxPages) {}
}
