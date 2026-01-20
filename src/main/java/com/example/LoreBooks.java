package com.example;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
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
    public static final String NEWS_TITLE_PREFIX = "Lorekeeper Gazette — Day ";

    private static final DateTimeFormatter NEWS_HEADER_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter NEWS_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private LoreBooks() {}

    public static ItemStack createNewsBook(List<LoreStorage.LoreEntry> entries, long dayNumber) {
        String content = buildNewsText(entries, dayNumber);
        return buildBook(buildNewsTitle(dayNumber), "Lorekeeper", content);
    }

    public static ItemStack createHistoryBook(List<LoreStorage.LoreEntry> entries) {
        String content = buildHistoryText(entries);
        return buildBook("Lorekeeper Archive", "Lorekeeper", content);
    }

    public static String formatNewsHeader(long dayNumber) {
        return "Lorekeeper Gazette — Day " + dayNumber + " (" + NEWS_HEADER_FORMAT.format(Instant.now()) + ")";
    }

    public static String formatEntry(LoreStorage.LoreEntry entry) {
        String stamp = NEWS_TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(entry.timestamp()));
        return "* " + entry.text() + "\n  — " + entry.author() + ", " + stamp;
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
        List<RawFilteredPair<Text>> pages = buildPages(content);
        if (pages.size() > MAX_PAGES) {
            pages = pages.subList(0, MAX_PAGES);
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
        return book;
    }

    private static String buildNewsTitle(long dayNumber) {
        return NEWS_TITLE_PREFIX + dayNumber;
    }

    private static String buildNewsText(List<LoreStorage.LoreEntry> entries, long dayNumber) {
        StringBuilder builder = new StringBuilder();
        builder.append("THE LOREKEEPER GAZETTE").append("\n");
        builder.append("Day ").append(dayNumber).append(" — ")
            .append(NEWS_HEADER_FORMAT.format(Instant.now())).append("\n");
        builder.append("----------------------------------------").append("\n\n");
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

    private static String buildHistoryText(List<LoreStorage.LoreEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append("THE LOREKEEPER ARCHIVE").append("\n");
        builder.append("Compiled ").append(NEWS_HEADER_FORMAT.format(Instant.now())).append("\n");
        builder.append("----------------------------------------").append("\n\n");
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
}
