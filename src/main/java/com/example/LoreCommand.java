package com.example;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import java.util.List;
import net.minecraft.server.command.CommandManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class LoreCommand {

    private LoreCommand() {}

    private static final int MAX_ENTRY_LENGTH = 280;
    private static final int DEFAULT_LIST_COUNT = 10;
    private static final int MAX_LIST_COUNT = 100;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("lore")
                    .then(CommandManager.literal("log")
                        .then(CommandManager.argument("text", StringArgumentType.greedyString())
                            .executes(LoreCommand::logLore)
                        )
                    )
                    .then(CommandManager.literal("news")
                        .executes(LoreCommand::sendNews)
                    )
                    .then(CommandManager.literal("archive")
                        .executes(LoreCommand::sendArchive)
                        .then(CommandManager.literal("page")
                            .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> listArchivePage(ctx, IntegerArgumentType.getInteger(ctx, "page"), DEFAULT_LIST_COUNT))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, MAX_LIST_COUNT))
                                    .executes(ctx -> listArchivePage(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "page"),
                                        IntegerArgumentType.getInteger(ctx, "count")
                                    ))
                                )
                            )
                        )
                    )
                    .then(CommandManager.literal("interview")
                        .then(CommandManager.literal("accept")
                            .executes(LoreCommand::acceptInterview)
                        )
                        .then(CommandManager.literal("decline")
                            .executes(LoreCommand::declineInterview)
                        )
                        .then(CommandManager.literal("stop")
                            .executes(LoreCommand::stopInterview)
                        )
                        .then(CommandManager.literal("optout")
                            .then(CommandManager.literal("on")
                                .executes(ctx -> setInterviewOptOut(ctx, true))
                            )
                            .then(CommandManager.literal("off")
                                .executes(ctx -> setInterviewOptOut(ctx, false))
                            )
                        )
                    )
                    .then(CommandManager.literal("admin")
                        .requires(LoreCommand::hasAdminPermission)
                        .then(CommandManager.literal("list")
                            .executes(LoreCommand::listLore)
                            .then(CommandManager.argument("count", IntegerArgumentType.integer(1, MAX_LIST_COUNT))
                                .executes(LoreCommand::listLoreWithCount)
                            )
                            .then(CommandManager.literal("page")
                                .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                    .executes(ctx -> listLorePage(
                                        ctx,
                                        IntegerArgumentType.getInteger(ctx, "page"),
                                        DEFAULT_LIST_COUNT
                                    ))
                                    .then(CommandManager.argument("count", IntegerArgumentType.integer(1, MAX_LIST_COUNT))
                                        .executes(ctx -> listLorePage(
                                            ctx,
                                            IntegerArgumentType.getInteger(ctx, "page"),
                                            IntegerArgumentType.getInteger(ctx, "count")
                                        ))
                                    )
                                )
                            )
                        )
                        .then(CommandManager.literal("search")
                            .then(CommandManager.argument("query", StringArgumentType.string())
                                .executes(ctx -> searchLore(
                                    ctx,
                                    StringArgumentType.getString(ctx, "query"),
                                    DEFAULT_LIST_COUNT,
                                    1
                                ))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, MAX_LIST_COUNT))
                                    .executes(ctx -> searchLore(
                                        ctx,
                                        StringArgumentType.getString(ctx, "query"),
                                        IntegerArgumentType.getInteger(ctx, "count"),
                                        1
                                    ))
                                    .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                        .executes(ctx -> searchLore(
                                            ctx,
                                            StringArgumentType.getString(ctx, "query"),
                                            IntegerArgumentType.getInteger(ctx, "count"),
                                            IntegerArgumentType.getInteger(ctx, "page")
                                        ))
                                    )
                                )
                            )
                        )
                        .then(CommandManager.literal("delete")
                            .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .executes(LoreCommand::deleteLore)
                            )
                        )
                        .then(CommandManager.literal("clear")
                            .executes(LoreCommand::promptClearLore)
                            .then(CommandManager.literal("confirm")
                                .executes(LoreCommand::clearLore)
                            )
                        )
                        .then(CommandManager.literal("news")
                            .then(CommandManager.literal("publish")
                                .executes(LoreCommand::publishNews)
                            )
                            .then(CommandManager.literal("preview")
                                .executes(LoreCommand::previewNews)
                            )
                        )
                        .then(CommandManager.literal("reload")
                            .executes(LoreCommand::reloadConfig)
                        )
                    )
            );
        });
    }

    private static int logLore(CommandContext<ServerCommandSource> ctx) {
        String text = StringArgumentType.getString(ctx, "text").trim();
        if (text.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Lore cannot be empty."));
            return 0;
        }
        if (text.length() > MAX_ENTRY_LENGTH) {
            ctx.getSource().sendError(Text.literal("Lore is too long (max " + MAX_ENTRY_LENGTH + " chars)."));
            return 0;
        }

        String author = ctx.getSource().getName();
        long timestamp = System.currentTimeMillis();
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null && player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            LoreStorage.LoreEntry entry = LoreStorage.buildEntry(
                text,
                author,
                timestamp,
                serverWorld,
                player.getBlockPos(),
                LoreStorage.DEFAULT_EVENT_TYPE,
                List.of("manual")
            );
            storage.addEntry(entry);
        } else {
            storage.addEntryText(text, author, timestamp);
        }

        LorekeeperMod.LOGGER.info("[LORE] {} - {}", author, text);
        ctx.getSource().sendFeedback(() -> Text.literal("Lore recorded."), false);
        return 1;
    }

    private static int sendNews(CommandContext<ServerCommandSource> ctx) {
        LorekeeperNewsPublisher.PublishResult result =
            LorekeeperNewsPublisher.publishWeeklyNews(ctx.getSource().getServer(), true);
        long weekNumber = result.weekNumber();
        List<LoreStorage.LoreEntry> entries = result.entries();
        String aiSummary = result.aiSummary();
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            LoreBooks.BookResult bookResult = LoreBooks.createNewsBookResult(entries, weekNumber, aiSummary);
            if (!player.giveItemStack(bookResult.book())) {
                ctx.getSource().sendError(Text.literal("Inventory full; couldn't deliver the news book."));
                return 0;
            }
            ctx.getSource().sendFeedback(() -> Text.literal("A news book has been delivered."), false);
            warnIfTruncated(ctx.getSource(), bookResult, "News book");
        } else {
            String header = LoreBooks.formatNewsHeader(weekNumber);
            ctx.getSource().sendFeedback(() -> Text.literal(header), false);
            if (aiSummary != null && !aiSummary.isBlank()) {
                ctx.getSource().sendFeedback(() -> Text.literal(aiSummary), false);
            } else {
                for (LoreStorage.LoreEntry entry : entries) {
                    ctx.getSource().sendFeedback(() -> Text.literal(LoreBooks.formatEntry(entry)), false);
                }
            }
        }

        return entries.size();
    }

    private static int acceptInterview(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can accept interviews."));
            return 0;
        }
        return LorekeeperInterviewManager.accept(player) ? 1 : 0;
    }

    private static int declineInterview(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can decline interviews."));
            return 0;
        }
        LorekeeperInterviewManager.decline(player);
        return 1;
    }

    private static int stopInterview(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can stop interviews."));
            return 0;
        }
        if (!LorekeeperInterviewManager.stopInterview(player)) {
            ctx.getSource().sendFeedback(() -> Text.literal("There is no active interview to stop."), false);
            return 0;
        }
        return 1;
    }

    private static int setInterviewOptOut(CommandContext<ServerCommandSource> ctx, boolean optOut) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can change interview settings."));
            return 0;
        }
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        if (config != null && !config.interviewAllowOptOut) {
            ctx.getSource().sendError(Text.literal("Interview opt-out is disabled on this server."));
            return 0;
        }
        LorekeeperPlayerData data = LorekeeperPlayerData.get(ctx.getSource().getServer());
        data.setInterviewOptOut(player.getUuid(), optOut);
        if (optOut) {
            LorekeeperInterviewManager.stopInterview(player);
            LorekeeperInterviewManager.clearPending(player);
            ctx.getSource().sendFeedback(() -> Text.literal("You will no longer receive interview requests."), false);
        } else {
            ctx.getSource().sendFeedback(() -> Text.literal("You can receive interview requests again."), false);
        }
        return 1;
    }

    private static boolean hasAdminPermission(ServerCommandSource source) {
        return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.fromLevel(2)));
    }

    private static int listLore(CommandContext<ServerCommandSource> ctx) {
        return listLore(ctx, DEFAULT_LIST_COUNT);
    }

    private static int listLoreWithCount(CommandContext<ServerCommandSource> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        return listLore(ctx, count);
    }

    private static int listLore(CommandContext<ServerCommandSource> ctx, int count) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        List<LoreStorage.LoreEntry> entries = storage.getAllEntries();
        if (entries.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("No lore entries recorded."), false);
            return 0;
        }
        int total = entries.size();
        int start = Math.max(total - count, 0);
        ctx.getSource().sendFeedback(
            () -> Text.literal("Showing " + (total - start) + " of " + total + " lore entries (newest first)."),
            false
        );
        for (int i = total - 1; i >= start; i--) {
            int id = i + 1;
            String line = "#" + id + " " + LoreBooks.formatEntry(entries.get(i)).replace('\n', ' ');
            ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        return total - start;
    }

    private static int deleteLore(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        int index = id - 1;
        LoreStorage.LoreEntry removed = storage.removeEntryAt(index);
        if (removed == null) {
            ctx.getSource().sendError(Text.literal("Lore id out of range (1-" + storage.getEntryCount() + ")."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Deleted lore #" + id + "."), false);
        return 1;
    }

    private static int promptClearLore(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendError(Text.literal("This will delete all lore. Run /lore admin clear confirm to proceed."));
        return 0;
    }

    private static int clearLore(CommandContext<ServerCommandSource> ctx) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        int count = storage.getEntryCount();
        storage.clearAll();
        ctx.getSource().sendFeedback(
            () -> Text.literal("Cleared " + count + " lore entries and all weekly news snapshots."),
            false
        );
        return count;
    }

    private static int publishNews(CommandContext<ServerCommandSource> ctx) {
        LorekeeperNewsPublisher.PublishResult result =
            LorekeeperNewsPublisher.publishWeeklyNews(ctx.getSource().getServer(), true);
        if (result.alreadyPublished()) {
            ctx.getSource().sendFeedback(
                () -> Text.literal("News for week " + result.weekNumber() + " was already published."),
                false
            );
        } else {
            ctx.getSource().sendFeedback(
                () -> Text.literal("Published news for week " + result.weekNumber() + "."),
                false
            );
        }
        return result.entries().size();
    }

    private static int previewNews(CommandContext<ServerCommandSource> ctx) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        long weekNumber = LoreStorage.getCurrentWeek(ctx.getSource().getServer());
        List<LoreStorage.LoreEntry> entries = storage.getNewsSnapshot(weekNumber);
        boolean published = entries != null;
        String aiSummary = storage.getAiNewsSummary(weekNumber);
        if (entries == null) {
            entries = storage.getLatestEntries(LoreBooks.NEWS_ENTRY_LIMIT);
        }

        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            LoreBooks.BookResult bookResult = LoreBooks.createNewsBookResult(entries, weekNumber, aiSummary);
            if (!player.giveItemStack(bookResult.book())) {
                ctx.getSource().sendError(Text.literal("Inventory full; couldn't deliver the preview book."));
                return 0;
            }
            String label = published ? "Published news preview delivered." : "Preview book delivered (not yet published).";
            ctx.getSource().sendFeedback(() -> Text.literal(label), false);
            warnIfTruncated(ctx.getSource(), bookResult, "News preview");
        } else {
            String header = LoreBooks.formatNewsHeader(weekNumber);
            String suffix = published ? " (published)" : " (preview)";
            ctx.getSource().sendFeedback(() -> Text.literal(header + suffix), false);
            if (aiSummary != null && !aiSummary.isBlank()) {
                ctx.getSource().sendFeedback(() -> Text.literal(aiSummary), false);
            } else {
                for (LoreStorage.LoreEntry entry : entries) {
                    ctx.getSource().sendFeedback(() -> Text.literal(LoreBooks.formatEntry(entry)), false);
                }
            }
        }

        return entries.size();
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> ctx) {
        LorekeeperMod.CONFIG = LorekeeperConfig.load();
        ctx.getSource().sendFeedback(() -> Text.literal("Lorekeeper config reloaded."), false);
        return 1;
    }

    private static int sendArchive(CommandContext<ServerCommandSource> ctx) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        List<LoreStorage.LoreEntry> entries = storage.getAllEntries();
        String aiSummary = LorekeeperAiService.getOrCreateHistorySummary(ctx.getSource().getServer(), entries);
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            LoreBooks.BookResult result = LoreBooks.createHistoryBookResult(entries, aiSummary);
            if (!player.giveItemStack(result.book())) {
                ctx.getSource().sendError(Text.literal("Inventory full; couldn't deliver the archive book."));
                return 0;
            }
            ctx.getSource().sendFeedback(() -> Text.literal("The archive has been delivered."), false);
            warnIfTruncated(ctx.getSource(), result, "Archive book");
        } else {
            ctx.getSource().sendFeedback(() -> Text.literal("Lorekeeper Archive"), false);
            if (aiSummary != null && !aiSummary.isBlank()) {
                ctx.getSource().sendFeedback(() -> Text.literal(aiSummary), false);
            } else {
                for (LoreStorage.LoreEntry entry : entries) {
                    ctx.getSource().sendFeedback(() -> Text.literal(LoreBooks.formatEntry(entry)), false);
                }
            }
        }
        return entries.size();
    }

    private static int listArchivePage(CommandContext<ServerCommandSource> ctx, int page, int count) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        return listEntriesPage(ctx.getSource(), storage.getAllEntries(), page, count, "Archive");
    }

    private static int listLorePage(CommandContext<ServerCommandSource> ctx, int page, int count) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        return listEntriesPage(ctx.getSource(), storage.getAllEntries(), page, count, "Lore entries");
    }

    private static int searchLore(CommandContext<ServerCommandSource> ctx, String query, int count, int page) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        List<LoreStorage.LoreEntry> entries = storage.getAllEntries();
        List<LoreStorage.LoreEntry> results = filterEntries(entries, query);
        return listEntriesPage(ctx.getSource(), results, page, count, "Search results for \"" + query + "\"");
    }

    private static List<LoreStorage.LoreEntry> filterEntries(List<LoreStorage.LoreEntry> entries, String query) {
        String needle = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        if (needle.isBlank()) {
            return entries;
        }
        List<LoreStorage.LoreEntry> matches = new java.util.ArrayList<>();
        for (LoreStorage.LoreEntry entry : entries) {
            if (matchesQuery(entry, needle)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private static boolean matchesQuery(LoreStorage.LoreEntry entry, String needle) {
        if (entry.text().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (entry.author().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (entry.dimension() != null && entry.dimension().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (entry.eventType() != null && entry.eventType().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
            return true;
        }
        if (entry.tags() != null) {
            for (String tag : entry.tags()) {
                if (tag != null && tag.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int listEntriesPage(
        ServerCommandSource source,
        List<LoreStorage.LoreEntry> entries,
        int page,
        int count,
        String label
    ) {
        if (entries.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No lore entries recorded."), false);
            return 0;
        }
        int total = entries.size();
        int pageSize = Math.max(count, 1);
        int totalPages = Math.max((int) Math.ceil(total / (double) pageSize), 1);
        int pageIndex = Math.min(Math.max(page, 1), totalPages);
        int endExclusive = total - (pageIndex - 1) * pageSize;
        int start = Math.max(endExclusive - pageSize, 0);

        int shown = endExclusive - start;
        String header = label + " — page " + pageIndex + "/" + totalPages + " (newest first)";
        source.sendFeedback(() -> Text.literal(header), false);
        for (int i = endExclusive - 1; i >= start; i--) {
            String line = "#" + (i + 1) + " " + LoreBooks.formatEntry(entries.get(i)).replace('\n', ' ');
            source.sendFeedback(() -> Text.literal(line), false);
        }
        return shown;
    }

    private static void warnIfTruncated(ServerCommandSource source, LoreBooks.BookResult result, String label) {
        if (!result.truncated()) {
            return;
        }
        source.sendFeedback(
            () -> Text.literal(label + " truncated to " + result.maxPages() + " pages."),
            false
        );
    }
}
